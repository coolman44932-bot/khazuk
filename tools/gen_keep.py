"""The Dwarfs' founding hall, the Keep (the user, 2026-11-02: "a heart hall, basically you can place that first, a defensive position that
counts as your first building then you can place the heart"): a stone keep in a walled yard, raised by the god before the hold exists
(the engine's Founding), its Shrine set on the dais inside. Written straight to the room template (no game needed): every cell of its box,
in 26.1's data version, as the bridge's structure_save writes it.

Frame (the engine's): x away from the gate (the gate on the x = 0 face, at y 1), y up (y 0 the ground layer), z across.
- The yard wall: 5 high round the box's edge, battlements on it, a tower 3 x 3 at each corner standing 8 high.
- The gate: 3 wide in the middle of the front wall, open fence gates across it (the engine's walls shut them when foes come), a carved
  lintel and the hold's rune over it.
- The keep: 11 x 11 in the yard's middle, 9 high with a parapet, arrow slits (iron bars), its door toward the gate.
- Inside: the dais, its top the Shrine's spot (the "heart" station), braziers either side, an ancestor's face on the back wall, lanterns.

    python tools/gen_keep.py           writes data/khazuk/structure/room/dawi_keep.nbt and prints the heart station
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "structgen"))
from gen_halls import Hall, AIR, WALL, TRIM, CARVE, SB, GOLD, BRONZE, POL, TILE, CHIS, DARK, LANTERN, CHAIN, FIRE  # noqa: E402
import nbtio  # noqa: E402

OUT = os.path.join(HERE, "..", "src", "main", "resources", "data", "khazuk", "structure", "room", "dawi_keep.nbt")
DATA_VERSION = 4790                      # 26.1.2's, as the bridge's structure_save writes it

SX, SY, SZ = 25, 14, 25
GATE_Z = 12
KX0, KX1, KZ0, KZ1 = 9, 19, 7, 17        # the keep
HEART = (14, 1, 12)                      # the Shrine's spot: on the floor, in the dais's ring (a keep raised round a Shrine sits level)


def stone(x, y, z, h):
    """Dressed stone: stone bricks, every fourth course polished andesite, a few cracked or mossy at the foot."""
    if y % 4 == 0:
        return TRIM
    n = h.r.random()
    if y <= 2 and n < 0.10:
        return "minecraft:mossy_stone_bricks"
    return "minecraft:cracked_stone_bricks" if n < 0.06 else SB


def keep():
    h = Hall(SX, SY, SZ, GATE_Z, 3, 3, 23)
    # the ground layer: a flagged yard, a paved way from the gate to the keep
    for x in range(SX):
        for z in range(SZ):
            h.put(x, 0, z, "minecraft:polished_andesite" if abs(z - GATE_Z) <= 1 and x < KX0 else
                  ("minecraft:stone_bricks" if (x + z) % 3 else "minecraft:andesite"))
    # the yard wall, 5 high, two thick, battlements on its outer face
    for x in range(SX):
        for z in range(SZ):
            edge = min(x, z, SX - 1 - x, SZ - 1 - z)
            if edge <= 1:
                for y in range(1, 6):
                    h.put(x, y, z, stone(x, y, z, h))
                if edge == 0 and (x + z) % 2 == 0:
                    h.put(x, 6, z, CARVE)                          # the crenels
    # a tower 3 x 3 at each corner, 8 high, crenellated
    for cx, cz in ((0, 0), (SX - 3, 0), (0, SZ - 3), (SX - 3, SZ - 3)):
        for x in range(cx, cx + 3):
            for z in range(cz, cz + 3):
                for y in range(1, 8):
                    h.put(x, y, z, stone(x, y, z, h))
                if (x + z) % 2 == 0:
                    h.put(x, 8, z, CARVE)
        h.put(cx + 1, 8, cz + 1, "minecraft:lantern")
    # the gate: 3 wide, the fence gates across it open (the walls shut them at an alarm), 3 high, a carved lintel and the rune over it
    for z in range(GATE_Z - 1, GATE_Z + 2):
        for x in (0, 1):
            h.put(x, 1, z, "minecraft:dark_oak_fence_gate[facing=west,open=true]" if x == 0 else AIR)
            h.put(x, 2, z, AIR)
            h.put(x, 3, z, AIR)
        h.put(0, 4, z, CARVE)
    h.put(0, 5, GATE_Z, GOLD)
    for y in range(1, 5):                                          # gate pillars
        h.put(0, y, GATE_Z - 2, CHIS)
        h.put(0, y, GATE_Z + 2, CHIS)
    # the keep, 11 x 11, 9 high, a parapet round its roof
    for x in range(KX0, KX1 + 1):
        for z in range(KZ0, KZ1 + 1):
            wall = x in (KX0, KX1) or z in (KZ0, KZ1)
            h.put(x, 0, z, "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:deepslate_tiles")
            for y in range(1, 9):
                h.put(x, y, z, stone(x, y, z, h) if wall else AIR)
            h.put(x, 9, z, TILE)                                    # the roof
            if wall and (x + z) % 2 == 0:
                h.put(x, 10, z, CARVE)                              # its parapet
    for x, z in ((KX0, KZ0), (KX0, KZ1), (KX1, KZ0), (KX1, KZ1)):
        for y in range(1, 10):
            h.put(x, y, z, POL)                                     # the keep's corners
    # its door toward the gate, 3 wide and 4 high, a carved lintel
    for z in range(GATE_Z - 1, GATE_Z + 2):
        for y in range(1, 5):
            h.put(KX0, y, z, AIR)
        h.put(KX0, 5, z, CARVE)
    h.put(KX0, 6, GATE_Z, GOLD)
    # arrow slits on every side
    for y in (3, 6):
        for z in (KZ0 + 2, KZ1 - 2):
            h.put(KX0, y, z, "minecraft:iron_bars")
            h.put(KX1, y, z, "minecraft:iron_bars")
        for x in (KX0 + 3, KX1 - 3):
            h.put(x, y, KZ0, "minecraft:iron_bars")
            h.put(x, y, KZ1, "minecraft:iron_bars")
    # the dais: a ring a step up round the Shrine's spot, the Shrine standing in it on a chiselled stone, braziers on the ring
    hx, hy, hz = HEART
    h.box(hx - 2, 1, hz - 2, hx + 2, 1, hz + 2, POL)
    for dz in range(-2, 3):
        h.put(hx - 3, 1, hz + dz, "minecraft:polished_deepslate_stairs[facing=east]")
    h.put(hx, hy - 1, hz, CHIS)                                    # the Shrine stands on this, in the floor
    h.put(hx, hy, hz, AIR)                                          # its spot
    h.put(hx - 1, 1, hz, "minecraft:polished_deepslate_stairs[facing=east]")   # the step in to it from the door's side
    for dz in (-2, 2):                                              # braziers either side, on the ring
        h.put(hx, 2, hz + dz, FIRE)
    # an ancestor's face on the back wall
    h.face(KX1 - 1, GATE_Z, 2)
    for x in (KX0 + 2, KX1 - 2):                                    # lanterns on chains from the roof
        for z in (KZ0 + 2, KZ1 - 2):
            h.put(x, 8, z, CHAIN)
            h.put(x, 7, z, LANTERN)
    return h


def save(h, path):
    cells = sorted(h.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))
    palette, index, blocks = [], {}, []
    for (x, y, z), st in cells:
        if st not in index:
            index[st] = len(palette)
            name, props = st, {}
            if "[" in st:
                name, rest = st.split("[", 1)
                props = dict(kv.split("=") for kv in rest.rstrip("]").split(","))
            entry = {"Name": nbtio.String(name)}
            if props:
                entry["Properties"] = nbtio.Compound({k: nbtio.String(v) for k, v in props.items()})
            palette.append(nbtio.Compound(entry))
        blocks.append(nbtio.Compound({"pos": nbtio.List([nbtio.Int(x), nbtio.Int(y), nbtio.Int(z)]), "state": nbtio.Int(index[st])}))
    root = nbtio.Compound({
        "DataVersion": nbtio.Int(DATA_VERSION),
        "size": nbtio.List([nbtio.Int(SX), nbtio.Int(SY), nbtio.Int(SZ)]),
        "palette": nbtio.List(palette, nbtio.COMPOUND),
        "blocks": nbtio.List(blocks, nbtio.COMPOUND),
        "entities": nbtio.List([], nbtio.COMPOUND),
    })
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    nbtio.write(path, root)


if __name__ == "__main__":
    k = keep()
    save(k, OUT)
    solid = sum(1 for s in k.b.values() if s != AIR)
    print(f"dawi_keep: size ({SX}, {SY}, {SZ}), door (0, 1, {GATE_Z}), heart station {HEART}, {solid} solid of {len(k.b)} cells -> {os.path.relpath(OUT)}")
