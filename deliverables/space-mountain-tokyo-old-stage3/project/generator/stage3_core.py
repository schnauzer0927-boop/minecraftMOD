"""Additive Stage 3 placement: existing shell, rail and walk space are separate."""
from collections import Counter
import math

from generator.geometry import Model


def sample_path(points,step=0.5):
    result=[]
    for a,b in zip(points,points[1:]):
        n=max(1,math.ceil(math.dist(a,b)/step))
        for i in range(n+1):
            t=i/n
            result.append(tuple(round(a[k]*(1-t)+b[k]*t) for k in range(3)))
    return list(dict.fromkeys(result))


def footprint(points,radius):
    return {(x+dx,z+dz) for x,y,z in sample_path(points)
            for dx in range(-radius,radius+1) for dz in range(-radius,radius+1)
            if dx*dx+dz*dz<=radius*radius+1}


class AdditiveModel(Model):
    def __init__(self,baseline,protected,walking):
        super().__init__(protected)
        self.baseline=baseline
        self.walking=walking
        self.exterior_skips=Counter()
        self.walkway_skips=Counter()

    def set(self,x,y,z,block,group):
        x,y,z=int(x),int(y),int(z)
        key=(x,y,z)
        if key in self.baseline:
            self.exterior_skips[group]+=1
            return
        if key in self.walking:
            self.walkway_skips[group]+=1
            return
        super().set(x,y,z,block,group)


def walking_reservations(paths):
    columns={}
    stair_columns={}
    for path in paths:
        radius=path.get('clear_radius',2)
        nodes=path['checkpoints']
        # Rising/dropping staircase nodes must keep their own floor height.
        # Spreading a low approach-Y over a higher tread would remove supports.
        for i,(x,fy,z) in enumerate(nodes):
            neighbors=nodes[max(0,i-1):i]+nodes[i+1:i+2]
            stair_node=fy>min(p[1] for p in nodes) and any(
                abs(fy-p[1])==1 and abs(x-p[0])+abs(z-p[2])==1 for p in neighbors)
            if stair_node:
                for dx in range(-radius,radius+1):
                    stair_columns[x+dx,z]=fy
        for x,feet_y,z in sample_path(path['checkpoints']):
            for dx in range(-radius,radius+1):
                for dz in range(-radius,radius+1):
                    key=(x+dx,z+dz)
                    columns[key]=max(columns.get(key,feet_y),feet_y)
    columns.update(stair_columns)
    air={(x,fy+dy,z) for (x,z),fy in columns.items() for dy in range(4)}
    return air


def build_corridor(model,points,group,*,radius=3,floor_y=4,ceiling_y=12,enclosed=True):
    cells=footprint(points,radius)
    for x,z in sorted(cells):
        # Low foundation below the new floor, above the retained original floor.
        for y in range(2,floor_y+1):
            model.set(x,y,z,'minecraft:polished_andesite' if y<floor_y else 'minecraft:smooth_quartz',group)
        boundary=any((x+dx,z+dz) not in cells for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])
        if enclosed:
            model.set(x,ceiling_y,z,'minecraft:white_concrete',group)
        if boundary and enclosed:
            for y in range(floor_y+1,ceiling_y):
                material='minecraft:white_concrete' if y<floor_y+3 or y>floor_y+6 else 'minecraft:light_gray_concrete'
                model.set(x,y,z,material,group)
            if (x+z)%7==0:
                model.set(x,floor_y+3,z,'minecraft:sea_lantern',group)
                model.set(x,floor_y+4,z,'minecraft:light_blue_stained_glass',group)
        elif enclosed and (x+z)%9==0:
            model.set(x,ceiling_y-1,z,'minecraft:sea_lantern',group)
    for x,y,z in sample_path(points,step=1):
        model.set(x,floor_y,z,'minecraft:light_blue_concrete',group)
    return cells
