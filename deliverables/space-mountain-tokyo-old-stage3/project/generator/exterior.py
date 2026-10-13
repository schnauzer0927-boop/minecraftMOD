"""Parametric *estimated* exterior study, not a measured architectural survey.

The ribbed white frustum, stepped skirt, V columns and crown are based on the
visually reviewed S-GH-TDL-01. The photograph has no measured scale or original
provenance. Therefore all dimensions, counts and hidden sides are estimates.
All slope/rib dimensions stay explicit in config.json for later calibration.
"""
import math

WHITE = 'minecraft:white_concrete'
QUARTZ = 'minecraft:smooth_quartz'
GRAY = 'minecraft:light_gray_concrete'
DARK = 'minecraft:gray_concrete'
BLUE = 'minecraft:blue_concrete'
GLASS = 'minecraft:blue_stained_glass'


def roof_height(r, c):
    fraction = min(1.0, max(0.0, (c['roof_radius']-r)/(c['roof_radius']-c['roof_top_radius'])))
    return c['roof_eave_y']+(c['roof_apex_y']-c['roof_eave_y'])*fraction**c['roof_profile_power']


def generate_exterior(model, c):
    cx, cz = c['center_x'], c['center_z']
    base = c['base_radius']
    # Two tier horizontal bands and recessed walls, not an opaque white drum.
    # The south entry stays open; unseen rear details are regularized estimates.
    for low, high, radius, material in [(0,2,base+4,GRAY),(3,9,base-9,DARK),
                                        (10,12,base+1,QUARTZ),(13,19,base-5,GRAY),
                                        (20,21,base,WHITE)]:
        for x in range(cx-radius-1,cx+radius+2):
            for z in range(cz-radius-1,cz+radius+2):
                r=math.hypot(x-cx,z-cz)
                if not radius-2.4 <= r <= radius:
                    continue
                for y in range(low,high+1):
                    south_portal = z>cz and abs(x-cx)<c['entrance_width']/2 and 3<=y<=c['entrance_top_y']
                    if not south_portal:
                        model.set(x,y,z,material,'base')
    # Ribbed concave cone. The floor/inside remains air for later manual rails.
    roof_radius = c['roof_radius']
    sectors = c['radial_ribs']
    phase=math.pi/sectors
    for x in range(cx-roof_radius-1,cx+roof_radius+2):
        for z in range(cz-roof_radius-1,cz+roof_radius+2):
            dx,dz=x-cx,z-cz
            r=math.hypot(dx,dz)
            if r>roof_radius:
                continue
            surface=round(roof_height(r,c))
            angle=math.atan2(dz,dx)+phase
            delta=((angle+math.pi/sectors)%(2*math.pi/sectors))-math.pi/sectors
            rib_distance=abs(math.sin(delta)*r)
            is_rib = rib_distance < c['rib_half_width'] and r>c['roof_top_radius']
            top=surface+(c['rib_raise'] if is_rib else 0)
            for y in range(surface-c['roof_thickness']+1,top+1):
                model.set(x,y,z,QUARTZ if is_rib else WHITE,'roof')
    # Raised eave fingers tie each rib into the skirt, matching the seen fins.
    for i in range(sectors):
        theta=i*2*math.pi/sectors-phase
        inner=roof_radius-7
        outer=base-1
        a=(cx+math.cos(theta)*inner,round(roof_height(inner,c))+2,cz+math.sin(theta)*inner)
        b=(cx+math.cos(theta)*outer,22,cz+math.sin(theta)*outer)
        model.line(a,b,QUARTZ,'roof',radius=1)
        # Top sidewall posts and the distinctive lower V/X column rhythm.
        ax=round(cx+math.cos(theta)*outer)
        az=round(cz+math.sin(theta)*outer)
        model.line((ax,13,az),(ax,20,az),WHITE,'base',radius=1)
        bottom=(cx+math.cos(theta)*outer,2,cz+math.sin(theta)*outer)
        left=(cx+math.cos(theta-math.pi/sectors)*outer,10,cz+math.sin(theta-math.pi/sectors)*outer)
        right=(cx+math.cos(theta+math.pi/sectors)*outer,10,cz+math.sin(theta+math.pi/sectors)*outer)
        # Skip braces in the south portal, including outside the reserved prism.
        if not (az>cz and abs(ax-cx)<c['entrance_width']/2+6):
            model.line(bottom,left,QUARTZ,'base',radius=1)
            model.line(bottom,right,QUARTZ,'base',radius=1)
        # Shadowed glazing/recesses between visible structural bays.
        for rr in [base-5]:
            px=round(cx+math.cos(theta+math.pi/sectors)*rr)
            pz=round(cz+math.sin(theta+math.pi/sectors)*rr)
            model.box((px-1,14,pz-1),(px+1,17,pz+1),GLASS,'base')
    # Flat circular crown with a raised rim, open void and needle cluster.
    # Photograph supports this form, but heights and antenna count are inferred.
    top=c['roof_top_radius']
    model.cylinder(cx,cz,top+2,c['roof_apex_y']+1,c['roof_apex_y']+3,WHITE,'crown',thickness=2)
    model.cylinder(cx,cz,1,c['roof_apex_y']+1,c['roof_apex_y']+7,QUARTZ,'crown')
    model.line((cx,c['roof_apex_y']+8,cz),(cx,c['roof_apex_y']+c['spire_height'],cz),QUARTZ,'crown')
    for i in range(12):
        theta=i*math.pi/6
        r=top-1
        x,z=round(cx+r*math.cos(theta)),round(cz+r*math.sin(theta))
        for dy in range(1,13):
            half=1 if dy<5 else 0
            model.box((x-half,c['roof_apex_y']+dy,z-half),
                      (x+half,c['roof_apex_y']+dy,z+half),QUARTZ,'crown')
    # A ground datum below all rail reservations. No upper floor closes the hall.
    model.cylinder(cx,cz,base-3,0,1,'minecraft:polished_andesite','foundation')
    return model
