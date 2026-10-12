#!/usr/bin/env python3
"""Package a normally saved, commissioned native coaster world without vendor JARs.

This does not generate terrain, fabricate NBT, clean a test world, or change saved
physics. The caller must prepare a stopped distribution copy first. Missing or
negative commissioning evidence is a hard failure, not a warning.
"""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import gzip
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import tempfile
import tomllib
import uuid
import zipfile


PROJECT = Path(__file__).resolve().parent.parent
WORLD_FOLDER = "Coaster World - Thunder Mine Expedition"
WORLD_ZIP = "CoasterWorld-ThunderMineExpedition.zip"
SOURCE_ZIP = "ThunderMineExpedition-Source.zip"
HELPER_NAME = "thunder-mine-tools-1.0.0.jar"
EXCLUDED_ROOTS = {"playerdata", "advancements", "stats", "screenshots"}
EXCLUDED_NAMES = {"session.lock", "usercache.json", "ops.json", "whitelist.json",
                  "banned-ips.json", "banned-players.json", "server.properties",
                  "eula.txt", "level.dat_old"}
REQUIRED_FLAGS = ("runtime", "full_lap", "station_stop", "normal_save", "reloaded_rerun",
                  "world_prepared", "native_cart_stationary", "scenery_clearance_check")


def fail(message: str) -> None:
    raise ValueError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


