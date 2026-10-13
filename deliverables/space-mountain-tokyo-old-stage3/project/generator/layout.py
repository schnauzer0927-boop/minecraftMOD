"""Standalone plan of construction reservations; not a measured ride layout."""
import math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont


def render_layout(c,route,areas,path):
    im=Image.new('RGB',(1800,1950),(245,248,250))
    d=ImageDraw.Draw(im)
    def font(n):
        try:return ImageFont.truetype('DejaVuSans.ttf',n)
        except OSError:return ImageFont.load_default(size=n)
    scale=6
    ox,oz=180,210
    def point(x,z):return ox+x*scale,oz+z*scale
    d.text((80,55),'TOKYO OLD SPACE MOUNTAIN / STAGE 2',font=font(39),fill=(28,48,67))
    d.text((80,115),'Rail-free reservations - proposed only; NOT a historic track map',font=font(25),fill=(87,109,123))
    n=c['site_size']
    d.rectangle((*point(0,0),*point(n,n)),fill=(234,240,242),outline=(107,129,141),width=3)
    for v in range(0,n+1,20):
        x,z=point(v,0);d.line((x,oz,x,oz+n*scale),fill=(212,223,228))
        x,z=point(0,v);d.line((ox,z,ox+n*scale,z),fill=(212,223,228))
        d.text((ox+v*scale-10,oz+n*scale+15),str(v),font=font(20),fill=(74,97,110))
        d.text((ox-52,oz+v*scale-12),str(v),font=font(20),fill=(74,97,110))
    cx,cz=c['center_x'],c['center_z']
    r=c['base_radius']+4
    d.ellipse((*point(cx-r,cz-r),*point(cx+r,cz+r)),fill=(255,255,252),outline=(71,95,107),width=4)
    r=c['roof_radius']
    d.ellipse((*point(cx-r,cz-r),*point(cx+r,cz+r)),outline=(160,177,186),width=3)
    overlay=Image.new('RGBA',im.size,(0,0,0,0));o=ImageDraw.Draw(overlay)
    for a in areas:
        lo,hi=a['min'],a['max']
        if a['id'].startswith('guide_'):
            o.rectangle((*point(lo[0],lo[2]),*point(hi[0]+1,hi[2]+1)),fill=(38,144,202,26))
        else:
            o.rectangle((*point(lo[0],lo[2]),*point(hi[0]+1,hi[2]+1)),fill=(42,154,172,60),outline=(41,134,153,210),width=3)
    im=Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB');d=ImageDraw.Draw(im)
    for a,b in zip(route['centerline_points'],route['centerline_points'][1:]):
        d.line((*point(a[0],a[2]),*point(b[0],b[2])),fill=(184,43,143),width=4)
    for index,(x,y,z) in enumerate(route['centerline_points'][:-1]):
        px,pz=point(x,z);d.ellipse((px-8,pz-8,px+8,pz+8),fill=(184,43,143))
        d.text((px+12,pz-12),f'{index+1}: Y{y}',font=font(24),fill=(85,33,79))
    front=cz+c['base_radius']+1
    d.rectangle((*point(cx-23,front-9),*point(cx+23,front+17)),outline=(94,122,130),width=3)
    d.text(point(cx-26,front+22),'Entrance massing',font=font(24),fill=(51,83,98))
    d.text((1555,240),'N (-Z)',font=font(24),fill=(29,100,126))
    d.line((1590,380,1590,295),fill=(29,100,126),width=5)
    d.polygon((1590,281,1580,300,1600,300),fill=(29,100,126))
    d.ellipse((*point(cx-2,cz-2),*point(cx+2,cz+2)),outline=(75,100,114),width=2)
    d.text((80,1690),'Blue: reserved air / magenta: removable glass-dot guide / grey: building outline',font=font(24),fill=(42,81,99))
    d.text((80,1740),'Shared paste anchor (0,0,0). X east, Y up, Z south. All coordinates are in blocks.',font=font(23),fill=(71,95,109))
    d.text((80,1785),'Normal guide envelope: 9 x 9 blocks of clear space. Station reservation is wider.',font=font(23),fill=(71,95,109))
    d.text((80,1830),'Volumes constrain Python generation; they do not create a live WorldEdit region.',font=font(23),fill=(71,95,109))
    Path(path).parent.mkdir(parents=True,exist_ok=True)
    im.save(path)
    return {'path':str(path),'size':list(im.size),'historical_route_verified':False}
