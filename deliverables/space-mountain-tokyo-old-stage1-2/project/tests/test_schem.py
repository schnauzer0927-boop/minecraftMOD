"""Schematic interoperability constraints, malformed input, and paste alignment."""

import gzip
from itertools import product
from pathlib import Path
import struct
import tempfile
import unittest

from generator.export_schem import export_schem
from generator.validate_schem import SchematicError, read_schem, validate_schem


class SchematicTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name)

    def write(self, blocks=None, name="shape.schem", **kwargs):
        path = self.folder / name
        export_schem(path, blocks or {(0, 0, 0): "minecraft:stone"}, **kwargs)
        return path

    def replace_bytes(self, path, old, new):
        raw = gzip.decompress(path.read_bytes())
        self.assertEqual(raw.count(old), 1, "Mutation must identify one serialized field")
        path.write_bytes(gzip.compress(raw.replace(old, new), mtime=0))

    def test_yzx_storage_order_and_air_holes(self):
        blocks = {
            (0, 0, 0): "minecraft:stone",
            (1, 0, 1): "minecraft:stone",
            (0, 1, 0): "minecraft:stone",
            (1, 1, 1): "minecraft:stone",
        }
        loaded = read_schem(self.write(blocks))
        # x advances first, then z, then y. The other four cells are air.
        self.assertEqual(loaded["block_data"], bytes([1, 0, 0, 1, 1, 0, 0, 1]))
        self.assertEqual(loaded["blocks"], blocks)
        self.assertEqual(loaded["dimensions"], (2, 2, 2))

    def test_varints_cross_127_without_corrupting_next_position(self):
        states = []
        for wood in ("oak", "spruce", "birch", "jungle", "acacia"):
            for values in product(("false", "true"), repeat=5):
                properties = ",".join(f"{key}={value}" for key, value in zip(("east", "north", "south", "waterlogged", "west"), values))
                states.append(f"minecraft:{wood}_fence[{properties}]")
        states = sorted(states)[:130]
        blocks = {(i, 0, 0): state for i, state in enumerate(states)}
        loaded = read_schem(self.write(blocks))
        self.assertEqual(loaded["blocks"], blocks)
        self.assertEqual(loaded["palette"][states[127]], 128)
        self.assertEqual(loaded["block_data"][126:], b"\x7f\x80\x01\x81\x01\x82\x01")

    def test_components_and_final_align_at_one_paste_position(self):
        facade = {(-5, 4, 2): "minecraft:quartz_block", (5, 5, 8): "minecraft:quartz_block"}
        roof = {(-1, 12, 5): "minecraft:white_concrete", (2, 15, 6): "minecraft:white_concrete"}
        final = read_schem(self.write(facade | roof, "final.schem"))
        paste_position = (200, 80, -300)
        final_positions = {tuple(p[i] - final["origin"][i] + paste_position[i] for i in range(3)): state for p, state in final["blocks"].items()}
        for name, component in (("facade", facade), ("roof", roof)):
            loaded = read_schem(self.write(component, name + ".schem"))
            self.assertEqual(loaded["origin"], (0, 0, 0))
            if name == "roof":
                self.assertNotEqual(loaded["offset"], final["offset"])
            for point, state in loaded["blocks"].items():
                pasted = tuple(point[i] - loaded["origin"][i] + paste_position[i] for i in range(3))
                self.assertEqual(final_positions[pasted], state)

    def test_nonzero_shared_input_anchor_and_tight_bounds(self):
        blocks = {(-8, 3, 4): "minecraft:stone", (-3, 5, 9): "minecraft:quartz_block", (1000, 1000, 1000): "minecraft:air"}
        path = self.write(blocks, origin=(-10, 2, 7))
        loaded = read_schem(path)
        self.assertEqual(loaded["offset"], (2, 1, -3))
        self.assertEqual(loaded["origin"], (0, 0, 0))
        self.assertEqual(loaded["dimensions"], (6, 3, 6))
        self.assertEqual(loaded["blocks"], {(2, 1, -3): "minecraft:stone", (7, 3, 2): "minecraft:quartz_block"})
        self.assertTrue(validate_schem(path)["valid"])

    def test_deterministic_gzip_and_palette(self):
        blocks = {(-1, 3, 2): "minecraft:stone", (2, 1, -3): "minecraft:quartz_block"}
        first = self.write(blocks, "first.schem")
        second = self.write(dict(reversed(list(blocks.items()))), "second.schem")
        self.assertEqual(first.read_bytes(), second.read_bytes())
        self.assertEqual(first.read_bytes()[4:8], b"\x00\x00\x00\x00")
        self.assertEqual(first.read_bytes()[9], 255)

    def test_metadata_unicode_and_java_modified_utf8(self):
        path = self.write(metadata={"Title": "旧東京\x00🚀", "Source": {"Checked": True}, "Parts": ["roof", "base"]})
        self.assertEqual(read_schem(path)["metadata"]["Title"], "旧東京\x00🚀")
        raw = gzip.decompress(path.read_bytes())
        self.assertIn(b"\xc0\x80\xed\xa0\xbd\xed\xba\x80", raw)

    def test_reject_invalid_input_and_reserved_offset(self):
        for blocks in ({}, {(0, 0, 0): "minecraft:air"}, {(0, 0): "minecraft:stone"}, {(0.5, 0, 0): "minecraft:stone"}, {(0, 0, 0): "stone"}):
            with self.subTest(blocks=blocks), self.assertRaises(ValueError):
                export_schem(self.folder / "bad.schem", blocks)
        with self.assertRaisesRegex(ValueError, "reserved"):
            self.write(metadata={"WEOffsetX": 10})
        with self.assertRaisesRegex(ValueError, "keys"):
            self.write(metadata={1: "bad", "ok": "text"})

    def test_reject_oversized_dimensions_before_allocation(self):
        with self.assertRaisesRegex(ValueError, "short"):
            self.write({(0, 0, 0): "minecraft:stone", (32767, 0, 0): "minecraft:stone"})
        with self.assertRaisesRegex(ValueError, "signed NBT array"):
            self.write({(0, 0, 0): "minecraft:stone", (32766, 32766, 32766): "minecraft:stone"})

    def test_export_and_reader_both_reject_rails(self):
        for state in ("minecraft:rail[shape=north_south]", "minecraft:powered_rail[powered=false,shape=east_west]", "minecraft:detector_rail", "minecraft:activator_rail"):
            with self.subTest(state=state), self.assertRaisesRegex(ValueError, "Rail"):
                self.write({(0, 0, 0): state})
        path = self.write()
        self.replace_bytes(path, b"\x00\x0fminecraft:stone", b"\x00\x0eminecraft:rail")
        with self.assertRaisesRegex(SchematicError, "Rail"):
            read_schem(path)

    def test_reader_detects_unknown_palette_reference(self):
        path = self.write()
        self.replace_bytes(path, b"\x07\x00\x09BlockData\x00\x00\x00\x01\x01", b"\x07\x00\x09BlockData\x00\x00\x00\x01\x02")
        with self.assertRaisesRegex(SchematicError, "unknown palette"):
            read_schem(path)

    def test_reader_detects_truncated_varint_and_count_mismatch(self):
        for payload, message in ((b"\x80", "Truncated.*VarInt"), (b"", "count"), (b"\x01\x01", "more blocks")):
            path = self.write(name=f"bad-{len(payload)}.schem")
            self.replace_bytes(path, b"\x07\x00\x09BlockData\x00\x00\x00\x01\x01", b"\x07\x00\x09BlockData" + struct.pack(">i", len(payload)) + payload)
            with self.subTest(payload=payload), self.assertRaisesRegex(SchematicError, message):
                read_schem(path)

    def test_reader_detects_palette_max_and_dimension_corruption(self):
        path = self.write()
        self.replace_bytes(path, b"\x03\x00\x0aPaletteMax\x00\x00\x00\x02", b"\x03\x00\x0aPaletteMax\x00\x00\x00\x03")
        with self.assertRaisesRegex(SchematicError, "PaletteMax"):
            read_schem(path)
        path = self.write(name="negative.schem")
        self.replace_bytes(path, b"\x02\x00\x05Width\x00\x01", b"\x02\x00\x05Width\xff\xff")
        with self.assertRaisesRegex(SchematicError, "positive"):
            read_schem(path)

    def test_validator_detects_common_origin_drift(self):
        path = self.write()
        self.replace_bytes(path, b"\x03\x00\x09WEOffsetX\x00\x00\x00\x00", b"\x03\x00\x09WEOffsetX\x00\x00\x00\x01")
        self.assertEqual(read_schem(path)["origin"], (-1, 0, 0))
        with self.assertRaisesRegex(SchematicError, "shared zero"):
            validate_schem(path)

    def test_validator_detects_loose_air_border(self):
        path = self.write()
        self.replace_bytes(path, b"\x02\x00\x05Width\x00\x01", b"\x02\x00\x05Width\x00\x02")
        self.replace_bytes(path, b"\x07\x00\x09BlockData\x00\x00\x00\x01\x01", b"\x07\x00\x09BlockData\x00\x00\x00\x02\x01\x00")
        with self.assertRaisesRegex(SchematicError, "tight"):
            validate_schem(path)

    def test_reader_detects_truncated_gzip_and_trailing_nbt(self):
        path = self.write()
        path.write_bytes(path.read_bytes()[:-5])
        with self.assertRaisesRegex(SchematicError, "gzip"):
            read_schem(path)
        path = self.write(name="trailing.schem")
        path.write_bytes(gzip.compress(gzip.decompress(path.read_bytes()) + b"junk", mtime=0))
        with self.assertRaisesRegex(SchematicError, "Trailing"):
            read_schem(path)


if __name__ == "__main__":
    unittest.main()
