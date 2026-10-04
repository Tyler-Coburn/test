#!/usr/bin/env python3
"""Writes the GameTest template data/emerald/structure/gametest_platform.nbt.

An original, trivial structure: a 12x6x12 box with a smooth-stone floor and air above, in the vanilla
structure-file layout (gzipped NBT: DataVersion, size, palette, blocks, entities). Re-run to regenerate.
"""
import gzip, struct, sys

DATA_VERSION = 3955  # Minecraft 1.21.1
W, H, D = 12, 6, 12

def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def tag_int(n, v): return b"\x03" + name(n) + struct.pack(">i", v)
def tag_str(n, v): return b"\x08" + name(n) + name(v)
def tag_list(n, elem_type, payloads):
    return b"\x09" + name(n) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)
def compound_payload(*tags): return b"".join(tags) + b"\x00"
def int_payload(v): return struct.pack(">i", v)

palette = [compound_payload(tag_str("Name", "minecraft:smooth_stone")),
           compound_payload(tag_str("Name", "minecraft:air"))]
blocks = []
for x in range(W):
    for y in range(H):
        for z in range(D):
            state = 0 if y == 0 else 1
            blocks.append(compound_payload(tag_list("pos", 3, [int_payload(x), int_payload(y), int_payload(z)]),
                                           tag_int("state", state)))
root = b"\x0a" + name("") + compound_payload(
    tag_int("DataVersion", DATA_VERSION),
    tag_list("size", 3, [int_payload(W), int_payload(H), int_payload(D)]),
    tag_list("palette", 10, palette),
    tag_list("blocks", 10, blocks),
    tag_list("entities", 0, []))
out = sys.argv[1] if len(sys.argv) > 1 else "gametest_platform.nbt"
with gzip.open(out, "wb") as f:
    f.write(root)
print("wrote", out, len(blocks), "blocks")
