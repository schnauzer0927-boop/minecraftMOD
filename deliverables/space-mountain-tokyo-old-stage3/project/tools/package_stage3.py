"""Package a separate Stage3 release without touching the Stage1/2 ZIPs."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]
EXCLUDED={'source_images','official','local_analysis','video_frames','__pycache__','.venv','dist'}
TOP={'README.md','LICENSE','.gitignore','requirements.txt','config.json','stage3_config.json',
     'user_keepout.json','generate_windows.bat','generate_stage3_windows.bat'}
DIRS={'docs','generator','tests','tools','references','output'}


def source_files():
    for p in sorted(ROOT.rglob('*')):
        rel=p.relative_to(ROOT)
        if not p.is_file() or any(part in EXCLUDED for part in rel.parts):continue
        if p.suffix in {'.pyc','.jar','.jpg','.jpeg','.mp4','.ogg','.pdf'}:continue
        if len(rel.parts)==1 and rel.name in TOP:yield p
        elif rel.parts[0] in DIRS:yield p


def main():
    out=ROOT/'dist/release-stage3'
    out.mkdir(parents=True,exist_ok=True)
    chosen=list(source_files())
    manifest=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(ROOT).as_posix()+'\n' for p in chosen)
    dest=out/'SpaceMountainTokyoOld-Stage3.zip'
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in chosen:
            i=zipfile.ZipInfo('SpaceMountainTokyoOld-Stage3/'+p.relative_to(ROOT).as_posix(),(2026,10,14,0,0,0))
            i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16
            z.writestr(i,p.read_bytes())
        z.writestr('SpaceMountainTokyoOld-Stage3/SHA256SUMS',manifest)
    with zipfile.ZipFile(dest) as z:
        assert z.testzip() is None
        assert all(i.file_size>0 for i in z.infolist())
        assert sum(i.filename.endswith('.schem') for i in z.infolist())==22
        assert not any(i.filename.endswith(('.jar','.jpg','.pdf')) for i in z.infolist())
    facts={'file':dest.name,'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),
           'files':len(chosen)+1,'stage3_schematics':8,'retained_stage2_schematics':14,'zip_crc':'PASS'}
    (out/'archives.json').write_text(json.dumps(facts,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(facts,indent=2))


if __name__=='__main__':main()
