"""Independent NBT/Sponge v2 reader and project format validation.

This module does not import the exporter. It checks serialized files, not a
Minecraft game session. WorldEdit 7.3.8 source defines the legacy offset rule.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import zlib


class SchematicError(ValueError):
    """An invalid or unsupported serialized schematic."""


class _NBTList(list):
    def __init__(self, element_type: int, values):
        super().__init__(values)
        self.element_type = element_type


class _NBTReader:
    def __init__(self, data: bytes):
        self.data = data
        self.position = 0

    def take(self, count: int) -> bytes:
        if count < 0 or self.position + count > len(self.data):
            raise SchematicError("Truncated NBT payload")
        result = self.data[self.position:self.position + count]
        self.position += count
        return result

    def number(self, kind: str):
        return struct.unpack(">" + kind, self.take(struct.calcsize(">" + kind)))[0]

    def string(self) -> str:
        try:
            data = self.take(self.number("H"))
            # Accept Java's modified UTF-8, including surrogate pairs. The
            # decoded UTF-16 round trip combines supplementary characters.
            text = data.replace(b"\xc0\x80", b"\x00").decode("utf-8", errors="surrogatepass")
            return text.encode("utf-16-be", errors="surrogatepass").decode("utf-16-be")
        except UnicodeDecodeError as exc:
            raise SchematicError("Invalid UTF-8 NBT string") from exc

    def payload(self, kind: int, depth: int = 0):
        if depth > 64:
            raise SchematicError("NBT nesting exceeds supported depth")
        if kind in {1, 2, 3, 4, 5, 6}:
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.take(self.number("i"))
        if kind == 8:
            return self.string()
        if kind == 9:
            child_kind, count = self.number("B"), self.number("i")
            if count < 0 or count > len(self.data) - self.position:
                raise SchematicError("Invalid NBT list length")
            if child_kind == 0 and count != 0:
                raise SchematicError("Nonempty NBT list has TAG_End type")
            return _NBTList(child_kind, (self.payload(child_kind, depth + 1) for _ in range(count)))
        if kind == 10:
            result = {}
            while True:
                child_kind = self.number("B")
                if child_kind == 0:
                    return result
                name = self.string()
                if name in result:
                    raise SchematicError(f"Duplicate NBT compound key: {name}")
                # Keep type information for structural validation.
                result[name] = (child_kind, self.payload(child_kind, depth + 1))
        if kind in {11, 12}:
            count = self.number("i")
            width, scalar = (4, "i") if kind == 11 else (8, "q")
            if count < 0 or count > (len(self.data) - self.position) // width:
                raise SchematicError("Invalid NBT array length")
            return [self.number(scalar) for _ in range(count)]
        raise SchematicError(f"Unknown NBT tag type: {kind}")


def _require(compound: dict, name: str, kind: int):
    value = compound.get(name)
    if value is None or value[0] != kind:
        raise SchematicError(f"Required tag {name} must have NBT type {kind}")
    return value[1]


def _plain(value):
    if isinstance(value, dict):
        return {key: _plain(child) for key, (_, child) in value.items()}
    if isinstance(value, list):
        return [_plain(item) for item in value]
    return value


def _block_ids(data: bytes, volume: int) -> list[int]:
    values = []
    accumulator = shift = 0
    for value in data:
        if shift == 28 and value & 0xF8:
            raise SchematicError("BlockData VarInt exceeds nonnegative signed int range")
        accumulator |= (value & 127) << shift
        if value & 128:
            shift += 7
            if shift > 28:
                raise SchematicError("BlockData VarInt exceeds five bytes")
            continue
        if shift and value == 0:
            raise SchematicError("Noncanonical BlockData VarInt")
        values.append(accumulator)
        if len(values) > volume:
            raise SchematicError("BlockData contains more blocks than dimensions")
        accumulator = shift = 0
    if shift:
        raise SchematicError("Truncated BlockData VarInt")
    if len(values) != volume:
        raise SchematicError(f"BlockData count {len(values)} does not match volume {volume}")
    return values


def read_schem(path: str | Path) -> dict:
    """Read an exported schematic; return sparse non-air clipboard positions.

    ``blocks`` coordinates include root Offset and correspond to the WorldEdit
    clipboard positions. ``origin`` is Offset minus the legacy WEOffset values.
    This reader validates this project's empty entities and Minecraft version.
    """
    source = Path(path)
    try:
        raw = gzip.decompress(source.read_bytes())
    except (OSError, EOFError, zlib.error) as exc:
        raise SchematicError(f"Invalid gzip schematic: {exc}") from exc
    reader = _NBTReader(raw)
    if reader.number("B") != 10:
        raise SchematicError("NBT root must be a compound")
    root_name = reader.string()
    if root_name != "Schematic":
        raise SchematicError(f"Sponge v2 root must be named Schematic, got {root_name!r}")
    root = reader.payload(10)
    if reader.position != len(raw):
        raise SchematicError("Trailing bytes after NBT root")
    version = _require(root, "Version", 3)
    data_version = _require(root, "DataVersion", 3)
    if version != 2:
        raise SchematicError(f"Expected Sponge schematic v2, got {version}")
    if data_version != 3955:
        raise SchematicError(f"Expected Minecraft 1.21.1 DataVersion 3955, got {data_version}")
    dimensions = tuple(_require(root, axis, 2) for axis in ("Width", "Height", "Length"))
    if any(dimension <= 0 for dimension in dimensions):
        raise SchematicError("Dimensions must be positive signed shorts")
    offset = tuple(_require(root, "Offset", 11))
    if len(offset) != 3:
        raise SchematicError("Offset must contain exactly three ints")
    metadata_tags = _require(root, "Metadata", 10)
    we_offset = tuple(_require(metadata_tags, "WEOffset" + axis, 3) for axis in "XYZ")
    origin = tuple(offset[i] - we_offset[i] for i in range(3))
    palette_tags = _require(root, "Palette", 10)
    palette = {}
    state_pattern = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?$")
    for state, entry in palette_tags.items():
        if entry[0] != 3 or not state_pattern.fullmatch(state):
            raise SchematicError(f"Invalid palette entry: {state}")
        palette[state] = entry[1]
        if state.partition("[")[0].partition(":")[2] in {"rail", "powered_rail", "detector_rail", "activator_rail"}:
            raise SchematicError(f"Rail block is forbidden in exterior prototype: {state}")
    if palette.get("minecraft:air") != 0:
        raise SchematicError("Palette must reserve minecraft:air as id 0")
    if sorted(palette.values()) != list(range(len(palette))):
        raise SchematicError("Palette ids must be unique and contiguous from zero")
    if _require(root, "PaletteMax", 3) != len(palette):
        raise SchematicError("PaletteMax does not match palette size")
    for name in ("BlockEntities", "Entities"):
        entities = _require(root, name, 9)
        if entities.element_type != 10:
            raise SchematicError(f"{name} must be a compound list")
        if entities:
            raise SchematicError(f"{name} must be empty for this prototype")
    width, height, length = dimensions
    ids = _block_ids(_require(root, "BlockData", 7), width * height * length)
    by_id = {index: state for state, index in palette.items()}
    blocks = {}
    for index, palette_id in enumerate(ids):
        if palette_id not in by_id:
            raise SchematicError(f"BlockData references unknown palette id {palette_id}")
        state = by_id[palette_id]
        if state.partition("[")[0] in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}:
            continue
        y, horizontal = divmod(index, width * length)
        z, x = divmod(horizontal, width)
        position = (x + offset[0], y + offset[1], z + offset[2])
        blocks[position] = state
    return {
        "version": version,
        "data_version": data_version,
        "dimensions": dimensions,
        "offset": offset,
        "origin": origin,
        "palette": palette,
        "metadata": _plain(metadata_tags),
        "block_data": _require(root, "BlockData", 7),
        "blocks": blocks,
        "volume": len(ids),
    }


def validate_schem(path: str | Path) -> dict:
    """Validate format plus common-origin/rail constraints; return file facts."""
    source = Path(path)
    schematic = read_schem(source)
    if schematic["origin"] != (0, 0, 0):
        raise SchematicError(f"Clipboard origin must be shared zero, got {schematic['origin']}")
    blocks = schematic["blocks"]
    if not blocks:
        raise SchematicError("Exterior schematic must contain non-air blocks")
    minimum = tuple(min(p[i] for p in blocks) for i in range(3))
    maximum = tuple(max(p[i] for p in blocks) for i in range(3))
    expected_dimensions = tuple(maximum[i] - minimum[i] + 1 for i in range(3))
    if minimum != schematic["offset"] or expected_dimensions != schematic["dimensions"]:
        raise SchematicError("Schematic bounding box must be tight around non-air blocks")
    return {
        "path": str(source),
        "valid": True,
        "format": "Sponge schematic v2",
        "data_version": schematic["data_version"],
        "dimensions": list(schematic["dimensions"]),
        "offset": list(schematic["offset"]),
        "clipboard_origin": list(schematic["origin"]),
        "volume": schematic["volume"],
        "non_air_blocks": len(blocks),
        "air_blocks": schematic["volume"] - len(blocks),
        "palette_size": len(schematic["palette"]),
        "rails_present": False,
        "file_bytes": source.stat().st_size,
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "validation_scope": "Independent gzip/NBT/VarInt/palette/offset validation; game paste not tested",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("schematics", nargs="+", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps([validate_schem(path) for path in args.schematics], indent=2))
    except (OSError, SchematicError) as exc:
        parser.exit(1, f"Invalid schematic: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
