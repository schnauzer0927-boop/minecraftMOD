#!/usr/bin/env python3
"""Author the APEX 1.3 predator-mask Bedrock model and paired 512 px atlases.

All geometry, materials and pixels are created here, with a deterministic seed.
The supplied APEX concept is interpreted as original cube geometry. Front is -Z,
left is +X. Bone poses are neutral so authored GeckoLib animation can articulate
every section. Preview is a software-rasterized inspection artifact, not an
asset used by the game. Run from any directory with Python, Pillow and NumPy.
"""
from pathlib import Path
import sys

# This historical procedural author is a backup, not the editable model source.
# Check before optional imaging imports or any of the original file writes.
_EDITABLE_APEX = Path(__file__).resolve().parents[2] / "models/blockbench/apex.bbmodel"
if _EDITABLE_APEX.exists() and "--legacy-overwrite-model" not in sys.argv:
    raise SystemExit(
        "Apex is now authored in models/blockbench/apex.bbmodel. "
        "Use scripts/blockbench/apex_body.py export to apply edits. "
        "Historical regeneration requires the explicit --legacy-overwrite-model flag "
        "and will replace the runtime geometry and textures."
    )
import json
import math
import random
import subprocess
from io import BytesIO

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/apexpredator'
SIZE = 512
RNG = random.Random(13027)
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

# 1.3 concept-driven remake. Preserve all rig names, hierarchy and pivots above.
# Form is carried by swept segmented shells around narrow black mechanisms,
# rather than by large rectangular armor blocks. One unit is 1/16 block.
centered('waist', [0, 23.1, .35], [3.9, 4.1, 2.9], 'joint')
for i in range(3):
    y = 24.4 - i * 1.25
    width = 1.65 - i * .17
    for s in [1, -1]:
        centered('waist', [s * .95, y, -1.6], [width, .6, 1.15], 'armor', [-14, s * -13, s * -24])
    centered('waist', [0, y - .2, -2.21], [.14, .8, .11], 'seam', [-8, 0, 0])
for s in [1, -1]:
    centered('waist', [s * 2.48, 22.9, .45], [1.15, 3.0, 2.4], 'armor', [-9, s * 9, s * -18])
    centered('waist', [s * 2.9, 23.9, 1.7], [.48, 2.6, 2.45], 'blade', [-24, s * 11, s * -16])
centered('waist', [0, 22.6, 2.05], [3.5, 1.2, .65], 'metal', [8, 0, 0])

# Layered V-shaped ribs close around a slim orange sternum fissure. The dark
# inner chassis deliberately remains visible between the separately lit plates.
centered('chest', [0, 29.45, .1], [5.6, 6.2, 3.3], 'joint')
centered('chest', [0, 25.95, .0], [3.65, 2.4, 2.8], 'joint')
for s in [1, -1]:
    centered('chest', [s * 2.2, 32.0, -.95], [3.5, .48, 2.3], 'armor', [-22, s * -15, s * -25])
    for i in range(5):
        y = 30.95 - i * 1.17
        x = 2.36 - i * .26
        width = 3.2 - i * .33
        centered('chest', [s * x, y, -1.86 + i * .12], [width, .6, 1.35],
                 'armor' if i % 2 == 0 else 'metal', [-17, s * -17, s * -27])
    for i in range(3):
        centered('chest', [s * (2.6 - i * .25), 29.0 - i * 1.6, .9], [.52, .45, 2.7 - i * .24],
                 'joint', [-15, 0, s * 13])
    centered('chest', [s * .53, 29.4, -2.55], [.36, 5.4, .62], 'armor', [-5, s * -8, s * -7])
for i in range(4):
    centered('chest', [0, 31.25 - i * 1.32, -2.87 + i * .04], [.15, .96, .12],
             'core' if i == 1 else 'seam', [-5, 0, 0])

# A continuous black cervical frame reaches inside the chest and the new skull.
# The three floating-looking 1.2 neck pieces are replaced with overlapping
# collars; the thin recessed seam is the only luminous cervical surface.
centered('chest', [0, 33.9, .2], [2.4, 2.4, 2.3], 'joint')
centered('neck', [0, 35.2, .22], [2.02, 4.8, 1.95], 'joint', [-5, 0, 0])
for y, width in [(33.55, 2.7), (35.1, 2.45), (36.7, 2.15)]:
    centered('neck', [0, y, .2], [width, .43, 2.22], 'armor', [-8, 0, 0])
