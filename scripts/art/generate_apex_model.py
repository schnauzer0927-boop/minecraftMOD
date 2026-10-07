#!/usr/bin/env python3
"""Author the original APEX 1.1 Bedrock model and its paired 256 px atlases.

All geometry, materials and pixels are created here, with a deterministic seed.
No third-party mesh, texture or unprovided concept image is used. Front is -Z,
left is +X. Bone poses are neutral so authored GeckoLib animation can articulate
every section. Preview is a software-rasterized inspection artifact, not an
asset used by the game. Run from any directory with Python, Pillow and NumPy.
"""
from pathlib import Path
import json
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/apexpredator'
SIZE = 256
RNG = random.Random(11027)
BONES = []
MATERIALS = []


def bone(name, pivot, parent=None):
    value = {'name': name, 'pivot': pivot}
    if parent:
        value['parent'] = parent
    BONES.append(value)
    return value


def cube(name, origin, size, material='armor', rotation=None, pivot=None):
    owner = next(b for b in BONES if b['name'] == name)
    result = {'origin': origin, 'size': size}
    if rotation:
        result['rotation'] = rotation
        result['pivot'] = pivot if pivot is not None else [origin[i] + size[i] / 2 for i in range(3)]
    owner.setdefault('cubes', []).append(result)
    MATERIALS.append((result, material, name))
    return result


def centered(name, center, size, material='armor', rotation=None, pivot=None):
    return cube(name, [round(center[i] - size[i] / 2, 3) for i in range(3)], size,
                material, rotation, pivot)


bone('root', [0, 0, 0])
bone('body', [0, 22, .5], 'root')
bone('waist', [0, 22, .5], 'body')
bone('chest', [0, 27, 0], 'body')
bone('neck', [0, 34, 0], 'chest')
bone('head', [0, 36, -.7], 'neck')
bone('face_plate', [0, 36, -.7], 'head')
bone('eye_glow', [0, 36, -.7], 'head')
for side, s in [('left', 1), ('right', -1)]:
    bone(f'{side}_shoulder', [s * 5.2, 31, 0], 'chest')
    bone(f'{side}_arm', [s * 5.8, 30, 0], f'{side}_shoulder')
    bone(f'{side}_forearm', [s * 6.8, 23, -.5], f'{side}_arm')
    bone(f'{side}_blade', [s * 8, 24, -1], f'{side}_forearm')
    bone(f'{side}_hand', [s * 7, 16, -2], f'{side}_forearm')
    bone(f'{side}_leg', [s * 2.8, 22, .5], 'waist')
    bone(f'{side}_lower_leg', [s * 3.1, 13, -1], f'{side}_leg')
    bone(f'{side}_foot', [s * 3.1, 4, 2], f'{side}_lower_leg')
bone('back_spines', [0, 31, 2.5], 'chest')
bone('back_blade_left', [3.5, 31.5, 2.5], 'back_spines')
bone('back_blade_right', [-3.5, 31.5, 2.5], 'back_spines')
bone('tail_or_rear_spine', [0, 23, 2.8], 'waist')

# Segmented exoskeleton: an unmistakably narrow predator torso with a floating
# faceted chest shield, inset reactor and abdominal plates, not a vanilla box.
centered('waist', [0, 22.9, .7], [6.5, 3.4, 4], 'joint')
centered('waist', [0, 23.9, -1.9], [4.8, 2.2, 1.2], 'armor', [-12, 0, 0])
centered('waist', [0, 22.1, -1.65], [4, 1.2, 1.2], 'edge', [12, 0, 0])
centered('waist', [0, 23.7, -2.55], [.55, 2.5, .14], 'seam')
centered('waist', [0, 22, 2.6], [5.2, 1.7, .8], 'armor')
centered('chest', [0, 28.6, .2], [7.8, 8.3, 4.5], 'joint')
centered('chest', [0, 31.6, -.2], [9.0, 2.5, 4.4], 'armor', [-7, 0, 0])
centered('chest', [0, 27.3, -1.9], [5.8, 3.5, 1.5], 'armor', [-11, 0, 0])
centered('chest', [0, 25.4, -1.5], [5.1, 1.3, 1.6], 'metal', [-10, 0, 0])
for s in [1, -1]:
    centered('chest', [s * 3.65, 30.5, -2.85], [3.7, 3.35, 1.0], 'armor',
             [-5, s * -8, s * 18])
    centered('chest', [s * 2.55, 28.3, -2.9], [1.55, 3.5, .8], 'edge',
             [0, s * -10, s * -14])
    centered('chest', [s * 3.25, 31.15, -3.42], [2.7, .2, .18], 'seam', [0, 0, s * 13])
    centered('chest', [s * 1.6, 28.1, -3.36], [.17, 3.4, .13], 'seam', [0, 0, s * -16])
    for i in range(3):
        centered('chest', [s * 3.75, 27.9 - i * 1.05, .6], [.8, .5, 3.3], 'metal', [0, 0, s * -9])
    centered('waist', [s * 4.0, 22.1, -.5], [1.4, 3.5, 2.6], 'armor', [-7, 0, s * -22])
    centered('waist', [s * 4.9, 22.7, .6], [.65, 3.8, 1.65], 'blade', [19, 0, s * -28])
