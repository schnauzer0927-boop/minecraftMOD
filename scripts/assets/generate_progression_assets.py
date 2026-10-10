#!/usr/bin/env python3
"""Author the Apex Beacon and Apex Fang models, atlases and crafting data.

These are original vanilla cuboid item meshes. Forge 47.4.10's forge_data
lightmap makes only the orange grooves fullbright; it does not create world
lighting or require an entity renderer. Model rotations use Minecraft 1.20.1's
allowed 22.5/45 degree steps. Existing boss art is never read or changed.

Run with Python 3 and Pillow. All pixel noise uses a fixed seed.
"""
from __future__ import annotations

import argparse
import json
import math
import random
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/apexpredator"
DATA = ROOT / "src/main/resources/data/apexpredator"
SIZE = 128
TILE = 32
FACES = ("down", "up", "north", "south", "west", "east")
MATERIALS = {
    "armor": (0, 0, (29, 35, 42)),
    "metal": (32, 0, (51, 61, 72)),
    "edge": (64, 0, (105, 118, 128)),
    "black": (96, 0, (13, 16, 21)),
    "energy": (0, 32, (243, 92, 12)),
    "core": (32, 32, (255, 170, 46)),
    "red": (64, 32, (115, 25, 6)),
    "copper": (96, 32, (112, 65, 33)),
    "grip": (0, 64, (22, 25, 30)),
    "joint": (32, 64, (41, 47, 53)),
    "obsidian": (64, 64, (27, 20, 37)),
    "rift": (96, 64, (90, 40, 123)),
    "inscription": (0, 96, (40, 48, 56)),
    "vent": (32, 96, (22, 28, 35)),
    "edge_dark": (64, 96, (60, 71, 80)),
    "eye": (96, 96, (250, 124, 25)),
}
EMISSIVE = {"energy", "core", "eye"}
LOCALIZED_FEEDBACK = {
    "en_us": {
        "message.apexpredator.beacon.busy": "A summoning ritual is already underway.",
        "message.apexpredator.beacon.unsafe_ground": "Use the beacon on the top face of safe, solid ground outside water.",
        "message.apexpredator.beacon.invalid_item": "Keep the reserved Apex Beacon in your inventory until the ritual completes.",
        "advancement.apexpredator.apex_predator.title": "Apex Predator",
    },
    "ja_jp": {
        "message.apexpredator.beacon.busy": "すでに召喚儀式が進行中です。",
        "message.apexpredator.beacon.unsafe_ground": "水中を避け、安全で安定した地面の上面から起動してください。",
        "message.apexpredator.beacon.invalid_item": "儀式完了まで、使用したApex Beaconをインベントリに残してください。",
        "advancement.apexpredator.apex_predator.title": "最強の捕食者",
    },
}


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def atlas(name, seed):
    rng = random.Random(seed)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for material, (ox, oy, color) in MATERIALS.items():
        for y in range(TILE):
            for x in range(TILE):
                bevel = 8 if min(x, y, TILE - 1 - x, TILE - 1 - y) < 2 else 0
                grain = rng.randrange(-4, 5) + ((y % 4) - 2)
                if material in EMISSIVE:
                    heat = 9 * math.cos((x - 15.5) * math.pi / 32)
                    rgb = [min(255, max(0, int(c + heat + grain))) for c in color]
                elif material == "grip":
                    weave = 7 if (x + 2 * y) % 8 in (0, 1) else -3
                    rgb = [min(255, max(0, c + weave + grain)) for c in color]
                elif material in {"obsidian", "rift"}:
                    facets = ((x // 5 + y // 7) % 3 - 1) * 6
                    rgb = [min(255, max(0, c + facets + grain)) for c in color]
                else:
                    rgb = [min(255, max(0, c + bevel + grain)) for c in color]
                image.putpixel((ox + x, oy + y), (*rgb, 255))
        if material in {"armor", "metal", "edge_dark", "inscription"}:
            draw.line((ox + 3, oy + 4, ox + 27, oy + 4), fill=(64, 75, 84, 255))
            draw.line((ox + 4, oy + 27, ox + 26, oy + 27), fill=(15, 20, 26, 255))
            draw.line((ox + 10, oy + 10, ox + 16, oy + 7, ox + 22, oy + 10), fill=(23, 29, 35, 255))
            draw.line((ox + 16, oy + 8, ox + 16, oy + 20), fill=(70, 80, 88, 255))
            draw.rectangle((ox + 4, oy + 5, ox + 6, oy + 7), fill=(12, 16, 20, 255))
            draw.point((ox + 5, oy + 6), fill=(104, 115, 122, 255))
            draw.rectangle((ox + 25, oy + 23, ox + 27, oy + 25), fill=(12, 16, 20, 255))
            draw.point((ox + 26, oy + 24), fill=(104, 115, 122, 255))
        elif material == "vent":
            for y in range(4, 28, 4):
                draw.line((ox + 3, oy + y, ox + 28, oy + y), fill=(8, 11, 16, 255), width=2)
                draw.line((ox + 4, oy + y + 1, ox + 27, oy + y + 1), fill=(60, 69, 78, 255))
        elif material in EMISSIVE:
            draw.line((ox + 14, oy + 1, ox + 14, oy + 30), fill=(255, 181, 59, 255))
            draw.line((ox + 16, oy + 1, ox + 16, oy + 30), fill=(255, 218, 115, 255))
            for y in (5, 14, 23):
                draw.line((ox + 5, oy + y, ox + 12, oy + y + 4), fill=(184, 37, 4, 255))
                draw.line((ox + 20, oy + y + 4, ox + 27, oy + y), fill=(184, 37, 4, 255))
        elif material == "rift":
            draw.line((ox + 6, oy, ox + 12, oy + 9, ox + 9, oy + 17, ox + 21, oy + 31),
                      fill=(179, 88, 216, 255), width=2)
    path = ASSETS / "textures/item" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    return image


def element(start, end, material, *, rotation=None, origin=None, axis="z"):
    assert all(-16 <= v <= 32 for v in (*start, *end))
    assert all(a < b for a, b in zip(start, end))
    ox, oy, _ = MATERIALS[material]
    # A narrow inset avoids atlas bleeding from a neighbouring material tile.
    uv = [(ox + 1) / 8, (oy + 1) / 8, (ox + 31) / 8, (oy + 31) / 8]
    result = {
        "from": list(start), "to": list(end),
        "faces": {face: {"uv": uv, "texture": "#atlas"} for face in FACES},
    }
    if rotation is not None:
        assert rotation in (-45, -22.5, 0, 22.5, 45)
        result["rotation"] = {"angle": rotation, "axis": axis, "origin": list(origin), "rescale": False}
    if material in EMISSIVE:
        result["shade"] = False
        result["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return result


def apex_fang():
    e = []

    def box(a, b, material, **kwargs):
        e.append(element(a, b, material, **kwargs))

    # Wrapped, offset tang and a faceted pommel. These form an actual thick grip
    # instead of a generated flat sword sprite.
    box((6.8, -11.5, 6.65), (9.2, -4, 9.1), "grip")
    box((6.4, -12.5, 6.25), (9.6, -10.6, 9.6), "armor")
    box((6.65, -12.6, 6.1), (9.35, -12.15, 9.7), "edge")
    box((7.6, -12.9, 6.05), (8.4, -11.8, 6.2), "energy")
    for y in (-9.9, -8.3, -6.7, -5.1):
        box((6.63, y, 6.5), (9.37, y + .42, 9.25), "metal")
    box((5.25, -4.25, 6.0), (11.35, -2.7, 9.9), "armor")
    box((4.4, -3.9, 6.5), (6.25, -1.25, 9.25), "edge_dark",
        rotation=-22.5, origin=(5.35, -2.6, 7.8))
    box((10.25, -3.9, 6.5), (12.1, -1.25, 9.25), "edge_dark",
        rotation=22.5, origin=(11.2, -2.6, 7.8))
    box((6.45, -2.9, 6.25), (10.45, 4.4, 9.6), "armor")
    box((6.2, -.25, 5.95), (10.7, 3.4, 6.3), "inscription")
    box((7.35, .55, 5.8), (9.55, 2.75, 5.98), "energy",
        rotation=45, origin=(8.45, 1.65, 5.9))
    box((8.12, .55, 5.68), (8.77, 2.75, 5.82), "core")

    # Long primary edge: a narrow cutting rail, three taper sections and a
    # swept tip, flanked by a shorter second blade with its own glowing channel.
    box((5.15, 3.1, 6.6), (8.5, 22.5, 8.75), "metal")
    box((5.15, 21.9, 6.7), (7.75, 26.2, 8.55), "metal")
    # Five narrowing facets close the main blade into an actual needle tip.
    # Every facet shares one 22.5-degree sweep; the terminal face is only
    # 0.18 units wide, so the silhouette cannot read as a blunt machine rail.
    tip_origin = (6.4, 26.0, 7.6)
    for left, right, low, high, front, back in (
        (5.4, 7.4, 25.75, 27.8, 6.8, 8.4),
        (5.7, 7.1, 27.65, 29.2, 6.94, 8.26),
        (6.0, 6.8, 29.05, 30.4, 7.12, 8.08),
        (6.17, 6.63, 30.25, 31.2, 7.27, 7.93),
        (6.31, 6.49, 31.05, 31.75, 7.46, 7.74),
    ):
        box((left, low, front), (right, high, back), "edge_dark",
            rotation=-22.5, origin=tip_origin)
        box((left - .06, low, front - .02), (left + .13, high, back + .02), "edge",
            rotation=-22.5, origin=tip_origin)
    box((4.65, 3.2, 6.55), (5.22, 25.8, 8.8), "edge")
    box((4.95, 24.75, 6.66), (5.45, 26.0, 8.56), "edge",
        rotation=-22.5, origin=tip_origin)
    box((7.75, 3.65, 6.9), (8.65, 21.4, 8.55), "armor")
    box((6.38, 3.1, 6.37), (6.81, 25.0, 6.59), "energy")
    box((6.5, 4.0, 6.22), (6.69, 24.6, 6.4), "core")
    box((6.38, 3.1, 8.74), (6.81, 25.0, 8.94), "energy")
    box((6.31, 25.7, 6.68), (6.49, 28.9, 6.86), "energy",
        rotation=-22.5, origin=tip_origin)
    box((9.75, 3.0, 7.1), (12.2, 20.1, 9.35), "metal")
    # The shorter auxiliary blade sweeps outward into a separate four-stage
    # cutting point. Offset tips stay distinct even in the GUI oblique view.
    auxiliary_origin = (11.15, 20.0, 8.2)
    for left, right, low, high, front, back in (
        (10.35, 11.95, 19.4, 22.0, 7.2, 9.2),
        (10.6, 11.7, 21.85, 23.75, 7.4, 9.0),
        (10.85, 11.45, 23.6, 25.3, 7.66, 8.74),
        (11.06, 11.24, 25.15, 26.7, 8.03, 8.37),
    ):
        box((left, low, front), (right, high, back), "edge_dark",
            rotation=-22.5, origin=auxiliary_origin)
        box((right - .12, low, front - .02), (right + .08, high, back + .02), "edge",
            rotation=-22.5, origin=auxiliary_origin)
    box((12.08, 3.2, 7.0), (12.57, 21.3, 9.42), "edge")
    box((10.85, 3.6, 6.9), (11.27, 19.9, 7.12), "energy")
    box((10.85, 3.6, 9.33), (11.27, 19.9, 9.55), "energy")
    box((8.55, 4.2, 7.3), (9.8, 5.1, 9.05), "joint")
    box((8.55, 9.9, 7.3), (9.8, 10.75, 9.05), "joint")
    for y in (5.7, 12.0, 18.4):
        box((7.78, y, 7.25), (9.5, y + 3.5, 8.45), "armor",
            rotation=-22.5, origin=(8.65, y + 1.75, 7.85))
        box((8.4, y + 1.0, 7.06), (9.24, y + 1.45, 7.28), "red")
    # A compact reverse spur reinforces the forearm-blade silhouette.
    box((11.5, .75, 7.2), (13.1, 5.7, 9.2), "armor",
        rotation=-22.5, origin=(12.3, 3.2, 8.2))
    box((12.45, .7, 7.15), (12.95, 5.2, 9.25), "edge",
        rotation=-22.5, origin=(12.3, 3.2, 8.2))
    return {
        "gui_light": "front", "textures": {"atlas": "apexpredator:item/apex_fang", "particle": "apexpredator:item/apex_fang"},
        "elements": e,
        "display": {
            "gui": {"rotation": [30, -40, -30], "translation": [0, -1.5, 0], "scale": [.4, .4, .4]},
            "ground": {"rotation": [0, 0, 90], "translation": [0, 3, 0], "scale": [.34, .34, .34]},
            "fixed": {"rotation": [0, 0, -30], "translation": [0, -2.4, 0], "scale": [.43, .43, .43]},
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, .5], "scale": [.58, .58, .58]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, .5], "scale": [.58, .58, .58]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [.46, .46, .46]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [.46, .46, .46]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 11, 0], "scale": [.4, .4, .4]},
        },
    }


