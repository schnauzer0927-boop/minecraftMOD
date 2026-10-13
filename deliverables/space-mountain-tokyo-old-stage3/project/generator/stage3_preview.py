"""Stage 3 interior views drawn only from the delivered Minecraft voxels.

The axonometric and plan views remove roofs and upper walls for inspection. The
visitor panels use a perspective voxel ray caster with a Minecraft player camera
1.65 blocks above the walking surface. No photograph, concept image, or game
screenshot is used.
Slabs, stairs, panes and other partial blocks retain full-cube preview silhouettes.
"""
from __future__ import annotations

import math
import time
from collections import Counter
from pathlib import Path
from typing import Mapping

import numpy as np
from PIL import Image, ImageDraw

from generator.preview import _font, _is_air, _material_color


_PAPER = (244, 247, 249)
_INK = (31, 48, 66)
_MUTED = (90, 109, 123)
_BLUE = (29, 108, 147)
_RULE = (214, 224, 230)
_LIGHT = (230, 239, 245)


def _normal_parts(parts: Mapping) -> dict:
    result = {}
    for group in ("entrance", "queue", "station"):
        found = {}
        for key, blocks in parts.items():
            if group in str(key).lower():
                found.update({tuple(p): b for p, b in blocks.items() if not _is_air(b)})
        if not found:
            raise ValueError(f"Stage 3 preview requires a nonempty {group} part")
        result[group] = found
    return result


def _data(blocks: Mapping) -> dict:
    placed = [(p, b) for p, b in blocks.items() if not _is_air(b)]
    if not placed:
        raise ValueError("Cannot preview an empty Stage 3 block map")
    points = np.array([p for p, _ in placed], dtype=np.int32)
    if points.ndim != 2 or points.shape[1] != 3:
        raise ValueError("Block positions must be (x, y, z) triples")
    low, high = points.min(axis=0), points.max(axis=0)
    shape = tuple((high - low + 3).tolist())
    if math.prod(shape) > 30_000_000:
        raise ValueError("Stage 3 preview region is too large")
    states = sorted({b for _, b in placed})
    lookup = {state: i + 1 for i, state in enumerate(states)}
    grid = np.zeros(shape, dtype=np.uint16)
    coords = points - low
    grid[coords[:, 0], coords[:, 1], coords[:, 2]] = [lookup[b] for _, b in placed]
    palette = np.array([(0, 0, 0)] + [_material_color(b) for b in states], dtype=np.uint8)
    alpha = np.ones(len(states) + 1, dtype=np.float32)
    alpha[0] = 0
    luminous = np.zeros(len(states) + 1, dtype=bool)
    for i, state in enumerate(states, 1):
        name = state.split("[", 1)[0]
        if "glass" in name:
            alpha[i] = 0.56 if "tinted" in name else 0.29
        luminous[i] = any(s in name for s in ("sea_lantern", "glowstone", "end_rod", ":light"))
    return {"grid": grid, "low": low, "high": high, "palette": palette,
            "alpha": alpha, "luminous": luminous, "states": states,
            "count": len(placed), "materials": Counter(b.split("[", 1)[0] for _, b in placed)}


def _page(title: str, subtitle: str, size=(2200, 2050)):
    image = Image.new("RGB", size, _PAPER)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, size[0], 11), fill=_BLUE)
    draw.text((72, 42), "SPACE MOUNTAIN / TOKYO OLD", font=_font(39, bold=True), fill=_INK)
    draw.text((74, 98), title, font=_font(31, bold=True), fill=_BLUE)
    draw.text((74, 143), subtitle, font=_font(22), fill=_MUTED)
    return image, draw


def _footer(image, draw, block_count, meters_per_block):
    y = image.height - 151
    draw.line((72, y - 20, image.width - 72, y - 20), fill=_RULE, width=2)
    lines = [
        f"Stage 3 interior prototype / estimated interpretation, not a measured historical interior.  |  {block_count:,} source blocks",
        f"Real placed-voxel views; partial blocks shown as cubes.  |  1 block ~ {meters_per_block:g} m (estimated design scale)",
        "Shared paste origin (0, 0, 0) / X east, Y up, Z south.  |  No ride track; not a Minecraft screenshot.",
    ]
    for i, line in enumerate(lines):
        draw.text((74, y + 35 * i), line, font=_font(20), fill=_MUTED)