centered('chest', [0, 29.6, -3.53], [2.1, 2.1, .18], 'core', [0, 0, 45])
centered('chest', [0, 29.6, -3.77], [.25, 2.05, .13], 'hot')
for s in [1, -1]:
    centered('chest', [s * .9, 30.5, -3.65], [.22, 2.8, .4], 'edge', [0, 0, s * 45])
    centered('chest', [s * .9, 28.7, -3.65], [.22, 2.8, .4], 'edge', [0, 0, s * -45])

# Floating neck rings, a triangular armored mask and swept-back crown.
centered('neck', [0, 34.4, -.1], [2.2, 2.4, 2.2], 'joint')
for y in [33.8, 34.55, 35.3]:
    centered('neck', [0, y, -.28], [2.5, .22, 2.35], 'seam')
    centered('neck', [0, y + .25, -.18], [2.7, .3, 2.5], 'metal')
centered('head', [0, 38.05, -.2], [3.4, 4.8, 3.6], 'armor')
centered('head', [0, 40.15, .7], [2.25, 3.0, 3.2], 'metal', [-23, 0, 0])
centered('head', [0, 41.6, 1.7], [1.1, 1.7, 1.55], 'blade', [-22, 0, 0])
centered('head', [0, 42.6, 2.25], [.45, 1.2, .8], 'blade')
centered('face_plate', [0, 37.8, -2.65], [2.35, 3.65, .85], 'metal', [-16, 0, 0])
centered('face_plate', [0, 36.0, -3.1], [1.4, 1.9, .65], 'armor', [-27, 0, 0])
centered('face_plate', [0, 39.65, -2.8], [.55, 3.1, 1.0], 'edge', [-24, 0, 0])
centered('face_plate', [0, 36.9, -3.5], [.35, 2.4, .35], 'edge', [-17, 0, 0])
for s in [1, -1]:
    centered('head', [s * 1.9, 38.2, -.1], [.85, 4.1, 3.0], 'armor', [-11, s * 16, s * -18])
    centered('head', [s * 1.65, 39.8, 1.95], [.7, 3.8, 2.4], 'blade', [-39, 0, s * -14])
    centered('face_plate', [s * 1.12, 39.15, -2.9], [2.2, .5, 1.3], 'edge', [0, s * -12, s * 17])
    centered('face_plate', [s * 1.51, 37.35, -2.73], [.8, 2.6, .75], 'armor', [0, s * -16, s * -24])
    centered('eye_glow', [s * 1.08, 38.5, -3.44], [1.7, .2, .14], 'eye', [0, s * -7, s * 17])
    centered('eye_glow', [s * .37, 38.25, -3.51], [.3, .18, .1], 'hot')

