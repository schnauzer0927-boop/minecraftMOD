"""Independent, conservative voxel checks for the Stage 3 additions.

This module checks sparse project-relative block maps, not a live Minecraft
world.  Its walking graph requires two empty body cells, support beneath the
feet, and correctly facing stairs for every one-block rise.  It does not
simulate a player, vehicles, block updates, WorldEdit pastes, or fractional
collision shapes.  Routes are numerical evidence only.
"""
from __future__ import annotations

from collections import deque
from collections.abc import Mapping

XYZ = tuple[int, int, int]
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
RAILS = {"minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail",
         "minecraft:activator_rail"}
FUTURE_CORE = {"id": "future_ride_core", "min": [85, 5, 69],
               "max": [135, 31, 119]}
FACING = {"east": (1, 0), "west": (-1, 0),
          "south": (0, 1), "north": (0, -1)}
FULL_BLOCKS = {
    "stone", "smooth_stone", "cobblestone", "mossy_cobblestone", "bedrock",
    "andesite", "polished_andesite", "diorite", "polished_diorite", "granite",
    "polished_granite", "deepslate", "cobbled_deepslate", "polished_deepslate",
    "tuff", "calcite", "dirt", "coarse_dirt", "rooted_dirt", "grass_block",
    "podzol", "mycelium", "sand", "red_sand", "gravel", "sandstone",
    "smooth_sandstone", "cut_sandstone", "red_sandstone", "smooth_red_sandstone",
    "cut_red_sandstone", "bricks", "stone_bricks", "cracked_stone_bricks",
    "mossy_stone_bricks", "chiseled_stone_bricks", "deepslate_bricks",
    "deepslate_tiles", "mud_bricks", "nether_bricks", "red_nether_bricks",
    "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar",
    "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block",
    "smooth_quartz", "glass", "tinted_glass", "sea_lantern", "glowstone",
    "terracotta", "obsidian", "crying_obsidian", "netherrack", "basalt",
    "polished_basalt", "blackstone", "polished_blackstone",
    "polished_blackstone_bricks", "iron_block", "gold_block", "diamond_block",
    "emerald_block", "lapis_block", "coal_block", "redstone_block",
    "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
    "waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper",
    "waxed_oxidized_copper", "prismarine", "prismarine_bricks", "dark_prismarine",
    "packed_ice", "blue_ice", "ice", "snow_block", "bone_block",
    "crimson_stem", "warped_stem", "stripped_crimson_stem", "stripped_warped_stem",
}


def _state(block: str) -> tuple[str, dict[str, str]]:
    name, separator, properties = block.partition("[")
    values = {}
    if separator:
        for item in properties.rstrip("]").split(","):
            key, found, value = item.partition("=")
            if found:
                values[key] = value
    return name, values


def _bottom_stair(block: str | None) -> str | None:
    if block is None:
        return None
    name, properties = _state(block)
    if (name.startswith("minecraft:") and name.endswith("_stairs")
            and properties.get("half") == "bottom"
            and properties.get("shape", "straight") == "straight"
            and properties.get("waterlogged", "false") == "false"
            and properties.get("facing") in FACING):
        return properties["facing"]
    return None


def _full_block(block: str | None) -> bool:
    """Recognize full support in the project's vanilla material families.

    Unrecognized materials cannot silently become walkable.  Thin panes,
    fences, walls, trapdoors and single slabs therefore never provide support.
    Leaves and ordinary glass have full block collision support.
    """
    if block is None:
        return False
    name, properties = _state(block)
    if not name.startswith("minecraft:"):
        return False
    local = name.removeprefix("minecraft:")
    if local.endswith("_slab"):
        return (properties.get("type") == "double"
                and properties.get("waterlogged", "false") == "false")
    return (local in FULL_BLOCKS or local.endswith((
        "_concrete", "_wool", "_terracotta", "_planks", "_leaves",
        "_stained_glass", "_log", "_wood", "_hyphae")))


def _xyz(value, label: str) -> XYZ:
    if (not isinstance(value, (list, tuple)) or len(value) != 3
            or any(type(v) is not int for v in value)):
        raise ValueError(f"{label} must contain three integer XYZ coordinates")
    return tuple(value)


