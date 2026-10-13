"""Package only authored source, generated assets and research metadata."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]
EXCLUDED={'source_images','official','local_analysis','video_frames','__pycache__','.venv','dist'}
TOP={'README.md','LICENSE','.gitignore','requirements.txt','config.json','user_keepout.json','generate_windows.bat'}
DIRS={'docs','generator','tests','tools','references','output'}


def files():
    for p in sorted(ROOT.rglob('*')):
        rel=p.relative_to(ROOT)
        if not p.is_file() or any(part in EXCLUDED for part in rel.parts):continue
        if p.suffix in {'.pyc','.jar','.jpg','.jpeg','.mp4','.ogg'}:continue
        if len(rel.parts)==1 and rel.name in TOP:yield p
        elif rel.parts[0] in DIRS:yield p


def archive(path,chosen):
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in chosen:
            rel=p.relative_to(ROOT)
            info=zipfile.ZipInfo('SpaceMountainTokyoOld/'+rel.as_posix(),date_time=(2026,10,13,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644<<16
            z.writestr(info,p.read_bytes())
    with zipfile.ZipFile(path) as z:
        corrupt=z.testzip()
        if corrupt:raise RuntimeError(f'ZIP CRC failure: {corrupt}')
        if any(i.file_size==0 for i in z.infolist()):raise RuntimeError('Unexpected zero byte item')
    return {'file':path.name,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'files':len(chosen),'zip_crc':'PASS'}


def main():
    out=ROOT/'dist'
    out.mkdir(exist_ok=True)
    chosen=list(files())
    sums=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(ROOT).as_posix()+'\n' for p in chosen)
    sumfile=ROOT/'SHA256SUMS'
    sumfile.write_text(sums,encoding='ascii')
    chosen.append(sumfile)
    research=[p for p in chosen if p.relative_to(ROOT).parts[0] in {'docs','references'}
              or p.name=='stage1_checkpoint.json']
    results=[archive(out/'SpaceMountainTokyoOld-Stage1-2.zip',chosen),
             archive(out/'SpaceMountainTokyoOld-Stage1-Research.zip',research)]
    (out/'archives.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(results,indent=2))


if __name__=='__main__':main()
