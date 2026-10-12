#!/usr/bin/env python3
"""Enable the real saved world's command flag without rewriting other NBT tags."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import zipfile

spec = importlib.util.spec_from_file_location("world_package", Path(__file__).with_name("package-mine-world.py"))
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def enable(raw):
    before = package.NbtReader(raw)
    assert before.number(">B") == 10
    before.text()
    original = before.payload(10)
    reader = package.NbtReader(raw)
    assert reader.number(">B") == 10
    reader.text()
    offset = None
    while (kind := reader.number(">B")) != 0:
        name = reader.text()
        if name != "Data":
            reader.payload(kind)
            continue
        assert kind == 10
        while (child := reader.number(">B")) != 0:
            key = reader.text()
            if key == "allowCommands":
                assert child == 1 and offset is None
                offset = reader.stream.tell()
            reader.payload(child)
    assert offset is not None, "Missing real Data.allowCommands byte"
    patched = bytearray(raw)
    patched[offset] = 1
    check = package.NbtReader(bytes(patched))
    assert check.number(">B") == 10
    check.text()
    result = check.payload(10)
    original["Data"]["allowCommands"] = 1
    assert result == original, "An unrelated NBT value changed"
    assert sum(a != b for a, b in zip(raw, patched)) <= 1
    return bytes(patched)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "Never overwrite an existing release"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    changed = []
    with zipfile.ZipFile(args.source) as source, zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as target:
        assert source.testzip() is None
        for info in source.infolist():
            data = source.read(info)
            if info.filename.endswith("/level.dat"):
                data = gzip.compress(enable(gzip.decompress(data)), mtime=0)
                changed.append(info.filename)
            target.writestr(info, data)
    assert len(changed) == 1
    with zipfile.ZipFile(args.source) as source, zipfile.ZipFile(args.output) as target:
        assert target.testzip() is None
        assert source.namelist() == target.namelist()
        for name in source.namelist():
            if name not in changed:
                assert source.read(name) == target.read(name), name
    report = {"source_sha256": digest(args.source.read_bytes()), "zip_sha256": digest(args.output.read_bytes()),
              "size_bytes": args.output.stat().st_size, "changed_files": changed,
              "changed_nbt_field": "Data.allowCommands", "new_value": 1,
              "other_nbt_values_unchanged": True, "other_world_files_unchanged": True, "zip_crc_valid": True}
    args.output.with_suffix(".verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