for s in [1, -1]:
    centered('neck', [s * .72, 35.2, .78], [.23, 3.7, .3], 'joint', [-7, 0, 0])
    centered('neck', [s * .93, 35.2, -.14], [.36, 3.05, .78], 'armor', [-14, s * -12, s * -11])
centered('neck', [0, 35.28, -.805], [.11, 2.65, .11], 'seam', [-5, 0, 0])

# Every 1.2 head/face/eye cube is discarded. The new mask has no exposed nasal
# bridge, mouth aperture or helmet side walls: five armor families converge on
# one narrow inverted-triangle ridge. Dark sensor beds hide behind thin brows.
centered('head', [0, 39.12, .25], [1.03, 2.22, 1.82], 'joint', [-17, 0, 0])
centered('head', [0, 38.15, .2], [1.14, 1.21, 1.74], 'joint', [-18, 0, 0])
centered('head', [0, 39.95, .65], [1.23, .72, 2.55], 'joint', [9, 0, 0])
centered('head', [0, 40.0, -1.05], [1.2, .85, 1.63], 'joint', [-23, 0, 0])
centered('head', [0, 38.55, -1.24], [1.0, 1.4, 2.2], 'joint', [-20, 0, 0])
centered('head', [0, 38.32, -2.43], [.82, 1.18, 1.65], 'joint', [-25, 0, 0])
# This small head-owned axle is concealed inside the neck collars. It crosses
# the unchanged head pivot, so teleport, stagger, death and look rotations
# cannot pull the skull entirely off the neck mechanism.
CERVICAL_HUB = centered('head', [0, 37.1, -.18], [1.12, 3.2, 1.05], 'joint')
for y, z, width, height, depth in [(39.9, -2.77, .17, 1.1, .35),
                                   (38.85, -3.25, .14, 1.15, .35),
                                   (37.91, -3.68, .10, .95, .24)]:
    centered('face_plate', [0, y, z], [width, height, depth], 'edge', [-25, 0, 0])
for s in [1, -1]:
    # Forehead vanes overlap the ridge and swept cranial chassis, rather than
    # sitting on a broad horizontal helmet brow.
    centered('face_plate', [s * .76, 40.18, -1.87], [1.17, .83, 1.13], 'armor',
             [-25, s * -20, s * 19])
    centered('face_plate', [s * .64, 39.76, -2.8], [1.03, .16, .46], 'metal',
             [-24, s * -21, s * 13])
    # Low cheek facets taper inward toward the tiny closed chin, avoiding a
    # human jaw line or a segmented mouth-like gap.
    for x, y, z, width, height in [(.57, 39.25, -2.65, .97, .92),
                                  (.4, 38.49, -3.08, .73, .93),
                                  (.2, 37.81, -3.47, .38, .76)]:
        centered('face_plate', [s * x, y, z], [width, height, .36], 'armor',
                 [-25, s * -17, s * 13])
    # The sensor slit itself is 0.055 model units tall. Its black cavity is much deeper
    # than the emitting insert; the bright insert is never an armor panel.
    centered('face_plate', [s * .64, 39.56, -2.9], [.98, .17, .29], 'joint',
             [-24, s * -21, s * 13])
    centered('eye_glow', [s * .64, 39.565, -3.04], [.73, .055, .07], 'eye',
             [-24, s * -21, s * 13])
    centered('head', [s * .8, 39.15, -1.66], [.3, .87, 2.06], 'armor',
             [-17, s * 25, s * 13])
    centered('head', [s * .39, 38.1, -2.12], [.24, .58, 2.04], 'metal',
             [-18, s * 20, s * 19])
    # Narrow lateral shells run aft into the rear crest, not outward into
    # helmet cheeks. Their differently angled lower plate covers the neck.
    for x, y, z, height, depth, rx, ry in [
            (1.17, 40.02, .45, .66, 2.72, -8, 21),
            (.89, 39.28, .48, .77, 2.5, -18, 16),
            (.59, 38.5, .3, .74, 1.85, -26, 14)]:
        centered('head', [s * x, y, z], [.28, height, depth], 'armor',
                 [rx, s * ry, s * 23])
    # Small oblique bridging facets close the seams between the side tiers.
    centered('head', [s * .86, 39.76, .39], [.45, .49, 2.44], 'metal',
             [-12, s * 15, s * 28])
    centered('head', [s * .64, 38.92, .32], [.37, .47, 1.93], 'metal',
             [-23, s * 16, s * 25])
    centered('head', [s * .55, 40.47, 1.62], [.38, .55, 2.27], 'blade',
             [18, s * 13, s * 16])