# Arm sections: asymmetrically layered hard-surface plates, exposed conduits,
# slim wrists, three articulated-looking fingers and oversized cutting fins.
for side, s in [('left', 1), ('right', -1)]:
    sh, arm, fore, blade, hand = [f'{side}_{n}' for n in ['shoulder', 'arm', 'forearm', 'blade', 'hand']]
    centered(sh, [s * 5.45, 30.55, 0], [3.9, 3.35, 4.4], 'joint')
    centered(sh, [s * 6.4, 32.0, .1], [4.0, 1.45, 4.6], 'armor', [-9, 0, s * -16])
    centered(sh, [s * 7.45, 31.5, .6], [1.0, 4.5, 3.2], 'blade', [19, 0, s * -28])
    centered(sh, [s * 6.3, 31.55, -2.23], [2.8, .24, .17], 'seam', [0, 0, s * -16])
    centered(sh, [s * 5.65, 33, 1.7], [1.55, 2.5, 3.5], 'metal', [-23, 0, s * -15])
    centered(arm, [s * 6.3, 26.9, -.05], [2.3, 6.6, 2.5], 'joint', [0, 0, s * 5])
    centered(arm, [s * 6.5, 27.7, -1.2], [2.85, 4.6, 1.2], 'armor', [0, 0, s * 5])
    centered(arm, [s * 7.3, 26.5, .5], [1.3, 4.45, 1.8], 'metal', [0, 0, s * 5])
    centered(arm, [s * 6.8, 23.6, -.55], [2.7, 1.4, 2.8], 'metal')
    centered(arm, [s * 6.75, 23.6, -2.0], [1.85, .24, .2], 'seam')
    centered(fore, [s * 6.9, 20.35, -1.1], [2.3, 6.0, 2.4], 'joint', [11, 0, s * -2])
    centered(fore, [s * 7.2, 20.8, -1.85], [3.1, 5.1, 1.25], 'armor', [12, 0, s * -6])
    centered(fore, [s * 7.6, 19.8, .1], [2.7, 4.3, 1.3], 'metal', [8, 0, s * -9])
    centered(fore, [s * 7.2, 20.65, -2.52], [1.8, .2, .17], 'seam', [0, 0, s * -6])
    centered(fore, [s * 7.25, 22.0, -2.35], [.23, 2.1, .17], 'seam', [12, 0, s * -6])
    centered(blade, [s * 8.25, 22, -.3], [1.45, 5.25, 3.3], 'edge', [7, 0, s * -12])
    centered(blade, [s * 9.35, 18.0, -1.0], [2.05, 10.4, 2.2], 'blade', [11, s * -18, s * 11])
    centered(blade, [s * 10.05, 11.75, -2.6], [.9, 3.8, 1.45], 'blade', [20, s * -18, s * 17])
    centered(blade, [s * 10.48, 9.35, -3.37], [.32, 1.5, .75], 'edge', [26, s * -18, s * 19])
    centered(blade, [s * 8.2, 25.5, 1.05], [.85, 3.25, 1.6], 'metal', [-27, 0, s * -14])
    centered(blade, [s * 9.1, 18.6, -2.15], [.26, 8.0, .16], 'seam', [11, s * -18, s * 11])
    centered(blade, [s * 10.2, 18.0, -1.0], [.14, 10.4, 2.0], 'edge', [11, s * -18, s * 11])
    centered(blade, [s * 10.07, 11.7, -3.31], [.18, 2.4, .13], 'seam', [20, s * -18, s * 17])
    centered(hand, [s * 7, 16.15, -2.0], [2.3, 2.45, 2.25], 'metal', [16, 0, 0])
    centered(hand, [s * 7, 16.1, -3.16], [1.9, 1.4, .5], 'armor', [16, 0, 0])
    for i in range(3):
        x = s * 7 + (i - 1) * .77
        centered(hand, [x, 14.75, -2.85], [.56, 2.15, .62], 'joint', [25, 0, (i - 1) * -7])
        centered(hand, [x, 13.7, -3.65], [.4, 1.6, .55], 'blade', [48, 0, (i - 1) * -7])
    centered(hand, [s * 5.75, 15.7, -2.2], [.7, 1.85, .75], 'edge', [12, 0, s * 28])

