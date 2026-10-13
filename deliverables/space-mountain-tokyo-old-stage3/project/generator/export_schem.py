"""Deterministic, dependency-free Sponge schematic v2 export.

Coordinates are in one project coordinate system. The supplied ``origin`` is
the shared paste anchor, not the bounding-box corner. Sparse input becomes a
tight rectangular schematic; use WorldEdit ``//paste -a`` for additive parts.
"""

from __future__ import annotations

import gzip
import hashlib
import math
from pathlib import Path
import re
import struct
from collections.abc import Mapping
from typing import Any


DATA_VERSION = 3955  # Minecraft Java 1.21.1
SCHEMATIC_VERSION = 2
AIR = "minecraft:air"
_STATE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?$")
_RAILS = {"rail", "powered_rail", "detector_rail", "activator_rail"}
_AIR_STATES = {AIR, "minecraft:cave_air", "minecraft:void_air"}


def _coordinate(value: Any, name: str) -> tuple[int, int, int]:
    if not isinstance(value, (tuple, list)) or len(value) != 3:
        raise ValueError(f"{name} must have three integer components")
    if any(type(v) is not int for v in value):
        raise ValueError(f"{name} must have three integer components")
    return tuple(value)


def _nbt_string(value: str) -> bytes:
    # NBT uses Java modified UTF-8: NUL is two bytes and supplementary
    # characters are encoded as two UTF-16 surrogate code units.
    data = bytearray()
    utf16 = value.encode("utf-16-be")
    for index in range(0, len(utf16), 2):
        unit = (utf16[index] << 8) | utf16[index + 1]
        if 0 < unit <= 0x7F:
            data.append(unit)
        elif unit <= 0x7FF:
            data.extend((0xC0 | (unit >> 6), 0x80 | (unit & 0x3F)))
        else:
            data.extend((0xE0 | (unit >> 12), 0x80 | ((unit >> 6) & 0x3F), 0x80 | (unit & 0x3F)))
    if len(data) > 65535:
        raise ValueError("NBT string exceeds 65535 bytes")
    return struct.pack(">H", len(data)) + data


def _named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes([tag]) + _nbt_string(name) + payload


def _metadata_tag(value: Any) -> tuple[int, bytes]:
    """Encode ordinary metadata without requiring an NBT package."""
    if isinstance(value, str):
        return 8, _nbt_string(value)
    if isinstance(value, bool):
        return 1, struct.pack(">b", int(value))
    if type(value) is int:
        if -(2**31) <= value < 2**31:
            return 3, struct.pack(">i", value)
        if -(2**63) <= value < 2**63:
            return 4, struct.pack(">q", value)
        raise ValueError("Metadata integer exceeds NBT long range")
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("Metadata numbers must be finite")
        return 6, struct.pack(">d", value)
    if isinstance(value, Mapping):
        entries = []
        if any(not isinstance(name, str) for name in value):
            raise ValueError("Metadata keys must be strings")
        for name, child in sorted(value.items()):
            child_type, payload = _metadata_tag(child)
            entries.append(_named(child_type, name, payload))
        return 10, b"".join(entries) + b"\x00"
    if isinstance(value, (list, tuple)):
        encoded = [_metadata_tag(item) for item in value]
        tag = encoded[0][0] if encoded else 10
        if any(t != tag for t, _ in encoded):
            raise ValueError("Metadata lists must contain a single NBT type")
        return 9, struct.pack(">bi", tag, len(encoded)) + b"".join(p for _, p in encoded)
    raise ValueError(f"Unsupported metadata type: {type(value).__name__}")


def _varint(value: int) -> bytes:
    data = bytearray()
    while value >= 128:
        data.append((value & 127) | 128)
        value >>= 7
    data.append(value)
    return bytes(data)


