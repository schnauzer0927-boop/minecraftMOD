"""Public Stage 3 invariants and deliberately damaged walking fixtures."""
import copy
import json
import unittest

from generator.stage3_validation import FUTURE_CORE, validate_stage3

STONE = "minecraft:smooth_stone"
STAIR = "minecraft:smooth_quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"


def fixture(stairs=False):
    """An isolated public corridor, not a copy of the production geometry."""
    lane = {"id": "station_lane", "min": [20, 5, 0], "max": [28, 15, 3]}
    protected = {"areas": [copy.deepcopy(FUTURE_CORE), copy.deepcopy(lane)]}
    baseline = {(0, 0, 0): STONE}
    if stairs:
        additions = {(1, 1, 0): STAIR, (2, 2, 0): STAIR, (3, 2, 0): STONE}
        points = [[0, 1, 0], [1, 2, 0], [2, 3, 0], [3, 3, 0]]
    else:
        additions = {(x, 0, 0): STONE for x in range(1, 5)}
        points = [[0, 1, 0], [2, 1, 0], [4, 1, 0]]
    layout = {
        "station": {"lane": copy.deepcopy(lane)},
        "walk_paths": [
            {"id": "entrance_to_boarding", "checkpoints": points},
            {"id": "alighting_to_exit", "checkpoints": list(reversed(points))},
        ],
    }
    return baseline, additions, protected, layout


