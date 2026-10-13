"""Add Stage 3 to the verified Stage 2 shell, without rewriting that shell."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import sys
import time

if __package__ in {None,''}:sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from generator.clearance import as_cuboids,read_user_areas
from generator.export_schem import export_schem
from generator.main import dump
from generator.stage3_core import AdditiveModel,walking_reservations,sample_path
from generator.stage3_entrance import generate_entrance_interior
from generator.stage3_queue import generate_queue
from generator.stage3_return import generate_return_path
from generator.stage3_station import generate_station,station_geometry
from generator.validate_schem import read_schem,validate_schem

ROOT=Path(__file__).resolve().parents[1]
BASELINE_SHA='a5e921aca4850bfe4c73eb6076fa505409affefae547766f4cd8cf4ed3833f7b'


def layout_document(c):
    foot=c['queue_floor_y']+1
    stationfoot=c['station_floor_y']+1
    landing_z=175-c['queue_floor_y']
    entrance=[[107,foot,landing_z],[107,foot,161],[107,foot,153],[102,foot,149]]
    queue=[[102,foot,149],[98,foot,144],[86,foot,139],[76,foot,129],
           [69,foot,122],[69,foot,117],[78,foot,117],[78,foot,126],
           [82,foot,130],[85,foot,134],[94,stationfoot,134],[104,stationfoot,136]]
    returns=[[116,stationfoot,136],[124,stationfoot,136],[125,stationfoot,141],
             [124,foot,143],[119,foot,145],[115,foot,149],[115,foot,161],[115,foot,landing_z]]
    approach=[[107,1,178]]+[[107,i+2,175-i] for i in range(c['queue_floor_y'])]
    exitsteps=[[115,i+2,175-i] for i in reversed(range(c['queue_floor_y']))]+[[115,1,178]]
    station=station_geometry(c)
    return {'stage':3,'coordinate_space':'project_relative_xyz','origin':[0,0,0],
            'historical_interior_verified':False,'meters_per_block':0.5,'floor_y':c['queue_floor_y'],
            'entrance_centerline':entrance,
            'queue_centerline':queue[:-2]+[[90,stationfoot,134]],'return_centerline':returns,
            'walk_paths':[{'id':'entrance_to_boarding','clear_radius':c['queue_width_blocks']//2,'checkpoints':approach+entrance+queue,
                           'purpose':'入口→導入→折返し整列→星図→発車直前→左ホーム'},
                          {'id':'alighting_to_exit','clear_radius':1,'checkpoints':returns+exitsteps,
                           'purpose':'右ホーム→別帰還通路→同じ外観入口の右側出口'}],
            'queue_zones':[{'id':'arrival','name':'宇宙旅行ターミナル導入','confidence':'creative_completion'},
                           {'id':'organized_queue','name':'整然とした折返し整列','confidence':'creative_completion'},
                           {'id':'stellar_navigation','name':'星図・航路ギャラリー','confidence':'creative_completion'},
                           {'id':'launch_reveal','name':'明るい乗り場への視界の開放','confidence':'creative_completion'}],
            'station':station,
            'preview_cameras':{'entrance':{'eye':[107.5,foot+1.65,161.5],'target':[105,foot+3,152]},
                               'queue':{'eye':[76.5,foot+1.65,117.5],'target':[65,foot+3.5,120]},
                               'station':{'eye':[102.5,stationfoot+1.65,135.5],'target':[111,9.5,123.5]}}}


def planning_documents(c):
    old=json.loads((ROOT/'output/json/protected_areas.json').read_text(encoding='utf-8'))
    core=next(a for a in old['areas'] if a['id']=='future_ride_core')
    rail_y=c['station_floor_y']+2
    points=[[110,rail_y,141],[110,rail_y,122],[110,rail_y+4,108],[100,rail_y+12,95],
            [105,rail_y+16,81],[122,rail_y+12,93]]
    lane={'id':'station_rail_lane','min':[106,rail_y-1,120],'max':[114,rail_y+9,143],
          'purpose':'中央幅9の車両・レール予定空間。左右ホームはこの外側。',
          'confidence':'creative_completion'}
    areas=[dict(core),lane]
    half=math.ceil((c['normal_rail_width_blocks']-1)/2)
    for i,(x,y,z) in enumerate(sample_path(points,step=1)):
        areas.append({'id':f'stage3_departure_{i}',
                      'min':[x-half,y-c['normal_rail_below_blocks'],z-half],
                      'max':[x+half,y+c['normal_rail_above_blocks'],z+half],
                      'purpose':'発車口から暗所空間への予定通路。曲線・走行は手動敷設時に検証。',
                      'confidence':'creative_completion'})
    areas.extend(read_user_areas(ROOT/c['user_keepout_file']))
    protected={'schema_version':2,'stage':3,'coordinate_space':'project_relative_xyz','bounds_inclusive':True,
               'applies_to':'Python generation only; no live WorldEdit region',
               'retained_stage2_area_ids':['future_ride_core'],
               'superseded_stage2_planning_ids':['south_station_approach','guide_*'],
               'update_reason':'手動敷設前の仮駅予約を中央幅9へ具体化し、左右ホームを車両に届く位置へ追加。既存ワールドを変換・削除する処理ではない。',
               'rail_blocks_generated':False,'areas':areas}
    route={'schema_version':2,'stage':3,'coordinate_space':'project_relative_xyz',
           'historical_route_verified':False,'native_mod_physics_verified':False,'closed_course_designed':False,
           'rail_blocks_generated':False,'centerline_points':points,
           'interpolation':'piecewise linear reservations only; manually lay spline rails in game',
           'station':{'rail_height':rail_y,'platform_floor_y':c['station_floor_y'],'lane':lane,
                      'launch_direction':[0,0,-1],'brake_candidate':{'from':points[0],'to':points[1],
                                                                  'confidence':'creative_completion'}},
           'segments':[{'id':f'stage3_{i}','from':a,'to':b,
                        'use':['station_and_brake','launch_portal','dark_entry','rise_candidate','turn_candidate'][i],
                        'recommended_altitude':[a[1],b[1]],'confidence':'creative_completion',
                        'source_ids':['USER-STAGE3']} for i,(a,b) in enumerate(zip(points,points[1:]))],
           'warning':'東京版の実コースではありません。Stage3では駅と暗所入口への仮案だけを更新。残りのコース・帰還レールは手動設計。'}
    return route,protected


def build_stage3(c=None):
    c=c or json.loads((ROOT/'stage3_config.json').read_text(encoding='utf-8'))
    if c['station_floor_y']!=c['queue_floor_y']:
        raise ValueError('This connected layout requires matching station_floor_y and queue_floor_y; adapt entrance stairs when changing floors')
    if type(c['queue_floor_y']) is not int or not 2<=c['queue_floor_y']<=6:
        raise ValueError('Queue/platform floor must be an integer Y2..6 for the retained portal')
    if c['queue_width_blocks'] not in {3,5,7}:
        raise ValueError('Pedestrian clear width must be 3, 5 or 7')
    if not 8<=c['normal_rail_width_blocks']<=15:
        raise ValueError('Normal rail clearance width must be 8..15')
    baseline_path=ROOT/'output/schem/tokyo_old_stage2_site.schem'
    if hashlib.sha256(baseline_path.read_bytes()).hexdigest()!=BASELINE_SHA:
        raise ValueError('Stage 2 exterior differs from the preserved baseline; inspect before extending')
    baseline=read_schem(baseline_path)['blocks']
    layout=layout_document(c)
    route,protected=planning_documents(c)
    model=AdditiveModel(baseline,as_cuboids(protected['areas']),walking_reservations(layout['walk_paths']))
    generate_entrance_interior(model,c,layout)
    generate_queue(model,c,layout)
    generate_return_path(model,c,layout)
    generate_station(model,c)
    station=layout['station']
    station['lane']=next(a for a in protected['areas'] if a['id']=='station_rail_lane')
    parts={name:{p:b for p,b in model.blocks.items() if model.groups[p]==group}
           for name,group in [('entrance','entrance'),('queue','queue'),('return','return')]}
    parts['station']={p:b for p,b in model.blocks.items() if model.groups[p].startswith('station')}
    return baseline,model,parts,layout,route,protected


def generate(output=ROOT/'output/stage3',previews=True):
    start=time.monotonic()
    c=json.loads((ROOT/'stage3_config.json').read_text(encoding='utf-8'))
    baseline,model,parts,layout,route,protected=build_stage3(c)
    merged={**baseline,**model.blocks}
    files=[]
    def export(name,blocks):
        facts=export_schem(output/'schem'/(name+'.schem'),blocks,metadata={'Stage':3,'Name':name,
                          'Geometry':'Estimated interior; user Stage3 brief','Rails':False})
        facts['path']=str(Path('schem')/(name+'.schem'))
        facts['validation']=validate_schem(output/facts['path'])
        assert read_schem(output/facts['path'])['blocks']==blocks
        files.append(facts)
    for name,part in [('stage3_entrance_interior','entrance'),('stage3_queue','queue'),
                      ('stage3_station','station'),('stage3_return_path','return')]:
        export(name,parts[part])
    export('stage3_interior_combined',model.blocks)
    export('tokyo_old_with_stage3',merged)
    export('stage3_station_platforms',{p:b for p,b in model.blocks.items() if model.groups[p]=='station_platforms'})
    guide={p:'minecraft:magenta_stained_glass' for p in sample_path(route['centerline_points'],step=c['guide_interval_blocks'])}
    export('stage3_guide_optional',guide)
    dump(output/'json/updated_route_guide.json',route)
    dump(output/'json/updated_protected_areas.json',protected)
    dump(output/'json/walking_layout.json',layout)
    from generator.stage3_validation import validate_stage3
    validation=validate_stage3(baseline,model.blocks,protected,layout)
    dump(output/'reports/numerical_validation.json',validation)
    report={'stage':3,'baseline_sha256':BASELINE_SHA,'baseline_blocks':len(baseline),
            'added_blocks':len(model.blocks),'combined_blocks':len(merged),'rail_blocks':0,
            'historical_interior_verified':False,'game_paste_verified':False,
            'user_reported_stage2_silhouette_in_game':'概ね良好。ユーザー申告。Stage3のゲーム検証を意味しない。',
            'exterior_voxels_changed':sum(merged[p]!=b for p,b in baseline.items()),
            'groups':dict(Counter(model.groups.values())),'existing_voxel_writes_skipped':dict(model.exterior_skips),
            'pedestrian_air_writes_skipped':dict(model.walkway_skips),'rail_space_writes_skipped':dict(model.skipped),
            'files':files,'validation':validation}
    if not validation['valid']:
        dump(output/'reports/generation_report.json',report)
        print(json.dumps(validation.get('errors'),ensure_ascii=False))
        raise RuntimeError('Stage 3 geometry validation failed; inspect report and fix')
    if previews:
        from generator.stage3_preview import render_stage3_previews
        report['previews']=render_stage3_previews(parts,merged,output/'preview',layout)
    report['seconds']=round(time.monotonic()-start,3)
    dump(output/'reports/generation_report.json',report)
    print(json.dumps({'stage':3,'added_blocks':len(model.blocks),'schematics':len(files),
                      'exterior_changed':report['exterior_voxels_changed'],'geometry_valid':validation['valid'],
                      'output':str(output),'seconds':report['seconds']},ensure_ascii=False))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'output/stage3')
    parser.add_argument('--no-previews',action='store_true')
    a=parser.parse_args()
    generate(a.output.resolve(),not a.no_previews)