def _panel_label(draw, xy, label, detail):
    x, y = xy
    draw.text((x, y), label, font=_font(22, bold=True), fill=_BLUE)
    draw.text((x, y + 32), detail, font=_font(19), fill=_MUTED)


def _cutaway(blocks, cut_y, *, open_sides=True):
    """Only omit voxels; the cut surface never invents model geometry."""
    points = np.array(list(blocks), dtype=np.int32)
    high = points.max(axis=0)
    return {p: b for p, b in blocks.items()
            if p[1] <= cut_y and not (open_sides and p[1] >= 6 and
                                     (p[0] >= high[0] - 1 or p[2] >= high[2] - 1))}


def _iso(image, box, data):
    draw = ImageDraw.Draw(image)
    grid, low, palette = data["grid"], data["low"], data["palette"]
    occupied = grid != 0
    face_sets = []
    for axis in range(3):
        adjacent = np.zeros_like(occupied)
        a, b = [slice(None)] * 3, [slice(None)] * 3
        a[axis], b[axis] = slice(None, -1), slice(1, None)
        adjacent[tuple(a)] = occupied[tuple(b)]
        positions = np.argwhere(occupied & ~adjacent)
        ids = grid[positions[:, 0], positions[:, 1], positions[:, 2]]
        face_sets.append(np.column_stack((positions + low, np.full(len(ids), axis), ids)))
    faces = np.concatenate(face_sets)
    faces = faces[np.argsort(faces[:, :3].sum(axis=1), kind="stable")]
    cos30 = math.sqrt(3) / 2
    offsets = {
        0: ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)),
        1: ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
        2: ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
    }
    def uv(x, y, z):
        return (x - z) * cos30, (x + z) * .5 - y
    coords = faces[:, :3]
    uu = (coords[:, 0] - coords[:, 2]) * cos30
    vv = (coords[:, 0] + coords[:, 2]) * .5 - coords[:, 1]
    umin, umax, vmin, vmax = uu.min()-1, uu.max()+1, vv.min()-1, vv.max()+1
    left, top, right, bottom = box
    scale = min((right-left)/(umax-umin), (bottom-top)/(vmax-vmin))
    ox = (left+right)/2 - (umin+umax)*scale/2
    oy = (top+bottom)/2 - (vmin+vmax)*scale/2
    def point(x, y, z):
        u, v = uv(x, y, z)
        return ox + u * scale, oy + v * scale
    for yy in range(top, bottom):
        t = (yy-top)/max(bottom-top, 1)
        draw.line((left, yy, right, yy), fill=tuple(round(a*(1-t)+b*t) for a,b in zip(_LIGHT, _PAPER)))
    lit = [[tuple(c) for c in (palette * factor).clip(0,255).astype(np.uint8)]
           for factor in (.83, 1.0, .73)]
    for x, y, z, axis, material in faces.tolist():
        polygon = [point(x+dx, y+dy, z+dz) for dx,dy,dz in offsets[axis]]
        draw.polygon(polygon, fill=lit[axis][material])
    draw.text((right-330, bottom-38), "N (-Z) / E (+X)", font=_font(18), fill=_MUTED)
    return {"visible_faces": len(faces), "pixels_per_block": round(scale, 3)}