# A single swept sagittal crest replaces the 1.2 vertical antenna-like horns.
# Narrow overlapping blade segments retain a 2.70-block overall height while
# the face and skull below are visibly smaller and elongated fore-to-aft.
centered('head', [0, 40.64, 1.66], [1.18, 1.02, 3.17], 'armor', [19, 0, 0])
centered('head', [0, 41.37, 3.48], [.77, .7, 3.13], 'blade', [23, 0, 0])
centered('head', [0, 42.15, 4.85], [.43, .62, 1.78], 'blade', [35, 0, 0])
centered('head', [0, 42.72, 5.22], [.38, .36, 2.25], 'blade', [16, 0, 0])
centered('head', [0, 43.0, 6.59], [.12, .17, 1.15], 'edge', [-9, 0, 0])
centered('head', [0, 39.5, 1.77], [1.12, .33, 2.51], 'armor', [-7, 0, 0])

# Flexible abdominal chassis remains behind both independently animated chest
# and waist shells. The upper overlapping V plates conceal the waist seam.
centered('body', [0, 25.0, .15], [3.25, 3.65, 2.64], 'joint')
for s in [1, -1]:
    centered('waist', [s * .88, 25.3, -1.49], [1.85, .65, 1.17], 'armor',
             [-14, s * -13, s * -24])

