"""Reference-model QA using actual cube corners, joints and the unchanged rig.

Run with Python 3 only: python scripts/blockbench/test_apex_reference.py -v.
The dimensional tests catch regressions in the authored model; they do not
replace the front/side/back image comparison or Minecraft animation inspection.
"""
import copy
import hashlib
import itertools
import json
import math
import unittest

import apex_body as body


PARENTS = {
    "root": None, "body": "root", "waist": "body", "chest": "body", "neck": "chest",
    "head": "neck", "face_plate": "head", "eye_glow": "head", "back_spines": "chest",
    "back_blade_left": "back_spines", "back_blade_right": "back_spines", "tail_or_rear_spine": "waist",
    **{f"{side}_{part}": f"{side}_{parent}" if parent else anchor for side in ("left", "right")
       for part, parent, anchor in (("shoulder", None, "chest"), ("arm", "shoulder", None),
                                   ("forearm", "arm", None), ("blade", "forearm", None),
                                   ("hand", "forearm", None), ("leg", None, "waist"),
                                   ("lower_leg", "leg", None), ("foot", "lower_leg", None))},
}
ANIMATION_SHA256 = "c12bf23e69775ab74fa4af038fe0611391a312743bdc3c66bb9b07c771b54a16"
TIMING_SHA256 = "3d8af3a277a99812669185753150db7fa0a9f2b0c31c0018b5f4aa88fe966525"
PROTECTED_X_SHA256 = "4e148195aaeb14f0b0d4ec25feb86781534e46206e9d12bc965bb25003656821"
UNCHANGED_CLIPS_SHA256 = "11c2c5538788c31c8c4cce92041d6ece0b7d2964dda16ecfeddcc103dc0402d9"
CORRECTED_CLIPS = {"animation.apex.charge", "animation.apex.charge_prepare"}


def canonical_digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("ascii")).hexdigest()


def animation_contract_fingerprints(animations):
    """Baseline guards permit only X-angle values in four approved channels.

    A fresh runtime_timeline is generated for each signature. Replacing an
    allowed vector with a new list avoids mutating the runtime animation input.
    This canonical JSON encoding is also used by the editor ZIP verifier.
    """
    timing = body.runtime_timeline(animations)
    for clip in timing.values():
        for channels in clip["bones"].values():
            for frames in channels.values():
                for sample in frames.values():
                    del sample["vector"]
    protected = body.runtime_timeline(animations)
    for name in CORRECTED_CLIPS:
        for bone in ("chest", "neck"):
            for sample in protected[name]["bones"][bone]["rotation"].values():
                sample["vector"] = [None, *sample["vector"][1:]]
    unchanged = {name: clip for name, clip in body.runtime_timeline(animations).items() if name not in CORRECTED_CLIPS}
    return canonical_digest(timing), canonical_digest(protected), canonical_digest(unchanged)


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(len(b))) for j in range(len(b[0]))] for i in range(len(a))]