def apex_beacon():
    e = []

    def box(a, b, material, **kwargs):
        e.append(element(a, b, material, **kwargs))

    box((2.25, 1, 2.25), (13.75, 3.1, 13.75), "obsidian")
    box((2.5, 2.55, 2.5), (13.5, 4.1, 13.5), "armor")
    box((3.7, 3.6, 3.7), (12.3, 5.8, 12.3), "metal")
    box((4.7, 5.4, 4.7), (11.3, 7.1, 11.3), "black")
    box((5.8, 7.05, 5.8), (10.2, 7.5, 10.2), "energy")
    box((6.7, 7.5, 6.7), (9.3, 12.9, 9.3), "energy")
    box((7.4, 8.0, 7.4), (8.6, 13.3, 8.6), "core")
    box((6.4, 10.3, 6.4), (9.6, 13.5, 9.6), "core",
        rotation=45, axis="y", origin=(8, 11.9, 8))
    box((6.85, 13.0, 6.85), (9.15, 14.2, 9.15), "eye",
        rotation=45, axis="y", origin=(8, 13.6, 8))
    for x, z in ((3.4, 3.4), (11.4, 3.4), (3.4, 11.4), (11.4, 11.4)):
        box((x, 4.4, z), (x + 1.2, 11.7, z + 1.2), "armor")
        box((x - .35, 10.1, z - .35), (x + 1.55, 12.0, z + 1.55), "edge_dark")
        box((x + .35, 5.1, z - .12), (x + .85, 10.7, z + .1), "energy")
        box((x + .35, 5.1, z + 1.1), (x + .85, 10.7, z + 1.32), "energy")
        box((x, 1.75, z), (x + 1.2, 3.0, z + 1.2), "rift")
    box((3.4, 11.5, 3.4), (12.6, 12.25, 4.6), "metal")
    box((3.4, 11.5, 11.4), (12.6, 12.25, 12.6), "metal")
    box((3.4, 11.5, 4.6), (4.6, 12.25, 11.4), "metal")
    box((11.4, 11.5, 4.6), (12.6, 12.25, 11.4), "metal")
    for a, b in [((4.7, 3.3, 2.25), (11.3, 3.65, 2.45)),
                 ((4.7, 3.3, 13.55), (11.3, 3.65, 13.75)),
                 ((2.25, 3.3, 4.7), (2.45, 3.65, 11.3)),
                 ((13.55, 3.3, 4.7), (13.75, 3.65, 11.3))]:
        box(a, b, "energy")
    box((6.5, 4.1, 3.38), (9.5, 5.1, 3.72), "inscription")
    box((7.7, 4.0, 3.2), (8.3, 5.0, 3.4), "eye")
    return {
        "gui_light": "front", "textures": {"atlas": "apexpredator:item/apex_beacon", "particle": "apexpredator:item/apex_beacon"},
        "elements": e,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, .2, 0], "scale": [.95, .95, .95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.5, .5, .5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.75, .75, .75]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 1], "scale": [.5, .5, .5]},
            "thirdperson_lefthand": {"rotation": [75, -45, 0], "translation": [0, 2, 1], "scale": [.5, .5, .5]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, .5, 0], "scale": [.6, .6, .6]},
            "firstperson_lefthand": {"rotation": [0, -45, 0], "translation": [0, .5, 0], "scale": [.6, .6, .6]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [.8, .8, .8]},
        },
    }