class Stage3ValidationTests(unittest.TestCase):
    def test_supported_flat_routes_and_report_are_self_contained(self):
        data = fixture()
        before = copy.deepcopy(data)
        report = validate_stage3(*data)
        self.assertTrue(report["valid"], report["errors"])
        self.assertEqual(data, before, "Validation must not mutate input maps or documents")
        self.assertFalse(report["game_verified"])
        self.assertFalse(report["worldedit_paste_verified"])
        self.assertFalse(report["actual_player_walk_verified"])
        self.assertEqual(0, report["checks"]["rail_free_palette"]["rail_block_count"])
        json.dumps(report)
        route = report["walking"]["routes"][0]
        self.assertEqual([0, 1, 0], route["resolved_path"][0])
        self.assertEqual([4, 1, 0], route["resolved_path"][-1])
        self.assertIn([2, 1, 0], route["resolved_path"])

    def test_oriented_stairs_connect_ascent_and_descent_without_jumping(self):
        report = validate_stage3(*fixture(stairs=True))
        self.assertTrue(report["valid"], report["errors"])
        self.assertEqual([2, 2], [r["stair_height_transitions"]
                                  for r in report["walking"]["routes"]])
        self.assertFalse(report["walking"]["requires_jumping"])

    def test_any_baseline_write_is_rejected_even_if_value_is_identical(self):
        for block in (STONE, "minecraft:white_concrete"):
            with self.subTest(block=block):
                baseline, additions, protected, layout = fixture()
                additions[(0, 0, 0)] = block
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                check = report["checks"]["baseline_preservation"]
                self.assertEqual(1, check["overlap_count"])
                self.assertEqual(block == STONE,
                                 check["all_baseline_blocks_unchanged_in_merge"])

    def test_rail_or_air_additions_fail_even_away_from_walking_routes(self):
        for block, check in (
            ("minecraft:rail[shape=north_south]", "rail_free_palette"),
            ("minecraft:powered_rail[shape=east_west,powered=false]", "rail_free_palette"),
            ("minecraft:air", "additive_vanilla_blocks"),
            ("example:decorative_block", "additive_vanilla_blocks"),
        ):
            with self.subTest(block=block):
                baseline, additions, protected, layout = fixture()
                additions[(8, 0, 0)] = block
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertFalse(report["checks"][check]["passed"])

    def test_all_protected_cuboids_and_original_core_remain_empty(self):
        for position in ((85, 5, 69), (28, 15, 3), (9, 0, 0)):
            with self.subTest(position=position):
                baseline, additions, protected, layout = fixture()
                protected["areas"].append({"id": "user_keepout", "min": [9, 0, 0],
                                           "max": [9, 2, 0]})
                additions[position] = STONE
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertEqual(1, report["checks"]["protected_volumes"]["addition_conflict_count"])

    def test_missing_original_future_core_cannot_weaken_validation(self):
        baseline, additions, protected, layout = fixture()
        protected["areas"] = protected["areas"][1:]
        additions[(85, 5, 69)] = STONE
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["valid"])
        self.assertFalse(report["checks"]["protected_volumes"]["future_core_recorded"])
        self.assertEqual(1, report["checks"]["protected_volumes"]["addition_conflict_count"])

    def test_obstructed_feet_head_and_missing_support_break_routes(self):
        for mutation, reason in (("feet", "occupied_feet"), ("head", "occupied_head"),
                                 ("support", "missing_full_block_or_stair_support")):
            with self.subTest(mutation=mutation):
                baseline, additions, protected, layout = fixture()
                if mutation == "support":
                    del additions[(2, 0, 0)]
                else:
                    additions[(2, 1 if mutation == "feet" else 2, 0)] = STONE
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertFalse(report["walking"]["passed"])
                invalid = report["walking"]["routes"][0]["invalid_checkpoints"]
                self.assertEqual(reason, invalid[0]["reason"])

    def test_wrong_facing_half_shape_or_full_cube_requires_a_jump(self):
        for bad in (STAIR.replace("facing=east", "facing=west"),
                    STAIR.replace("half=bottom", "half=top"),
                    STAIR.replace("shape=straight", "shape=inner_left"), STONE):
            with self.subTest(block=bad):
                baseline, additions, protected, layout = fixture(stairs=True)
                additions[(1, 1, 0)] = bad
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertFalse(report["walking"]["passed"])

    def test_low_ceiling_blocks_the_head_sweep_during_ascent(self):
        baseline, additions, protected, layout = fixture(stairs=True)
        additions[(0, 3, 0)] = STONE
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["walking"]["passed"])
        self.assertFalse(report["valid"])

    def test_thin_partial_blocks_cannot_be_treated_as_full_floor(self):
        for block in ("minecraft:glass_pane", "minecraft:oak_fence",
                      "minecraft:cobblestone_wall", "minecraft:stone_slab[type=bottom]",
                      "minecraft:oak_trapdoor[half=top,open=false]"):
            with self.subTest(block=block):
                baseline, additions, protected, layout = fixture()
                additions[(2, 0, 0)] = block
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["walking"]["passed"])

    def test_lane_cannot_be_crossed_using_a_lower_supported_bridge(self):
        baseline, _, protected, layout = fixture()
        additions = {(x, 0, 1): STONE for x in range(19, 30)}
        points = [[19, 1, 1], [29, 1, 1]]
        for route in layout["walk_paths"]:
            route["checkpoints"] = points
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertTrue(report["checks"]["protected_volumes"]["passed"])
        self.assertTrue(report["checks"]["station_lane"]["passed"])
        self.assertFalse(report["walking"]["passed"])
        self.assertFalse(report["valid"])

    def test_forbidden_volume_checks_head_as_well_as_feet(self):
        baseline, additions, protected, layout = fixture()
        layout["walking_forbidden_areas"] = [
            {"id": "head_keepout", "min": [2, 2, 0], "max": [2, 4, 0]}]
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["walking"]["passed"])
        self.assertEqual("inside_pedestrian_forbidden_volume",
                         report["walking"]["routes"][0]["invalid_checkpoints"][0]["reason"])

    def test_crop_stems_are_not_solid_floor_despite_their_vanilla_namespace(self):
        for block in ("minecraft:pumpkin_stem[age=7]", "minecraft:melon_stem[age=7]",
                      "minecraft:attached_pumpkin_stem[facing=north]"):
            with self.subTest(block=block):
                baseline, additions, protected, layout = fixture()
                additions[(2, 0, 0)] = block
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertFalse(report["walking"]["passed"])

    def test_station_lane_also_must_be_empty_in_the_combined_model(self):
        baseline, additions, protected, layout = fixture()
        baseline[(20, 5, 0)] = STONE
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["valid"])
        self.assertFalse(report["checks"]["station_lane"]["passed"])
        self.assertEqual(1, report["checks"]["station_lane"]["combined_occupied_block_count"])

    def test_stair_head_sweep_cannot_enter_an_empty_forbidden_volume(self):
        baseline, additions, protected, layout = fixture(stairs=True)
        layout["walking_forbidden_areas"] = [
            {"id": "rising_head_keepout", "min": [0, 3, 0], "max": [0, 3, 0]}]
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["valid"])
        self.assertFalse(report["walking"]["passed"])

    def test_missing_alighting_route_cannot_pass_a_boarding_only_check(self):
        baseline, additions, protected, layout = fixture()
        layout["walk_paths"] = layout["walk_paths"][:1]
        report = validate_stage3(baseline, additions, protected, layout)
        self.assertFalse(report["valid"])
        self.assertEqual(["alighting_to_exit"], report["walking"]["missing_required_routes"])

    def test_station_lane_dimensions_and_recorded_protection_are_required(self):
        for mutation in ("width", "height", "record"):
            with self.subTest(mutation=mutation):
                baseline, additions, protected, layout = fixture()
                if mutation == "record":
                    protected["areas"] = protected["areas"][:1]
                else:
                    axis = 0 if mutation == "width" else 1
                    layout["station"]["lane"]["max"][axis] -= 1
                    protected["areas"][1] = copy.deepcopy(layout["station"]["lane"])
                report = validate_stage3(baseline, additions, protected, layout)
                self.assertFalse(report["valid"])
                self.assertFalse(report["checks"]["station_lane"]["passed"])