class NbtReader:
    """Bounded, read-only NBT decoder for level.dat and our SavedData receipt."""
    def __init__(self, data: bytes):
        self.stream = io.BytesIO(data)
        self.remaining_elements = 1_000_000

    def read(self, count: int) -> bytes:
        if count < 0 or count > 64 * 1024 * 1024:
            fail("Invalid NBT byte length")
        result = self.stream.read(count)
        if len(result) != count:
            fail("Truncated NBT")
        return result

    def number(self, format_: str):
        return struct.unpack(format_, self.read(struct.calcsize(format_)))[0]

    def text(self) -> str:
        # NBT uses modified UTF-8. ASCII tag names (the only fields checked here)
        # have the same representation; display strings are decoded for names.
        return self.read(self.number(">H")).decode("utf-8", errors="replace")

    def payload(self, kind: int, depth: int = 0):
        if depth > 64:
            fail("NBT nesting limit exceeded")
        self.remaining_elements -= 1
        if self.remaining_elements < 0:
            fail("NBT element limit exceeded")
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if kind in formats:
            return self.number(formats[kind])
        if kind == 7:
            return self.read(self.number(">i"))
        if kind == 8:
            return self.text()
        if kind == 9:
            element_kind = self.number(">B")
            count = self.number(">i")
            if count < 0 or count > self.remaining_elements or (element_kind == 0 and count):
                fail("Invalid NBT list length or type")
            return [self.payload(element_kind, depth + 1) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                member_kind = self.number(">B")
                if member_kind == 0:
                    return result
                name = self.text()
                if name in result:
                    fail("Duplicate NBT compound tag")
                result[name] = self.payload(member_kind, depth + 1)
        if kind in (11, 12):
            count = self.number(">i")
            if count < 0 or count > self.remaining_elements:
                fail("Invalid NBT array length")
            format_ = ">i" if kind == 11 else ">q"
            self.remaining_elements -= count
            return [self.number(format_) for _ in range(count)]
        fail(f"Unsupported NBT tag type {kind}")


def read_nbt(path: Path) -> dict:
    if not path.is_file() or not 0 < path.stat().st_size < 64 * 1024 * 1024:
        fail(f"Missing or invalid-sized NBT file: {path}")
    # Bound expanded data as well as compressed input.
    with gzip.open(path, "rb") as stream:
        data = stream.read(64 * 1024 * 1024 + 1)
    if len(data) > 64 * 1024 * 1024:
        fail("Expanded NBT exceeds read limit")
    reader = NbtReader(data)
    if reader.number(">B") != 10:
        fail("NBT root is not a compound")
    reader.text()
    result = reader.payload(10)
    if reader.stream.read(1):
        fail("Unexpected bytes after NBT root")
    return result


def saved_uuid(values: list[int]) -> str:
    if not isinstance(values, list) or len(values) != 4 or not all(isinstance(v, int) for v in values):
        fail("Saved native cart UUID is missing")
    raw = b"".join((value & 0xFFFFFFFF).to_bytes(4, "big") for value in values)
    return str(uuid.UUID(bytes=raw))


@contextlib.contextmanager
def stopped_world_lock(world: Path):
    path = world / "session.lock"
    handle = None
    try:
        if path.exists():
            if path.is_symlink():
                fail("World session.lock must not be a symlink")
            handle = path.open("r+b")
            try:
                # Java FileChannel locks use POSIX record locks on this host.
                fcntl.lockf(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                fail("World is still open in Minecraft; stop it normally before packaging")
        yield
    finally:
        if handle is not None:
            fcntl.lockf(handle.fileno(), fcntl.LOCK_UN)
            handle.close()


def world_files(world: Path) -> list[Path]:
    result = []
    for path in sorted(world.rglob("*")):
        if path.is_symlink():
            fail(f"Symlink in world is not accepted: {path.relative_to(world)}")
        if not path.is_file():
            continue
        relative = path.relative_to(world)
        if relative.parts[0] in EXCLUDED_ROOTS or relative.name in EXCLUDED_NAMES:
            continue
        if relative.suffix.lower() == ".jar":
            fail("World contains a JAR; original third-party MODs must remain external")
        result.append(path)
    return result


def require_real_native_files(world: Path, files: list[Path]) -> None:
    for directory in ("region", "data", "sublevels"):
        if not (world / directory).is_dir():
            fail(f"Missing required normally saved world directory: {directory}")
    names = [path.relative_to(world).as_posix() for path in files]
    for label, predicate in (
        ("terrain region", lambda name: name.startswith("region/") and name.endswith(".mca")),
        ("Sable holding region", lambda name: name.startswith("sublevels/") and name.endswith(".slvlr")),
        ("Sable cart region", lambda name: name.startswith("sublevels/") and name.endswith(".slvls")),
    ):
        matches = [world / name for name in names if predicate(name)]
        if not matches or any(path.stat().st_size == 0 for path in matches):
            fail(f"Missing or empty saved native {label}")


def safe_relative(project: Path, raw: str) -> Path:
    path = Path(raw)
    if path.is_absolute() or ".." in path.parts:
        fail(f"Evidence path must be relative to the project: {raw}")
    resolved = (project / path).resolve()
    if not resolved.is_relative_to(project.resolve()) or not resolved.is_file():
        fail(f"Evidence file missing or outside project: {raw}")
    return resolved


def inspect_inputs(world: Path, helper: Path, commissioning: Path) -> tuple[dict, list[Path], list[Path]]:
    if not world.is_dir() or world.is_symlink():
        fail("Provide a real stopped distribution world directory")
    if not helper.is_file() or helper.name != HELPER_NAME or helper.stat().st_size == 0:
        fail(f"Validated final helper is required as {HELPER_NAME}")
    report = json.loads(commissioning.read_text())
    if report.get("schema") != 1:
        fail("Commissioning report schema must be 1")
    for flag in REQUIRED_FLAGS:
        if report.get(flag) is not True:
            fail(f"Actual completed commissioning flag is missing or false: {flag}")
    route = PROJECT / "data/mine-route.json"
    fingerprint = sha256(route)
    if report.get("route_fingerprint") != fingerprint:
        fail("Commissioning result does not match the current authored native course")
    for key, path in (
        ("world_level_dat_sha256", world / "level.dat"),
        ("native_course_receipt_sha256", world / "data/thunder_mine_native_course.dat"),
        ("helper_jar_sha256", helper),
    ):
        if not path.is_file() or report.get(key) != sha256(path):
            fail(f"Commissioning SHA-256 missing or stale: {key}")
    level = read_nbt(world / "level.dat").get("Data", {})
    if "Player" in level:
        fail("Distribution level.dat still contains the test player's state; prepare a clean copy first")
    if level.get("DataVersion") != 3955:
        fail("Distribution world is not a genuine Minecraft 1.21.1 save (DataVersion 3955)")
    if level.get("LevelName") != WORLD_FOLDER:
        fail(f"Distribution LevelName must be {WORLD_FOLDER!r}")
    receipt = read_nbt(world / "data/thunder_mine_native_course.dat").get("data", {})
    if receipt.get("Schema") != 1 or receipt.get("Built") != 1 or receipt.get("Reserved") != 1:
        fail("Native course construction receipt is not successfully committed")
    if receipt.get("Pending") != 0 or receipt.get("Invalid") != 0:
        fail("Native course construction is pending or invalid")
    if receipt.get("RouteFingerprint") != fingerprint:
        fail("Saved native course differs from the commissioned course")
    if report.get("cart_uuid") != saved_uuid(receipt.get("Cart")):
        fail("Saved native cart identity differs from the commissioned cart")
    with zipfile.ZipFile(helper) as archive:
        if archive.testzip() is not None:
            fail("Helper JAR CRC check failed")
        metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
        if not any(mod.get("modId") == "thunder_mine_tools" and mod.get("version") == "1.0.0"
                   for mod in metadata.get("mods", [])):
            fail("Helper metadata is not the final thunder_mine_tools 1.0.0")
        if archive.read("data/thunder_mine_tools/course/mine-route.json") != route.read_bytes():
            fail("Final helper JAR contains a different native route")
    raw_evidence = report.get("evidence_paths", [])
    if not isinstance(raw_evidence, list) or not raw_evidence:
        fail("Commissioning report must reference actual recorded runtime evidence")
    evidence = [safe_relative(PROJECT, raw) for raw in raw_evidence]
    if any(path.suffix.lower() in (".jar", ".class", ".so", ".dll") for path in evidence):
        fail("Vendor executable files are not accepted as distributable evidence")
    files = world_files(world)
    require_real_native_files(world, files)
    return report, files, evidence


def source_files(evidence: list[Path], commissioning: Path) -> list[Path]:
    result = set()
    for filename in ("README.md", "TEST_RESULTS.md"):
        path = PROJECT / filename
        if not path.is_file():
            fail(f"Missing source documentation: {filename}")
        result.add(path)
    for directory in ("docs", "data", "scripts"):
        for path in (PROJECT / directory).rglob("*"):
            if path.is_file() and not path.is_symlink() and "__pycache__" not in path.parts:
                if path.suffix.lower() in (".md", ".json", ".py", ".sh", ".txt"):
                    result.add(path)
    helper = PROJECT / "helper"
    for filename in ("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat",
                     "LICENSE", "MDK_TEMPLATE_LICENSE.txt", "gradle/wrapper/gradle-wrapper.jar",
                     "gradle/wrapper/gradle-wrapper.properties"):
        path = helper / filename
        if not path.is_file() or path.is_symlink():
            fail(f"Missing project source/build file: helper/{filename}")
        result.add(path)
    for path in (helper / "src").rglob("*"):
        if path.is_file():
            if path.is_symlink():
                fail("Symlink in source tree is not accepted")
            if path.suffix.lower() in (".jar", ".class", ".so", ".dll"):
                fail("Third-party executable in source tree is not accepted")
            result.add(path)
    result.update(evidence)
    result.add(commissioning)
    return sorted(result)


def write_zip(target: Path, paths: list[Path], root: Path, folder: str) -> None:
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6,
                         strict_timestamps=False) as archive:
        for path in paths:
            archive.write(path, folder + "/" + path.relative_to(root).as_posix())
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            fail(f"Generated ZIP failed CRC check: {target.name}")
        if not archive.namelist() or target.stat().st_size == 0:
            fail("Generated empty archive")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", required=True, type=Path)
    parser.add_argument("--helper-jar", required=True, type=Path)
    parser.add_argument("--commissioning", type=Path, default=PROJECT / "evidence/native-main/commissioning.json")
    parser.add_argument("--output", type=Path, default=PROJECT / "dist/release-1.0.0")
    parser.add_argument("--check-only", action="store_true", help="Validate; never create archives")
    args = parser.parse_args()
    world = args.world.resolve()
    helper = args.helper_jar.resolve()
    commissioning = args.commissioning.resolve()
    if not commissioning.is_relative_to(PROJECT):
        fail("Commissioning report must remain inside this project")
    with stopped_world_lock(world):
        report, files, evidence = inspect_inputs(world, helper, commissioning)
        sources = source_files(evidence, commissioning)
        if args.check_only:
            print(json.dumps({"validated": True, "world_files": len(files), "source_files": len(sources),
                              "route_fingerprint": report["route_fingerprint"]}, indent=2))
            return
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=True)
        document_copies = {
            "README.md": PROJECT / "README.md",
            "TEST_RESULTS.md": PROJECT / "TEST_RESULTS.md",
            "commissioning.json": commissioning,
        }
        document_copies.update({path.relative_to(PROJECT).as_posix(): path
                                for path in sorted((PROJECT / "docs").glob("*.md"))})
        artifact_names = (WORLD_ZIP, SOURCE_ZIP, HELPER_NAME, *document_copies)
        names = (*artifact_names, "SHA256SUMS.txt", "distribution-manifest.json")
        if any((output / name).exists() for name in names):
            fail("Existing release artifacts will not be overwritten; choose a new output directory")
        before = {path.relative_to(world).as_posix(): sha256(path) for path in files}
        with tempfile.TemporaryDirectory(prefix=".mine-package-", dir=output) as staging_name:
            staging = Path(staging_name)
            write_zip(staging / WORLD_ZIP, files, world, WORLD_FOLDER)
            write_zip(staging / SOURCE_ZIP, sources, PROJECT, "ThunderMineExpedition-Source")
            shutil.copy2(helper, staging / HELPER_NAME)
            for name, path in document_copies.items():
                (staging / name).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, staging / name)
            after = {path.relative_to(world).as_posix(): sha256(path) for path in files}
            if after != before or world_files(world) != files:
                fail("World changed while packaging; no artifacts were published")
            artifacts = [{"filename": name, "size_bytes": (staging / name).stat().st_size,
                          "sha256": sha256(staging / name)} for name in artifact_names]
            manifest = {"schema": 1, "world_folder": WORLD_FOLDER, "minecraft": "1.21.1",
                        "loader": "NeoForge", "loader_version": "21.1.228", "java": 21,
                        "route_fingerprint": report["route_fingerprint"], "native_cart_uuid": report["cart_uuid"],
                        "third_party_jars_bundled": False, "world_file_sha256": before,
                        "source_files": len(sources), "artifacts": artifacts,
                        "commissioning_sha256": sha256(commissioning)}
            (staging / "distribution-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            (staging / "SHA256SUMS.txt").write_text("".join(f"{item['sha256']}  {item['filename']}\n" for item in artifacts))
            for name in names:
                (output / name).parent.mkdir(parents=True, exist_ok=True)
                (staging / name).replace(output / name)
        print(json.dumps({"output": str(output), "artifacts": artifacts, "world_files": len(files)}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        raise SystemExit("Packaging refused: " + str(error))