# Arms stay on the original shoulder/elbow/wrist rig. Thin upper arms and long
# tapered blades create the elongated silhouette without moving any joints.
for side, s in [('left', 1), ('right', -1)]:
    sh, arm, fore, blade, hand = [f'{side}_{n}' for n in ['shoulder', 'arm', 'forearm', 'blade', 'hand']]
    centered('chest', [s * 4.65, 31, 0], [3.1, 1.6, 2.25], 'joint')
    centered('chest', [s * 4.37, 31.34, -.84], [.86, .4, 1.74], 'armor',
             [-14, s * -11, s * -14])
    centered(sh, [s * 5.15, 30.65, .4], [2.15, 2.5, 2.65], 'joint')
    # Socket axis spans the chest/shoulder seam, with a second sleeve carried
    # by the upper arm. Both remain centered on the original animated joints.
    centered(sh, [s * 5.2, 31, 0], [3.45, 1.7, 2.12], 'joint')
    centered(sh, [s * 5.12, 31.27, -1.03], [2.75, .45, .63], 'armor',
             [-11, s * -8, s * -12])
    centered(arm, [s * 5.8, 30, 0], [2.13, 2.05, 2.15], 'joint')
    for i in range(3):
        centered(sh, [s * (5.65 + i * .53), 32.1 - i * .57, 1.1 + i * .85],
                 [1.81 - i * .45, .4, 3.5 - i * .3], 'armor' if i == 0 else 'blade',
                 [-26 - i * 7, s * 13, s * -23])
    centered(sh, [s * 6.52, 32.45, 2.95], [.29, .35, 3.7], 'blade', [-35, s * 10, s * -20])
    centered(sh, [s * 6.39, 31.53, 3.57], [.21, .3, 2.23], 'edge', [-42, s * 11, s * -19])
    centered(sh, [s * 6.54, 30.65, 4.98], [.115, .21, .93], 'blade', [-47, s * 11, s * -19])
    centered(sh, [s * 5.84, 31.65, -.62], [1.65, .65, 1.95], 'metal', [-20, s * -9, s * -21])
    centered(arm, [s * 6.17, 27.0, .25], [1.62, 6.15, 1.85], 'joint', [0, 0, s * 5])
    centered(arm, [s * 6.28, 28.02, -.61], [1.86, 3.56, .92], 'armor', [-12, s * -8, s * -9])
    centered(arm, [s * 6.58, 25.61, -.1], [1.72, 2.18, 1.83], 'metal', [-8, 0, s * 5])
    centered(arm, [s * 6.85, 23.72, -.49], [2.08, 1.24, 2.08], 'joint')
    centered(arm, [s * 6.75, 23.7, -1.56], [.94, .13, .1], 'seam')
    centered(fore, [s * 6.8, 23, -.5], [2.48, 1.58, 2.12], 'joint')
    centered(fore, [s * 6.8, 23, -1.54], [2.12, .91, .44], 'metal', [-14, 0, 0])
    centered(fore, [s * 6.9, 20.1, -1.1], [1.77, 6.05, 1.96], 'joint', [11, 0, s * -3])
    for i in range(3):
        centered(fore, [s * (7.08 + i * .17), 22.43 - i * 1.44, -1.67 - i * .24],
                 [2.52 - i * .12, 1.56, 1.37], 'armor' if i != 1 else 'metal',
                 [17, s * -10, s * -17])
    centered(fore, [s * 7.2, 20.4, .05], [1.81, 3.8, 1.09], 'armor', [10, 0, s * -8])
    centered(fore, [s * 7.28, 21.0, -2.55], [.13, 2.65, .12], 'seam', [17, s * -10, s * -17])
    centered(fore, [s * 7, 16.45, -2], [1.52, 1.78, 1.62], 'joint')
    centered(hand, [s * 7, 16, -2], [1.96, .42, 1.93], 'metal', [7, 0, 0])
    # Four overlapping swept main-blade sections create a convex armored spine
    # and a hooked cutting tip, with distinct narrower bevel facets.
    for i, (x, y, z, w, h, d, rx, rz) in enumerate([
            (8.18, 23.15, .38, 1.24, 4.8, 2.0, -5, -8),
            (8.69, 19.69, -.27, 1.24, 4.98, 1.43, -12, 8),
            (9.14, 15.83, -1.54, .87, 4.46, 1.12, -25, 12),
            (9.54, 12.84, -3.1, .41, 2.85, .73, -35, 15)]):
        centered(blade, [s * x, y, z], [w, h, d], 'blade', [rx, s * -13, s * rz])
        centered(blade, [s * (x + w * .33), y + .15, z - d * .1],
                 [max(.1, w * .18), h * .92, d * .9], 'edge', [rx, s * -13, s * rz])
    centered(blade, [s * 9.77, 11.54, -4.0], [.13, 1.05, .35], 'edge', [-38, s * -13, s * 15])
    centered(blade, [s * 9.92, 11.02, -4.34], [.055, .53, .19], 'blade', [-45, s * -13, s * 16])
    for i in range(3):
        centered(blade, [s * (8.5 + i * .42), 22.63 - i * 2.94, -1.01 - i * .78],
                 [.13, 2.75 - i * .28, .1], 'seam', [-5 - i * 10, s * -13, s * (4 + i * 5)])
    centered(blade, [s * 8.72, 20.74, -1.54], [.31, 2.12, .33], 'armor', [-20, s * -13, s * 18])
    centered(blade, [s * 9.17, 17.57, -2.32], [.22, 1.76, .25], 'blade', [-29, s * -13, s * 17])
    # Rear auxiliary blade is shorter and reversed, never a second flat plank.
    centered(blade, [s * 7.84, 24.1, 2.16], [.67, 3.46, 1.75], 'armor', [-36, s * 8, s * -14])
    centered(blade, [s * 8.1, 26.26, 3.37], [.32, 1.84, 1.25], 'blade', [-40, s * 8, s * -14])
    centered(blade, [s * 8.23, 27.34, 4.27], [.12, .7, .55], 'edge', [-44, s * 8, s * -14])
    centered(hand, [s * 7.0, 16.35, -2.04], [1.68, 2.2, 1.74], 'joint', [14, 0, 0])
    centered(hand, [s * 7.0, 16.18, -2.85], [1.81, 1.57, .61], 'armor', [17, 0, 0])
    for i in range(3):
        x = s * 7 + (i - 1) * .61
        centered(hand, [x, 14.64, -2.86], [.37, 2.5, .42], 'metal', [-20, 0, (i - 1) * -10])
        centered(hand, [x, 13.21, -3.42], [.24, 1.39, .43], 'blade', [-46, 0, (i - 1) * -10])

    for i in range(3):
        centered(blade, [s * (8.42 + i * .29), 21.83 - i * 2.25, .45 - i * .75],
                 [.85 - i * .11, 2.13, .49], 'armor', [-20 - i * 7, s * -15, s * 17])

