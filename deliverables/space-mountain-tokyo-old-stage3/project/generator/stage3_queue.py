"""Four changes in atmosphere: arrival, orderly queue, star map, launch reveal."""
import math
from generator.stage3_core import build_corridor,sample_path


def generate_queue(model,c,layout):
    floor=c['queue_floor_y']
    points=layout['queue_centerline']
    # Curved southwest gallery followed by an orderly double-back navigation hall.
    build_corridor(model,points,'queue',radius=c['queue_width_blocks']//2+1,floor_y=floor,ceiling_y=c['queue_ceiling_y'])
    # A brighter, taller observation nook at the far end of the fold.
    for x in range(66,82):
        for z in range(114,124):
            if 67<=x<=80 and 115<=z<=122:
                # Remove only this draft's low ceiling, never the Stage 2 shell.
                for y in [c['queue_ceiling_y']-1,c['queue_ceiling_y']]:
                    p=(x,y,z)
                    if model.groups.get(p)=='queue' and model.blocks.get(p) in {
                        'minecraft:white_concrete','minecraft:sea_lantern'}:
                        model.blocks.pop(p)
                        model.groups.pop(p)
            model.set(x,floor,z,'minecraft:smooth_quartz','queue')
            model.set(x,floor+10,z,'minecraft:white_concrete','queue')
            if x in {66,81} or z in {114,123}:
                for y in range(floor+7,floor+10):
                    model.set(x,y,z,'minecraft:white_concrete','queue')
    # Glass dividers preserve the parallel flow, low enough to see the gallery.
    model.box((73,floor+1,120),(73,floor+2,125),'minecraft:smooth_quartz','queue')
    model.box((73,floor+3,120),(73,floor+3,125),'minecraft:light_blue_stained_glass','queue')
    # Curved white frame around a deep-blue star chart on the western wall.
    x=64
    for z in range(116,124):
        for y in range(floor+2,floor+7):
            model.set(x,y,z,'minecraft:blue_concrete','queue')
            model.set(x+1,y,z,'minecraft:tinted_glass','queue')
    for y,z in [(floor+3,117),(floor+5,120),(floor+2,122),(floor+4,123),(floor+6,118)]:
        model.set(x+1,y,z,'minecraft:sea_lantern','queue')
    for theta in range(0,360,20):
        r=math.radians(theta)
        model.set(x+1,round(floor+4+2*math.sin(r)),round(120+3*math.cos(r)),
                  'minecraft:smooth_quartz','queue')
    for z in range(116,124):
        for y in range(floor+2,floor+7):
            model.set(66,y,z,'minecraft:light_blue_stained_glass','queue')
    model.box((65,floor+1,115),(65,floor+6,115),'minecraft:smooth_quartz','queue')
    model.box((65,floor+1,124),(65,floor+6,124),'minecraft:smooth_quartz','queue')
    # Three quiet departure-information panels at different heights along travel.
    for xx,zz in [(87,139),(75,132),(83,124)]:
        model.box((xx,floor+3,zz),(xx,floor+5,zz+2),'minecraft:blue_concrete','queue')
        model.set(xx,floor+6,zz+1,'minecraft:sea_lantern','queue')
    # Smooth launch-gallery ribs: sightline broadens before the station door.
    for x in [80,84]:
        for z in [131,137]:
            model.line((x,floor+1,z),(x,floor+6,z),'minecraft:smooth_quartz','queue')
    model.box((85,floor+6,131),(87,floor+7,137),'minecraft:light_blue_concrete','queue')
    model.box((86,floor+8,130),(87,floor+8,138),'minecraft:sea_lantern','queue')
