"""Run the actual project regression suite and save reproducible test evidence."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import platform
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))


def run():
    suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'))
    stream=io.StringIO()
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    directory=ROOT/'output'/'reports'
    directory.mkdir(parents=True,exist_ok=True)
    output=stream.getvalue()
    (directory/'python_tests.txt').write_text(output,encoding='utf-8')
    paths=sorted((ROOT/'generator').glob('*.py'))+sorted((ROOT/'tests').glob('*.py'))+[ROOT/'config.json',ROOT/'user_keepout.json',Path(__file__)]
    doc={'python':sys.version,'platform':platform.system(),'test_count':result.testsRun,
         'failures':len(result.failures),'errors':len(result.errors),'skips':len(result.skipped),
         'passed':result.wasSuccessful(),'gameplay_verified':False,'windows_run_verified':False,
         'sources':[{'file':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]}
    (directory/'python_tests.json').write_text(json.dumps(doc,indent=2)+'\n',encoding='utf-8')
    print(output,end='')
    return 0 if result.wasSuccessful() else 1


if __name__=='__main__':raise SystemExit(run())