# Digitigrade leg architecture: knee forward, hock backward, long claws. The
# neutral pose gives each calf visible non-human articulation before animation.
for side, s in [('left', 1), ('right', -1)]:
    leg, shin, foot = [f'{side}_{n}' for n in ['leg', 'lower_leg', 'foot']]
    centered(leg, [s * 2.9, 18.0, -.2], [3.2, 7.4, 3.15], 'joint', [-9, 0, s * -2])
    centered(leg, [s * 3.0, 18.35, -1.48], [3.7, 6.3, 1.35], 'armor', [-9, 0, s * -3])
    centered(leg, [s * 4.4, 18.5, .1], [1.0, 5.2, 2.75], 'metal', [0, 0, s * -9])
    centered(leg, [s * 3.05, 16.7, -2.22], [.25, 3.6, .16], 'seam', [-9, 0, s * -3])
    centered(leg, [s * 3.1, 13.8, -1.05], [3.25, 2.25, 3.2], 'joint')
    centered(leg, [s * 3.1, 13.8, -2.62], [3.4, 1.7, .8], 'edge', [-16, 0, 0])
    centered(leg, [s * 3.1, 13.9, -3.06], [1.85, .2, .15], 'seam')
    centered(shin, [s * 3.1, 8.6, .58], [1.8, 9.2, 1.9], 'joint', [19, 0, 0])
    centered(shin, [s * 3.1, 9.0, -.3], [2.65, 7.7, 1.15], 'armor', [19, 0, 0])
    centered(shin, [s * 3.13, 8.75, -.85], [.21, 5.8, .16], 'seam', [19, 0, 0])
    centered(shin, [s * 3.1, 7.4, 2.0], [1.9, 6.2, 1.25], 'metal', [19, 0, 0])
    centered(shin, [s * 3.1, 5.1, 3.0], [1.0, 3.25, 2.2], 'blade', [-22, 0, 0])
    centered(shin, [s * 4.1, 10.65, 1.0], [.6, 4.8, 1.8], 'blade', [-7, 0, s * -13])
    centered(foot, [s * 3.1, 3.4, 1.65], [2.5, 2.5, 2.8], 'joint')
    centered(foot, [s * 3.1, 1.5, -.2], [3.1, 2.0, 4.8], 'metal', [12, 0, 0])
    centered(foot, [s * 3.1, 2.3, -.3], [2.8, .85, 3.3], 'armor', [10, 0, 0])
    for i in range(3):
        x = s * 3.1 + (i - 1) * 1.0
        centered(foot, [x, .95, -2.8], [.75, 1.1, 2.85], 'metal', [0, (i - 1) * 7, 0])
        centered(foot, [x, .45, -4.7], [.48, .9, 1.8], 'blade', [0, (i - 1) * 10, 0])
    centered(foot, [s * 3.1, 3.0, -1.45], [1.75, .18, .18], 'seam')

# Dorsal vents and deployable fins with clear downward/rearward flow. They are
# individually parented to allow the phase-change animation to open the armor.
centered('back_spines', [0, 30.3, 2.68], [1.25, 6.8, 1.1], 'metal')
for i in range(4):
    y = 33.25 - i * 2.05
    centered('back_spines', [0, y - .3, 4.3 - i * .13], [1.3 - i * .12, 2.5, 3.9 - i * .4],
             'blade', [-27, 0, 0])
    centered('back_spines', [0, y + .5, 4.05], [.45, .19, 2.7 - i * .25], 'seam', [-27, 0, 0])
for side, s in [('left', 1), ('right', -1)]:
    n = f'back_blade_{side}'
    centered(n, [s * 3.5, 31.8, 3.7], [2.15, 3.15, 2.7], 'armor', [-16, s * 17, s * -13])
    centered(n, [s * 4.4, 31.1, 5.6], [1.2, 6.1, 3.2], 'blade', [-31, s * 23, s * -18])
    centered(n, [s * 5.0, 28.9, 7.7], [.65, 3.3, 2.1], 'edge', [-32, s * 25, s * -18])
    centered(n, [s * 4.23, 31.8, 4.9], [.24, 3.4, .25], 'seam', [-31, s * 23, s * -18])
    centered(n, [s * 2.55, 27.7, 4.1], [.85, 3.4, 3.1], 'metal', [-28, s * 12, s * -9])
centered('tail_or_rear_spine', [0, 23.0, 3.4], [1.75, 2.5, 2.5], 'armor', [-22, 0, 0])
centered('tail_or_rear_spine', [0, 21.55, 5.3], [1.15, 3.8, 3.3], 'blade', [-30, 0, 0])
centered('tail_or_rear_spine', [0, 19.6, 7.0], [.6, 2.3, 2.05], 'edge', [-34, 0, 0])
centered('tail_or_rear_spine', [0, 22.1, 4.65], [.25, 2.65, .18], 'seam', [-30, 0, 0])

