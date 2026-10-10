#!/usr/bin/env python3
"""Edit Apex in Blockbench, then explicitly export geometry without touching animations.

The coordinate and face conversions follow Blockbench 5's project/Bedrock codecs
and the official GeckoLib Models & Animations plugin. Runtime JSON is used only
by the explicit one-time import command; it is never read during an export.
"""
from __future__ import annotations

import argparse
import base64
import copy
from fractions import Fraction
import json
import math
from pathlib import Path
import struct
import uuid

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/apexpredator"
PROJECT = ROOT / "models/blockbench/apex.bbmodel"
GEOMETRY = ASSETS / "geo/apex.geo.json"
ANIMATIONS = ASSETS / "animations/apex.animation.json"
FACES = ("north", "south", "east", "west", "up", "down")
NAMESPACE = uuid.UUID("ca236001-1707-5ab6-a586-c3abcdf4c5c9")


def identity(label):
    return str(uuid.uuid5(NAMESPACE, label))


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")


def vector(value, context):
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError(f"{context}: expected an XYZ vector")
    result = [float(v) for v in value]
    if not all(math.isfinite(v) for v in result):
        raise ValueError(f"{context}: non-finite coordinates")
    return result


def pivot(value):
    return [-value[0], value[1], value[2]]


def rotation(value):
    return [-value[0], -value[1], value[2]]


def tidy(value):
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("Non-finite value")
        return int(value) if value.is_integer() else round(value, 12)
    if isinstance(value, list):
        return [tidy(v) for v in value]
    if isinstance(value, dict):
        return {k: tidy(v) for k, v in value.items()}
    return value


def embedded_texture(path, identifier):
    raw = Path(path).read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path}: expected PNG")
    width, height = struct.unpack(">II", raw[16:24])
    return {
        "name": Path(path).name, "id": str(identifier),
        "uuid": identity("texture:" + Path(path).name),
        "width": width, "height": height, "uv_width": width, "uv_height": height,
        "namespace": "apexpredator", "folder": "entity", "mode": "bitmap",
        "internal": True, "saved": True, "visible": True,
        "render_mode": "default", "render_sides": "auto", "particle": False,
        "source": "data:image/png;base64," + base64.b64encode(raw).decode("ascii"),
    }


def texture_bytes(project, name):
    matches = [t for t in project.get("textures", []) if t.get("name") == name]
    if len(matches) != 1:
        raise ValueError(f"Exactly one embedded {name} texture is required")
    source = matches[0].get("source", "")
    if not source.startswith("data:image/png;base64,"):
        raise ValueError(f"{name}: save the project with embedded PNG textures enabled")
    raw = base64.b64decode(source.split(",", 1)[1], validate=True)
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{name}: malformed embedded PNG")
    return raw


def animation_points(channel, values):
    values = list(values)
    if channel in ("position", "rotation"):
        values[0] = -values[0]
    if channel == "rotation":
        values[1] = -values[1]
    # Blockbench stores Molang values as strings, including numeric constants.
    return [{axis: str(value) for axis, value in zip("xyz", values)}]


def animation_snapping(times):
    """A useful UI grid without quantizing the stored raw keyframe timestamps.

    Blockbench clamps snapping to 10..500. Some charge keyframes need a 1/10000
    second grid, so no legal UI snapping can exactly represent every frame.
    The explicit animation exporter below deliberately bypasses Timeline.snapTime.
    """
    required = 20
    for time in times:
        rational = Fraction(str(time)).limit_denominator(10000)
        if not math.isclose(float(rational), float(time), rel_tol=0, abs_tol=1e-10):
            return 500
        required = math.lcm(required, rational.denominator)
        if required > 500:
            return 500
    return required


