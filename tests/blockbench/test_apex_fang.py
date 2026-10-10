"""Checks that the editable Fang source really drives runtime model edits."""
import base64
import binascii
import copy
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("apex_fang_export", ROOT / "scripts/blockbench/apex_fang.py")
fang = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fang)


def painted_png(width=128, height=128):
    """A real, differently painted RGBA image, produced without Pillow."""
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xffffffff)
    scanlines = (b"\x00" + bytes((255, 96, 12, 255)) * width) * height
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(scanlines)) + chunk(b"IEND", b""))


class ApexFangExportTest(unittest.TestCase):
    def setUp(self):
        self.project = fang.load_project(fang.DEFAULT_PROJECT)

    def test_authoritative_source_matches_runtime_model_and_embedded_texture(self):
        model = fang.export_model(self.project)
        expected = json.loads(fang.DEFAULT_MODEL.read_text(encoding="utf-8"))
        self.assertEqual(expected, model)
        self.assertEqual(56, len(model["elements"]))
        self.assertEqual(9, sum("forge_data" in cube for cube in model["elements"]))
        self.assertEqual(8, len(model["display"]))
        self.assertEqual(fang.DEFAULT_TEXTURE.read_bytes(), fang.exported_textures(self.project)["item/apex_fang.png"])

    def test_cube_uv_pivot_and_display_edits_are_exported_without_old_json_template(self):
        cube = self.project["elements"][0]
        cube["from"][0] = 6.5
        cube["to"][0] = 9.5
        cube["rotation"] = [0, 22.5, 0]
        cube["origin"] = [8, -7, 8]
        cube["rescale"] = True
        cube["faces"]["north"]["uv"] = [33, 1, 63, 31]
        cube["faces"]["north"]["rotation"] = 90
        self.project["display"]["firstperson_righthand"]["translation"] = [2, 3, 1]
        model = fang.export_model(self.project)
        exported = model["elements"][0]
        self.assertEqual(6.5, exported["from"][0])
        self.assertEqual(9.5, exported["to"][0])
        self.assertEqual({"angle": 22.5, "axis": "y", "origin": [8, -7, 8], "rescale": True}, exported["rotation"])
        self.assertEqual([4.125, .125, 7.875, 3.875], exported["faces"]["north"]["uv"])
        self.assertEqual(90, exported["faces"]["north"]["rotation"])
        self.assertEqual([2, 3, 1], model["display"]["firstperson_righthand"]["translation"])

    def test_native_saved_wrapped_display_angles_keep_same_runtime_orientation(self):
        expected = fang.export_model(self.project)
        # Observed in Blockbench 5.2.2's native Save Project: +180 -> -180.
        self.project["display"]["head"]["rotation"][1] = -180
        # Whole turns in an edited display pose are likewise equivalent.
        rotation = self.project["display"]["firstperson_righthand"]["rotation"]
        self.project["display"]["firstperson_righthand"]["rotation"] = [angle + 360 for angle in rotation]
        self.assertEqual(expected, fang.export_model(self.project))
        self.assertEqual([0, 180, 0], expected["display"]["head"]["rotation"])
        self.assertEqual(30.1, fang.canonical_angle(30.1))

    def test_emitting_group_handles_added_accents_and_preserves_native_save_properties(self):
        cube = copy.deepcopy(self.project["elements"][0])
        cube.update(uuid="new-accent", name="new_thin_orange_line", shade=True)
        self.project["elements"].append(cube)
        accent = next(group for group in self.project["groups"] if group["name"] == "accent_left")

        def add_to(nodes):
            for node in nodes:
                if isinstance(node, dict):
                    if node["uuid"] == accent["uuid"]:
                        node["children"].append(cube["uuid"])
                        return True
                    if add_to(node["children"]):
                        return True
            return False

        self.assertTrue(add_to(self.project["outliner"]))
        # The exporter needs only native saved groups, not unknown forge fields.
        self.assertNotIn("forge_data", cube)
        model = fang.export_model(self.project)
        self.assertEqual(10, sum("forge_data" in element for element in model["elements"]))
        self.assertFalse(model["elements"][-1]["shade"])
        self.assertEqual({"block_light": 15, "sky_light": 15, "ambient_occlusion": False}, model["elements"][-1]["forge_data"])

    def test_painted_embedded_bitmap_is_written_to_actual_resource_path(self):
        bitmap = painted_png()
        self.project["textures"][0]["source"] = "data:image/png;base64," + base64.b64encode(bitmap).decode()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            project_file = root / "edited.bbmodel"
            project_file.write_text(json.dumps(self.project), encoding="utf-8")
            self.assertEqual(0, fang.main(["export", "--project", str(project_file), "--output", str(root / "edited.json"), "--textures-dir", str(root / "textures")]))
            self.assertEqual(bitmap, (root / "textures/item/apex_fang.png").read_bytes())
            self.assertNotEqual(fang.DEFAULT_TEXTURE.read_bytes(), bitmap)

    def test_unsupported_item_transform_and_missing_emitting_group_fail_explicitly(self):
        cases = []
        project = copy.deepcopy(self.project)
        project["elements"][0]["rotation"] = [22.5, 22.5, 0]
        cases.append(project)
        project = copy.deepcopy(self.project)
        project["groups"][0]["rotation"] = [0, 30, 0]
        cases.append(project)
        project = copy.deepcopy(self.project)
        next(group for group in project["groups"] if group["name"] == "core")["name"] = "renamed_core"
        cases.append(project)
        for project in cases:
            with self.subTest(project=project["elements"][0]["name"]):
                with self.assertRaises(fang.ProjectError):
                    fang.export_model(project)

    def test_truncated_or_bad_checksum_bitmap_is_rejected(self):
        bitmap = bytearray(painted_png())
        bitmap[20] ^= 1
        for corrupt in (bytes(bitmap), painted_png()[:-10]):
            texture = copy.deepcopy(self.project["textures"][0])
            texture["source"] = "data:image/png;base64," + base64.b64encode(corrupt).decode()
            with self.assertRaises(fang.ProjectError):
                fang.png_bytes(texture)


if __name__ == "__main__":
    unittest.main()