class Stage3ConstructionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from generator.stage3_main import build_stage3
        (cls.baseline, cls.additions, cls.parts, cls.layout,
         cls.route, cls.protected) = build_stage3()
        cls.report = validate_stage3(cls.baseline, cls.additions.blocks,
                                     cls.protected, cls.layout)

    def test_production_model_keeps_exterior_and_provides_both_supported_routes(self):
        self.assertTrue(self.report["valid"], self.report["errors"])
        self.assertFalse(set(self.baseline).intersection(self.additions.blocks))
        merged = dict(self.baseline)
        merged.update(self.additions.blocks)
        self.assertTrue(all(merged[p] == block for p, block in self.baseline.items()))
        expected = {p["id"]: p["checkpoints"] for p in self.layout["walk_paths"]}
        for route in self.report["walking"]["routes"]:
            self.assertTrue(route["passed"])
            self.assertEqual(expected[route["id"]][0], route["resolved_path"][0])
            self.assertEqual(expected[route["id"]][-1], route["resolved_path"][-1])
            self.assertGreater(route["stair_height_transitions"], 0)
        self.assertFalse(self.report["game_verified"])

    def test_production_parts_partition_the_additions_without_overlap(self):
        union = {}
        for part in self.parts.values():
            self.assertTrue(part, "Each promised construction part must contain blocks")
            self.assertFalse(set(union).intersection(part))
            union.update(part)
        self.assertEqual(self.additions.blocks, union)

    def test_raising_connected_floors_preserves_both_supported_forecourt_connections(self):
        from generator.stage3_main import ROOT, build_stage3
        config = json.loads((ROOT / "stage3_config.json").read_text(encoding="utf-8"))
        config["queue_floor_y"] += 1
        config["station_floor_y"] += 1
        config["queue_ceiling_y"] += 1
        config["station_ceiling_y"] += 1
        baseline, model, _, layout, guide, protected = build_stage3(config)
        report = validate_stage3(baseline, model.blocks, protected, layout)
        self.assertTrue(report["valid"], report["errors"])
        self.assertEqual(self.baseline, baseline)
        self.assertGreater(layout["station"]["passenger_foot_y"],
                           self.layout["station"]["passenger_foot_y"])
        self.assertGreater(guide["station"]["rail_height"],
                           self.route["station"]["rail_height"])
        original = {r["id"]: r for r in self.report["walking"]["routes"]}
        for route in report["walking"]["routes"]:
            self.assertTrue(route["passed"])
            self.assertGreater(route["stair_height_transitions"],
                               original[route["id"]]["stair_height_transitions"])


if __name__ == "__main__":
    unittest.main()