# Digitigrade legs. Long slim thigh plates point into an elevated angular knee;
# the tibia slopes back to a fine hock before the long low three-clawed foot.
for side, s in [('left', 1), ('right', -1)]:
    leg, shin, foot = [f'{side}_{n}' for n in ['leg', 'lower_leg', 'foot']]
    centered(leg, [s * 2.8, 22, .5], [2.25, 2.08, 2.4], 'joint')
    centered('waist', [s * 3.65, 22.3, .5], [.42, 2.22, 2.3], 'armor',
             [-12, s * 10, s * -8])
    centered(leg, [s * 2.85, 18.27, -.16], [2.04, 7.4, 2.21], 'joint', [-12, 0, s * -2])
    for i in range(3):
        centered(leg, [s * (3.0 + i * .055), 20.49 - i * 2.02, -1.09 - i * .22],
                 [2.39 - i * .2, 2.18, 1.02], 'armor' if i != 1 else 'metal',
                 [-17, s * -5, s * -11])
    centered(leg, [s * 3.97, 19.29, .28], [.45, 3.3, 1.64], 'blade', [-21, 0, s * -12])
    centered(leg, [s * 3.09, 14.14, -1.15], [2.09, 1.54, 2.1], 'joint')
    centered(leg, [s * 3.09, 14.37, -2.17], [2.32, 1.46, .8], 'blade', [-22, 0, 0])
    centered(leg, [s * 3.09, 14.71, -2.4], [.61, 1.09, .65], 'edge', [-31, 0, 0])
    centered(leg, [s * 3.02, 17.65, -1.89], [.12, 2.18, .1], 'seam', [-17, s * -5, s * -11])
    centered(shin, [s * 3.1, 13, -1], [2.0, 1.81, 1.9], 'joint')
    centered(shin, [s * 3.1, 13.0, -1.99], [2.14, .93, .45], 'metal', [-14, 0, 0])
    centered(shin, [s * 3.1, 8.7, .56], [1.16, 9.29, 1.46], 'joint', [23, 0, 0])
    for i in range(3):
        centered(shin, [s * 3.1, 11.42 - i * 2.19, -.32 + i * .93],
                 [1.92 - i * .21, 2.63, .87], 'armor' if i != 1 else 'metal', [25, 0, 0])
    centered(shin, [s * 3.1, 9.53, 1.85], [1.39, 4.53, .91], 'armor', [27, 0, 0])
    centered(shin, [s * 3.1, 5.33, 2.68], [1.33, 2.43, 1.9], 'joint', [5, 0, 0])
    centered(shin, [s * 3.1, 6.09, 3.71], [.49, 2.71, 1.55], 'blade', [-33, 0, 0])
    centered(shin, [s * 3.93, 10.48, 1.51], [.31, 3.3, 1.58], 'blade', [-19, 0, s * -13])
    centered(shin, [s * 3.1, 10.2, -.44], [.12, 2.6, .1], 'seam', [25, 0, 0])
    centered(shin, [s * 3.1, 4, 2], [1.45, 1.65, 1.85], 'joint')
    centered(foot, [s * 3.1, 4.05, 2], [1.8, .38, 2.1], 'metal', [8, 0, 0])
    centered(foot, [s * 3.1, 3.55, 1.68], [1.65, 2.62, 2.34], 'joint', [17, 0, 0])
    centered(foot, [s * 3.1, 1.83, -.3], [2.1, 1.62, 4.6], 'armor', [13, 0, 0])
    centered(foot, [s * 3.1, 2.15, -1.1], [1.72, .62, 2.7], 'metal', [18, 0, 0])
    for i in range(3):
        x = s * 3.1 + (i - 1) * .77
        centered(foot, [x, .94, -2.82], [.52, .82, 2.94], 'metal', [3, (i - 1) * 9, 0])
        centered(foot, [x, .4, -4.62], [.31, .8, 1.83], 'blade', [0, (i - 1) * 12, 0])

    for i in range(2):
        centered(shin, [s * 3.44, 10.58 - i * 2.77, -.24 + i * .83], [.49, 3.25, .61],
                 'blade', [28, s * 4, s * -11])
    for i in range(3):
        x = s * 3.1 + (i - 1) * .77
        centered(foot, [x, .23, -5.45 - (1 if i == 1 else 0) * .13], [.125, .46, .72],
                 'edge', [0, (i - 1) * 12, 0])

# Long dorsal armor follows the sagittal/rearward axis. Lateral width remains
# inside the shoulders; the original deployable back_* bones remain untouched.
centered('back_spines', [0, 29.68, 2.23], [.83, 7.8, .8], 'armor', [-3, 0, 0])
for i in range(4):
    y = 33.57 - i * 2.15
    z = 3.52 + i * .27
    centered('back_spines', [0, y, z], [.67 - i * .08, 1.08 - i * .08, 4.25 - i * .27], 'armor', [-32, 0, 0])
    centered('back_spines', [0, y - 1.14, z + 2.76 - i * .15], [.22 - i * .025, .32, 2.59 - i * .2], 'blade', [-39, 0, 0])
    centered('back_spines', [0, y + .39, z + .44], [.115, .13, 1.81], 'seam', [-32, 0, 0])