PALETTE = {
    'joint': (16, 19, 23), 'armor': (38, 44, 51), 'metal': (53, 61, 70),
    'edge': (84, 94, 104), 'blade': (61, 70, 80),
    'seam': (232, 73, 13), 'core': (242, 79, 12), 'hot': (255, 185, 57),
    'eye': (255, 110, 16),
}
GLOW = {'seam', 'core', 'hot', 'eye'}
FACES = ('north', 'south', 'west', 'east', 'up', 'down')


def dimensions(size, face):
    x, y, z = size
    a, b = (x, y) if face in ['north', 'south'] else ((z, y) if face in ['west', 'east'] else (x, z))
    return max(1, math.ceil(a * 2)), max(1, math.ceil(b * 2))


# Shelf packing sorts tallest first. One texel of gutters around every face
# keeps neighbouring UV patches from leaking through nearest sampling edges.
patches = []
for c, mat, owner in MATERIALS:
    c['uv'] = {}
    for face in FACES:
        w, h = dimensions(c['size'], face)
        patches.append((w, h, face, c, mat, owner))
patches.sort(key=lambda p: (-p[1], -p[0]))
base = Image.new('RGBA', (SIZE, SIZE), (8, 11, 15, 255))
glow = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
base_px, glow_px = base.load(), glow.load()
cursor_x = cursor_y = shelf = 0
face_brightness = {'north': 1.08, 'south': .83, 'west': .98, 'east': .92, 'up': 1.2, 'down': .7}

for w, h, face, c, mat, owner in patches:
    if cursor_x + w + 2 > SIZE:
        cursor_x = 0
        cursor_y += shelf
        shelf = 0
    if cursor_y + h + 2 > SIZE:
        raise RuntimeError('Texture atlas overflow; increase packing density or atlas size')
    x0, y0 = cursor_x + 1, cursor_y + 1
    c['uv'][face] = {'uv': [x0, y0], 'uv_size': [w, h]}
    color = PALETTE[mat]
    for yy in range(h):
        for xx in range(w):
            border = xx in [0, w - 1] or yy in [0, h - 1]
            if mat in GLOW:
                # Solid tiny eye/seam islands; larger reactor inset carries a
                # subtly pulsing-looking hot center without checkerboard noise.
                center = 1 - abs((xx + .5) / w * 2 - 1)
                strength = .89 + center * .11
                value = [min(255, int(v * strength)) for v in color]
                if mat == 'core' and not border and (yy % 7 == 2):
                    value = [255, 151, 29]
                if mat == 'hot':
                    value = [255, 200, 86]
                glow_px[x0 + xx, y0 + yy] = (*value, 255)
            else:
                rough = RNG.choice([-2, -1, 0, 0, 0, 1, 2])
                exposure = face_brightness[face]
                bevel = 11 if yy == 0 else (-12 if yy == h - 1 else 0)
                bevel += 5 if xx == 0 else (-6 if xx == w - 1 else 0)
                # Purposeful panel machining; mostly unbroken broad metal.
                etch = -9 if w > 7 and h > 9 and xx == w - 3 and 3 <= yy < h - 3 else 0
                if mat == 'joint':
                    etch = -5 if yy % 5 == 1 else 0
                    bevel = 1 if border else 0
                if mat == 'blade':
                    exposure += .22 * max(0, 1 - (xx / max(1, w - 1)) * 2)
                value = [max(4, min(255, int(v * exposure + rough + bevel + etch))) for v in color]
                if w >= 9 and h >= 9 and xx in [2, w - 3] and yy == 2:
                    value = [62, 70, 80]  # Recessed two-pixel fastening holes.
            base_px[x0 + xx, y0 + yy] = (*value, 255)
    # Duplicate island boundary in its gutter; only the base is opaque there.
    for xx in range(-1, w + 1):
        base_px[x0 + xx, y0 - 1] = base_px[x0 + min(w - 1, max(0, xx)), y0]
        base_px[x0 + xx, y0 + h] = base_px[x0 + min(w - 1, max(0, xx)), y0 + h - 1]
    for yy in range(h):
        base_px[x0 - 1, y0 + yy] = base_px[x0, y0 + yy]
        base_px[x0 + w, y0 + yy] = base_px[x0 + w - 1, y0 + yy]
    cursor_x += w + 2
    shelf = max(shelf, h + 2)