def _camera(data, config, group):
    low, high, grid = data["low"], data["high"], data["grid"]
    floor_y = float(config.get("floor_y", 4))
    setting = config.get("preview_cameras", {}).get(group, {})
    if "eye" in setting and "target" in setting:
        return np.asarray(setting["eye"], dtype=np.float32), np.asarray(setting["target"], dtype=np.float32)
    eye_y = floor_y + 2.65
    # Select a real floor with empty feet and head blocks, away from side walls.
    # Caller camera overrides are preferable for the planned visitor route.
    candidates = []
    yi = int(math.floor(eye_y)) - int(low[1])
    fy = int(floor_y) - int(low[1])
    if 0 <= fy < grid.shape[1]-1 and 0 <= yi < grid.shape[1] - 1:
        for x, z in np.argwhere((grid[:, fy, :] != 0) & (grid[:, fy+1, :] == 0) &
                               (grid[:, yi, :] == 0) & (grid[:, yi+1, :] == 0)):
            wx, wz = x + int(low[0]) + .5, z + int(low[2]) + .5
            if low[0] + 2 < wx < high[0] - 2 and low[2] + 2 < wz < high[2] - 1:
                candidates.append((wx, wz))
    cx = (float(low[0]) + float(high[0])) / 2
    desired = (cx, float(high[2]) - 4)
    if candidates:
        ex, ez = min(candidates, key=lambda p: (p[0]-desired[0])**2 + (p[1]-desired[1])**2)
    else:
        ex, ez = desired
    eye = np.array((ex, eye_y, ez), dtype=np.float32)
    target = np.array((cx, eye_y+.4, max(float(low[2])+3, ez-18)), dtype=np.float32)
    return eye, target


def _perspective(data, eye, target, *, size=(1000, 560), fov=78):
    """Vectorised exact grid traversal; glass composites real successive hits."""
    width, height = size
    grid, low, high = data["grid"], data["low"].astype(np.float32), data["high"].astype(np.float32)+1
    eye = np.asarray(eye, dtype=np.float32)
    local_eye = np.floor(eye-low).astype(int)
    if np.all(local_eye >= 0) and np.all(local_eye < np.array(grid.shape)):
        if grid[tuple(local_eye)] and data["alpha"][grid[tuple(local_eye)]] >= 1:
            raise ValueError(f"Visitor camera is inside an occupied block: {eye.tolist()}")
    forward = np.asarray(target, dtype=np.float32) - eye
    forward /= max(float(np.linalg.norm(forward)), .001)
    right = np.cross(forward, (0, 1, 0)).astype(np.float32)
    right /= max(float(np.linalg.norm(right)), .001)
    up = np.cross(right, forward).astype(np.float32)
    tangent = math.tan(math.radians(fov)/2)
    xx, yy = np.meshgrid(((np.arange(width)+.5)/width*2-1)*tangent,
                         (1-(np.arange(height)+.5)/height*2)*tangent*height/width)
    rays = (forward + xx[:, :, None]*right + yy[:, :, None]*up).reshape(-1, 3).astype(np.float32)
    rays /= np.linalg.norm(rays, axis=1)[:, None]
    safe = np.where(abs(rays) < 1e-7, 1e-7, rays)
    ta, tb = (low-eye)/safe, (high-eye)/safe
    enter, leave = np.minimum(ta, tb).max(axis=1), np.maximum(ta, tb).min(axis=1)
    distance = np.maximum(enter, 0) + 1e-4
    active = (leave >= distance) & (leave > 0)
    pos = np.floor(eye+distance[:, None]*rays-low).astype(np.int32)
    step = np.sign(rays).astype(np.int32)
    step[step == 0] = 1
    delta = np.abs(1/safe)
    next_t = (low+pos+(step > 0)-eye)/safe
    axis_hit = np.argmax(np.minimum(ta, tb), axis=1)
    shape = np.array(grid.shape)
    color = np.zeros((len(rays), 3), dtype=np.float32)
    transmission = np.ones(len(rays), dtype=np.float32)
    hit_count = np.zeros(len(rays), dtype=np.int16)
    palette = data["palette"].astype(np.float32)
    max_steps = min(900, int(shape.sum())*2)
    for _ in range(max_steps):
        idx = np.flatnonzero(active)
        if not len(idx):
            break
        valid = np.all((pos[idx] >= 0) & (pos[idx] < shape), axis=1) & (distance[idx] <= leave[idx]+1e-3)
        active[idx[~valid]] = False
        idx = idx[valid]
        if not len(idx):
            continue
        cells = pos[idx]
        materials = grid[cells[:, 0], cells[:, 1], cells[:, 2]]
        mask = materials != 0
        hits, mats = idx[mask], materials[mask]
        if len(hits):
            hit_xyz = eye + distance[hits, None]*rays[hits]
            frac = hit_xyz - np.floor(hit_xyz)
            axes = axis_hit[hits]
            # Cube-edge and material modulation reveal actual voxel size.
            face_frac = frac.copy()
            face_frac[np.arange(len(hits)), axes] = .5
            edge = np.min(np.minimum(face_frac, 1-face_frac), axis=1) < .022
            illumination = np.array((.84, 1.0, .92))[axes]
            grain = np.floor(hit_xyz*13)
            texture = .988 + .012*np.sin(grain[:, 0]*127.1 + grain[:, 1]*311.7 + grain[:, 2]*74.7)
            illumination *= np.where(edge, .85, texture)
            illumination[data["luminous"][mats]] = 1.05
            rgb = np.minimum(palette[mats]*illumination[:, None], 255)
            fog = np.minimum(distance[hits]/200, .12)[:, None]
            rgb = rgb*(1-fog) + np.array(_LIGHT)*fog
            opacity = data["alpha"][mats]
            # A straight line through contiguous glass remains visible rather
            # than rendering only a fictitious single front glass surface.
            color[hits] += transmission[hits, None]*opacity[:, None]*rgb
            transmission[hits] *= 1-opacity
            hit_count[hits] += 1
            active[hits[(transmission[hits] < .025) | (hit_count[hits] >= 12)]] = False
        idx = idx[active[idx]]
        if not len(idx):
            continue
        axes = np.argmin(next_t[idx], axis=1)
        distance[idx] = next_t[idx, axes]+1e-5
        pos[idx, axes] += step[idx, axes]
        next_t[idx, axes] += delta[idx, axes]
        axis_hit[idx] = axes
    sky_top = np.array((205, 222, 234), dtype=np.float32)
    sky_bottom = np.array((234, 240, 243), dtype=np.float32)
    blend = np.repeat(np.linspace(0, 1, height), width)[:, None]
    color += transmission[:, None]*(sky_top*(1-blend)+sky_bottom*blend)
    raster = Image.fromarray(color.clip(0, 255).astype(np.uint8).reshape(height, width, 3))
    return raster, {"eye_blocks": [round(float(x), 3) for x in eye],
                    "target_blocks": [round(float(x), 3) for x in target],
                    "horizontal_fov_degrees": fov,
                    "ray_cast_resolution": [width, height], "rays_hit_voxels": int((hit_count > 0).sum())}