for side, s in [('left', 1), ('right', -1)]:
    n = f'back_blade_{side}'
    for i in range(3):
        x, y, z = 2.64 - i * .37, 33.26 - i * 3.32, 2.88 + i * .21
        centered(n, [s * x, y, z], [.62, 1.23 - i * .11, 3.95 - i * .55], 'armor', [-29, s * 7, s * -12])
        centered(n, [s * (x + .24), y - 1.11, z + 2.72 - i * .42], [.35, .78 - i * .1, 3.56 - i * .46], 'blade', [-39, s * 6, s * -9])
        centered(n, [s * (x + .32), y - 2.05, z + 4.61 - i * .78], [.135, .21, 1.89 - i * .31], 'edge', [-48, s * 6, s * -9])
        centered(n, [s * (x + .34), y - 2.72 + i * .11, z + 5.22 - i * .88], [.065, .1, .65], 'blade', [-54, s * 6, s * -9])
    centered(n, [s * 2.9, 32.81, 3.56], [.13, 1.76, .13], 'seam', [-39, s * 6, s * -9])
centered('tail_or_rear_spine', [0, 23.09, 2.56], [.89, .51, 2.29], 'armor', [-21, 0, 0])
centered('tail_or_rear_spine', [0, 22.07, 4.16], [.51, .39, 3.23], 'blade', [-33, 0, 0])
centered('tail_or_rear_spine', [0, 20.56, 5.88], [.18, .21, 2.25], 'edge', [-41, 0, 0])
centered('tail_or_rear_spine', [0, 22.5, 3.87], [.11, 1.58, .12], 'seam', [-33, 0, 0])

PALETTE = {
    'joint': (10, 12, 15), 'armor': (38, 43, 49), 'metal': (51, 57, 65),
    'edge': (81, 88, 97), 'blade': (54, 61, 70),
    'seam': (232, 73, 13), 'core': (242, 79, 12), 'hot': (255, 185, 57),
    'eye': (255, 110, 16),
}
GLOW = {'seam', 'core', 'hot', 'eye'}
FACES = ('north', 'south', 'west', 'east', 'up', 'down')


def dimensions(size, face):
    x, y, z = size
    a, b = (x, y) if face in ['north', 'south'] else ((z, y) if face in ['west', 'east'] else (x, z))
    return max(1, math.ceil(a * 4)), max(1, math.ceil(b * 4))


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
    panel_variation = RNG.randint(-4, 5) if mat not in GLOW else 0
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
                bevel = 16 if yy == 0 else (-14 if yy == h - 1 else 0)
                bevel += 10 if xx == 0 else (-7 if xx == w - 1 else 0)
                # Deliberate panel-level light/dark separation, a subtly darker
                # center, and a narrow secondary polished edge.
                bevel += 4 if xx == 1 and w > 5 else 0
                cavity = -5 * max(0, 1 - abs((xx + .5) / w * 2 - 1))
                bevel += cavity + panel_variation + 3 * (1 - yy / max(1, h - 1))
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
    # Small controlled edge scratches. They occupy few pixels on each shell,
    # keeping clean machine armor rather than covering it in random dirt.
    if mat not in GLOW and mat != 'joint' and w >= 6 and h >= 6:
        for scratch in range(1 if w * h < 100 else 2):
            sx = RNG.randrange(1, max(2, min(4, w - 1)))
            sy = RNG.randrange(1, h - 2)
            for step in range(min(RNG.randint(2, 4), w - sx - 1, h - sy - 1)):
                old = base_px[x0 + sx + step, y0 + sy + step]
                base_px[x0 + sx + step, y0 + sy + step] = tuple(min(255, v + 13) for v in old[:3]) + (255,)
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
        # GeckoLib mirrors X and negates source X/Y rotations. Mirror the
        # final game X back for author-space inspection: Rz(-Z) Ry(Y) Rx(-X).
        angles = [-c['rotation'][0], c['rotation'][1], -c['rotation'][2]]
        v = (v - pivot) @ rot_xyz(angles).T + pivot
    return v


FACE_VERTS = {
    'north': [0, 1, 2, 3], 'south': [5, 4, 7, 6],
    'west': [4, 0, 3, 7], 'east': [1, 5, 6, 2],
    'up': [3, 2, 6, 7], 'down': [4, 5, 1, 0],
}