geometry = {
    'format_version': '1.12.0',
    'minecraft:geometry': [{
        'description': {
            'identifier': 'geometry.apexpredator.apex', 'texture_width': SIZE,
            'texture_height': SIZE, 'visible_bounds_width': 4.5,
            'visible_bounds_height': 3.8, 'visible_bounds_offset': [0, 1.4, 0],
        },
        'bones': BONES,
    }],
}
(ASSETS / 'geo').mkdir(parents=True, exist_ok=True)
(ASSETS / 'textures/entity').mkdir(parents=True, exist_ok=True)
(ASSETS / 'geo/apex.geo.json').write_text(json.dumps(geometry, indent=2) + '\n')
base.save(ASSETS / 'textures/entity/apex.png')
glow.save(ASSETS / 'textures/entity/apex_glow.png')


def rot_xyz(angles):
    x, y, z = np.radians(angles)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def vertices(c):
    o, size = np.array(c['origin']), np.array(c['size'])
    v = np.array([o + size * np.array(p) for p in
                  [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0),
                   (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]])
    if 'rotation' in c:
        pivot = np.array(c['pivot'])
        v = (v - pivot) @ rot_xyz(c['rotation']).T + pivot
    return v


FACE_VERTS = {
    'north': [0, 1, 2, 3], 'south': [5, 4, 7, 6],
    'west': [4, 0, 3, 7], 'east': [1, 5, 6, 2],
    'up': [3, 2, 6, 7], 'down': [4, 5, 1, 0],
}


