"""Small voxel primitives. All positions share the northwest-corner paste origin.

Construction coordinates: east=+X, up=+Y, south=+Z. Materials are vanilla only.
Protected volumes are enforced at *every* block write, including later overlays.
"""
from __future__ import annotations

import math
from collections import Counter


class Model:
    def __init__(self, protected=()):
        self.blocks = {}
        self.groups = {}
        self.protected = tuple(protected)
        self.skipped = Counter()

    def is_protected(self, x, y, z):
        return any(a[0] <= x <= b[0] and a[1] <= y <= b[1] and a[2] <= z <= b[2]
                   for a, b in self.protected)

    def set(self, x, y, z, block, group):
        x, y, z = int(x), int(y), int(z)
        if self.is_protected(x, y, z):
            self.skipped[group] += 1
            return
        if not block.startswith('minecraft:'):
            raise ValueError('Only vanilla minecraft: blocks are allowed')
        if 'rail' in block or block == 'minecraft:tnt':
            raise ValueError('Rail/explosive blocks are forbidden')
        key = (x, y, z)
        self.blocks[key] = block
        self.groups[key] = group

    def box(self, a, b, block, group):
        for x in range(a[0], b[0] + 1):
            for y in range(a[1], b[1] + 1):
                for z in range(a[2], b[2] + 1):
                    self.set(x, y, z, block, group)

    def line(self, a, b, block, group, radius=0):
        n = max(1, math.ceil(math.dist(a, b) * 2))
        for i in range(n + 1):
            t = i / n
            p = [round(a[j] * (1 - t) + b[j] * t) for j in range(3)]
            for dx in range(-radius, radius + 1):
                for dy in range(-radius, radius + 1):
                    for dz in range(-radius, radius + 1):
                        self.set(p[0]+dx, p[1]+dy, p[2]+dz, block, group)

    def cylinder(self, cx, cz, radius, bottom, top, block, group, thickness=None):
        for x in range(math.floor(cx-radius), math.ceil(cx+radius)+1):
            for z in range(math.floor(cz-radius), math.ceil(cz+radius)+1):
                r = math.hypot(x-cx, z-cz)
                if r <= radius and (thickness is None or r >= radius-thickness):
                    for y in range(bottom, top+1):
                        self.set(x,y,z,block,group)

    def layer(self, group):
        return {p: b for p,b in self.blocks.items() if self.groups[p] == group}