def _group_view(group, blocks, full, output_path, layout):
    title = {"entrance": "ENTRANCE / departure lobby", "queue": "QUEUE / space travel concourse",
             "station": "STATION / dispatch platforms"}[group]
    image, draw = _page(title, "Interior study / cutaway inspection and a Minecraft player-eye perspective from the actual block model", (2200, 2600))
    cut_y = int(layout.get("preview_cut_y", 9))
    cut = _data(_cutaway(blocks, cut_y))
    _panel_label(draw, (74, 201), "01 / OPEN INTERIOR", f"Upper walls and roof omitted above Y={cut_y}; east and south edges opened for inspection.")
    iso_report = _iso(image, (74, 274, 2126, 1035), cut)
    eye, target = _camera(_data(blocks), layout, group)
    floor_surface = float(layout.get("floor_y", 4))+1
    eye_height = float(eye[1])-floor_surface
    _panel_label(draw, (74, 1074), "02 / MINECRAFT PLAYER-EYE PERSPECTIVE",
                 f"Complete uncut model / eye Y={eye[1]:g}, {eye_height:.2f} blocks above the floor; transparent glass transmits actual geometry.")
    view, camera_report = _perspective(full, eye, target)
    camera_report["eye_above_floor_blocks"] = round(float(eye[1])-float(layout.get("floor_y", 4))-1, 3)
    image.paste(view.resize((2052, 1149), Image.Resampling.LANCZOS), (74, 1147))
    draw = ImageDraw.Draw(image)
    draw.rectangle((74, 1147, 2125, 2295), outline=_RULE, width=2)
    # Footer begins after the perspective; keep all provenance legible.
    _footer(image, draw, len(blocks), float(layout.get("meters_per_block", .5)))
    image.save(output_path, optimize=True)
    return {"path": str(output_path), "size": list(image.size), "source_blocks": len(blocks),
            "cutaway_y_max": cut_y, "cutaway": iso_report, "visitor_camera": camera_report}


