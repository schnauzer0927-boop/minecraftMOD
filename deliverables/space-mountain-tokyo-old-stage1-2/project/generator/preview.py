"""Render orthographic PNGs directly from the generated Minecraft block mapping.

No reference photograph or generated concept art enters this renderer.  Hidden
voxel faces are culled against the same occupancy grid as the exported model;
front and plan views choose the nearest actual placed block on each sightline.
Non-full blocks (stairs, slabs, glass panes, leaves) are shown as occupied voxels,
which keeps these previews useful for checking layout and exterior massing.
"""
from __future__ import annotations

import hashlib
import math
import time
from collections import Counter
from pathlib import Path
from typing import Mapping

import numpy as np
from PIL import Image, ImageDraw, ImageFont


_INK = (35, 49, 65)
_MUTED = (96, 112, 126)
_PAPER = (243, 246, 248)
_ACCENT = (33, 102, 130)
_GRID = (214, 222, 228)

# Flat colours deliberately stay close to the vanilla material families.  The
# isometric renderer applies face lighting separately to reveal the radial ribs.
_COLORS = {
    "white_concrete": (228, 231, 226),
    "white_concrete_powder": (219, 222, 215),
    "quartz_block": (242, 238, 226),
    "smooth_quartz": (246, 242, 231),
    "quartz_pillar": (238, 235, 224),
    "chiseled_quartz_block": (235, 231, 221),
    "snow_block": (246, 251, 252),
    "white_wool": (223, 226, 225),
    "light_gray_concrete": (163, 172, 175),
    "gray_concrete": (99, 110, 119),
    "black_concrete": (39, 46, 52),
    "smooth_stone": (162, 170, 171),
    "stone": (141, 151, 154),
    "stone_bricks": (135, 145, 147),
    "polished_andesite": (146, 155, 158),
    "andesite": (133, 145, 148),
    "polished_diorite": (213, 218, 217),
    "diorite": (204, 211, 210),
    "calcite": (223, 224, 210),
    "iron_block": (211, 220, 225),
    "iron_bars": (134, 153, 163),
    "sea_lantern": (202, 241, 235),
    "end_rod": (255, 248, 207),
    "glowstone": (228, 190, 109),
    "light": (249, 230, 168),
    "glass": (163, 213, 224),
    "glass_pane": (153, 203, 217),
    "light_blue_stained_glass": (104, 178, 200),
    "light_blue_stained_glass_pane": (104, 178, 200),
    "cyan_stained_glass": (62, 143, 156),
    "cyan_stained_glass_pane": (62, 143, 156),
    "blue_stained_glass": (67, 105, 156),
    "blue_stained_glass_pane": (67, 105, 156),
    "gray_stained_glass": (73, 102, 118),
    "gray_stained_glass_pane": (73, 102, 118),
    "black_stained_glass": (47, 73, 88),
    "black_stained_glass_pane": (47, 73, 88),
    "tinted_glass": (57, 69, 84),
    "water": (70, 151, 181),
    "grass_block": (102, 137, 95),
    "moss_block": (92, 129, 80),
    "dirt": (143, 117, 88),
    "coarse_dirt": (133, 110, 83),
    "oak_leaves": (71, 117, 71),
    "spruce_leaves": (54, 98, 67),
    "azalea_leaves": (90, 129, 78),
    "flowering_azalea_leaves": (106, 135, 96),
    "oak_log": (114, 94, 65),
    "spruce_log": (97, 81, 61),
    "oak_planks": (175, 147, 99),
    "spruce_planks": (132, 109, 77),
    "sandstone": (224, 210, 165),
    "smooth_sandstone": (230, 217, 175),
    "sand": (226, 215, 174),
    "yellow_concrete": (231, 194, 63),
    "orange_concrete": (214, 134, 67),
    "cyan_concrete": (66, 132, 145),
    "blue_concrete": (66, 98, 161),
    "light_blue_concrete": (97, 174, 196),
    "red_concrete": (174, 81, 75),
    "dark_prismarine": (56, 110, 108),
    "polished_deepslate": (78, 87, 93),
    "deepslate_tiles": (67, 74, 81),
    "cobbled_deepslate": (84, 89, 94),
    "dark_oak_fence": (77, 63, 47),
    "oak_fence": (155, 132, 93),
    "birch_fence": (191, 179, 140),
    "iron_trapdoor": (172, 185, 189),
    "smooth_quartz_slab": (242, 238, 226),
    "smooth_quartz_stairs": (242, 238, 226),
    "quartz_stairs": (242, 238, 226),
    "quartz_slab": (242, 238, 226),
    "smooth_stone_slab": (160, 170, 173),
    "stone_brick_slab": (137, 147, 149),
    "stone_brick_stairs": (137, 147, 149),
    "blackstone": (65, 65, 73),
    "polished_blackstone": (66, 66, 74),
    "lime_concrete": (137, 175, 71),
    "green_concrete": (78, 111, 56),
    "brown_concrete": (119, 92, 70),
    "terracotta": (154, 119, 96),
    "white_terracotta": (209, 180, 158),
}


