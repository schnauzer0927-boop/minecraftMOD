"""Entrance massing placeholder for Stage 2; its authentic interior is Stage 3."""


def generate_entrance(model,c):
    cx,cz=c['center_x'],c['center_z']
    front=cz+c['base_radius']+1
    # Two curved-roof-inspired layered portico wings and an unobstructed doorway.
    for side in [-1,1]:
        x1=cx+side*18
        model.box((x1-3,2,front-6),(x1+3,13,front+12),'minecraft:smooth_quartz','entrance')
        model.box((x1-4,14,front-8),(x1+4,15,front+14),'minecraft:white_concrete','entrance')
        model.box((x1-2,6,front+13),(x1+2,10,front+13),'minecraft:blue_stained_glass','entrance')
    model.box((cx-22,16,front-5),(cx+22,18,front+14),'minecraft:smooth_quartz','entrance')
    model.box((cx-18,18,front-3),(cx+18,19,front+11),'minecraft:light_gray_concrete','entrance')
    # Suspended cyan-blue panel only; no fabricated historic logo texture.
    model.box((cx-11,14,front+14),(cx+11,15,front+15),'minecraft:cyan_concrete','entrance')
    model.box((cx-10,14,front+16),(cx+10,14,front+16),'minecraft:light_blue_concrete','entrance')


def generate_forecourt(model,c):
    n=c['site_size']
    # Forecourt is an illustrative layout, not a survey of Tomorrowland paths.
    for x in range(34,n-34):
        for z in range(c['center_z']+c['base_radius']-6,n-9):
            block='minecraft:smooth_stone' if (x//6+z//6)%5 else 'minecraft:light_gray_concrete'
            model.set(x,0,z,block,'forecourt')
    for x in [46,n-47]:
        model.box((x-4,1,174),(x+4,2,205),'minecraft:smooth_quartz','forecourt')
        model.box((x-3,3,175),(x+3,3,204),'minecraft:dark_oak_leaves[persistent=true]','forecourt')
    for x in [60,160]:
        for z in [182,204]:
            model.box((x-1,1,z-1),(x+1,2,z+1),'minecraft:smooth_quartz','forecourt')
            model.line((x,3,z),(x,10,z),'minecraft:iron_block','forecourt')
            model.box((x-2,10,z-2),(x+2,10,z+2),'minecraft:sea_lantern','forecourt')