def import_animations(animations, bone_ids):
    output = []
    for name, clip in animations["animations"].items():
        extra = set(clip) - {"loop", "animation_length", "bones"}
        if extra:
            raise ValueError(f"{name}: unsupported clip attributes {sorted(extra)}")
        loop = "hold" if clip["loop"] == "hold_on_last_frame" else "loop" if clip["loop"] else "once"
        animation = {
            "uuid": identity("animation:" + name), "name": name, "loop": loop,
            "length": clip["animation_length"], "override": False, "snapping": 20,
            "selected": False, "animators": {},
        }
        for bone, channels in clip["bones"].items():
            if bone not in bone_ids:
                raise ValueError(f"{name}: missing animation bone {bone}")
            frames = []
            for channel, values in channels.items():
                if channel not in ("rotation", "position", "scale"):
                    raise ValueError(f"{name}/{bone}: unsupported channel {channel}")
                times = {"0": values} if isinstance(values, list) else values
                for time, sample in times.items():
                    data = sample if isinstance(sample, dict) else {"vector": sample}
                    if set(data) - {"vector", "easing"}:
                        raise ValueError(f"{name}/{bone}/{time}: unsupported keyframe attributes")
                    frame = {
                        "uuid": identity(f"frame:{name}:{bone}:{channel}:{time}"),
                        "channel": channel, "time": float(time),
                        "data_points": animation_points(channel, data["vector"]),
                        "interpolation": "linear",
                    }
                    if "easing" in data:
                        frame["easing"] = data["easing"]
                    frames.append(frame)
            animation["animators"][bone_ids[bone]] = {"name": bone, "type": "bone", "keyframes": frames}
        animation["snapping"] = animation_snapping([animation["length"], *(
            frame["time"] for animator in animation["animators"].values() for frame in animator["keyframes"]
        )])
        output.append(animation)
    return output


def import_project(geometry, animations, base_texture, glow_texture):
    if len(geometry.get("minecraft:geometry", [])) != 1:
        raise ValueError("Expected exactly one geometry")
    geo = geometry["minecraft:geometry"][0]
    description = geo["description"]
    bone_ids = {b["name"]: identity("bone:" + b["name"]) for b in geo["bones"]}
    if len(bone_ids) != len(geo["bones"]):
        raise ValueError("Duplicate bone names")
    groups, elements, nodes = [], [], {}
    for index, bone in enumerate(geo["bones"]):
        if set(bone) - {"name", "parent", "pivot", "rotation", "cubes", "mirror", "reset", "material", "binding"}:
            raise ValueError(f"{bone['name']}: unsupported bone attributes")
        name = bone["name"]
        group = {
            "name": name, "uuid": bone_ids[name], "origin": pivot(bone["pivot"]),
            "rotation": rotation(bone.get("rotation", [0, 0, 0])),
            "color": index % 8, "export": True, "visibility": True,
            "locked": False, "autouv": 0, "isOpen": name == "root",
        }
        for source, target in (("mirror", "mirror_uv"), ("reset", "reset"), ("material", "material"), ("binding", "bedrock_binding")):
            if source in bone:
                group[target] = bone[source]
        groups.append(group)
        node = {"uuid": bone_ids[name], "isOpen": name == "root", "children": []}
        nodes[name] = node
        for count, cube in enumerate(bone.get("cubes", [])):
            if set(cube) - {"origin", "size", "rotation", "pivot", "uv", "inflate", "mirror"}:
                raise ValueError(f"{name}: unsupported cube attributes")
            origin, size = cube["origin"], cube["size"]
            start = [-(origin[0] + size[0]), origin[1], origin[2]]
            identifier = identity(f"cube:{name}:{count}")
            item = {
                "name": f"{name}_{count + 1:03}", "type": "cube", "uuid": identifier,
                "from": start, "to": [start[i] + size[i] for i in range(3)],
                "origin": pivot(cube.get("pivot", [0, 0, 0])),
                "rotation": rotation(cube.get("rotation", [0, 0, 0])),
                "autouv": 0, "color": index % 8, "box_uv": isinstance(cube["uv"], list),
                "faces": {},
            }
            if "inflate" in cube:
                item["inflate"] = cube["inflate"]
            if "mirror" in cube:
                item["mirror_uv"] = cube["mirror"]
            if item["box_uv"]:
                item["uv_offset"] = cube["uv"]
            for direction in FACES:
                face = cube["uv"].get(direction) if not item["box_uv"] else None
                if face:
                    uv = list(face["uv"]) + [face["uv"][i] + face["uv_size"][i] for i in range(2)]
                    if direction in ("up", "down"):
                        uv = uv[2:] + uv[:2]
                    item["faces"][direction] = {"uv": uv, "texture": 0}
                    if "uv_rotation" in face:
                        item["faces"][direction]["rotation"] = face["uv_rotation"]
                    if "material_instance" in face:
                        item["faces"][direction]["material_name"] = face["material_instance"]
                else:
                    item["faces"][direction] = {"uv": [0, 0, 0, 0], "texture": 0 if item["box_uv"] else None}
            elements.append(item)
            node["children"].append(identifier)
    outliner = []
    for bone in geo["bones"]:
        if "parent" in bone:
            nodes[bone["parent"]]["children"].append(nodes[bone["name"]])
        else:
            outliner.append(nodes[bone["name"]])
    bounds = description.get("visible_bounds_offset", [0, 0, 0])
    if bounds[0] or bounds[2]:
        raise ValueError("Blockbench visible_box cannot encode nonzero X/Z bounds offsets")
    return {
        "meta": {"format_version": "5.0", "model_format": "geckolib_model", "box_uv": False},
        "name": "Apex", "model_identifier": description["identifier"].removeprefix("geometry."),
        "geckolib_modid": "apexpredator", "geckolib_model_type": "Entity",
        "resolution": {"width": description["texture_width"], "height": description["texture_height"]},
        "visible_box": [description["visible_bounds_width"], description["visible_bounds_height"], bounds[1]],
        "elements": elements, "groups": groups, "outliner": outliner,
        "textures": [embedded_texture(base_texture, 0), embedded_texture(glow_texture, 1)],
        "animations": import_animations(animations, bone_ids),
    }