def _cuboid(value, label: str) -> dict:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be a cuboid object")
    lo = _xyz(value.get("min"), f"{label}.min")
    hi = _xyz(value.get("max"), f"{label}.max")
    if any(a > b for a, b in zip(lo, hi)):
        raise ValueError(f"{label} has inverted bounds")
    return {"id": str(value.get("id", label)), "min": list(lo), "max": list(hi)}


def _inside(point: XYZ, area: Mapping) -> bool:
    return all(a <= v <= b for v, a, b in
               zip(point, area["min"], area["max"]))


def _examples(points, limit=12) -> list[list[int]]:
    return [list(p) for p in sorted(points)[:limit]]


class WalkingGraph:
    """Discrete supported-foot graph; edges never model jumping or falling."""

    def __init__(self, blocks: Mapping[XYZ, str], forbidden=()):
        self.blocks = blocks
        self.forbidden = tuple(forbidden)
        self.support = {}
        self.stairs = {}
        for (x, y, z), block in blocks.items():
            facing = _bottom_stair(block)
            if _full_block(block) or facing:
                point = (x, y + 1, z)
                if self.reason(point) is None:
                    self.support[point] = block
                    if facing:
                        self.stairs[point] = facing

    def forbidden_at(self, point: XYZ) -> bool:
        return any((area.get("pedestrian_exclusion") == "entire_xz_footprint" and
                area["min"][0] <= point[0] <= area["max"][0] and
                area["min"][2] <= point[2] <= area["max"][2]) or
               _inside(point, area) for area in self.forbidden)

    def reason(self, point: XYZ) -> str | None:
        if any(self.forbidden_at((point[0], point[1] + height, point[2]))
               for height in (0, 1)):
            return "inside_pedestrian_forbidden_volume"
        for height in (0, 1):
            block = self.blocks.get((point[0], point[1] + height, point[2]))
            if block is not None and _state(block)[0] not in AIR:
                return "occupied_feet" if height == 0 else "occupied_head"
        below = self.blocks.get((point[0], point[1] - 1, point[2]))
        if not (_full_block(below) or _bottom_stair(below)):
            return "missing_full_block_or_stair_support"
        return None

    def neighbors(self, point: XYZ):
        x, y, z = point
        for dx, dz in FACING.values():
            for dy in (0, 1, -1):
                target = (x + dx, y + dy, z + dz)
                if target not in self.support:
                    continue
                if dy == 0:
                    yield target
                else:
                    high = target if dy == 1 else point
                    uphill = (dx, dz) if dy == 1 else (-dx, -dz)
                    facing = self.stairs.get(high)
                    low = point if dy == 1 else target
                    swept_point = (low[0], low[1] + 2, low[2])
                    swept_head = self.blocks.get(swept_point)
                    if (facing and FACING[facing] == uphill and
                            not self.forbidden_at(swept_point) and
                            (swept_head is None or _state(swept_head)[0] in AIR)):
                        yield target

    def path(self, start: XYZ, end: XYZ) -> tuple[list[XYZ] | None, int]:
        if start not in self.support or end not in self.support:
            return None, 0
        predecessor = {start: None}
        queue = deque([start])
        while queue:
            point = queue.popleft()
            if point == end:
                route = []
                while point is not None:
                    route.append(point)
                    point = predecessor[point]
                return list(reversed(route)), len(predecessor)
            for target in self.neighbors(point):
                if target not in predecessor:
                    predecessor[target] = point
                    queue.append(target)
        return None, len(predecessor)


