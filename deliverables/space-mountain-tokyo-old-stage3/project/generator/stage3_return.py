"""Separate, clearly marked return path; it uses the existing exterior portal."""
from generator.stage3_core import build_corridor


def generate_return_path(model,c,layout):
    floor=c['queue_floor_y']
    points=layout['return_centerline']
    build_corridor(model,points,'return',radius=2,floor_y=floor,ceiling_y=floor+7)
    for z in [153,160,166]:
        model.set(118,floor+4,z,'minecraft:sea_lantern','return')
        model.line((117,floor+5,z),(119,floor+5,z),'minecraft:light_blue_concrete','return')
    # Down-stairs, matching the entrance rise without sharing its queue lane.
    for i in range(floor):
        z=175-i
        for x in range(113,118):
            for y in range(1,i+1):
                model.set(x,y,z,'minecraft:smooth_quartz','return')
            model.set(x,i+1,z,'minecraft:smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]','return')