def project_nodes(project):
    if project.get("meta", {}).get("model_format") != "geckolib_model":
        raise ValueError("Apex must use the GeckoLib Animated Model format")
    elements = {e["uuid"]: e for e in project.get("elements", [])}
    groups = {g["uuid"]: copy.deepcopy(g) for g in project.get("groups", [])}
    if len(elements) != len(project.get("elements", [])) or len(groups) != len(project.get("groups", [])):
        raise ValueError("Duplicate element/group UUID")
    if set(elements) & set(groups):
        raise ValueError("An element and group share a UUID")
    parents, children, seen = {}, {}, set()
    def walk(nodes, parent=None):
        for node in nodes:
            identifier = node if isinstance(node, str) else node["uuid"]
            if identifier in seen:
                raise ValueError(f"Repeated/cyclic outliner reference {identifier}")
            seen.add(identifier)
            parents[identifier] = parent
            if isinstance(node, dict):
                if identifier not in groups:
                    if "name" not in node:
                        raise ValueError(f"Missing group record {identifier}")
                    groups[identifier] = {k: v for k, v in node.items() if k != "children"}
                children[identifier] = node.get("children", [])
                walk(node.get("children", []), identifier)
            elif identifier not in elements:
                raise ValueError(f"Missing element {identifier}")
            elif parent is None:
                raise ValueError("All Apex cubes must belong to a bone")
    walk(project.get("outliner", []))
    if seen != set(elements) | set(groups):
        raise ValueError("Every element and bone must appear in the outliner exactly once")
    names = [g["name"] for g in groups.values()]
    if len(names) != len(set(names)):
        raise ValueError("Duplicate bone names")
    return elements, groups, parents, children


