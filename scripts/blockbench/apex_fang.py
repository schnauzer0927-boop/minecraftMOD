#!/usr/bin/env python3
"""Export the editable Blockbench Java item project to Minecraft 1.20.1.

The bbmodel is the authoring source. Geometry, UVs, display settings and painted
embedded textures are read from it; the old runtime JSON is never a template.
Native Blockbench groups identify the Forge fullbright accents and survive an
ordinary Ctrl+S. No Blockbench plugin or Python imaging dependency is required.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import copy
import json
import math
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT = ROOT / "models/blockbench/apex_fang.bbmodel"
DEFAULT_MODEL = ROOT / "src/main/resources/assets/apexpredator/models/item/apex_fang.json"
DEFAULT_TEXTURE = ROOT / "src/main/resources/assets/apexpredator/textures/item/apex_fang.png"
EMISSIVE_GROUPS = frozenset({"energy_channel", "core", "accent_left", "accent_right"})
FACES = ("down", "up", "north", "south", "west", "east")
ANGLES = frozenset({-45, -22.5, 0, 22.5, 45})
DISPLAY_SLOTS = frozenset({"thirdperson_righthand", "thirdperson_lefthand",
                           "firstperson_righthand", "firstperson_lefthand",
                           "head", "gui", "ground", "fixed"})


class ProjectError(ValueError):
    """An authoring feature cannot be represented safely by Minecraft 1.20.1."""


def number(value, context):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ProjectError(f"{context}: expected a finite number")
    return value


def vector(value, length, context):
    if not isinstance(value, list) or len(value) != length:
        raise ProjectError(f"{context}: expected {length} numbers")
    return [number(component, context) for component in value]


def canonical_angle(angle):
    """Keep equivalent native Blockbench display rotations deterministic.

    Blockbench's normal Save Project wraps +180 to -180. Minecraft renders
    these identically, but using the original +180 convention makes native
    save/export checks stable without changing the committed item model.
    Avoid touching angles already in range, retaining their float precision.
    """
    if -180 < angle <= 180:
        return angle
    wrapped = angle % 360
    return wrapped - 360 if wrapped > 180 else wrapped


def load_project(path):
    try:
        project = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ProjectError(f"Cannot read Blockbench project {path}: {error}") from error
    if project.get("meta", {}).get("model_format") != "java_block":
        raise ProjectError("Apex Fang must remain a Blockbench Java Block/Item project (java_block)")
    return project


def texture_resources(project):
    textures = project.get("textures", [])
    if not textures:
        raise ProjectError("The project has no embedded textures")
    result = {}
    used_ids = set()
    for index, texture in enumerate(textures):
        tag = str(texture.get("id", index))
        if not tag or tag in used_ids or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_/-" for character in tag):
            raise ProjectError(f"Texture {index}: invalid or duplicate Java texture id {tag!r}")
        used_ids.add(tag)
        namespace = texture.get("namespace") or "apexpredator"
        folder = texture.get("folder", "item").strip("/")
        name = Path(texture.get("name", "")).name
        if not name.endswith(".png"):
            raise ProjectError(f"Texture {index}: use a named .png texture")
        stem = name[:-4]
        resource = f"{namespace}:{folder + '/' if folder else ''}{stem}"
        if namespace != "apexpredator" or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_/-" for character in resource.split(":", 1)[1]):
            raise ProjectError(f"Texture {index}: use a lower-case apexpredator resource path")
        if any(part in {"", ".", ".."} for part in resource.split(":", 1)[1].split("/")):
            raise ProjectError(f"Texture {index}: unsafe resource path")
        result[index] = (tag, resource)
    return result


def png_bytes(texture):
    source = texture.get("source", "")
    prefix = "data:image/png;base64,"
    if not source.startswith(prefix):
        raise ProjectError(f"Texture {texture.get('name')}: enable embedded textures when saving in Blockbench")
    try:
        data = base64.b64decode(source[len(prefix):], validate=True)
    except binascii.Error as error:
        raise ProjectError(f"Texture {texture.get('name')}: invalid embedded PNG") from error
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ProjectError(f"Texture {texture.get('name')}: invalid PNG header")
    width, height = struct.unpack(">II", data[16:24])
    if width < 1 or height < 1:
        raise ProjectError("PNG dimensions must be positive")
    if texture.get("width", width) != width or texture.get("height", height) != height:
        raise ProjectError(f"Texture {texture.get('name')}: embedded image and declared dimensions differ")
    # Validate chunks so an accidentally truncated editor bitmap is never shipped.
    offset = 8
    has_idat = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ProjectError("Truncated embedded PNG chunk")
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        end = offset + 12 + length
        if end > len(data):
            raise ProjectError("Truncated embedded PNG payload")
        kind = data[offset + 4:offset + 8]
        expected = struct.unpack(">I", data[end - 4:end])[0]
        if binascii.crc32(data[offset + 4:end - 4]) & 0xffffffff != expected:
            raise ProjectError("Embedded PNG has a corrupt chunk checksum")
        has_idat |= kind == b"IDAT"
        offset = end
        if kind == b"IEND":
            if offset != len(data) or not has_idat:
                raise ProjectError("Invalid embedded PNG ending")
            return data
    raise ProjectError("Embedded PNG has no IEND chunk")


def group_membership(project):
    groups = {group["uuid"]: group for group in project.get("groups", [])}
    elements = {element["uuid"]: element for element in project.get("elements", [])}
    if len(groups) != len(project.get("groups", [])) or len(elements) != len(project.get("elements", [])):
        raise ProjectError("Duplicate Blockbench UUIDs")
    present_names = {group.get("name") for group in groups.values()}
    missing = EMISSIVE_GROUPS - present_names
    if missing:
        raise ProjectError("Keep the emitting group names (empty groups are allowed): " + ", ".join(sorted(missing)))
    for group in groups.values():
        if any(vector(group.get("rotation", [0, 0, 0]), 3, "Group rotation")):
            raise ProjectError(f"Group {group.get('name')}: Java item group rotations cannot be exported; rotate individual cubes instead")
    membership = {}
    visited_groups = set()

    def visit(nodes, emitted=False, enabled=True):
        for node in nodes:
            if isinstance(node, str):
                if node not in elements or node in membership:
                    raise ProjectError(f"Outliner has an unknown or duplicated cube UUID {node}")
                membership[node] = (emitted, enabled)
            elif isinstance(node, dict) and node.get("uuid") in groups:
                uuid = node["uuid"]
                if uuid in visited_groups:
                    raise ProjectError(f"Outliner has a duplicated group UUID {uuid}")
                visited_groups.add(uuid)
                group = groups[uuid]
                visit(node.get("children", []), emitted or group.get("name") in EMISSIVE_GROUPS,
                      enabled and group.get("export", True))
            else:
                raise ProjectError("Outliner contains an unsupported or missing group")

    visit(project.get("outliner", []))
    if set(membership) != set(elements):
        raise ProjectError("Every cube must belong to the saved Blockbench outliner")
    if visited_groups != set(groups):
        raise ProjectError("Every group must belong to the saved Blockbench outliner")
    return membership


def export_model(project):
    resources = texture_resources(project)
    for texture in project["textures"]:
        png_bytes(texture)
    resolution = project.get("resolution", {})
    width = number(resolution.get("width"), "UV width")
    height = number(resolution.get("height"), "UV height")
    if width <= 0 or height <= 0:
        raise ProjectError("UV dimensions must be positive")
    membership = group_membership(project)
    elements = []
    for cube in project.get("elements", []):
        if cube.get("type", "cube") != "cube":
            raise ProjectError("Minecraft 1.20.1 item models support cubes, not meshes")
        glowing, parent_enabled = membership[cube["uuid"]]
        if cube.get("export", True) is False or not parent_enabled:
            continue
        name = cube.get("name", cube["uuid"])
        if cube.get("box_uv", False):
            raise ProjectError(f"{name}: use per-face UVs for the Java item atlas")
        stretch = vector(cube.get("stretch", [1, 1, 1]), 3, f"{name} stretch")
        if stretch != [1, 1, 1]:
            raise ProjectError(f"{name}: unsupported stretched cube; edit from/to instead")
        inflate = number(cube.get("inflate", 0), f"{name} inflate")
        start = [value - inflate for value in vector(cube.get("from"), 3, f"{name} from")]
        end = [value + inflate for value in vector(cube.get("to"), 3, f"{name} to")]
        if any(value < -16 or value > 32 for value in start + end) or any(a > b for a, b in zip(start, end)):
            raise ProjectError(f"{name}: cube coordinates must remain between -16 and 32, with from <= to")
        result = {"from": start, "to": end, "faces": {}}
        rotation = vector(cube.get("rotation", [0, 0, 0]), 3, f"{name} rotation")
        active_axes = [index for index, angle in enumerate(rotation) if angle != 0]
        if len(active_axes) > 1 or any(angle not in ANGLES for angle in rotation):
            raise ProjectError(f"{name}: use one rotation axis and 0, +/-22.5 or +/-45 degrees for Minecraft 1.20.1")
        if active_axes or cube.get("rescale", False):
            index = active_axes[0] if active_axes else "xyz".index(cube.get("rotation_axis", "y"))
            result["rotation"] = {"angle": rotation[index], "axis": "xyz"[index],
                                  "origin": vector(cube.get("origin", [8, 8, 8]), 3, f"{name} pivot"),
                                  "rescale": bool(cube.get("rescale", False))}
        for direction in FACES:
            face = cube.get("faces", {}).get(direction)
            if not face or face.get("texture") is None:
                continue
            texture_index = face.get("texture")
            if isinstance(texture_index, bool) or not isinstance(texture_index, int) or texture_index not in resources:
                raise ProjectError(f"{name}/{direction}: assign a project texture in Blockbench")
            uv = vector(face.get("uv"), 4, f"{name}/{direction} UV")
            converted = [component * 16 / (width if index % 2 == 0 else height) for index, component in enumerate(uv)]
            if any(component < 0 or component > 16 for component in converted):
                raise ProjectError(f"{name}/{direction}: UV extends beyond the atlas")
            target = {"uv": converted, "texture": "#" + resources[texture_index][0]}
            if face.get("enabled", True) is False:
                # The native Java codec interprets a disabled face as default UV.
                target.pop("uv")
            angle = number(face.get("rotation", 0), f"{name}/{direction} UV rotation")
            if angle not in (0, 90, 180, 270):
                raise ProjectError(f"{name}/{direction}: UV rotation must be 0/90/180/270")
            if angle:
                target["rotation"] = angle
            if face.get("cullface"):
                if face["cullface"] not in FACES:
                    raise ProjectError(f"{name}/{direction}: invalid cull face")
                target["cullface"] = face["cullface"]
            tint = face.get("tint", -1)
            if tint >= 0:
                if isinstance(tint, bool) or not isinstance(tint, int):
                    raise ProjectError(f"{name}/{direction}: tint index must be an integer")
                target["tintindex"] = tint
            result["faces"][direction] = target
        if not result["faces"]:
            continue
        if glowing or cube.get("shade", True) is False:
            result["shade"] = False
        if glowing:
            result["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        elements.append(result)
    textures = {tag: resource for tag, resource in resources.values()}
    particles = [resources[index][1] for index, texture in enumerate(project["textures"]) if texture.get("particle")]
    if len(particles) > 1:
        raise ProjectError("Select only one particle texture in Blockbench")
    textures["particle"] = particles[0] if particles else resources[0][1]
    model = {"textures": textures, "elements": elements}
    if project.get("front_gui_light", False):
        model["gui_light"] = "front"
    if project.get("ambientocclusion", True) is False:
        model["ambientocclusion"] = False
    if project.get("parent"):
        model["parent"] = project["parent"]
    display = copy.deepcopy(project.get("display", {}))
    if any(slot not in DISPLAY_SLOTS for slot in display):
        raise ProjectError("Unsupported item display context")
    for slot, transform in display.items():
        for key in ("rotation", "translation", "scale"):
            if key in transform:
                values = vector(transform[key], 3, f"{slot}/{key}")
                if key == "rotation":
                    transform[key] = [canonical_angle(angle) for angle in values]
                if key == "translation" and any(abs(value) > 80 for value in values):
                    raise ProjectError(f"{slot}: display translation must remain within +/-80")
                if key == "scale" and any(abs(value) > 4 for value in values):
                    raise ProjectError(f"{slot}: display scale must remain within +/-4")
        if any(key not in {"rotation", "translation", "scale", "mirror"} for key in transform):
            raise ProjectError(f"{slot}: unsupported display transform property")
    if display:
        model["display"] = display
    return model


def exported_textures(project):
    resources = texture_resources(project)
    return {resource.split(":", 1)[1] + ".png": png_bytes(project["textures"][index])
            for index, (_, resource) in resources.items()}


def write_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    export = subparsers.add_parser("export", help="Export bbmodel geometry/display and optionally painted PNGs")
    export.add_argument("--project", type=Path, default=DEFAULT_PROJECT)
    export.add_argument("--output", type=Path, default=DEFAULT_MODEL)
    export.add_argument("--textures-dir", type=Path, help="The apexpredator/textures directory; writes the embedded edited PNGs")
    check = subparsers.add_parser("check", help="Validate the bbmodel against committed runtime model and PNG")
    check.add_argument("--project", type=Path, default=DEFAULT_PROJECT)
    check.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    check.add_argument("--texture", type=Path, default=DEFAULT_TEXTURE)
    args = parser.parse_args(argv)
    try:
        project = load_project(args.project)
        model = export_model(project)
        bitmaps = exported_textures(project)
        if args.command == "export":
            write_json(args.output, model)
            if args.textures_dir:
                for relative, bitmap in bitmaps.items():
                    target = args.textures_dir / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(bitmap)
            print(f"Exported {len(model['elements'])} cubes, {sum('forge_data' in cube for cube in model['elements'])} emitting accents: {args.output}")
        else:
            expected = json.loads(args.model.read_text(encoding="utf-8"))
            if model != expected:
                raise ProjectError("bbmodel export differs from runtime item JSON; export the edited project before building")
            if bitmaps.get("item/apex_fang.png") != args.texture.read_bytes():
                raise ProjectError("bbmodel painted PNG differs from runtime PNG; export with --textures-dir before building")
            print(f"Apex Fang round trip OK: {len(model['elements'])} cubes, {sum('forge_data' in cube for cube in model['elements'])} emitting accents, {len(model.get('display', {}))} display contexts; geometry, UV, lightmap and PNG match")
        return 0
    except (ProjectError, OSError, KeyError, TypeError, ValueError) as error:
        print(f"Apex Fang export error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