def preview(yaw, pitch, width=600, height=740, *, materials=None,
            texture=None, glow_texture=None, focus=None, pixels_per_unit=None):
    """Z-buffer each textured triangle at 2x, then downsample for inspection."""
    aa = 2
    width, height = width * aa, height * aa
    rgba = np.zeros((height, width, 4), dtype=np.uint8)
    rgba[:, :, :] = (179, 184, 189, 255)
    zbuffer = np.full((height, width), -1e9)
    emissive = np.zeros((height, width, 4), dtype=np.uint8)
    camera = rot_xyz([pitch, yaw, 0])
    # camera looks at the negative Z facade and sees larger camera-space Z.
    scale = (pixels_per_unit or 13.4 * (height / (740 * aa))) * aa
    light = np.array([-.5, .8, .65])
    light /= np.linalg.norm(light)
    tex = np.asarray(texture if texture is not None else base)
    glowtex = np.asarray(glow_texture if glow_texture is not None else glow)
    for c, mat, owner in materials if materials is not None else MATERIALS:
        v = vertices(c)
        if focus is not None:
            v = v - np.array(focus)
        camera_v = v @ camera.T
        baseline = height * .58 if focus is not None else height - (78 / 740) * height
        screen = np.column_stack([width / 2 + camera_v[:, 0] * scale,
                                  baseline - camera_v[:, 1] * scale,
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
            lighting = .78 + .57 * max(0, float(normal @ light))
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


def reference_120():
    """Load exact 1.2 assets for matched cameras; never synthesize old images."""
    baseline = ROOT / 'build/remake-1.3/baseline'
    commit = 'a993aa4142e2c23c7e3af3bfccca368cfdeed592'

    def read(filename, source):
        local = baseline / filename
        return local.read_bytes() if local.exists() else subprocess.check_output(
            ['git', 'show', f'{commit}:src/main/resources/assets/apexpredator/{source}'], cwd=ROOT)

    old_geometry = json.loads(read('apex.geo.json', 'geo/apex.geo.json'))
    old_materials = [(c, 'reference', b['name']) for b in
                     old_geometry['minecraft:geometry'][0]['bones'] for c in b.get('cubes', [])]
    old_base = Image.open(BytesIO(read('apex.png', 'textures/entity/apex.png'))).convert('RGBA')
    old_glow = Image.open(BytesIO(read('apex_glow.png', 'textures/entity/apex_glow.png'))).convert('RGBA')
    return old_materials, old_base, old_glow


if __name__ == '__main__':
    output = ROOT / 'build/art'
    output.mkdir(parents=True, exist_ok=True)
    # Camera yaw180 yields the north/-Z facade. Multiple orthographic views
    # expose the digitigrade joints and deployable dorsal blades explicitly.
    views = [('FRONT', 180, 0), ('PREDATOR / 3-QUARTER', 145, -5),
             ('SIDE', 90, 0), ('BACK', 0, 0)]
    sheet = Image.new('RGB', (2400, 850), (13, 18, 24))
    draw = ImageDraw.Draw(sheet)
    fontpath = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    try:
        font = ImageFont.truetype(fontpath, 19)
        title = ImageFont.truetype(fontpath, 32)
    except OSError:
        font = title = ImageFont.load_default()
    draw.text((35, 15), 'A P E X   /   P R E D A T O R', font=title, fill=(225, 233, 240))
    draw.text((35, 55), '1.3 predator mask + articulated joint sleeves | 2.70 blocks | original 28-bone rig',
              font=font, fill=(143, 156, 169))
    for i, (label, yaw, pitch) in enumerate(views):
        sheet.paste(preview(yaw, pitch), (600 * i, 90))
        draw.text((600 * i + 32, 806), label, font=font, fill=(236, 116, 44))
    sheet.save(output / 'apex-model-turnaround.png')
    visual_dir = ROOT / 'docs/visuals'
    visual_dir.mkdir(parents=True, exist_ok=True)
    for label, yaw in [('front', 180), ('side', 90), ('back', 0)]:
        preview(yaw, 0, 900, 1100).save(visual_dir / f'apex-1.3-{label}.png')
    sheet.save(visual_dir / 'apex-1.3-turnaround.png')
    old_materials, old_base, old_glow = reference_120()
    matched_focus = (0, 38.8, 1.0)
    comparison = Image.new('RGB', (2700, 1760), (13, 18, 24))
    comparison_draw = ImageDraw.Draw(comparison)
    for i, (label, yaw) in enumerate([('front', 180), ('side', 90), ('45deg', 135)]):
        options = dict(width=900, height=800, focus=matched_focus, pixels_per_unit=68)
        new_head = preview(yaw, 0, **options)
        old_head = preview(yaw, 0, materials=old_materials, texture=old_base,
                           glow_texture=old_glow, **options)
        new_head.save(visual_dir / f'apex-1.3-head-{label}.png')
        comparison.paste(old_head, (900 * i, 55))
        comparison.paste(new_head, (900 * i, 935))
        comparison_draw.text((900 * i + 25, 18), f'1.2 / {label.upper()} / original assets',
                             font=font, fill=(181, 191, 204))
        comparison_draw.text((900 * i + 25, 895), f'1.3 / {label.upper()} / new mask',
                             font=font, fill=(245, 122, 48))
        pair = Image.new('RGB', (1800, 855), (13, 18, 24))
        pair.paste(old_head, (0, 55))
        pair.paste(new_head, (900, 55))
        pair_draw = ImageDraw.Draw(pair)
        pair_draw.text((25, 18), f'1.2 / {label.upper()}', font=font, fill=(181, 191, 204))
        pair_draw.text((925, 18), f'1.3 / {label.upper()}', font=font, fill=(245, 122, 48))
        pair.save(visual_dir / f'apex-1.2-vs-1.3-head-{label}.png')
    comparison.save(visual_dir / 'apex-1.2-vs-1.3-head-comparison.png')
    body_comparison = Image.new('RGB', (1800, 1155), (13, 18, 24))
    body_comparison.paste(preview(180, 0, 900, 1100, materials=old_materials,
                                texture=old_base, glow_texture=old_glow), (0, 55))
    body_comparison.paste(preview(180, 0, 900, 1100), (900, 55))
    body_draw = ImageDraw.Draw(body_comparison)
    body_draw.text((25, 18), '1.2 / SAME NEUTRAL RIG / FRONT', font=font, fill=(181, 191, 204))
    body_draw.text((925, 18), '1.3 / CONTINUOUS ARTICULATED JOINTS', font=font, fill=(245, 122, 48))
    body_comparison.save(visual_dir / 'apex-1.2-vs-1.3-front.png')
    base.resize((1024, 1024), Image.Resampling.NEAREST).save(output / 'apex-atlas.png')
    glow.resize((1024, 1024), Image.Resampling.NEAREST).save(output / 'apex-glow-atlas.png')
    allv = np.concatenate([vertices(c) for c, _, _ in MATERIALS])
    cranial_names = {'head', 'face_plate', 'eye_glow'}
    old_head_v = np.concatenate([vertices(c) for c, _, n in old_materials if n in cranial_names])
    new_head_v = np.concatenate([vertices(c) for c, _, n in MATERIALS
                                 if n in cranial_names and c is not CERVICAL_HUB])
    old_head_size = np.ptp(old_head_v, axis=0)
    new_head_size = np.ptp(new_head_v, axis=0)
    raw_head_v = np.concatenate([vertices(c) for c, _, n in MATERIALS if n in cranial_names])
    metrics = {
        'bones': len(BONES), 'cubes': len(MATERIALS), 'quads': len(MATERIALS) * 6,
        'atlas_size': SIZE, 'atlas_last_row': cursor_y + shelf,
        'bounds_min_pixels': np.min(allv, axis=0).round(3).tolist(),
        'bounds_max_pixels': np.max(allv, axis=0).round(3).tolist(),
        'emissive_pixels': int(np.count_nonzero(np.asarray(glow)[:, :, 3])),
        'head_1_2_size_pixels': old_head_size.round(4).tolist(),
        'head_1_3_size_pixels': new_head_size.round(4).tolist(),
        'head_size_change_percent': ((new_head_size / old_head_size - 1) * 100).round(2).tolist(),
        'head_size_measurement': 'cranial silhouette; concealed neck-pivot axle excluded',
        'head_1_3_all_geometry_size_pixels': np.ptp(raw_head_v, axis=0).round(4).tolist(),
        'cervical_hub_bounds_min_pixels': vertices(CERVICAL_HUB).min(0).round(4).tolist(),
        'cervical_hub_bounds_max_pixels': vertices(CERVICAL_HUB).max(0).round(4).tolist(),
        'head_cubes_1_2': sum(n in cranial_names for c, _, n in old_materials),
        'head_cubes_1_3': sum(n in cranial_names for c, _, n in MATERIALS),
        'preserved_head_cube_definitions': 0,
        'hierarchy': [{'name': b['name'], 'parent': b.get('parent'), 'pivot': b['pivot']} for b in BONES],
    }
    (output / 'apex-model-metrics.json').write_text(json.dumps(metrics, indent=2) + '\n')
    print(json.dumps({k: v for k, v in metrics.items() if k != 'hierarchy'}, indent=2))