def export_geometry(project):
    elements, groups, parents, children = project_nodes(project)
    bones = []
    for identifier, group in groups.items():
        if group.get("export", True) is False:
            raise ValueError("Do not disable Apex bone export; animations depend on these bones")
        bone = {"name": group["name"], "pivot": pivot(vector(group.get("origin", [0, 0, 0]), "bone origin"))}
        if parents[identifier]:
            bone["parent"] = groups[parents[identifier]]["name"]
        angles = rotation(vector(group.get("rotation", [0, 0, 0]), "bone rotation"))
        if any(angles):
            bone["rotation"] = angles
        for source, target in (("mirror_uv", "mirror"), ("reset", "reset"), ("material", "material"), ("bedrock_binding", "binding")):
            if group.get(source):
                bone[target] = group[source]
        cubes = []
        for reference in children[identifier]:
            if isinstance(reference, dict):
                continue
            element = elements[reference]
            if element.get("export", True) is False:
                continue
            if element.get("type", "cube") != "cube":
                raise ValueError("Apex geometry export supports editable cubes only")
            start, end = vector(element["from"], "cube from"), vector(element["to"], "cube to")
            size = [end[i] - start[i] for i in range(3)]
            if any(v < 0 for v in size):
                raise ValueError("Cube from must be less than or equal to to on every axis")
            if any(v != 1 for v in element.get("stretch", [1, 1, 1])):
                raise ValueError("Stretched cubes cannot be losslessly exported; resize the cube instead")
            cube = {"origin": [-(start[0] + size[0]), start[1], start[2]], "size": size}
            angles = rotation(vector(element.get("rotation", [0, 0, 0]), "cube rotation"))
            cube_pivot = pivot(vector(element.get("origin", [0, 0, 0]), "cube pivot"))
            if any(angles) or any(cube_pivot):
                cube["pivot"] = cube_pivot
                cube["rotation"] = angles
            if element.get("inflate"):
                cube["inflate"] = float(element["inflate"])
            if element.get("box_uv", project.get("meta", {}).get("box_uv", False)):
                cube["uv"] = element.get("uv_offset", [0, 0])
                if bool(element.get("mirror_uv", False)) != bool(bone.get("mirror", False)):
                    cube["mirror"] = bool(element.get("mirror_uv", False))
            else:
                cube["uv"] = {}
                for direction in FACES:
                    face = element.get("faces", {}).get(direction, {})
                    if face.get("texture") is None:
                        continue
                    uv = list(face["uv"])
                    if direction in ("up", "down"):
                        uv = uv[2:] + uv[:2]
                    data = {"uv": uv[:2], "uv_size": [uv[2] - uv[0], uv[3] - uv[1]]}
                    if face.get("rotation"):
                        data["uv_rotation"] = face["rotation"]
                    if face.get("material_name"):
                        data["material_instance"] = face["material_name"]
                    cube["uv"][direction] = data
            cubes.append(cube)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    size = project["resolution"]
    visible = vector(project.get("visible_box", [4.5, 3.8, 1.4]), "visible bounds")
    return tidy({
        "format_version": "1.12.0", "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + project["model_identifier"].removeprefix("geometry."),
                "texture_width": size["width"], "texture_height": size["height"],
                "visible_bounds_width": visible[0], "visible_bounds_height": visible[1],
                "visible_bounds_offset": [0, visible[2], 0],
            }, "bones": bones,
        }],
    })


def animation_timeline(project):
    _, groups, _, _ = project_nodes(project)
    result = {}
    for clip in project.get("animations", []):
        name = clip["name"]
        if name in result:
            raise ValueError(f"Duplicate animation name {name}")
        bones = {}
        for identifier, animator in clip.get("animators", {}).items():
            if identifier not in groups:
                raise ValueError(f"{name}: animation references a missing bone")
            channels = {}
            for frame in animator.get("keyframes", []):
                channel = frame["channel"]
                if channel not in ("rotation", "position", "scale"):
                    raise ValueError(f"{name}: unsupported animation channel {channel}")
                if len(frame["data_points"]) != 1:
                    raise ValueError(f"{name}: cannot silently collapse pre/post keyframes")
                point = frame["data_points"][0]
                values = [float(point[axis]) for axis in "xyz"]
                if channel in ("position", "rotation"):
                    values[0] *= -1
                if channel == "rotation":
                    values[1] *= -1
                if frame.get("interpolation", "linear") != "linear":
                    raise ValueError(f"{name}: interpolation changed")
                time = float(frame["time"])
                frames = channels.setdefault(channel, {})
                if time in frames:
                    raise ValueError(f"{name}: duplicate channel timestamp")
                frames[time] = {"vector": values, "easing": frame.get("easing")}
            bones[groups[identifier]["name"]] = channels
        result[name] = {"length": clip["length"], "loop": clip["loop"], "bones": bones}
    return result


