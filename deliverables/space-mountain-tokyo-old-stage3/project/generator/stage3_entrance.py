"""A calm travel-terminal foyer; interior details are acknowledged proposals."""
from generator.stage3_core import build_corridor


def generate_entrance_interior(model,c,layout):
    floor=c['queue_floor_y']
    points=layout['entrance_centerline']
    build_corridor(model,points,'entrance',radius=c['queue_width_blocks']//2+1,floor_y=floor,ceiling_y=floor+10)
    # A real walkable four-riser stair from the original forecourt floor Y0.
    for i in range(floor):
        z=175-i
        for x in range(104,111):
            for y in range(1,i+1):
                model.set(x,y,z,'minecraft:smooth_quartz','entrance')
            model.set(x,i+1,z,'minecraft:smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]','entrance')
    # Arrival-side white rib rhythm and small illuminated orientation panels.
    for z in [153,158,164,169]:
        for x in [103,111]:
            model.box((x,floor+1,z),(x,floor+8,z),'minecraft:smooth_quartz','entrance')
        model.box((103,floor+9,z),(111,floor+9,z),'minecraft:smooth_quartz','entrance')
        model.box((105,floor+8,z),(109,floor+8,z),'minecraft:light_blue_concrete','entrance')
        model.line((104,floor+8,z),(110,floor+8,z),'minecraft:sea_lantern','entrance')
    # Glass-backed star navigation map behind the left wall, not a noisy console.
    model.box((102,floor+2,157),(102,floor+6,163),'minecraft:blue_concrete','entrance')
    for y,z in [(floor+3,158),(floor+5,161),(floor+3,162)]:
        model.set(103,y,z,'minecraft:sea_lantern','entrance')
    # Left-turn motif on floor and an open circulation junction.
    for x in range(100,108):model.set(x,floor,151,'minecraft:light_blue_concrete','entrance')
    model.box((112,floor+1,153),(112,floor+7,165),'minecraft:white_concrete','entrance')
    model.line((112,floor+5,154),(112,floor+5,164),'minecraft:light_blue_concrete','entrance')
