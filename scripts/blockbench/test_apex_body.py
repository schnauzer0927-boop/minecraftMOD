"""Regression checks for the editable Apex source and safe geometry-only export."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import apex_body as body


class ApexBodyProjectTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.project = body.read_json(body.PROJECT)
        cls.geometry = body.read_json(body.GEOMETRY)
        cls.animations = body.read_json(body.ANIMATIONS)

    def test_entire_rig_uv_and_all_animation_keyframes_match_runtime(self):
        result = body.check(self.project, self.geometry, self.animations,
                            body.ASSETS / "textures/entity/apex.png",
                            body.ASSETS / "textures/entity/apex_glow.png")
        self.assertEqual(result, {"bones": 28, "cubes": 333, "animations": 37, "keyframes": 5316})

    def test_official_bedrock_coordinate_conventions(self):
        original = self.geometry["minecraft:geometry"][0]["bones"]
        groups = {g["name"]: g for g in self.project["groups"]}
        self.assertEqual(groups["left_shoulder"]["origin"], [-5.2, 31, 0])
        self.assertEqual(groups["right_shoulder"]["origin"], [5.2, 31, 0])
        cubes = {c["name"]: c for c in self.project["elements"]}
        for bone in original:
            for index, source in enumerate(bone.get("cubes", [])):
                target = cubes[f"{bone['name']}_{index + 1:03}"]
                self.assertAlmostEqual(target["from"][0], -(source["origin"][0] + source["size"][0]))
                for axis, sign in enumerate((-1, -1, 1)):
                    self.assertAlmostEqual(target["rotation"][axis], sign * source.get("rotation", [0, 0, 0])[axis])
                top = source["uv"]["up"]
                self.assertEqual(target["faces"]["up"]["uv"], [top["uv"][0] + top["uv_size"][0],
                    top["uv"][1] + top["uv_size"][1], *top["uv"]])

    def test_real_editor_changes_are_exported_without_regeneration(self):
        project = copy.deepcopy(self.project)
        head = next(g for g in project["groups"] if g["name"] == "head")
        head["origin"] = [-0.4, 36.25, -0.7]
        head["rotation"] = [-7, 11, 3]
        cube = next(c for c in project["elements"] if c["name"] == "head_001")
        cube["to"][1] += 0.75
        cube["rotation"] = [-19, 5, 4]
        cube["faces"]["north"]["uv"] = [11, 13, 19, 21]
        cube["faces"]["up"]["uv"] = [23, 29, 17, 19]
        cube["faces"]["north"]["rotation"] = 90
        result = body.export_geometry(project)
        bone = next(b for b in result["minecraft:geometry"][0]["bones"] if b["name"] == "head")
        self.assertEqual(bone["pivot"], [0.4, 36.25, -0.7])
        self.assertEqual(bone["rotation"], [7, -11, 3])
        edited = bone["cubes"][0]
        self.assertEqual(edited["rotation"], [19, -5, 4])
        self.assertEqual(edited["uv"]["north"], {"uv": [11, 13], "uv_size": [8, 8], "uv_rotation": 90})
        self.assertEqual(edited["uv"]["up"], {"uv": [17, 19], "uv_size": [6, 10]})
        source_head = next(b for b in self.geometry["minecraft:geometry"][0]["bones"] if b["name"] == "head")
        self.assertAlmostEqual(edited["size"][1], source_head["cubes"][0]["size"][1] + 0.75)

    def test_native_outliner_reparenting_changes_the_exported_parent(self):
        project = copy.deepcopy(self.project)
        def detach(nodes, identifier):
            for node in nodes:
                if isinstance(node, dict):
                    if node["uuid"] == identifier:
                        nodes.remove(node)
                        return node
                    found = detach(node["children"], identifier)
                    if found:
                        return found
        blade = next(g for g in project["groups"] if g["name"] == "back_blade_left")
        chest = next(g for g in project["groups"] if g["name"] == "chest")
        detached = detach(project["outliner"], blade["uuid"])
        def find(nodes, identifier):
            for node in nodes:
                if isinstance(node, dict):
                    if node["uuid"] == identifier:
                        return node
                    found = find(node["children"], identifier)
                    if found:
                        return found
        find(project["outliner"], chest["uuid"])["children"].append(detached)
        exported = body.export_geometry(project)
        self.assertEqual(next(b for b in exported["minecraft:geometry"][0]["bones"] if b["name"] == "back_blade_left")["parent"], "chest")

    def test_broken_hierarchy_is_rejected_instead_of_silently_losing_cubes(self):
        project = copy.deepcopy(self.project)
        project["elements"].append(copy.deepcopy(project["elements"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            body.export_geometry(project)
        project = copy.deepcopy(self.project)
        project["elements"].pop()
        with self.assertRaisesRegex(ValueError, "Missing element"):
            body.export_geometry(project)

    def test_moved_animation_timing_and_changed_easing_are_detected(self):
        project = copy.deepcopy(self.project)
        frame = next(f for a in project["animations"] for ba in a["animators"].values()
                     for f in ba["keyframes"] if "easing" in f)
        frame["time"] += 0.01
        with self.assertRaises(ValueError):
            body.assert_equivalent(body.animation_timeline(project), body.runtime_timeline(self.animations))
        frame["time"] -= 0.01
        frame["easing"] = "easeOutSine"
        with self.assertRaises(ValueError):
            body.assert_equivalent(body.animation_timeline(project), body.runtime_timeline(self.animations))

    def test_portable_embedded_textures_are_exact_and_no_absolute_paths_are_needed(self):
        for name in ("apex.png", "apex_glow.png"):
            self.assertEqual(body.texture_bytes(self.project, name), (body.ASSETS / "textures/entity" / name).read_bytes())
        self.assertTrue(all("path" not in t and "relative_path" not in t for t in self.project["textures"]))

    def test_cli_explicit_export_includes_textures_and_never_writes_animations(self):
        before = body.ANIMATIONS.read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "apex.geo.json"
            textures = Path(directory) / "textures"
            result = subprocess.run([sys.executable, str(Path(body.__file__)), "export", "--output", str(output),
                                     "--textures-dir", str(textures)], text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            body.assert_equivalent(json.loads(output.read_text()), self.geometry)
            self.assertEqual((textures / "entity/apex.png").read_bytes(), body.texture_bytes(self.project, "apex.png"))
            self.assertEqual((textures / "entity/apex_glow.png").read_bytes(), body.texture_bytes(self.project, "apex_glow.png"))
            rejected = subprocess.run([sys.executable, str(Path(body.__file__)), "export", "--output", str(body.ANIMATIONS)],
                                      text=True, capture_output=True)
            self.assertNotEqual(rejected.returncode, 0)
        self.assertEqual(body.ANIMATIONS.read_bytes(), before)

    def test_explicit_raw_animation_export_preserves_every_clip_value_and_timestamp(self):
        exported = body.export_animations(self.project)
        body.assert_equivalent(exported, self.animations)
        # The native editor grid cannot represent 0.0563, even at its 500 maximum.
        charge = next(c for c in self.project["animations"] if c["name"] == "animation.apex.charge")
        self.assertEqual(charge["snapping"], 500)
        raw_times = [f["time"] for a in charge["animators"].values() for f in a["keyframes"]]
        self.assertIn(0.0563, raw_times)
        exported_times = [float(t) for b in exported["animations"]["animation.apex.charge"]["bones"].values()
                          for c in b.values() if isinstance(c, dict) for t in c]
        self.assertIn(0.0563, exported_times)
        self.assertNotIn(0.056, exported_times)
        # Changing only the UI grid must never change animation export times.
        project = copy.deepcopy(self.project)
        for c in project["animations"]:
            c["snapping"] = 20
        self.assertEqual(body.export_animations(project), exported)

    def test_minimal_head_neck_corrections_export_without_changing_attack_times(self):
        project = copy.deepcopy(self.project)
        clip = project["animations"][0]
        head_id = next(g["uuid"] for g in project["groups"] if g["name"] == "head")
        neck_id = next(g["uuid"] for g in project["groups"] if g["name"] == "neck")
        for identifier in (head_id, neck_id):
            frame = next(f for f in clip["animators"][identifier]["keyframes"] if f["channel"] == "rotation")
            frame["data_points"][0]["x"] = str(float(frame["data_points"][0]["x"]) + 2.5)
        original = body.export_animations(self.project)
        edited = body.export_animations(project)
        for name, output in edited["animations"].items():
            baseline = original["animations"][name]
            self.assertEqual(output["animation_length"], baseline["animation_length"])
            self.assertEqual(output["loop"], baseline["loop"])
            for bone, channels in output["bones"].items():
                for channel, values in channels.items():
                    if isinstance(values, dict):
                        self.assertEqual(values.keys(), baseline["bones"][bone][channel].keys())
                        for time, sample in values.items():
                            self.assertEqual(sample.get("easing"), baseline["bones"][bone][channel][time].get("easing"))
                    if name != clip["name"] or bone not in ("head", "neck") or channel != "rotation":
                        self.assertEqual(values, baseline["bones"][bone][channel])
        self.assertNotEqual(edited["animations"][clip["name"]]["bones"]["head"], original["animations"][clip["name"]]["bones"]["head"])
        self.assertNotEqual(edited["animations"][clip["name"]]["bones"]["neck"], original["animations"][clip["name"]]["bones"]["neck"])

    def test_animation_export_is_explicit_and_runtime_file_is_protected(self):
        before = body.ANIMATIONS.read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "apex.animation.json"
            result = subprocess.run([sys.executable, str(Path(body.__file__)), "export-animations", "--output", str(output)],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            body.assert_equivalent(json.loads(output.read_text()), self.animations)
            for path in (body.ANIMATIONS, body.GEOMETRY, body.PROJECT):
                rejected = subprocess.run([sys.executable, str(Path(body.__file__)), "export-animations", "--output", str(path)],
                                          capture_output=True, text=True)
                self.assertNotEqual(rejected.returncode, 0)
            missing = subprocess.run([sys.executable, str(Path(body.__file__)), "export-animations"], capture_output=True, text=True)
            self.assertNotEqual(missing.returncode, 0)
        self.assertEqual(body.ANIMATIONS.read_bytes(), before)

    def test_native_ui_snapping_is_valid_and_exact_where_editor_grid_can_represent_it(self):
        for clip in self.project["animations"]:
            self.assertGreaterEqual(clip["snapping"], 10)
            self.assertLessEqual(clip["snapping"], 500)
            if clip["name"] == "animation.apex.charge":
                continue
            for animator in clip["animators"].values():
                for frame in animator["keyframes"]:
                    cells = frame["time"] * clip["snapping"]
                    self.assertAlmostEqual(cells, round(cells), places=8)

    def test_legacy_import_and_generator_cannot_replace_authoritative_model(self):
        files = [body.PROJECT, body.GEOMETRY, body.ASSETS / "textures/entity/apex.png", body.ASSETS / "textures/entity/apex_glow.png"]
        hashes = {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
        importer = subprocess.run([sys.executable, str(Path(body.__file__)), "import-legacy"], capture_output=True, text=True)
        self.assertNotEqual(importer.returncode, 0)
        self.assertIn("already exists", importer.stderr)
        generator = subprocess.run([sys.executable, str(body.ROOT / "scripts/art/generate_apex_model.py")], capture_output=True, text=True)
        self.assertNotEqual(generator.returncode, 0)
        self.assertIn("--legacy-overwrite-model", generator.stderr)
        self.assertEqual(hashes, {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in files})


if __name__ == "__main__":
    unittest.main()
