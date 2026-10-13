"""Rail-free reservations, including user additions honored by every write."""
import json
import math


def initial_areas(c):
    cx,cz=c['center_x'],c['center_z']
    return [
        {'id':'future_ride_core','min':[cx-25,5,cz-25],'max':[cx+25,31,cz+25],
         'purpose':'後工程でユーザーがレールを敷く中央空間。実施設の構造ではなく設計予約。',
         'confidence':'creative_completion'},
        {'id':'south_station_approach','min':[cx-8,5,cz+27],'max':[cx+8,15,cz+66],
         'purpose':'入口と中央空間の間の駅候補・敷設導線。実測ではない。',
         'confidence':'creative_completion'}
    ]


def read_user_areas(path):
    doc=json.loads(path.read_text(encoding='utf-8'))
    result=[]
    for index,a in enumerate(doc['areas']):
        lo,hi=a['min'],a['max']
        if len(lo)!=3 or len(hi)!=3 or any(type(v) is not int for v in lo+hi):
            raise ValueError(f'Invalid integer cuboid at area {index}')
        if any(lo[i]>hi[i] for i in range(3)):
            raise ValueError(f'Inverted bounds at area {index}')
        result.append(dict(a,id=a.get('id',f'user_{index}'),confidence='user_defined'))
    return result


def as_cuboids(areas):
    return [(a['min'],a['max']) for a in areas]


def guide_reservations(points,c):
    result=[]
    half=math.ceil(c['clearance_width']/2)
    for segment,(a,b) in enumerate(zip(points,points[1:])):
        count=max(1,math.ceil(math.dist(a,b)))
        for i in range(count+1):
            t=i/count
            x,y,z=[round(a[k]*(1-t)+b[k]*t) for k in range(3)]
            result.append({'id':f'guide_{segment}_{i}',
                           'min':[x-half,y-c['clearance_below'],z-half],
                           'max':[x+half,y+c['clearance_above'],z+half],
                           'purpose':'推定の仮中心線周辺。レールでも既設コース再現でもない。',
                           'confidence':'creative_completion'})
    return result
