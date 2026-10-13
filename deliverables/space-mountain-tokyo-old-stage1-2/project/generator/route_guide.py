"""Preliminary hand-building hints, expressly NOT Tokyo's historic track map."""


def route_document(c):
    cx,cz=c['center_x'],c['center_z']
    points=[[cx,10,cz+50],[cx,16,cz+20],[cx-18,24,cz+2],
            [cx-8,27,cz-18],[cx+20,24,cz-4],[cx+20,15,cz+24],[cx,10,cz+50]]
    descriptions=[('platform_candidate','駅候補から低速の導入'),
                  ('rise_candidate','上昇・リフト候補'),('dark_turn_candidate','暗所カーブ候補'),
                  ('cruise_candidate','空間を見ながら回り込む候補'),
                  ('descent_candidate','下降・高速区間候補（走行速度未設定）'),
                  ('brake_candidate','帰還・ブレーキ候補')]
    segments=[]
    for i,(kind,text) in enumerate(descriptions):
        segments.append({'id':f'candidate_{i+1}','from':points[i],'to':points[i+1],
                         'purpose':kind,'description':text,'recommended_altitude_blocks':[points[i][1],points[i+1][1]],
                         'confidence':'creative_completion','source_ids':['USER-REQ'],
                         'historical_route_verified':False})
    return {'schema_version':1,'stage':'2-preliminary','coordinate_space':'project_relative_xyz',
            'warning':'東京旧版の実レール配置を再現したものではありません。後の設計のための仮案です。',
            'rail_blocks_generated':False,'native_mod_physics_verified':False,
            'centerline_points':points,'interpolation':'piecewise_linear_clearance_envelope_only',
            'clearance':{'width_blocks':c['clearance_width'],
                         'below_blocks':c['clearance_below'],'above_blocks':c['clearance_above'],
                         'inclusive_voxel_span':[2*((c['clearance_width']+1)//2)+1,
                                                 c['clearance_below']+c['clearance_above']+1]},
            'segments':segments,'platform_candidates':[{'id':'south_station_approach','center':points[0],
                                                       'confidence':'creative_completion'}]}


def generate_visual_guide(c,route):
    # Dotted magenta glass ONLY. Optional separate overlay, never in exterior.
    blocks={}
    import math
    for a,b in zip(route['centerline_points'],route['centerline_points'][1:]):
        count=max(1,math.ceil(math.dist(a,b)/3))
        for i in range(count+1):
            t=i/count
            p=tuple(round(a[k]*(1-t)+b[k]*t) for k in range(3))
            blocks[p]='minecraft:magenta_stained_glass'
    return blocks