def export_animations(project):
    """Explicit preview export from raw BB keyframes, without snapping timestamps.

    No runtime JSON template or legacy authoring script is read. This command is
    deliberately separate from geometry export and cannot replace the installed
    runtime animation file; callers must review a separate preview before using it.
    """
    timeline = animation_timeline(project)
    clips = {}
    loop_values = {"once": False, "loop": True, "hold": "hold_on_last_frame"}
    for name, clip in timeline.items():
        if clip["loop"] not in loop_values:
            raise ValueError(f"{name}: unsupported loop mode")
        if not math.isfinite(float(clip["length"])) or clip["length"] < 0:
            raise ValueError(f"{name}: invalid animation length")
        bones = {}
        for bone, channels in clip["bones"].items():
            bones[bone] = {}
            for channel, frames in channels.items():
                ordered = sorted(frames.items())
                for time, sample in ordered:
                    if not math.isfinite(time) or time < 0 or not all(math.isfinite(v) for v in sample["vector"]):
                        raise ValueError(f"{name}/{bone}: invalid timestamp or animation value")
                if len(ordered) == 1 and ordered[0][0] == 0 and ordered[0][1]["easing"] is None:
                    bones[bone][channel] = ordered[0][1]["vector"]
                    continue
                output = {}
                for time, sample in ordered:
                    # str(float) round-trips the original stored binary timestamp;
                    # don't round to display precision or the editor snapping grid.
                    timestamp = str(int(time)) if time.is_integer() else str(time)
                    data = {"vector": sample["vector"]}
                    if sample["easing"] is not None:
                        data["easing"] = sample["easing"]
                    output[timestamp] = data
                bones[bone][channel] = output
        clips[name] = {"loop": loop_values[clip["loop"]], "animation_length": clip["length"], "bones": bones}
    return {"format_version": "1.8.0", "animations": clips}


def runtime_timeline(animations):
    result = {}
    for name, clip in animations["animations"].items():
        bones = {}
        for bone, channels in clip["bones"].items():
            converted = {}
            for channel, values in channels.items():
                times = {"0": values} if isinstance(values, list) else values
                converted[channel] = {
                    float(t): {"vector": v["vector"] if isinstance(v, dict) else v,
                               "easing": v.get("easing") if isinstance(v, dict) else None}
                    for t, v in times.items()
                }
            bones[bone] = converted
        loop = "hold" if clip["loop"] == "hold_on_last_frame" else "loop" if clip["loop"] else "once"
        result[name] = {"length": clip["animation_length"], "loop": loop, "bones": bones}
    return result


def assert_equivalent(actual, expected, path="root", tolerance=1e-10):
    if isinstance(actual, (int, float)) and not isinstance(actual, bool) and isinstance(expected, (int, float)) and not isinstance(expected, bool):
        if not math.isclose(actual, expected, rel_tol=0, abs_tol=tolerance):
            raise ValueError(f"{path}: {actual!r} != {expected!r}")
    elif isinstance(actual, dict) and isinstance(expected, dict):
        if actual.keys() != expected.keys():
            raise ValueError(f"{path}: field names differ {set(actual) ^ set(expected)}")
        for key in actual:
            assert_equivalent(actual[key], expected[key], f"{path}/{key}", tolerance)
    elif isinstance(actual, list) and isinstance(expected, list):
        if len(actual) != len(expected):
            raise ValueError(f"{path}: lengths differ")
        for i, (a, e) in enumerate(zip(actual, expected)):
            assert_equivalent(a, e, f"{path}/{i}", tolerance)
    elif actual != expected:
        raise ValueError(f"{path}: {actual!r} != {expected!r}")