def export_schem(
    path: str | Path,
    blocks: Mapping[tuple[int, int, int], str],
    origin: tuple[int, int, int] = (0, 0, 0),
    metadata: dict | None = None,
) -> dict:
    """Write a Sponge v2 ``.schem`` and return JSON-serializable file facts.

    Dimensions are bounded to signed NBT shorts (1..32767). Explicit air is
    discarded before choosing the bounding box. Rail states are rejected.
    ``Offset`` and legacy ``WEOffset`` are identical, so WorldEdit reads a
    clipboard origin of zero and parts share one paste position.
    """
    anchor = _coordinate(origin, "origin")
    if not isinstance(blocks, Mapping):
        raise ValueError("blocks must be a mapping from coordinates to states")
    clean: dict[tuple[int, int, int], str] = {}
    for position, state in blocks.items():
        position = _coordinate(position, "block coordinate")
        if not isinstance(state, str) or not _STATE.fullmatch(state):
            raise ValueError(f"Invalid namespaced block state: {state!r}")
        identifier = state.partition("[")[0]
        if identifier.partition(":")[2] in _RAILS:
            raise ValueError(f"Rail blocks are outside this project: {state}")
        if identifier not in _AIR_STATES:
            clean[position] = state
    if not clean:
        raise ValueError("Cannot export an empty schematic")

    minimum = tuple(min(p[i] for p in clean) for i in range(3))
    maximum = tuple(max(p[i] for p in clean) for i in range(3))
    dimensions = tuple(maximum[i] - minimum[i] + 1 for i in range(3))
    if any(dimension > 32767 for dimension in dimensions):
        raise ValueError(f"Schematic dimensions exceed signed short range: {dimensions}")
    offset = tuple(minimum[i] - anchor[i] for i in range(3))
    if any(not -(2**31) <= value < 2**31 for value in offset):
        raise ValueError("Offset exceeds NBT signed integer range")

    palette = {AIR: 0}
    palette.update((state, i) for i, state in enumerate(sorted(set(clean.values())), start=1))
    width, height, length = dimensions
    if width * height * length > 2**31 - 1:
        raise ValueError("Schematic volume exceeds signed NBT array length")
    # Sponge data index: x + z*Width + y*Width*Length (x is fastest).
    data = bytearray(width * height * length)
    if len(palette) <= 128:
        for (x, y, z), state in clean.items():
            index = (x - minimum[0]) + (z - minimum[2]) * width + (y - minimum[1]) * width * length
            data[index] = palette[state]
    else:
        indices = {
            (x - minimum[0]) + (z - minimum[2]) * width + (y - minimum[1]) * width * length: palette[state]
            for (x, y, z), state in clean.items()
        }
        data = bytearray()
        for index in range(width * height * length):
            data.extend(_varint(indices.get(index, 0)))

    if metadata is not None and not isinstance(metadata, Mapping):
        raise ValueError("metadata must be a mapping")
    supplied_metadata = dict(metadata or {})
    for axis, value in zip("XYZ", offset):
        name = "WEOffset" + axis
        if name in supplied_metadata and (type(supplied_metadata[name]) is not int or supplied_metadata[name] != value):
            raise ValueError(f"{name} is reserved for the shared paste anchor")
        supplied_metadata[name] = value
    metadata_type, metadata_payload = _metadata_tag(supplied_metadata)

    entries = [
        _named(3, "Version", struct.pack(">i", SCHEMATIC_VERSION)),
        _named(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
        _named(metadata_type, "Metadata", metadata_payload),
        _named(2, "Width", struct.pack(">h", width)),
        _named(2, "Height", struct.pack(">h", height)),
        _named(2, "Length", struct.pack(">h", length)),
        _named(11, "Offset", struct.pack(">iiii", 3, *offset)),
        _named(3, "PaletteMax", struct.pack(">i", len(palette))),
        _named(10, "Palette", b"".join(_named(3, name, struct.pack(">i", i)) for name, i in palette.items()) + b"\x00"),
        _named(7, "BlockData", struct.pack(">i", len(data)) + data),
        _named(9, "BlockEntities", struct.pack(">bi", 10, 0)),
        _named(9, "Entities", struct.pack(">bi", 10, 0)),
    ]
    raw = _named(10, "Schematic", b"".join(entries) + b"\x00")
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    # GzipFile, unlike gzip.compress(mtime=0) on some Python versions, sets
    # platform-independent OS=255. Empty filename avoids leaking paths.
    with destination.open("wb") as output:
        with gzip.GzipFile(fileobj=output, mode="wb", filename="", mtime=0, compresslevel=9) as compressed:
            compressed.write(raw)
    compressed_data = destination.read_bytes()
    volume = width * height * length
    return {
        "path": str(destination),
        "format": "Sponge schematic v2",
        "data_version": DATA_VERSION,
        "origin": list(anchor),
        "clipboard_origin": [0, 0, 0],
        "bounds": {"min": list(minimum), "max": list(maximum)},
        "dimensions": list(dimensions),
        "offset": list(offset),
        "volume": volume,
        "non_air_blocks": len(clean),
        "air_blocks": volume - len(clean),
        "palette": palette,
        "palette_size": len(palette),
        "file_bytes": len(compressed_data),
        "sha256": hashlib.sha256(compressed_data).hexdigest(),
    }