def preview(yaw, pitch, width=600, height=740):
    """Z-buffer each textured triangle at 2x, then downsample for inspection."""
    aa = 2
    width, height = width * aa, height * aa
    rgba = np.zeros((height, width, 4), dtype=np.uint8)
    rgba[:, :, :] = (13, 18, 24, 255)
    zbuffer = np.full((height, width), -1e9)
    emissive = np.zeros((height, width, 4), dtype=np.uint8)
    camera = rot_xyz([pitch, yaw, 0])
    # camera looks at the negative Z facade and sees larger camera-space Z.
    scale = 13.4 * aa
    light = np.array([-.5, .8, .65])
    light /= np.linalg.norm(light)
    tex = np.asarray(base)
    glowtex = np.asarray(glow)
    for c, mat, owner in MATERIALS:
        v = vertices(c)
        camera_v = v @ camera.T
        screen = np.column_stack([width / 2 + camera_v[:, 0] * scale,
                                  height - 78 * aa - camera_v[:, 1] * scale,
                                  camera_v[:, 2]])
        for face, indices in FACE_VERTS.items():
            # The reverse front-facing camera transform below works with no
            # backface cull; z-buffer accurately clips overlapping plates.
            q = screen[indices]
            uv = c['uv'][face]
            tx, ty = uv['uv']
            tw, th = uv['uv_size']
            uvv = np.array([[tx, ty + th - 1], [tx + tw - 1, ty + th - 1],
                            [tx + tw - 1, ty], [tx, ty]])
            points = camera_v[indices]
            normal = np.cross(points[1] - points[0], points[2] - points[0])
            normal /= max(1e-10, np.linalg.norm(normal))
            # Face ordering is outward, but light is deliberately soft enough
            # that no pitch-black feature disappears in the design audit.
            lighting = .62 + .45 * max(0, float(normal @ light))
            for triangle in [(0, 1, 2), (0, 2, 3)]:
                p, u = q[list(triangle)], uvv[list(triangle)]
                minx = max(0, int(np.floor(p[:, 0].min())))
                maxx = min(width - 1, int(np.ceil(p[:, 0].max())))
                miny = max(0, int(np.floor(p[:, 1].min())))
                maxy = min(height - 1, int(np.ceil(p[:, 1].max())))
                if minx > maxx or miny > maxy:
                    continue
                xx, yy = np.meshgrid(np.arange(minx, maxx + 1) + .5,
                                     np.arange(miny, maxy + 1) + .5)
                ax, ay = p[0, :2]
                bx, by = p[1, :2]
                cx, cy = p[2, :2]
                denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(denom) < .000001:
                    continue
                a = ((by - cy) * (xx - cx) + (cx - bx) * (yy - cy)) / denom
                b = ((cy - ay) * (xx - cx) + (ax - cx) * (yy - cy)) / denom
                d = 1 - a - b
                depth = a * p[0, 2] + b * p[1, 2] + d * p[2, 2]
                visible = (a >= 0) & (b >= 0) & (d >= 0)
                region_z = zbuffer[miny:maxy + 1, minx:maxx + 1]
                visible &= depth >= region_z
                if not np.any(visible):
                    continue
                uu = np.clip(np.rint(a * u[0, 0] + b * u[1, 0] + d * u[2, 0]), 0, SIZE - 1).astype(int)
                vv = np.clip(np.rint(a * u[0, 1] + b * u[1, 1] + d * u[2, 1]), 0, SIZE - 1).astype(int)
                color = tex[vv, uu].copy()
                emission = glowtex[vv, uu]
                color[:, :, :3] = np.clip(color[:, :, :3] * lighting, 0, 255)
                lit = emission[:, :, 3] > 0
                color[lit] = emission[lit]
                region = rgba[miny:maxy + 1, minx:maxx + 1]
                region_e = emissive[miny:maxy + 1, minx:maxx + 1]
                region[visible] = color[visible]
                region_e[visible] = emission[visible]
                region_z[visible] = depth[visible]
    image = Image.fromarray(rgba)
    bloom = Image.fromarray(emissive).filter(ImageFilter.GaussianBlur(5 * aa))
    # Subtle halo is only an inspection aid; the game glow layer stays sharp.
    bloom_np = np.asarray(bloom).astype(np.float32)
    image_np = np.asarray(image).astype(np.float32)
    image_np[:, :, :3] += bloom_np[:, :, :3] * (bloom_np[:, :, 3:4] / 255) * .65
    image = Image.fromarray(np.clip(image_np, 0, 255).astype(np.uint8))
    return image.resize((width // aa, height // aa), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    output = ROOT / 'build/art'
    output.mkdir(parents=True, exist_ok=True)
    # Camera yaw180 yields the north/-Z facade. Multiple orthographic views
    # expose the digitigrade joints and deployable dorsal blades explicitly.
    views = [('FRONT', 180, 0), ('PREDATOR / 3-QUARTER', 145, -7),
             ('SIDE', 90, 0), ('DORSAL ARMOR', 35, -5)]
    sheet = Image.new('RGB', (2400, 850), (13, 18, 24))
    draw = ImageDraw.Draw(sheet)
    fontpath = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    try:
        font = ImageFont.truetype(fontpath, 19)
        title = ImageFont.truetype(fontpath, 32)
    except OSError:
        font = title = ImageFont.load_default()
    draw.text((35, 15), 'A P E X   /   P R E D A T O R', font=title, fill=(225, 233, 240))
    draw.text((35, 55), 'Original armored digitigrade boss  |  2.70 m  |  authored geometry + matching emissive atlas',
              font=font, fill=(143, 156, 169))
    for i, (label, yaw, pitch) in enumerate(views):
        sheet.paste(preview(yaw, pitch), (600 * i, 90))
        draw.text((600 * i + 32, 806), label, font=font, fill=(236, 116, 44))
    sheet.save(output / 'apex-model-turnaround.png')
    base.resize((1024, 1024), Image.Resampling.NEAREST).save(output / 'apex-atlas.png')
    glow.resize((1024, 1024), Image.Resampling.NEAREST).save(output / 'apex-glow-atlas.png')
    allv = np.concatenate([vertices(c) for c, _, _ in MATERIALS])
    metrics = {
        'bones': len(BONES), 'cubes': len(MATERIALS), 'quads': len(MATERIALS) * 6,
        'atlas_size': SIZE, 'atlas_last_row': cursor_y + shelf,
        'bounds_min_pixels': np.min(allv, axis=0).round(3).tolist(),
        'bounds_max_pixels': np.max(allv, axis=0).round(3).tolist(),
        'emissive_pixels': int(np.count_nonzero(np.asarray(glow)[:, :, 3])),
        'hierarchy': [{'name': b['name'], 'parent': b.get('parent'), 'pivot': b['pivot']} for b in BONES],
    }
    (output / 'apex-model-metrics.json').write_text(json.dumps(metrics, indent=2) + '\n')
    print(json.dumps({k: v for k, v in metrics.items() if k != 'hierarchy'}, indent=2))
