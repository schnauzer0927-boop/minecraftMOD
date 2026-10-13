"""Prepare a localhost-only server test; do not start it without --run.

Uses only read access to the existing NeoForge installation and helper compile
artifacts. All runtime changes stay in SpaceMountainTokyoOld/runtime-test.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OLD = Path('/workspace/thunder-mine-expedition/run-native-test-production')
DEV = Path('/workspace/thunder-mine-expedition/helper/build/moddev/artifacts/neoforge-21.1.228.jar')
JDK = Path('/workspace/.tools/thunder-mine-java21/extracted/usr/lib/jvm/java-21-openjdk-amd64/bin')


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sha256', required=True, help='Approved final full-schematic SHA256')
    parser.add_argument('--run', action='store_true')
    args = parser.parse_args()
    rt = ROOT / 'runtime-test'
    file = ROOT / 'output/stage3/schem/tokyo_old_with_stage3.schem'
    if sha256(file) != args.sha256:
        raise SystemExit('Final schematic hash does not match the approved value')
    rt.mkdir(exist_ok=True)
    if not (rt / 'libraries').exists():
        shutil.copytree(OLD / 'libraries', rt / 'libraries')
    (rt / 'eula.txt').write_text('eula=true\n')
    (rt / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25587
online-mode=true
enforce-secure-profile=true
white-list=true
enforce-whitelist=true
level-name=SpaceMountainStage3Verification
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
max-players=1
view-distance=3
simulation-distance=3
max-tick-time=180000
motd=Independent Space Mountain schematic verification
enable-rcon=false
enable-query=false
''')
    worldedit = rt / 'mods/worldedit-mod-7.3.8.jar'
    release = json.loads((ROOT / 'references/worldedit_mod_version.json').read_text())['data']['files'][0]
    if hashlib.sha512(worldedit.read_bytes()).hexdigest() != release['hashes']['sha512']:
        raise SystemExit('WorldEdit official artifact verification failed')
    runtime_args = rt / 'libraries/net/neoforged/neoforge/21.1.228/unix_args.txt'
    legacy = next(s for s in runtime_args.read_text().splitlines() if s.startswith('-DlegacyClassPath='))
    cp = [DEV, worldedit] + [rt / p for p in legacy.split('=', 1)[1].split(':')]
    classes = rt / 'testmod-classes'
    classes.mkdir(exist_ok=True)
    subprocess.run([str(JDK / 'javac'), '--release', '21', '-proc:none', '-cp', ':'.join(map(str, cp)),
                    '-d', str(classes), str(Path(__file__).with_name('SpaceMountainVerifier.java'))], check=True)
    (classes / 'META-INF').mkdir(exist_ok=True)
    shutil.copy2(Path(__file__).with_name('neoforge.mods.toml'), classes / 'META-INF/neoforge.mods.toml')
    verifier = rt / 'mods/space-mountain-verifier-1.0.0.jar'
    subprocess.run([str(JDK / 'jar'), '--create', '--file', str(verifier), '-C', str(classes), '.'], check=True)
    inputs = rt / 'inputs'
    inputs.mkdir(exist_ok=True)
    saved = inputs / file.name
    shutil.copy2(file, saved)
    numerical_file = ROOT / 'output/stage3/reports/numerical_validation.json'
    numerical = json.loads(numerical_file.read_text())
    if not numerical['valid']:
        raise SystemExit('Numerical validation has not passed')
    walking = sorted({tuple(p) for route in numerical['walking']['routes'] for p in route['resolved_path']})
    protected_file = ROOT / 'output/stage3/json/updated_protected_areas.json'
    protected = json.loads(protected_file.read_text())
    volumes = [v for v in protected['areas'] if v['id'] in {'future_ride_core', 'station_rail_lane'}]
    if len(volumes) != 2:
        raise SystemExit('The two critical air reservations were not found')
    manifest = {'schematic_path': str(saved), 'sha256': args.sha256, 'paste_anchor': [0, 64, 0],
                'protected_air_volumes': volumes, 'walking_checkpoints': walking,
                'walking_source': 'numerically_resolved_supported_feet_paths',
                'numerical_validation_sha256': sha256(numerical_file),
                'protected_areas_sha256': sha256(protected_file),
                'verifier_mod_sha256': sha256(verifier),
                'worldedit_sha512': release['hashes']['sha512']}
    (rt / 'verification-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'prepared': True, 'schematic_sha256': args.sha256,
                      'walking_cells': len(walking), 'runtime': str(rt), 'started': args.run}))
    if args.run:
        result_file = rt / 'runtime-verification.json'
        if result_file.exists():
            result_file.unlink()
        with (rt / 'server-console.log').open('w') as console:
            subprocess.run([str(JDK / 'java'), '-Xms512M', '-Xmx3G', '@' + str(runtime_args), 'nogui'],
                           cwd=rt, stdout=console, stderr=subprocess.STDOUT, check=True)
        if not result_file.exists():
            raise SystemExit('Server produced no verification report; inspect server-console.log')
        report = json.loads(result_file.read_text())
        if report.get('schematic_sha256') != args.sha256:
            raise SystemExit('Server verification did not use the approved schematic')
        print(json.dumps({k: report.get(k) for k in ('passed', 'non_air_blocks_checked',
                          'blockstate_mismatches', 'protected_air_occupied',
                          'blocked_walking_checkpoints', 'unsupported_walking_checkpoints', 'exception')}))
        if not report['passed']:
            raise SystemExit('Runtime verification failed; inspect runtime-verification.json')


if __name__ == '__main__':
    main()