def author_rotation(value):
    """GeckoLib author coordinates: Rz(-Z) * Ry(Y) * Rx(-X)."""
    x, y, z = map(math.radians, (-value[0], value[1], -value[2]))
    sx, cx, sy, cy, sz, cz = math.sin(x), math.cos(x), math.sin(y), math.cos(y), math.sin(z), math.cos(z)
    rx, ry, rz = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]], [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return multiply(multiply(rz, ry), rx)


def transform(matrix, point):
    return [sum(matrix[row][i] * point[i] for i in range(3)) + matrix[row][3] for row in range(3)]


def bone_matrices(geometry, pose=None):
    bones = {b["name"]: b for b in geometry["minecraft:geometry"][0]["bones"]}
    pose, matrices = pose or {}, {}
    def calculate(name):
        if name in matrices:
            return matrices[name]
        bone, sample = bones[name], pose.get(name, {})
        pivot = bone["pivot"]
        rotation = author_rotation([bone.get("rotation", [0, 0, 0])[i] + sample.get("rotation", [0, 0, 0])[i] for i in range(3)])
        scale, position = sample.get("scale", [1, 1, 1]), sample.get("position", [0, 0, 0])
        rotation = [[rotation[i][j] * scale[j] for j in range(3)] for i in range(3)]
        local = [rotation[i] + [position[i] + pivot[i] - sum(rotation[i][j] * pivot[j] for j in range(3))] for i in range(3)] + [[0, 0, 0, 1]]
        matrices[name] = multiply(calculate(bone["parent"]), local) if "parent" in bone else local
        return matrices[name]
    for name in bones:
        calculate(name)
    return matrices


def cube_corners(cube, matrix):
    pivot, inflate = cube.get("pivot", [0, 0, 0]), cube.get("inflate", 0)
    rotation = author_rotation(cube.get("rotation", [0, 0, 0]))
    for corner in itertools.product((0, 1), repeat=3):
        point = [cube["origin"][i] - inflate + corner[i] * (cube["size"][i] + 2 * inflate) - pivot[i] for i in range(3)]
        point = [sum(rotation[i][j] * point[j] for j in range(3)) + pivot[i] for i in range(3)]
        yield transform(matrix, point)


def model_points(geometry, pose=None, names=None):
    matrices = bone_matrices(geometry, pose)
    return [point for b in geometry["minecraft:geometry"][0]["bones"] if names is None or b["name"] in names
            for c in b.get("cubes", []) for point in cube_corners(c, matrices[b["name"]])]


def bounds(points):
    return [min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]


def horizontal_section(geometry, height, names):
    """Exact cube-edge/plane intersections; excludes arms and rear appendages."""
    matrices = bone_matrices(geometry)
    intersections = []
    for bone in geometry["minecraft:geometry"][0]["bones"]:
        if bone["name"] not in names:
            continue
        for cube in bone.get("cubes", []):
            corners = list(cube_corners(cube, matrices[bone["name"]]))
            for i, before in enumerate(corners):
                for bit in (1, 2, 4):
                    j = i ^ bit
                    if j < i:
                        continue
                    after = corners[j]
                    if min(before[1], after[1]) - 1e-9 <= height <= max(before[1], after[1]) + 1e-9:
                        if abs(after[1] - before[1]) < 1e-9:
                            if abs(height - before[1]) < 1e-9:
                                intersections.extend((before, after))
                        else:
                            fraction = (height - before[1]) / (after[1] - before[1])
                            intersections.append([before[axis] + fraction * (after[axis] - before[axis]) for axis in range(3)])
    if not intersections:
        raise ValueError("No torso armor intersects horizontal plane y=" + str(height))
    low, high = bounds(intersections)
    return {"y": height, "width": high[0] - low[0], "minimum": low, "maximum": high}


def section_extreme(geometry, low, high, names, narrowest):
    samples = [horizontal_section(geometry, low + (high - low) * i / 32, names) for i in range(33)]
    return (min if narrowest else max)(samples, key=lambda result: result["width"])


def sample_channel(values, time):
    if isinstance(values, list):
        return values
    frames = sorted((float(t), s if isinstance(s, dict) else {"vector": s}) for t, s in values.items())
    if time <= frames[0][0]:
        return frames[0][1]["vector"]
    for index in range(1, len(frames)):
        after, before = frames[index], frames[index - 1]
        if time <= after[0]:
            fraction = (time - before[0]) / (after[0] - before[0])
            easing = after[1].get("easing", "linear")
            if easing == "easeInOutSine":
                fraction = (1 - math.cos(math.pi * fraction)) / 2
            elif easing != "linear":
                raise ValueError("Add the real Gecko easing evaluator before inspecting " + easing)
            return [before[1]["vector"][i] + fraction * (after[1]["vector"][i] - before[1]["vector"][i]) for i in range(3)]
    return frames[-1][1]["vector"]


def sample_pose(clip, time):
    return {name: {channel: sample_channel(values, time) for channel, values in channels.items()}
            for name, channels in clip["bones"].items()}


def point_cube_distance(point, cube):
    """Distance to the actual oriented cube, rather than its oversized AABB."""
    pivot = cube.get("pivot", [0, 0, 0])
    rotation = author_rotation(cube.get("rotation", [0, 0, 0]))
    delta = [point[i] - pivot[i] for i in range(3)]
    local = [sum(rotation[j][i] * delta[j] for j in range(3)) + pivot[i] for i in range(3)]
    inflate = cube.get("inflate", 0)
    return math.sqrt(sum(max(cube["origin"][i] - inflate - local[i], 0,
                             local[i] - cube["origin"][i] - cube["size"][i] - inflate) ** 2 for i in range(3)))


def reference_metrics(geometry, animations):
    bones = {b["name"]: b for b in geometry["minecraft:geometry"][0]["bones"]}
    lo, hi = bounds(model_points(geometry))
    measurements = {"height_blocks": (hi[1] - lo[1]) / 16, "minimum": lo, "maximum": hi,
                    "cube_count": sum(len(b.get("cubes", [])) for b in bones.values()), "bone_count": len(bones)}
    for label, names in (("head", {"head", "face_plate", "eye_glow"}), ("chest", {"chest"}),
                         ("waist", {"waist"}), ("shoulders", {"left_shoulder", "right_shoulder"}),
                         ("back", {"back_spines", "back_blade_left", "back_blade_right"}),
                         ("left_blade", {"left_blade"}), ("left_foot", {"left_foot"})):
        minimum, maximum = bounds(model_points(geometry, names=names))
        measurements[label] = {"minimum": minimum, "maximum": maximum, "extent": [maximum[i] - minimum[i] for i in range(3)]}
    torso = {"body", "waist", "chest"}
    measurements["cross_sections"] = {
        "waist_y24_to26": section_extreme(geometry, 24, 26, torso, True),
        "waist_at_y25": horizontal_section(geometry, 25, torso),
        "waist_profiles": [horizontal_section(geometry, y, torso) for y in (24, 24.5, 25, 25.5, 26)],
        "hip_y20_to23": section_extreme(geometry, 20, 23, torso | {"left_leg", "right_leg"}, False),
        "chest_y28_to32": section_extreme(geometry, 28, 32, torso, False),
    }
    measurements["cross_sections"]["waist_to_chest_ratio"] = measurements["cross_sections"]["waist_y24_to26"]["width"] / measurements["cross_sections"]["chest_y28_to32"]["width"]
    measurements["cross_sections"]["waist_armor_to_chest_ratio"] = measurements["cross_sections"]["waist_at_y25"]["width"] / measurements["cross_sections"]["chest_y28_to32"]["width"]
    measurements["pose_bounds"] = {}
    for name in ("run", "sprint", "charge"):
        clip = animations["animations"]["animation.apex." + name]
        low, high = bounds(model_points(geometry, sample_pose(clip, clip["animation_length"] / 4)))
        measurements["pose_bounds"][name] = {"minimum": low, "maximum": high}
    return measurements


class ApexReferenceModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.project = body.read_json(body.PROJECT)
        cls.geometry = body.read_json(body.GEOMETRY)
        cls.animations = body.read_json(body.ANIMATIONS)
        cls.bones = {b["name"]: b for b in cls.geometry["minecraft:geometry"][0]["bones"]}

    def test_original_rig_timing_and_all_transforms_outside_approved_charge_angles_remain(self):
        self.assertEqual({name: bone.get("parent") for name, bone in self.bones.items()}, PARENTS)
        self.assertEqual(hashlib.sha256(body.ANIMATIONS.read_bytes()).hexdigest(), ANIMATION_SHA256)
        before = json.dumps(self.animations)
        self.assertEqual(animation_contract_fingerprints(self.animations),
                         (TIMING_SHA256, PROTECTED_X_SHA256, UNCHANGED_CLIPS_SHA256))
        self.assertEqual(json.dumps(self.animations), before, "Contract inspection must not mutate the animation input")
        body.assert_equivalent(body.export_animations(self.project), self.animations)
        allowed = copy.deepcopy(self.animations)
        allowed["animations"]["animation.apex.charge"]["bones"]["chest"]["rotation"]["0"]["vector"][0] += .125
        self.assertEqual(animation_contract_fingerprints(allowed), (TIMING_SHA256, PROTECTED_X_SHA256, UNCHANGED_CLIPS_SHA256))
        forbidden_axis = copy.deepcopy(allowed)
        forbidden_axis["animations"]["animation.apex.charge"]["bones"]["chest"]["rotation"]["0"]["vector"][1] += .125
        self.assertNotEqual(animation_contract_fingerprints(forbidden_axis)[1], PROTECTED_X_SHA256, "Y/Z changes are outside the approved correction")
        forbidden_position = copy.deepcopy(allowed)
        forbidden_position["animations"]["animation.apex.charge"]["bones"]["chest"]["position"][0] += .125
        self.assertNotEqual(animation_contract_fingerprints(forbidden_position)[1], PROTECTED_X_SHA256, "Position/scale values must remain unchanged")
        forbidden_time = copy.deepcopy(allowed)
        frames = forbidden_time["animations"]["animation.apex.charge"]["bones"]["chest"]["rotation"]
        frames["0.0001"] = frames.pop("0")
        self.assertNotEqual(animation_contract_fingerprints(forbidden_time)[0], TIMING_SHA256, "Approved pose corrections cannot move any attack timestamp")

    def test_editable_cubes_have_unique_descriptive_names_for_every_major_armor_part(self):
        names = [c["name"] for c in self.project["elements"]]
        self.assertEqual(len(names), len(set(names)))
        required = {"head_crown_01", "face_center", "face_cheek_left", "face_cheek_right",
                    "chest_plate_left_01", "chest_plate_right_01", "forearm_blade_main_left",
                    "forearm_blade_main_right", "forearm_blade_secondary_left", "forearm_blade_secondary_right",
                    "spine_01", "spine_02", "spine_03"}
        self.assertTrue(required <= set(names), "Meaningful design names are missing: " + str(required - set(names)))
        self.assertTrue(all(not name.lower().startswith("cube") for name in names))
        for side in ("left", "right"):
            self.assertTrue({f"hand_claw_tip_{side}_{index:02d}" for index in (1, 2, 3)} <= set(names))
            self.assertTrue({f"foot_claw_tip_{side}_{index:02d}" for index in (1, 2, 3)} <= set(names))

    def test_transformed_corners_keep_small_head_narrow_waist_and_rear_swept_fins(self):
        metrics = reference_metrics(self.geometry, self.animations)
        self.assertGreaterEqual(metrics["height_blocks"], 2.6)
        self.assertLessEqual(metrics["height_blocks"], 2.8)
        self.assertLess(abs(metrics["minimum"][1]), 0.5, "Feet must meet the floor after cube rotations")
        self.assertLess(metrics["head"]["extent"][0], metrics["chest"]["extent"][0] * 0.6, "The mask must be markedly narrower than the chest")
        self.assertLessEqual(metrics["cross_sections"]["waist_to_chest_ratio"], 0.55,
                             "The narrow waist at y24–26 must remain at most 55% of the chest width at y28–32")
        self.assertLessEqual(metrics["cross_sections"]["waist_armor_to_chest_ratio"], 0.55,
                             "Visible abdominal armor at y25 must also remain narrow, rather than only a gap between plates")
        self.assertLess(metrics["shoulders"]["extent"][0], (metrics["maximum"][1] - metrics["minimum"][1]) * 0.4)
        rear = metrics["back"]["maximum"][2]
        self.assertGreater(rear, max(abs(metrics["back"]["minimum"][0]), abs(metrics["back"]["maximum"][0])) * 1.5,
                           "Back fins should extend rearward rather than spreading into horizontal wings")
        self.assertGreater(metrics["left_blade"]["extent"][1], 14, "Layered forearm blades must retain their long reference silhouette")

    def test_digitigrade_hip_knee_hock_and_forward_three_claw_feet_are_distinct(self):
        for side in ("left", "right"):
            hip, knee, hock = (self.bones[f"{side}_{name}"]["pivot"] for name in ("leg", "lower_leg", "foot"))
            self.assertGreater(hip[1] - knee[1], 8)
            self.assertGreater(knee[1] - hock[1], 8)
            self.assertGreater(hock[2] - knee[2], 3, "Calf must fold rearward from the forward knee")
            low, high = bounds(model_points(self.geometry, names={f"{side}_foot"}))
            self.assertLess(low[2], hock[2] - 5, "Claws must project forward from the hock")
            shoulders = bounds(model_points(self.geometry, names={"left_shoulder", "right_shoulder"}))
            self.assertLess(high[0] - low[0], (shoulders[1][0] - shoulders[0][0]) * .35,
                            "Clawed feet must remain narrow compared with the shoulder span")

    def test_every_major_joint_keeps_overlapping_internal_support_around_its_pivot(self):
        joints = {"neck", "head", "waist", *(f"{side}_{part}" for side in ("left", "right")
                    for part in ("shoulder", "arm", "forearm", "hand", "leg", "lower_leg", "foot"))}
        for name in joints:
            bone = self.bones[name]
            own_gap = min(point_cube_distance(bone["pivot"], c) for c in bone["cubes"])
            parent_gap = min(point_cube_distance(bone["pivot"], c) for c in self.bones[bone["parent"]]["cubes"])
            self.assertLessEqual(own_gap, 1.5, name + " has lost its internal socket/ring")
            self.assertLessEqual(parent_gap, 1.3, name + " no longer reaches the supporting parent armor/frame")

    def test_all_thirty_seven_clips_transform_every_cube_without_exploding_the_rig(self):
        for name, clip in self.animations["animations"].items():
            for fraction in (0, .125, .25, .375, .5, .625, .75, .875, 1):
                points = model_points(self.geometry, sample_pose(clip, fraction * clip["animation_length"]))
                self.assertTrue(all(math.isfinite(v) for p in points for v in p), name)
                low, high = bounds(points)
                self.assertLess(max(high[i] - low[i] for i in range(3)), 100, name + " has detached or severely scaled armor")
        # Measure the real articulated head anchor. A mask/fin bounding-box
        # midpoint moves when armor is reshaped, especially with alpha cutouts.
        head_anchor = self.bones["head"]["pivot"]
        rest_head = transform(bone_matrices(self.geometry)["head"], head_anchor)
        for name in ("run", "sprint", "charge"):
            clip = self.animations["animations"]["animation.apex." + name]
            pose = sample_pose(clip, clip["animation_length"] / 4)
            head = transform(bone_matrices(self.geometry, pose)["head"], head_anchor)
            self.assertLess(head[1], rest_head[1] - 2, name + " must lower the head for the predatory forward stance")
            self.assertLess(head[2], rest_head[2] - 2, name + " must project the head forward")


if __name__ == "__main__":
    unittest.main()