def _plan(blocks, output_path, layout):
    cut_y = int(layout.get("plan_cut_y", 7))
    data = _data(_cutaway(blocks, cut_y, open_sides=False))
    image, draw = _page("STAGE 3 / INTERIOR PLAN", f"North up / highest actual voxel at or below Y={cut_y}; roofs and upper walls are removed", (2200, 1730))
    grid, low, high, palette = (data[k] for k in ("grid", "low", "high", "palette"))
    occupied = grid != 0
    top_y = grid.shape[1]-1-occupied[:, ::-1, :].argmax(axis=1)
    material = np.take_along_axis(grid, top_y[:, None, :], axis=1)[:, 0, :]
    colors = palette[material]
    colors[~occupied.any(axis=1)] = (224, 232, 236)
    nx, nz = int(high[0]-low[0]+1), int(high[2]-low[2]+1)
    raster = Image.fromarray(colors[:nx, :nz].transpose(1, 0, 2))
    scale = min(1850/nx, 1140/nz)
    rw, rh = round(nx*scale), round(nz*scale)
    px, py = round((2200-rw)/2), round(275+(1140-rh)/2)
    image.paste(raster.resize((rw,rh), Image.Resampling.NEAREST), (px, py))
    draw.rectangle((px,py,px+rw,py+rh), outline=_MUTED, width=3)
    overlay = Image.new("RGBA", image.size)
    od = ImageDraw.Draw(overlay)
    for x in range(int(low[0])//10*10, int(high[0])+2, 10):
        sx = px+(x-low[0])*scale
        if px <= sx <= px+rw:
            od.line((sx,py,sx,py+rh), fill=(35,62,77,38), width=1)
            draw.text((sx-12,py-32), str(x), font=_font(19), fill=_MUTED)
    for z in range(int(low[2])//10*10, int(high[2])+2, 10):
        sy = py+(z-low[2])*scale
        if py <= sy <= py+rh:
            od.line((px,sy,px+rw,sy), fill=(35,62,77,38), width=1)
            draw.text((px-56,sy-11), str(z), font=_font(19), fill=_MUTED)
    image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(image)
    # Only name regions at their actual delivered coordinates; these labels do
    # not add walls, floor markings or an invented visitor route to the raster.
    groups = _normal_parts(layout.get("_parts", {}))
    for name, part in groups.items():
        points = np.array(list(part), dtype=np.int32)
        bx, bz = points[:, 0].min(), points[:, 2].min()
        pos = (px+(bx-low[0])*scale+9, py+(bz-low[2])*scale+9)
        text = name.upper()
        bbox = draw.textbbox(pos, text, font=_font(22, bold=True))
        draw.rectangle((bbox[0]-7,bbox[1]-5,bbox[2]+7,bbox[3]+5), fill=_PAPER, outline=_RULE)
        draw.text(pos, text, font=_font(22,bold=True), fill=_BLUE)
    draw.text((74, 1460), "Coordinates in blocks / X east right, Z south down / FRONT: SOUTH (+Z)", font=_font(23), fill=_BLUE)
    _footer(image, draw, len(blocks), float(layout.get("meters_per_block", .5)))
    image.save(output_path, optimize=True)
    return {"path": str(output_path), "size": list(image.size), "cut_y_max": cut_y,
            "bounds_blocks": {"min": low.tolist(), "max": high.tolist()}, "camera": "above; north up"}


def _section(blocks, context, output_path, layout):
    points = np.array(list(blocks), dtype=np.int32)
    low, high = points.min(axis=0), points.max(axis=0)
    section_x = int(layout.get("section_x", 110))
    # A three-block view depth captures adjacent features while preserving an
    # explicit section plane; it never projects the entire room into the cut.
    thickness = int(layout.get("section_thickness", 3))
    cells = {p:b for p,b in context.items() if section_x <= p[0] < section_x+thickness and
             low[2]-2 <= p[2] <= high[2]+2 and low[1] <= p[1] <= max(high[1], 22)}
    if not cells:
        section_x = int((low[0]+high[0])//2)
        cells = {p:b for p,b in blocks.items() if section_x <= p[0] < section_x+thickness}
    image, draw = _page("STAGE 3 / LONGITUDINAL SECTION", f"Actual voxel section at X={section_x} / looking east along +X / south is at the right", (2200, 1670))
    data = _data(cells)
    grid, gl, gh, palette = (data[k] for k in ("grid", "low", "high", "palette"))
    occupied = grid != 0
    first = occupied.argmax(axis=0)
    ids = np.take_along_axis(grid, first[None,:,:], axis=0)[0]
    colors = palette[ids].copy()
    colors[~occupied.any(axis=0)] = _LIGHT
    ny, nz = int(gh[1]-gl[1]+1), int(gh[2]-gl[2]+1)
    raster = Image.fromarray(np.flip(colors[:ny, :nz], axis=0))
    scale = min(1880/nz, 1090/ny)
    rw, rh = round(nz*scale), round(ny*scale)
    px, py = round((2200-rw)/2), round(270+(1090-rh)/2)
    image.paste(raster.resize((rw,rh), Image.Resampling.NEAREST), (px,py))
    draw.rectangle((px,py,px+rw,py+rh), outline=_MUTED, width=3)
    for y in range(int(gl[1])//5*5, int(gh[1])+1, 5):
        sy = py+rh-(y-gl[1])*scale
        if py <= sy <= py+rh:
            draw.line((px-10,sy,px,sy), fill=_MUTED, width=2)
            draw.text((px-68,sy-12), f"Y{y}", font=_font(19), fill=_MUTED)
    for z in range(int(gl[2])//10*10, int(gh[2])+1, 10):
        sx = px+(z-gl[2])*scale
        if px <= sx <= px+rw:
            draw.line((sx,py+rh,sx,py+rh+10), fill=_MUTED,width=2)
            draw.text((sx-20,py+rh+17), f"Z{z}", font=_font(19), fill=_MUTED)
    draw.text((74, 1430), f"Section depth: X={section_x}..{section_x+thickness-1} / nearest real voxel in each Y-Z sightline", font=_font(22), fill=_BLUE)
    _footer(image, draw, len(blocks), float(layout.get("meters_per_block", .5)))
    image.save(output_path, optimize=True)
    return {"path": str(output_path), "size": list(image.size), "section_x": section_x,
            "section_thickness_blocks": thickness, "camera": "west, looking +X"}


def render_stage3_previews(parts: Mapping, full_blocks: Mapping, output_dir: str | Path, layout: dict) -> dict:
    """Render the three interior parts plus a Stage 3 plan and section.

    ``parts`` maps names containing entrance/queue/station to global-coordinate
    block mappings. ``full_blocks`` is the actual combined exterior/interior map
    and supplies context for the uncut visitor perspective. Optional ``layout``
    keys: ``preview_cameras`` (per group ``eye``/``target`` coordinate triples),
    ``floor_y`` (4), ``preview_cut_y`` (9), ``plan_cut_y`` (7), ``section_x``
    (110), ``section_thickness`` (3), and ``meters_per_block`` (.5).

    Returned paths are absolute; bounds/cameras use the original shared anchor.
    All figures state the estimated status and voxel-rendering limitations.
    """
    started = time.perf_counter()
    output_dir = Path(output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    groups = _normal_parts(parts)
    interiors = {}
    for blocks in groups.values():
        interiors.update(blocks)
    # Ray cast the complete delivered model. Cropping it to an individual room
    # would turn neighboring geometry or a distant roof into false open sky.
    full = _data(full_blocks)
    merged_layout = dict(layout)
    merged_layout["_parts"] = groups
    report = {"source": "actual delivered Minecraft voxel maps; no concept art or game screenshots",
              "historical_interior_verified": False, "stage": "3-interior-prototype",
              "paste_origin": [0,0,0], "files": {}, "views": {}}
    for group, blocks in groups.items():
        path = output_dir/f"{group}_preview.png"
        report["views"][group] = _group_view(group, blocks, full, path, merged_layout)
        report["files"][group] = str(path)
    path = output_dir/"top_view_stage3.png"
    report["views"]["top"] = _plan(interiors, path, merged_layout)
    report["files"]["top"] = str(path)
    path = output_dir/"section_view_stage3.png"
    report["views"]["section"] = _section(interiors, full_blocks, path, merged_layout)
    report["files"]["section"] = str(path)
    report["render_seconds"] = round(time.perf_counter()-started, 3)
    return report