def check(project, geometry, animations, base_texture=None, glow_texture=None):
    exported = export_geometry(project)
    # JSON object ordering does not change a geometry, but bone/cube order is retained.
    assert_equivalent(exported, geometry)
    assert_equivalent(animation_timeline(project), runtime_timeline(animations))
    for filename, reference in (("apex.png", base_texture), ("apex_glow.png", glow_texture)):
        raw = texture_bytes(project, filename)
        if reference and raw != Path(reference).read_bytes():
            raise ValueError(f"{filename}: embedded texture differs from runtime PNG")
        width, height = struct.unpack(">II", raw[16:24])
        if (width, height) != (project["resolution"]["width"], project["resolution"]["height"]):
            raise ValueError(f"{filename}: texture dimensions differ from project resolution")
    return {"bones": len(exported["minecraft:geometry"][0]["bones"]),
            "cubes": len(project["elements"]), "animations": len(project["animations"]),
            "keyframes": sum(len(a["keyframes"]) for c in project["animations"] for a in c["animators"].values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    exporter = sub.add_parser("export", help="Export edited bbmodel geometry; never writes animation files")
    exporter.add_argument("--project", type=Path, default=PROJECT)
    exporter.add_argument("--output", type=Path, required=True)
    exporter.add_argument("--textures-dir", type=Path, help="Explicitly export both embedded PNGs into textures-dir/entity/")
    exporter.add_argument("--base-texture-output", type=Path)
    exporter.add_argument("--glow-texture-output", type=Path)
    animation_exporter = sub.add_parser("export-animations", help="Explicitly export raw animation timestamps to a separate review file (never the runtime file)")
    animation_exporter.add_argument("--project", type=Path, default=PROJECT)
    animation_exporter.add_argument("--output", type=Path, required=True)
    checker = sub.add_parser("check", help="Check geometry, every animation keyframe and embedded PNGs")
    checker.add_argument("--project", type=Path, default=PROJECT)
    checker.add_argument("--geometry", type=Path, default=GEOMETRY)
    checker.add_argument("--animations", type=Path, default=ANIMATIONS)
    checker.add_argument("--base-texture", type=Path, default=ASSETS / "textures/entity/apex.png")
    checker.add_argument("--glow-texture", type=Path, default=ASSETS / "textures/entity/apex_glow.png")
    importer = sub.add_parser("import-legacy", help="One-time migration only: refuses to overwrite an existing authoritative project")
    importer.add_argument("--geometry", type=Path, default=GEOMETRY)
    importer.add_argument("--animations", type=Path, default=ANIMATIONS)
    importer.add_argument("--base-texture", type=Path, default=ASSETS / "textures/entity/apex.png")
    importer.add_argument("--glow-texture", type=Path, default=ASSETS / "textures/entity/apex_glow.png")
    importer.add_argument("--output", type=Path, default=PROJECT)
    args = parser.parse_args()
    try:
        if args.command == "import-legacy":
            if args.output.exists():
                raise ValueError("The authoritative bbmodel already exists. Import to a separate comparison file instead.")
            data = import_project(read_json(args.geometry), read_json(args.animations), args.base_texture, args.glow_texture)
            check(data, read_json(args.geometry), read_json(args.animations), args.base_texture, args.glow_texture)
            write_json(args.output, data)
            print(f"Imported once: {args.output}")
        else:
            data = read_json(args.project)
            if args.command == "check":
                print(json.dumps(check(data, read_json(args.geometry), read_json(args.animations), args.base_texture, args.glow_texture)))
            elif args.command == "export-animations":
                protected = [ANIMATIONS.resolve(), GEOMETRY.resolve(), args.project.resolve()]
                if args.output.resolve() in protected:
                    raise ValueError("Animation preview export cannot overwrite runtime animations, geometry, or the authoritative project")
                write_json(args.output, export_animations(data))
                print(f"Exported raw animation timestamps for review: {args.output}; runtime animations left unchanged")
            else:
                geometry = export_geometry(data)
                if args.textures_dir:
                    if args.base_texture_output or args.glow_texture_output:
                        raise ValueError("Use either --textures-dir or individual texture outputs")
                    args.base_texture_output = args.textures_dir / "entity/apex.png"
                    args.glow_texture_output = args.textures_dir / "entity/apex_glow.png"
                # Resolve and validate requested textures before writing any output.
                textures = [(output, texture_bytes(data, name)) for name, output in
                            (("apex.png", args.base_texture_output), ("apex_glow.png", args.glow_texture_output)) if output]
                protected = [ANIMATIONS.resolve(), args.project.resolve()]
                outputs = [args.output, *(output for output, _ in textures)]
                if any(path.resolve() in protected for path in outputs):
                    raise ValueError("Export cannot overwrite the authoritative project or protected animation file")
                if len({path.resolve() for path in outputs}) != len(outputs):
                    raise ValueError("Each export output needs a different destination")
                write_json(args.output, geometry)
                for output, raw in textures:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    output.write_bytes(raw)
                print(f"Exported geometry only: {args.output}; animations left unchanged")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