RECIPES = {
    "apex_beacon": {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["NEN", "CSC", "OEO"],
        "key": {
            "N": {"item": "minecraft:netherite_ingot"}, "E": {"item": "minecraft:ender_eye"},
            "C": {"item": "minecraft:echo_shard"}, "S": {"item": "minecraft:nether_star"},
            "O": {"item": "minecraft:crying_obsidian"},
        }, "result": {"item": "apexpredator:apex_beacon", "count": 1},
    },
    "apex_fang": {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["CNC", "CSC", "BNB"],
        "key": {
            "C": {"item": "apexpredator:apex_core"}, "N": {"item": "minecraft:netherite_ingot"},
            "S": {"item": "minecraft:netherite_sword"}, "B": {"item": "minecraft:blaze_rod"},
        }, "result": {"item": "apexpredator:apex_fang", "count": 1},
    },
}


def advancement(name, icon, criteria, frame, parent=None):
    display = {
        "icon": {"item": f"apexpredator:{icon}"},
        "title": {"translate": f"advancement.apexpredator.{name}.title"},
        "description": {"translate": f"advancement.apexpredator.{name}.description"},
        "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False,
    }
    value = {"display": display, "criteria": criteria}
    if parent:
        value["parent"] = f"apexpredator:{parent}"
    else:
        display["background"] = "minecraft:textures/block/crying_obsidian.png"
    return value