def validate_stage3(baseline: Mapping[XYZ, str], additions: Mapping[XYZ, str],
                    protected_doc: dict, layout: dict) -> dict:
    """Return self-contained evidence for immutable exterior and safe routes.

    ``protected_doc['areas']`` supplies every protected cuboid.  The original
    future-ride core is always enforced, even if a document omits it.
    ``layout['walk_paths']`` (alias ``expected_walk_paths``) is a list of objects
    with a unique ``id`` and ordered integer-foot ``checkpoints``.  Required
    routes have IDs ``entrance_to_boarding`` and ``alighting_to_exit``.
    ``layout['station']['lane']`` is excluded from walking; optional
    ``walking_forbidden_areas`` add further exclusions.  All errors are returned
    in the report; malformed maps are reported without attempting traversal.
    """
    errors = []
    checks = {}
    report = {
        "schema_version": 1,
        "coordinate_space": "project_relative_xyz",
        "validation_method": "sparse_voxel_maps_and_supported_feet_bfs",
        "valid": False, "errors": errors, "checks": checks,
        "game_verified": False, "worldedit_paste_verified": False,
        "actual_player_walk_verified": False,
        "historical_interior_verified": False,
    }
    for name, blocks in (("baseline", baseline), ("additions", additions)):
        if not isinstance(blocks, Mapping):
            errors.append(f"{name} must be an XYZ-to-block-state mapping")
            return report
        try:
            for point, block in blocks.items():
                _xyz(point, f"{name} position")
                if not isinstance(block, str):
                    raise ValueError(f"{name} block states must be strings")
        except ValueError as exc:
            errors.append(str(exc))
            return report
    if not isinstance(layout, Mapping) or not isinstance(protected_doc, Mapping):
        errors.append("layout and protected_doc must be objects")
        return report

    overlaps = set(baseline).intersection(additions)
    changed = [p for p in overlaps if additions[p] != baseline[p]]
    merged = dict(baseline)
    merged.update(additions)
    checks["baseline_preservation"] = {
        "passed": not overlaps,
        "baseline_block_count": len(baseline),
        "addition_block_count": len(additions),
        "overlap_count": len(overlaps),
        "changed_baseline_count": len(changed),
        "all_baseline_blocks_unchanged_in_merge": not changed,
        "overlap_examples": _examples(overlaps),
        "changed_examples": _examples(changed),
    }
    if overlaps:
        errors.append(f"Additions write {len(overlaps)} existing baseline coordinates")

    rails = [p for p, block in merged.items() if _state(block)[0] in RAILS]
    invalid = [p for p, block in additions.items()
               if not block.startswith("minecraft:") or _state(block)[0] in AIR]
    checks["rail_free_palette"] = {
        "passed": not rails, "rail_block_count": len(rails),
        "rail_examples": _examples(rails),
    }
    checks["additive_vanilla_blocks"] = {
        "passed": not invalid, "invalid_or_air_block_count": len(invalid),
        "examples": _examples(invalid),
    }
    if rails:
        errors.append(f"Combined model contains {len(rails)} rail blocks")
    if invalid:
        errors.append(f"Additions contain {len(invalid)} air or non-vanilla blocks")

    areas = []
    try:
        raw_areas = protected_doc.get("areas")
        if not isinstance(raw_areas, list):
            raise ValueError("protected_doc.areas must be a list")
        areas = [_cuboid(a, f"protected area {i}")
                 for i, a in enumerate(raw_areas)]
    except ValueError as exc:
        errors.append(str(exc))
    core_recorded = any(a["min"] == FUTURE_CORE["min"] and
                        a["max"] == FUTURE_CORE["max"] for a in areas)
    if not core_recorded:
        errors.append("Protected document must retain the original future ride core")
        areas.append(dict(FUTURE_CORE))
    conflicts = {p: [a["id"] for a in areas if _inside(p, a)]
                 for p in additions if any(_inside(p, a) for a in areas)}
    checks["protected_volumes"] = {
        "passed": not conflicts and core_recorded,
        "area_count": len(areas), "future_core_recorded": core_recorded,
        "future_core_bounds": dict(FUTURE_CORE),
        "addition_conflict_count": len(conflicts),
        "examples": [{"position": list(p), "areas": conflicts[p]}
                     for p in sorted(conflicts)[:12]],
    }
    if conflicts:
        errors.append(f"Additions occupy {len(conflicts)} protected coordinates")

    forbidden = []
    try:
        station = layout.get("station", {})
        lane = _cuboid(station.get("lane"), "station lane")
        lane["pedestrian_exclusion"] = "entire_xz_footprint"
        forbidden.append(lane)
        span = [b - a + 1 for a, b in zip(lane["min"], lane["max"])]
        lane_conflicts = [p for p in additions if _inside(p, lane)]
        merged_lane_conflicts = [p for p, block in merged.items()
                                 if _inside(p, lane) and _state(block)[0] not in AIR]
        lane_recorded = any(a["min"] == lane["min"] and
                            a["max"] == lane["max"] for a in areas)
        passed = (span[0] >= 9 and span[1] >= 11 and
                  not merged_lane_conflicts and lane_recorded)
        checks["station_lane"] = {
            "passed": passed, "bounds": lane, "inclusive_span": span,
            "minimum_width": 9, "minimum_height": 11,
            "recorded_in_protected_doc": lane_recorded,
            "addition_conflict_count": len(lane_conflicts),
            "combined_occupied_block_count": len(merged_lane_conflicts),
            "examples": _examples(merged_lane_conflicts),
        }
        if not passed:
            errors.append("Station lane needs protected, empty width 9 and height 11")
        extra = layout.get("walking_forbidden_areas", [])
        if not isinstance(extra, list):
            raise ValueError("walking_forbidden_areas must be a list")
        forbidden.extend(_cuboid(a, f"walking forbidden area {i}")
                         for i, a in enumerate(extra))
    except (ValueError, AttributeError) as exc:
        errors.append(str(exc))
        checks["station_lane"] = {"passed": False, "error": str(exc)}

    graph = WalkingGraph(merged, forbidden)
    walking = {
        "passed": False,
        "method": "four_cardinal_neighbors; feet and head empty; full support; oriented stairs for +/-1",
        "requires_jumping": False,
        "jump_edges_allowed": False,
        "station_lane_crossing_allowed": False,
        "candidate_supported_foot_cells": len(graph.support),
        "forbidden_areas": forbidden,
        "routes": [],
        "game_verified": False,
        "actual_player_walk_verified": False,
    }
    report["walking"] = walking
    paths = layout.get("walk_paths", layout.get("expected_walk_paths", []))
    if not isinstance(paths, list):
        errors.append("walk_paths must be a list")
        paths = []
    ids = set()
    for index, route in enumerate(paths):
        record = {"id": f"route_{index}", "passed": False,
                  "checkpoints": [], "segments": [], "resolved_path": []}
        walking["routes"].append(record)
        try:
            if not isinstance(route, Mapping):
                raise ValueError("Walking route must be an object")
            record["id"] = str(route.get("id", f"route_{index}"))
            if record["id"] in ids:
                raise ValueError(f"Duplicate walking route id {record['id']}")
            ids.add(record["id"])
            raw_points = route.get("checkpoints", route.get("points"))
            if not isinstance(raw_points, list) or len(raw_points) < 2:
                raise ValueError("Walking route needs at least two checkpoints")
            points = [_xyz(p, f"{record['id']} checkpoint {i}")
                      for i, p in enumerate(raw_points)]
            record["checkpoints"] = [list(p) for p in points]
            invalid_points = [{"position": list(p), "reason": graph.reason(p)}
                              for p in points if p not in graph.support]
            record["invalid_checkpoints"] = invalid_points
            resolved = []
            for start, end in zip(points, points[1:]):
                path, searched = graph.path(start, end)
                segment = {"from": list(start), "to": list(end),
                           "reachable": path is not None,
                           "searched_foot_cells": searched,
                           "step_count": len(path) - 1 if path else None,
                           "resolved_path": [list(p) for p in path] if path else []}
                record["segments"].append(segment)
                if path:
                    resolved.extend(path if not resolved else path[1:])
            record["passed"] = not invalid_points and all(
                s["reachable"] for s in record["segments"])
            record["resolved_path"] = ([list(p) for p in resolved]
                                       if record["passed"] else [])
            record["stair_height_transitions"] = sum(
                a[1] != b[1] for a, b in zip(resolved, resolved[1:]))
            if not record["passed"]:
                errors.append(f"Walking route {record['id']} is not continuously reachable")
        except ValueError as exc:
            record["error"] = str(exc)
            errors.append(str(exc))
    missing = sorted({"entrance_to_boarding", "alighting_to_exit"} - ids)
    if missing:
        errors.append("Missing required walking routes: " + ", ".join(missing))
    walking["missing_required_routes"] = missing
    walking["passed"] = bool(paths) and not missing and all(
        route["passed"] for route in walking["routes"])
    walking["requires_jumping"] = False if walking["passed"] else None
    checks["supported_walking_routes"] = {"passed": walking["passed"],
                                           "route_count": len(paths)}
    report["valid"] = not errors and all(c["passed"] for c in checks.values())
    return report
