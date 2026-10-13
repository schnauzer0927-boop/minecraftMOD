#!/usr/bin/env python3
"""Read .schem files with the official, unmodified WorldEdit 7.3.8 reader.

Python is stdlib-only. Java 21 with jdk.compiler is required. Dependencies and
compiled classes stay in the system temporary directory. A standalone vanilla block
registry validates parsing; this does not exercise Minecraft paste commands.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request


MAVEN = "https://repo.maven.apache.org/maven2/"
WORLDEDIT_URL = "https://cdn.modrinth.com/data/1u6JkXh5/versions/ecqqLKUO/worldedit-bukkit-7.3.8.jar"
WORLDEDIT_SHA512 = "ca8c25d1c2d894b7c3293617875abf23af1cb1c0a6a4e142ee514e7d06536a67d91664009ce8f54902c8f4fd05e32f8235f43cf52bfbd0a6c5622d6bef4f1c6e"
ARTIFACTS = (
    ("worldedit-bukkit-7.3.8.jar", WORLDEDIT_URL, "b11e40546987c1c9c5f8af216bba89a265a5ffac8dccd92273f61ebb86040556"),
    ("guava-32.1.3-jre.jar", MAVEN + "com/google/guava/guava/32.1.3-jre/guava-32.1.3-jre.jar", "6d4e2b5a118aab62e6e5e29d185a0224eed82c85c40ac3d33cf04a270c3b3744"),
    ("failureaccess-1.0.1.jar", MAVEN + "com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar", "a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26"),
    ("gson-2.10.1.jar", MAVEN + "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar", "4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593"),
    ("fastutil-8.5.12.jar", MAVEN + "it/unimi/dsi/fastutil/8.5.12/fastutil-8.5.12.jar", "b5543ee08d062d551cf0a5c9bc0fb70588b0382079029ba48941fa9c9be8a5d4"),
    ("log4j-api-2.22.1.jar", MAVEN + "org/apache/logging/log4j/log4j-api/2.22.1/log4j-api-2.22.1.jar", "5d7beae7ff15d8516d6517121d7f12a79a6ac180df64b5fcec55d5be21056e53"),
    ("log4j-core-2.22.1.jar", MAVEN + "org/apache/logging/log4j/log4j-core/2.22.1/log4j-core-2.22.1.jar", "46dccecac556623d8e2ce8648496824a82951d139062a4e61148aff1a25ed18d"),
    ("antlr4-runtime-4.13.1.jar", MAVEN + "org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar", "54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505"),
    ("snakeyaml-2.0.jar", MAVEN + "org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar", "880c9d896e4b74a06c549c15ca496450165d6909fa15d7e662bee8f6a66d7afa"),
    ("rhino-runtime-1.7.13.jar", MAVEN + "org/mozilla/rhino-runtime/1.7.13/rhino-runtime-1.7.13.jar", "11acf4e03f7f95d164d21d10ee170d7c878c9fd87871f7eaba9c4a66a1d617e7"),
    ("blocks-1.21.1.json", "https://raw.githubusercontent.com/misode/mcmeta/1.21.1-summary/blocks/data.json", "6c4c0d87b27d665b5f52be4edb000a6d83c4a9b2e6c5c9c931ba695a45f8df24"),
)


class HTTPSOnlyRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urllib.parse.urlsplit(new_url).scheme != "https":
            raise ValueError("Refusing a redirect away from HTTPS")
        return super().redirect_request(request, response, code, message, headers, new_url)


OPENER = urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl.create_default_context()), HTTPSOnlyRedirect()
)


def open_https(url: str):
    if urllib.parse.urlsplit(url).scheme != "https":
        raise ValueError("Only HTTPS download URLs are permitted")
    return OPENER.open(urllib.request.Request(url, headers={"User-Agent": "SpaceMountainTokyoOld-WorldEditCheck/1.0"}), timeout=45)


def digest(path: Path, algorithm: str = "sha256") -> str:
    with path.open("rb") as source:
        checksum = hashlib.new(algorithm)
        while chunk := source.read(1024 * 1024):
            checksum.update(chunk)
        return checksum.hexdigest()


def verify_modrinth() -> dict:
    url = "https://api.modrinth.com/v2/version/ecqqLKUO"
    with open_https(url) as response:
        release = json.load(response)
    if release.get("project_id") != "1u6JkXh5" or release.get("version_number") != "7.3.8":
        raise ValueError("Modrinth did not return the official WorldEdit 7.3.8 release")
    artifact = next((file for file in release["files"] if file["url"] == WORLDEDIT_URL), None)
    if artifact is None or artifact["hashes"].get("sha512") != WORLDEDIT_SHA512:
        raise ValueError("Official Modrinth WorldEdit artifact/hash differs from the pinned release")
    return {"metadataUrl": url, "projectId": release["project_id"], "versionId": release["id"], "sha512": WORLDEDIT_SHA512}


def fetch(cache: Path, filename: str, url: str, expected_hash: str) -> Path:
    target = cache / filename
    if target.is_file() and digest(target) == expected_hash:
        return target
    temporary = None
    try:
        with open_https(url) as response, tempfile.NamedTemporaryFile(dir=cache, delete=False) as output:
            temporary = Path(output.name)
            size = 0
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                if size > 64 * 1024 * 1024:
                    raise ValueError(f"Artifact exceeds size limit: {filename}")
                output.write(chunk)
        if digest(temporary) != expected_hash:
            raise ValueError(f"SHA-256 mismatch for {filename}")
        temporary.replace(target)
        return target
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def run(command: list[str], cwd: Path, timeout: int) -> str:
    result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        detail = (result.stderr or result.stdout).strip()[-3000:]
        raise RuntimeError(f"Java exited with status {result.returncode}: {detail}")
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("schematics", nargs="+", type=Path)
    parser.add_argument("--report", type=Path, help="Save decoded results plus artifact URLs and hashes as JSON")
    parser.add_argument("--cache-dir", type=Path, default=Path(tempfile.gettempdir()) / "space-schem-we-java")
    options = parser.parse_args()
    files = [path.resolve(strict=True) for path in options.schematics]
    input_hashes = [digest(path) for path in files]
    project_root = Path(__file__).resolve().parent.parent
    sys.path.insert(0, str(project_root))
    from generator.validate_schem import read_schem, _block_ids
    expected_files = [read_schem(path) for path in files]
    cache = options.cache_dir.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    official_release = verify_modrinth()
    paths = [fetch(cache, *artifact) for artifact in ARTIFACTS]
    worldedit = paths[0]
    if digest(worldedit, "sha512") != WORLDEDIT_SHA512:
        raise ValueError("WorldEdit SHA-512 does not match the official Modrinth artifact")
    classes = cache / "classes"
    classes.mkdir(exist_ok=True)
    sourcepath = cache / "empty-source"
    sourcepath.mkdir(exist_ok=True)
    # Explicit classpath avoids loading unrelated jars that happen to be cached.
    classpath = os.pathsep.join([str(path) for path in paths if path.suffix == ".jar"] + [str(classes)])
    source = Path(__file__).resolve().with_name("ReadSchem.java")
    run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-proc:none", "-sourcepath", str(sourcepath),
         "-cp", classpath, "-d", str(classes), str(source)], cache, 60)
    palette = sorted({state for expected in expected_files for state in expected["palette"]})
    with tempfile.NamedTemporaryFile(dir=cache, suffix=".json", mode="w", encoding="utf-8", delete=False) as target:
        palette_file = Path(target.name)
        json.dump(palette, target)
    try:
        output = run(["java", "-Xmx2G", "-cp", classpath, "ReadSchem", str(paths[-1]), str(palette_file), *map(str, files)], cache, 240)
    finally:
        palette_file.unlink(missing_ok=True)
    # Every expected palette entry has been parsed strictly by the official
    # BlockFactory, so reader fallback to air cannot produce a false PASS.
    decoded = json.loads(output)
    results = decoded["results"]
    canonical_states = decoded["canonicalStates"]
    if len(results) != len(expected_files):
        raise ValueError("Official reader returned the wrong number of results")
    for result, expected, input_hash in zip(results, expected_files, input_hashes):
        width, height, length = expected["dimensions"]
        volume = width * height * length
        maximum = [expected["offset"][axis] + expected["dimensions"][axis] - 1 for axis in range(3)]
        facts = {"dimensions": list(expected["dimensions"]), "minimum": list(expected["offset"]),
                 "maximum": maximum, "origin": list(expected["origin"]), "volume": volume,
                 "nonAir": len(expected["blocks"])}
        for key, value in facts.items():
            if result[key] != value:
                raise ValueError(f"{result['file']}: official {key}={result[key]} differs from independent {value}")
        by_id = {identifier: state for state, identifier in expected["palette"].items()}
        expected_counts = Counter(canonical_states[by_id[identifier]] for identifier in _block_ids(expected["block_data"], volume))
        if dict(expected_counts) != result["counts"]:
            raise ValueError(f"{result['file']}: official decoded state counts differ from independent NBT data")
        coordinates = hashlib.sha256()
        for (x, y, z), state in sorted(expected["blocks"].items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            coordinates.update(f"{x},{y},{z}={canonical_states[state]}\n".encode("utf-8"))
        if coordinates.hexdigest() != result["nonAirCoordinateSha256"]:
            raise ValueError(f"{result['file']}: official block positions/states differ from independent NBT data")
        result["independentComparisonPassed"] = True
        if digest(Path(result["file"])) != input_hash:
            raise ValueError(f"{result['file']}: schematic changed during validation; rerun after generation finishes")
        result["schematicSha256"] = input_hash
    report = {
        "worldeditVersion": "7.3.8",
        "minecraftDataVersion": 3955,
        "game_paste_verified": False,
        "scope": "Official WorldEdit reader with a standalone vanilla 1.21.1 registry; no in-game paste or physics test.",
        "officialRelease": official_release,
        "sources": [{"file": str(path.relative_to(project_root)), "sha256": digest(path)}
                    for path in (Path(__file__).resolve(), source, project_root / "generator" / "validate_schem.py")],
        "artifacts": [{"file": name, "url": url, "sha256": checksum} for name, url, checksum in ARTIFACTS],
        "results": results,
    }
    if options.report:
        options.report.parent.mkdir(parents=True, exist_ok=True)
        options.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(f"PASS {Path(result['file']).name}: {result['format']}, dimensions={result['dimensions']}, "
              f"minimum={result['minimum']}, origin={result['origin']}, nonAir={result['nonAir']}, "
              f"decodedStates={result['decodedStateCount']}")
    if options.report:
        print(f"Report: {options.report}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"WorldEdit validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
