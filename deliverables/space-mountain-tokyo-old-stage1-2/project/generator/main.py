"""Generate the Stage 1/2 rail-free deliverables. Run from any directory."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import time

if __package__ in {None, ''}:
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]))

from generator.clearance import as_cuboids, guide_reservations, initial_areas, read_user_areas
from generator.entrance import generate_entrance, generate_forecourt
from generator.exterior import generate_exterior
from generator.export_schem import export_schem
from generator.geometry import Model
from generator.route_guide import generate_visual_guide, route_document
from generator.validate_schem import read_schem, validate_schem

ROOT=Path(__file__).resolve().parents[1]


def dump(path,doc):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def config_at(path):
    c=json.loads(path.read_text(encoding='utf-8'))
    integers=['site_size','center_x','center_z','base_radius','roof_radius','roof_eave_y',
              'roof_apex_y','roof_top_radius','roof_thickness','radial_ribs','rib_raise',
              'spire_height','entrance_width','entrance_top_y','clearance_width',
              'clearance_below','clearance_above']
    for k in integers:
        if type(c[k]) is not int or c[k]<=0:
            raise ValueError(f'{k} must be a positive integer')
    if not 2<=c['radial_ribs']<=80 or not 0<c['roof_top_radius']<c['roof_radius']:
        raise ValueError('Invalid radial count or roof radii')
    if c['roof_apex_y']<=c['roof_eave_y'] or c['roof_thickness']>10:
        raise ValueError('Invalid roof height/thickness')
    if c['site_size']>1000 or c['base_radius']>400:
        raise ValueError('Large configuration requires an intentional generator extension')
    if any(c[k]<=0 for k in ['roof_profile_power','rib_half_width','meters_per_block']):
        raise ValueError('Profile power, rib width and scale must be positive')
    return c


def build_model(c,extra_areas=()):
    route=route_document(c)
    areas=initial_areas(c)+guide_reservations(route['centerline_points'],c)+list(extra_areas)
    model=Model(as_cuboids(areas))
    generate_exterior(model,c)
    generate_entrance(model,c)
    generate_forecourt(model,c)
    return model,route,areas


def generate(config_path=ROOT/'config.json',output=ROOT/'output',previews=True):
    start=time.monotonic()
    c=config_at(config_path)
    extra=read_user_areas(ROOT/c['user_keepout_file'])
    model,route,areas=build_model(c,extra)
    schem_dir=output/'schem'
    exported=[]

    def export(name,blocks,notes,optional=False):
        report=export_schem(schem_dir/(name+'.schem'),blocks,
                    metadata={'Name':name,'Author':'Original SpaceMountainTokyoOld generator',
                              'Stage':'2-exterior-study','Reference':'S-GH-TDL-01',
                              'Geometry':'Estimated; not an architectural survey',
                              'Notes':notes})
        report['path']=str(Path('schem')/(name+'.schem'))
        report['optional']=optional
        report['paste_origin_instruction']='Same fixed project anchor; //paste -a'
        exported.append(report)
        validated=validate_schem(schem_dir/(name+'.schem'))
        # Independently decoded coordinates must agree, including every part.
        restored=read_schem(schem_dir/(name+'.schem'))['blocks']
        if restored!=blocks:
            raise AssertionError(f'Export changed positions or states: {name}')
        return validated

    exterior={p:b for p,b in model.blocks.items() if model.groups[p] in {'base','roof','crown','foundation'}}
    checks=[]
    checks.append(export('tokyo_old_stage2_site',model.blocks,'Full Stage2 study with proposed entrance massing and forecourt.'))
    checks.append(export('tokyo_old_exterior',exterior,'Exterior only; independent of preliminary forecourt.'))
    # Four disjoint shell quadrants, all carrying the exact same paste anchor.
    for east,xname in [(False,'west'),(True,'east')]:
        for south,zname in [(False,'north'),(True,'south')]:
            part={p:b for p,b in exterior.items() if (p[0]>=c['center_x'])==east and (p[2]>=c['center_z'])==south}
            checks.append(export(f'exterior_{zname}_{xname}',part,'Disjoint quadrant of exterior. Do not move between pastes.'))
    for group in ['roof','base','crown','foundation','entrance','forecourt']:
        blocks=model.layer(group)
        if blocks:
            checks.append(export(group+'_stage2',blocks,'Editable additive layer; use same anchor and skip air.'))
    # A compact, rail-free first look. This is NOT part of the aligned site.
    small={tuple(int(v//2) for v in p):b for p,b in sorted(exterior.items(),key=lambda item:item[0][1])}
    checks.append(export('prototype_half_scale',small,'Standalone downsampled silhouette study, half the site scale.',True))
    guide=generate_visual_guide(c,route)
    checks.append(export('guide_optional',guide,'Magenta glass dots only. A proposed route; NOT the historic track.',True))
    dump(output/'json'/'route_guide.json',route)
    dump(output/'json'/'protected_areas.json',{'schema_version':1,'coordinate_space':'project_relative_xyz',
         'applies_to':'Python voxel writes only; not live WorldEdit protection',
         'paste_rule':'//paste -a and same anchor; user rails must stay within recorded reservations',
         'rail_blocks_generated':False,'bounds_inclusive':True,'areas':areas})
    bounds={'min':[min(p[i] for p in model.blocks) for i in range(3)],
            'max':[max(p[i] for p in model.blocks) for i in range(3)]}
    report={'stage_1':'Source log prepared; official site and requested video inaccessible; public photo reviewed',
            'stage_2':'Exterior prototype generated; photographic fidelity not certified',
            'stage_3_through_7':'Not implemented',
            'coordinate_space':{'origin':[0,0,0],'east':'+X','up':'+Y','south':'+Z'},
            'config':c,'bounds_blocks':bounds,'placed_blocks':len(model.blocks),
            'block_groups':dict(Counter(model.groups.values())),
            'protected_area_count':len(areas),'writes_skipped_for_protection':dict(model.skipped),
            'protected_occupied_blocks':sum(model.is_protected(*p) for p in model.blocks),
            'generated_rail_blocks':0,'gameplay_verified':False,'game_worldedit_paste_verified':False,
            'schematics':exported,'serialized_format_checks':checks}
    if report['protected_occupied_blocks']:
        raise AssertionError('Construction entered protected areas')
    if previews:
        from generator.preview import render_previews
        imgs=render_previews(model.blocks,output/'preview',site_size=c['site_size'],
                              meters_per_block=c['meters_per_block'],stage_label='Stage 2 - Tokyo old exterior study')
        report['previews']=imgs
        from generator.layout import render_layout
        report['layout']=render_layout(c,route,areas,output/'preview'/'layout-reservations.png')
        render_previews(small,output/'preview'/'prototype',site_size=c['site_size']//2,
                        meters_per_block=c['meters_per_block']*2,stage_label='Stage 2 - compact silhouette prototype')
    report['generation_seconds']=round(time.monotonic()-start,3)
    dump(output/'reports'/'generation_report.json',report)
    # Stable per-stage checkpoint; no impossible claim of a saved Minecraft world.
    dump(output/'reports'/'stage1_checkpoint.json',{'scope':'reference collection and evidence classification',
         'photo_reviewed':True,'source_id':'S-GH-TDL-01',
         'official_sources_verified':False,'user_video_viewed':False,
         'user_reference_images_found':False,'interior_verified':False})
    dump(output/'reports'/'stage2_checkpoint.json',{'scope':'rail-free exterior study',
         'schematic_files':len(exported),'block_count':len(model.blocks),'rails':0,
         'internal_air_reservations_checked':True,'historical_dimensions_verified':False,
         'gameplay_verified':False})
    print(json.dumps({'stage':'2','blocks':len(model.blocks),'schematics':len(exported),
                      'protected_conflicts':report['protected_occupied_blocks'],
                      'seconds':report['generation_seconds'],'output':str(output)},ensure_ascii=False))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,default=ROOT/'config.json')
    parser.add_argument('--output',type=Path,default=ROOT/'output')
    parser.add_argument('--no-previews',action='store_true')
    a=parser.parse_args()
    generate(a.config.resolve(),a.output.resolve(),not a.no_previews)