def _font(size: int, *, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:
        return ImageFont.truetype(name, size)
    except OSError:
        return ImageFont.load_default(size=size)


def _material_color(state: str) -> tuple[int, int, int]:
    name = state.split("[", 1)[0].removeprefix("minecraft:")
    if name in _COLORS:
        return _COLORS[name]
    for suffix in ("_stairs", "_slab", "_wall", "_fence", "_trapdoor"):
        if name.endswith(suffix):
            base = name.removesuffix(suffix)
            if base in _COLORS:
                return _COLORS[base]
            if base + "_block" in _COLORS:
                return _COLORS[base + "_block"]
            if base + "_planks" in _COLORS:
                return _COLORS[base + "_planks"]
    if "leaves" in name:
        return (75, 119, 72)
    if "glass" in name:
        return (128, 184, 199)
    if "quartz" in name:
        return (239, 235, 226)
    if "iron" in name:
        return (169, 187, 196)
    if "stone" in name:
        return (144, 151, 155)
    if "lantern" in name or "torch" in name:
        return (245, 208, 125)
    # Stable muted fallback makes unfamiliar materials distinguishable without
    # assigning a random colour differently on each Python interpreter startup.
    digest = hashlib.sha256(name.encode()).digest()
    return tuple(110 + component % 75 for component in digest[:3])


def _is_air(state: str) -> bool:
    return state.split("[", 1)[0] in {
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air"
    }


def _prepare(blocks: Mapping, site_size: int) -> dict:
    placed = [(p, b) for p, b in blocks.items() if not _is_air(b)]
    if not placed:
        raise ValueError("Cannot preview an empty block mapping")
    coords = np.array([p for p, _ in placed], dtype=np.int32)
    if coords.ndim != 2 or coords.shape[1] != 3:
        raise ValueError("Block coordinates must be (x, y, z) triples")
    low = np.minimum(coords.min(axis=0), (0, 0, 0))
    high = np.maximum(coords.max(axis=0), (site_size - 1, 0, site_size - 1))
    shape = tuple((high - low + 2).tolist())
    if math.prod(shape) > 100_000_000:
        raise ValueError("Preview extent is too large for a dense voxel grid")
    states = sorted({b for _, b in placed})
    if len(states) >= 65535:
        raise ValueError("Too many block states for the preview palette")
    lookup = {state: i + 1 for i, state in enumerate(states)}
    ids = np.array([lookup[b] for _, b in placed], dtype=np.uint16)
    local = coords - low
    grid = np.zeros(shape, dtype=np.uint16)
    grid[local[:, 0], local[:, 1], local[:, 2]] = ids
    palette = np.array([(0, 0, 0)] + [_material_color(b) for b in states], dtype=np.uint8)
    material_counts = Counter(b.split("[",1)[0] for _,b in placed)
    return {
        "grid": grid, "low": low, "high": high, "palette": palette,
        "block_count": len(placed), "states": states,
        "material_counts": material_counts,
        "bounds": {
            "min": coords.min(axis=0).tolist(),
            "max": coords.max(axis=0).tolist(),
        },
    }


def _canvas(size: tuple[int, int], subtitle: str, stage_label: str) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGB", size, _PAPER)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, size[0], 13), fill=_ACCENT)
    draw.text((84, 47), "SPACE MOUNTAIN / TOKYO", font=_font(43, bold=True), fill=_INK)
    draw.text((86, 107), subtitle, font=_font(29), fill=_ACCENT)
    draw.text((86, 151), stage_label + "  |  Generated Minecraft block data", font=_font(22), fill=_MUTED)
    return image, draw


def _footer(draw, width, height, data, meters_per_block):
    draw.line((84, height - 172, width - 84, height - 172), fill=_GRID, width=2)
    lines = [
        f"1 block = {meters_per_block:g} m (design scale, estimated)  |  {data['block_count']:,} placed blocks",
        "Paste origin (0, 0, 0)  |  X east / Y up / Z south  |  Full-cube silhouettes for partial blocks",
        "Stage prototype: dimensions and exterior interpretation are provisional; no ride track is shown.",
    ]
    for i, text in enumerate(lines):
        draw.text((86, height - 145 + i * 34), text, font=_font(21 if i < 2 else 19), fill=_MUTED)


def _scale_bar(draw, xy, pixels_per_block, meters_per_block, length_blocks=40):
    x, y = xy
    length = length_blocks * pixels_per_block
    draw.line((x, y, x + length, y), fill=_INK, width=4)
    for tick in (0, 0.5, 1):
        tx = x + length * tick
        draw.line((tx, y - 8, tx, y + 8), fill=_INK, width=3)
    label = f"{length_blocks} blocks / ~{length_blocks * meters_per_block:g} m"
    draw.text((x, y + 15), label, font=_font(20), fill=_MUTED)


def _arrow(draw, start, end, label, *, color=_ACCENT):
    draw.line((start, end), fill=color, width=4)
    dx, dy = end[0] - start[0], end[1] - start[1]
    norm = max(math.hypot(dx, dy), 1)
    ux, uy = dx / norm, dy / norm
    draw.polygon((end, (end[0] - ux*18 - uy*8, end[1] - uy*18 + ux*8),
                  (end[0] - ux*18 + uy*8, end[1] - uy*18 - ux*8)), fill=color)
    draw.text((end[0] + 12, end[1] - 16), label, font=_font(22, bold=True), fill=color)


def _legend(draw, data, xy):
    x,y = xy
    draw.text((x,y),"MATERIAL KEY",font=_font(20,bold=True),fill=_ACCENT)
    for i,(state,_) in enumerate(data["material_counts"].most_common(6)):
        yy = y+34+i*33
        draw.rectangle((x,yy,x+21,yy+21),fill=_material_color(state),outline=_MUTED,width=1)
        name = state.removeprefix("minecraft:").replace("_"," ").capitalize()
        draw.text((x+34,yy-1),name,font=_font(19),fill=_MUTED)
    draw.text((x,y+246),"Pale plane: site boundary",font=_font(18),fill=_MUTED)


def _isometric(data, output_path, stage_label, meters_per_block):
    image, draw = _canvas((2400, 1950), "ISOMETRIC / looking northwest from the southeast", stage_label)
    grid, low, high, palette = (data[k] for k in ("grid", "low", "high", "palette"))
    occupied = grid != 0
    # +X, +Y, +Z are the only faces visible from this orthographic camera.
    faces = []
    for axis in range(3):
        adjacent = np.zeros_like(occupied)
        here = [slice(None)] * 3
        there = [slice(None)] * 3
        here[axis] = slice(None, -1)
        there[axis] = slice(1, None)
        adjacent[tuple(here)] = occupied[tuple(there)]
        positions = np.argwhere(occupied & ~adjacent)
        if len(positions):
            ids = grid[positions[:, 0], positions[:, 1], positions[:, 2]]
            positions = positions + low
            faces.append(np.column_stack((positions, np.full(len(ids), axis), ids)))
    all_faces = np.concatenate(faces)
    # Every possible occluder is farther in all three camera-facing axes. The
    # painter order therefore agrees with the camera's depth for complete cubes.
    order = np.argsort(all_faces[:, :3].sum(axis=1), kind="stable")
    all_faces = all_faces[order]
    cos30 = math.sqrt(3) / 2

    def unscaled(x, y, z):
        return (x - z) * cos30, (x + z) * 0.5 - y

    # Fit the occupied surface rather than the entire bounding cuboid: a tall
    # roof at the site's centre must not create an artificial empty sky margin.
    surface_u = (all_faces[:,0]-all_faces[:,2])*cos30
    surface_v = (all_faces[:,0]+all_faces[:,2])*0.5-all_faces[:,1]
    umin, umax = float(surface_u.min()-cos30), float(surface_u.max()+cos30)
    vmin, vmax = float(surface_v.min()-1), float(surface_v.max()+1)
    # Empty space in the declared site is part of a layout preview too. Include
    # the neutral site plane in the fit, even when only a small plaza is built;
    # otherwise its corners and the paste-origin annotation reach the header.
    floor_world = [(low[0],low[1]-0.4,low[2]),(high[0]+1,low[1]-0.4,low[2]),
                   (high[0]+1,low[1]-0.4,high[2]+1),(low[0],low[1]-0.4,high[2]+1)]
    floor_uv = [unscaled(*p) for p in floor_world]
    umin,umax = min(umin,min(p[0] for p in floor_uv)),max(umax,max(p[0] for p in floor_uv))
    vmin,vmax = min(vmin,min(p[1] for p in floor_uv)),max(vmax,max(p[1] for p in floor_uv))
    left, top, right, bottom = 115, 300, 2290, 1670
    scale = min((right-left)/(umax-umin), (bottom-top)/(vmax-vmin))
    ox = (left+right)/2 - scale*(umin+umax)/2
    oy = (top+bottom)/2 - scale*(vmin+vmax)/2

    def project(x, y, z):
        u, v = unscaled(x, y, z)
        return ox+u*scale, oy+v*scale

    # A restrained sky gradient helps distinguish white exterior from the page.
    for yy in range(235, 1700):
        t = (yy - 235)/1465
        color = tuple(round(a*(1-t)+b*t) for a,b in zip((227,238,244),(243,246,247)))
        draw.line((55, yy, 2345, yy), fill=color)
    floor = [project(*p) for p in floor_world]
    draw.polygon(floor, fill=(219,226,227))
    offsets = {
        0: ((1,0,0),(1,0,1),(1,1,1),(1,1,0)),
        1: ((0,1,0),(1,1,0),(1,1,1),(0,1,1)),
        2: ((0,0,1),(1,0,1),(1,1,1),(0,1,1)),
    }
    lit = [(palette.astype(np.float32)*factor).clip(0,255).astype(np.uint8)
           for factor in (0.82, 1.0, 0.70)]
    colors = [[tuple(c) for c in table.tolist()] for table in lit]
    for x, y, z, axis, mat in all_faces.tolist():
        polygon = [project(x+dx,y+dy,z+dz) for dx,dy,dz in offsets[axis]]
        draw.polygon(polygon, fill=colors[axis][mat])
    p0 = project(0, 0, 0)
    draw.ellipse((p0[0]-7,p0[1]-7,p0[0]+7,p0[1]+7), fill=_ACCENT, outline="white", width=2)
    draw.text((p0[0]+17,p0[1]-32), "ORIGIN", font=_font(20, bold=True), fill=_ACCENT)
    arrow_origin = (2110, 1490)
    _arrow(draw, arrow_origin, (2180,1450), "N (-Z)")
    _arrow(draw, arrow_origin, (2180,1530), "E (+X)")
    _legend(draw,data,(120,275))
    _scale_bar(draw, (120,1690), scale, meters_per_block)
    _footer(draw, image.width, image.height, data, meters_per_block)
    image.save(output_path, optimize=True)
    return {"path": str(output_path), "size": list(image.size), "visible_faces": len(all_faces),
            "camera": "southeast; +X/+Z faces; Y up"}


def _front(data, output_path, stage_label, meters_per_block):
    image, draw = _canvas((2400, 1600), "SOUTH ELEVATION / front view along -Z", stage_label)
    grid, low, high, palette = (data[k] for k in ("grid", "low", "high", "palette"))
    occupied = grid != 0
    nz = grid.shape[2]
    nearest = nz - 1 - occupied[:, :, ::-1].argmax(axis=2)
    has_block = occupied.any(axis=2)
    material = np.take_along_axis(grid, nearest[:, :, None], axis=2)[:, :, 0]
    colors = palette[material].astype(np.float32)
    # Greater distance toward the north slightly softens the colours, while
    # preserving the actual front-most voxel selected for every elevation pixel.
    depth = np.clip((high[2] - (nearest + low[2])) / max(nz-1,1),0,1)
    factors = 0.86 + depth[:,:,None]*0.06
    colors = np.clip(colors*factors + depth[:,:,None]*np.array([9,11,12]),0,255).astype(np.uint8)
    sky = np.array([232,241,246], dtype=np.uint8)
    colors[~has_block] = sky
    raster = Image.fromarray(np.flip(colors.transpose(1,0,2),axis=0))
    xmin, ymin, zmin = low.tolist()
    xmax, ymax, zmax = high.tolist()
    # Exclude the padding row added for adjacent-face tests.
    raster = raster.crop((0, 1, xmax-xmin+1, ymax-ymin+2))
    left, top, right, bottom = 150, 285, 2240, 1245
    scale = min((right-left)/raster.width,(bottom-top)/raster.height)
    rw, rh = round(raster.width*scale), round(raster.height*scale)
    px, py = round((left+right-rw)/2), round(bottom-rh)
    raster = raster.resize((rw,rh), resample=Image.Resampling.NEAREST)
    draw.rectangle((left-20,top-20,right+20,bottom+10),fill=tuple(sky.tolist()))
    # Grid is drawn before the voxel mask so gridlines never invent gaps within
    # a building, but remain visible behind its silhouette.
    for y in range(0, ymax+1, 10):
        sy = py+rh-(y-ymin)*scale
        draw.line((left,sy,right,sy),fill=_GRID,width=1)
        draw.text((76,sy-10),str(y),font=_font(20),fill=_MUTED)
    # Keep grid behind sky pixels while using the exact nearest voxel pixels.
    mask = Image.fromarray(np.flip(has_block[:xmax-xmin+1,:ymax-ymin+1].T,axis=0).astype(np.uint8)*255)
    mask = mask.resize((rw,rh), resample=Image.Resampling.NEAREST)
    image.paste(raster,(px,py),mask)
    draw.line((px,py+rh,px+rw,py+rh),fill=_MUTED,width=3)
    for x in range(0, xmax+1, 20):
        sx = px+(x-xmin)*scale
        draw.line((sx,py+rh,sx,py+rh+11),fill=_MUTED,width=2)
        draw.text((sx-13,py+rh+19),str(x),font=_font(20),fill=_MUTED)
    draw.text((px,py+rh+65),"X / blocks (east ->)",font=_font(22),fill=_MUTED)
    draw.text((76,249),"Y",font=_font(22,bold=True),fill=_ACCENT)
    draw.text((1700,1325),"Front: SOUTH (+Z)",font=_font(24,bold=True),fill=_ACCENT)
    _scale_bar(draw,(160,1355),scale,meters_per_block)
    _footer(draw,image.width,image.height,data,meters_per_block)
    image.save(output_path,optimize=True)
    return {"path":str(output_path),"size":list(image.size),"camera":"south, looking -Z"}


def _top(data, output_path, stage_label, meters_per_block, site_size):
    image, draw = _canvas((2400, 2600), "SITE PLAN / highest placed block in each X-Z column", stage_label)
    grid, low, high, palette = (data[k] for k in ("grid", "low", "high", "palette"))
    occupied = grid != 0
    ny = grid.shape[1]
    top_y = ny - 1 - occupied[:, ::-1, :].argmax(axis=1)
    has_block = occupied.any(axis=1)
    material = np.take_along_axis(grid,top_y[:,None,:],axis=1)[:,0,:]
    colors = palette[material].astype(np.float32)
    heights = top_y + low[1]
    # Neighbouring height steps supply subtle directional shade. All contours
    # follow the model; no hand-drawn building silhouette is overlaid.
    west = np.roll(heights,1,axis=0)
    north = np.roll(heights,1,axis=1)
    shade = np.where((west>heights)|(north>heights),0.77,1.0)
    colors = np.clip(colors*shade[:,:,None],0,255).astype(np.uint8)
    colors[~has_block] = (225,232,235)
    width, depth = (high[0]-low[0]+1),(high[2]-low[2]+1)
    raster = Image.fromarray(colors[:width,:depth,:].transpose(1,0,2))
    scale = min(2020/width,1900/depth)
    rw,rh = round(width*scale),round(depth*scale)
    px,py = round((2400-rw)/2),310
    raster = raster.resize((rw,rh),resample=Image.Resampling.NEAREST)
    image.paste(raster,(px,py))
    draw.rectangle((px,py,px+rw,py+rh),outline=_MUTED,width=3)
    # Fine, partially transparent block grid avoids overwhelming the roof ribs.
    overlay = Image.new("RGBA",image.size,(0,0,0,0))
    grid_draw = ImageDraw.Draw(overlay)
    for x in range(0,int(high[0])+1,20):
        sx = px+(x-low[0])*scale
        grid_draw.line((sx,py,sx,py+rh),fill=(43,68,81,42),width=1)
        draw.text((sx-14,py-38),str(x),font=_font(21),fill=_MUTED)
        draw.text((sx-14,py+rh+18),str(x),font=_font(21),fill=_MUTED)
    for z in range(0,int(high[2])+1,20):
        sy = py+(z-low[2])*scale
        grid_draw.line((px,sy,px+rw,sy),fill=(43,68,81,42),width=1)
        draw.text((px-63,sy-11),str(z),font=_font(21),fill=_MUTED)
        draw.text((px+rw+18,sy-11),str(z),font=_font(21),fill=_MUTED)
    image = Image.alpha_composite(image.convert("RGBA"),overlay).convert("RGB")
    draw = ImageDraw.Draw(image)
    origin = (px-low[0]*scale,py-low[2]*scale)
    draw.ellipse((origin[0]-8,origin[1]-8,origin[0]+8,origin[1]+8),fill=_ACCENT,outline="white",width=2)
    draw.text((px,245),"ORIGIN (0, 0, 0)",font=_font(22,bold=True),fill=_ACCENT)
    _arrow(draw,(2170,247),(2170,199),"N (-Z)")
    draw.text((px+rw-310,py+rh+75),"FRONT / SOUTH (+Z)",font=_font(23,bold=True),fill=_ACCENT)
    draw.text((px,py+rh+75),"X east ->  |  Z south down",font=_font(23),fill=_MUTED)
    _scale_bar(draw,(px,py+rh+145),scale,meters_per_block)
    _footer(draw,image.width,image.height,data,meters_per_block)
    image.save(output_path,optimize=True)
    return {"path":str(output_path),"size":list(image.size),"camera":"above; north up",
            "site_size_blocks":[site_size,site_size]}


def render_previews(
    blocks: Mapping[tuple[int, int, int], str],
    output_dir: str | Path,
    *,
    site_size: int = 220,
    meters_per_block: float = 0.5,
    stage_label: str = "Stage 1-2 exterior prototype",
) -> dict:
    """Save isometric, south elevation, and site-plan PNGs of *blocks*.

    Returned paths are absolute. A design-scale label is explicitly provisional;
    all reported integer coordinate extents come directly from the supplied map.
    The implementation is vectorized for surface discovery and sightline views,
    and only exposed camera-facing polygons are passed to Pillow.
    """
    if site_size < 1 or meters_per_block <= 0:
        raise ValueError("site_size and meters_per_block must be positive")
    output_dir = Path(output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    data = _prepare(blocks,site_size)
    report = {
        "source": "placed Minecraft block map (full-cube voxel preview)",
        "source_block_count": data["block_count"],
        "bounds_blocks": data["bounds"],
        "meters_per_block": meters_per_block,
        "scale_status": "estimated design scale, not a surveyed dimension",
    }
    report["isometric"] = _isometric(data,output_dir/"preview-isometric.png",stage_label,meters_per_block)
    report["front"] = _front(data,output_dir/"preview-front.png",stage_label,meters_per_block)
    report["top"] = _top(data,output_dir/"preview-top.png",stage_label,meters_per_block,site_size)
    report["render_seconds"] = round(time.perf_counter()-started,3)
    return report