def unlock(name, ingredient):
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_ingredient": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [ingredient]}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": f"apexpredator:{name}"}},
        }, "requirements": [["has_ingredient", "has_the_recipe"]],
        "rewards": {"recipes": [f"apexpredator:{name}"]},
    }


def update_feedback():
    # Merge only owned progression keys; existing boss/sound translations stay
    # intact. The reserved ritual item may move between hands/inventory slots.
    for locale, changes in LOCALIZED_FEEDBACK.items():
        path = ASSETS / "lang" / f"{locale}.json"
        language = json.loads(path.read_text(encoding="utf-8"))
        language.update(changes)
        dump(path, language)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language-only", action="store_true", help="Refresh ritual feedback without touching models/textures/data")
    options = parser.parse_args()
    update_feedback()
    if options.language_only:
        return
    for name, model, seed in (("apex_beacon", apex_beacon(), 15001), ("apex_fang", apex_fang(), 15002)):
        atlas(name, seed)
        dump(ASSETS / "models/item" / f"{name}.json", model)
        dump(DATA / "recipes" / f"{name}.json", RECIPES[name])
        materials = Counter(e.get("forge_data", {}).get("block_light", 0) for e in model["elements"])
        print(f"{name}: {len(model['elements'])} solid elements, {materials[15]} fullbright accents, {SIZE}px dedicated atlas")
    dump(DATA / "advancements/the_predator_awakens.json", advancement(
        "the_predator_awakens", "apex_beacon", {"summoned": {"trigger": "minecraft:impossible"}}, "task"))
    dump(DATA / "advancements/apex_predator.json", advancement(
        "apex_predator", "apex_core", {"apex_defeated": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": {"type": "apexpredator:apex"}}}},
        "challenge", "the_predator_awakens"))
    dump(DATA / "advancements/become_the_apex.json", advancement(
        "become_the_apex", "apex_fang", {"fang_crafted": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": "apexpredator:apex_fang"}}},
        "goal", "apex_predator"))
    dump(DATA / "advancements/recipes/apex_beacon.json", unlock("apex_beacon", "minecraft:echo_shard"))
    dump(DATA / "advancements/recipes/apex_fang.json", unlock("apex_fang", "apexpredator:apex_core"))


if __name__ == "__main__":
    main()
