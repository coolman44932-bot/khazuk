"""Da Hold Gate (Khazuk! D1; the user's picture, 2026-11-01: "i want it the entrances to look like actual holds"): a karak's gate cut
into a crag. A tall flat-fronted gatehouse with a dark doorway in a stepped frame under a rune medallion; on its two towers, high up,
great carved ancestor faces with bronze helms and beards flaring to a row of gold beads; battlements between; two bronze ancestors on
plinths below them, flanking the door; a stepped terrace and a walled road; and the mountain's rock all round, so it reads as a hold
even on flat ground. The doorway opens on a passage that goes down into the hold.

Local axes: x across (0 the door's middle), y up (0 the first block over the ground), z out of the mountain (the facade's face at z -1,
the road and the viewer at negative z).

    python tools/gen_hold_gate.py                    the block map -> build/hold_gate.json (and a count)
    python tools/gen_hold_gate.py --build x y z      and build it in the running bridge game, its origin at x y z
"""
import json, math, os, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "build", "hold_gate.json")

R = random.Random(1291)
B = {}


def put(x, y, z, block):
    B[(x, y, z)] = block


def box(x0, y0, z0, x1, y1, z1, block):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, block(x, y, z) if callable(block) else block)


def clear(x0, y0, z0, x1, y1, z1):
    box(x0, y0, z0, x1, y1, z1, "minecraft:air")


# ------------------------------------------------------------------ palettes

def rock(x, y, z):
    """The mountain's rock: stone and andesite with tuff and deepslate seams, mossy low down."""
    n = R.random()
    if y < 3 and n < 0.25:
        return "minecraft:mossy_cobblestone"
    if n < 0.40:
        return "minecraft:stone"
    if n < 0.60:
        return "minecraft:andesite"
    if n < 0.73:
        return "minecraft:tuff"
    if n < 0.83:
        return "minecraft:cobblestone"
    if n < 0.93:
        return "minecraft:deepslate"
    return "minecraft:stone"


def wall(x, y, z):
    """The gatehouse's dressed stone, weathered: pale bricks with a few cracked and mossy ones (moss low down)."""
    n = R.random()
    if n < 0.07:
        return "minecraft:cracked_stone_bricks"
    if n < (0.18 if y < 6 else 0.05):
        return "minecraft:mossy_stone_bricks"
    return "minecraft:stone_bricks"


# bronze: the exposed (browner) copper for the mass, the bright for highlights, the cut for detail
BRONZE, BRONZE_HI, BRONZE_CUT = "minecraft:waxed_exposed_copper", "minecraft:waxed_copper_block", "minecraft:waxed_exposed_cut_copper"
GOLD, TRIM, CARVE, DARK = "minecraft:gold_block", "minecraft:polished_andesite", "minecraft:chiseled_stone_bricks", "minecraft:black_concrete"
TUFF_PAT, TUFF_POL, SMOOTH = "minecraft:chiseled_tuff_bricks", "minecraft:polished_tuff", "minecraft:smooth_stone"

# ------------------------------------------------------------------ the mountain

W = 30            # the crag reaches 30 out each side of the door
DEEP = 18
for x in range(-W, W + 1):
    edge = abs(x) / W
    rough = min(1.0, abs(x) / 12)                                # smooth over the gatehouse, rough out on the shoulders
    top = int(42 - 18 * edge ** 2 + rough * (3 * math.sin(x * 0.6) + 2 * math.sin(x * 1.7 + 1)))
    for z in range(0, DEEP):
        h = top - max(0, z - 12)
        for y in range(0, h):
            put(x, y, z, rock(x, y, z))
    if abs(x) >= 16:                                            # the shoulders: the cliff comes forward either side of the gatehouse
        reach = int(2 + 4 * (abs(x) - 16) / (W - 16) + R.random() * 2)
        for z in range(-reach, 0):
            for y in range(0, int(top * (0.5 + 0.45 * (z + reach) / reach))):
                put(x, y, z, rock(x, y, z))

# ------------------------------------------------------------------ the gatehouse

GH, TW, TOP, MID = 15, 9, 34, 26   # half-width, tower width, the towers' top, the middle's top
box(-GH, 0, -1, GH, TOP, 8, wall)                                # its mass, cut into the crag
clear(-GH + TW, MID + 1, -1, GH - TW, TOP + 2, 3)                # the middle stands lower, set between the towers
for x in (-GH, -GH + TW - 1, GH - TW + 1, GH):                   # the towers' edges stand proud a block
    box(x, 0, -2, x, TOP, -2, TRIM)
for x0 in (-GH, GH - TW + 1):
    box(x0, TOP, -2, x0 + TW - 1, TOP, -2, TRIM)                 # their cornice
    for x in range(x0, x0 + TW):                                 # their crenels
        put(x, TOP + 1, -1, CARVE)
        if (x - x0) % 2 == 0:
            put(x, TOP + 2, -1, CARVE)
box(-GH, 0, -2, GH, 0, -2, TRIM)                                 # a plinth course along the foot
box(-GH, 1, -2, GH, 1, -2, SMOOTH)
# the middle's battlements, with a walk behind them
box(-GH + TW, MID, -1, GH - TW, MID, -1, CARVE)
for x in range(-GH + TW, GH - TW + 1):
    if x % 2 == 0:
        put(x, MID + 1, -1, CARVE)
        put(x, MID + 2, -1, "minecraft:waxed_lightning_rod" if x % 4 == 0 else "minecraft:air")   # the gilt spikes of the picture
box(-GH + TW, MID, 0, GH - TW, MID, 3, "minecraft:stone_bricks")
# a carved frieze under the battlements: a zigzag of polished tuff in the patterned stone, edged in polished andesite
for x in range(-GH + TW, GH - TW + 1):
    put(x, MID - 1, -2, TRIM)
    put(x, MID - 4, -2, TRIM)
    for dy in (0, 1):
        put(x, MID - 3 + dy, -2, TUFF_POL if (x + dy) % 2 == 0 else TUFF_PAT)

# the door: 5 wide, 9 tall, its top corners cut (a dwarf's gate is squared, not arched)
DOOR_Y = 2        # its sill: the terrace's top
DH = 9
clear(-2, DOOR_Y, -2, 2, DOOR_Y + DH - 1, 14)
clear(-1, DOOR_Y + DH, -2, 1, DOOR_Y + DH, 14)
# its frame: two bands proud of the wall, stepped to a point over it, the patterned band inside
for band, block in ((3, TUFF_PAT), (4, TUFF_POL), (5, TRIM)):
    for y in range(DOOR_Y, DOOR_Y + DH + 1):
        put(-band, y, -2, block)
        put(band, y, -2, block)
    for i in range(band + 1):                                    # the stepped head
        put(-band + i, DOOR_Y + DH + i, -2, block)
        put(band - i, DOOR_Y + DH + i, -2, block)
# the rune medallion over the door, in the frame's peak: a bronze disc round a gold rune
MY = DOOR_Y + DH + 2
for dx in range(-1, 2):
    for dy in range(-1, 2):
        put(dx, MY + dy, -3, BRONZE_CUT if (dx == 0) != (dy == 0) else BRONZE)
put(0, MY, -3, GOLD)
for dx, dy in ((-2, 0), (2, 0), (0, 2), (0, -2)):
    put(dx, MY + dy, -3, BRONZE_HI)
# carved lozenges down the face beside the door (the dwarf diamond)
for side in (-1, 1):
    cx = side * 7
    for i, dy in enumerate(range(3, 3 + 7)):
        half = 2 - abs(3 - i) if abs(3 - i) <= 2 else 0
        for dx in range(-half, half + 1):
            put(cx + dx, DOOR_Y + dy, -2, TUFF_PAT if abs(dx) == half else CARVE)

# the passage behind: dark deepslate, lanterns, and a stair going down into the hold
box(-3, DOOR_Y - 1, -1, 3, DOOR_Y + DH + 1, 16, "minecraft:deepslate_bricks")
clear(-2, DOOR_Y, -1, 2, DOOR_Y + DH - 1, 16)
clear(-1, DOOR_Y + DH, -1, 1, DOOR_Y + DH, 16)
box(-2, DOOR_Y - 1, -1, 2, DOOR_Y - 1, 7, "minecraft:polished_deepslate")
for i, z in enumerate(range(8, 17)):                             # the stair down
    box(-2, DOOR_Y - 2 - i, z, 2, DOOR_Y - 2 - i, z, "minecraft:polished_deepslate")
    clear(-2, DOOR_Y - 1 - i, z, 2, DOOR_Y + DH - 1, z)
    for x in range(-2, 3):
        put(x, DOOR_Y - 1 - i, z, "minecraft:deepslate_brick_stairs[facing=north]")
for z in (2, 6):
    put(0, DOOR_Y + DH, z, "minecraft:iron_chain")
    put(0, DOOR_Y + DH - 1, z, "minecraft:lantern[hanging=true]")

# ------------------------------------------------------------------ the ancestor faces, high on the towers

FACE = [  # 9 wide, 16 tall, top row first. H helm, h its shade, B brim, c crest (gold), e eye (dark), r brow, n nose, s face,
          # m moustache, b beard, k beard's carving, g gold bead, o bronze bead, . the tower wall behind
    "...HcH...",
    "..HHcHH..",
    ".HHHcHHH.",
    ".HhHHHhH.",
    "HHHHHHHHH",
    "BBBBBBBBB",
    ".rrrrrrr.",
    ".seesees.",
    ".sssnsss.",
    "mmmmnmmmm",
    "bkbbkbbkb",
    "bkbbkbbkb",
    "bkbbkbbkb",
    "bkbbkbbkb",
    "bkbbkbbkb",
    "ogogogogo",
]
FACE_PAL = {"H": BRONZE, "h": BRONZE_HI, "B": BRONZE_CUT, "c": GOLD, "e": DARK, "r": TRIM, "n": TRIM, "s": SMOOTH, "m": TRIM,
            "b": "minecraft:calcite", "k": "minecraft:polished_andesite", "g": GOLD, "o": BRONZE_HI}
FACE_TOP = TOP - 2
for x0 in (-GH, GH - TW + 1):
    for row, line in enumerate(FACE):
        y = FACE_TOP - row
        for i, ch in enumerate(line):
            if ch == ".":
                continue
            put(x0 + i, y, -2, FACE_PAL[ch])
            if ch in "Hhc" and row <= 4 and 1 <= i <= 7:
                put(x0 + i, y, -3, FACE_PAL[ch])                 # the helm's dome stands out further
            if ch == "B":
                put(x0 + i, y, -3, BRONZE_CUT)                   # and its brim, a ledge over the eyes
            if ch == "n":
                put(x0 + i, y, -3, TRIM)                         # the nose

# ------------------------------------------------------------------ the terrace, the steps and the road

box(-15, 0, -11, 15, 1, -3, lambda x, y, z: TRIM if (x + z) % 7 == 0 else "minecraft:stone_bricks")
for x in range(-15, 16):
    put(x, 2, -11, "minecraft:stone_brick_wall" if abs(x) > 4 else "minecraft:air")
for z in range(-11, -2):
    put(-15, 2, z, "minecraft:stone_brick_wall")
    put(15, 2, z, "minecraft:stone_brick_wall")
for x in range(-4, 5):                                           # the steps up to it, the road's width
    put(x, 1, -12, "minecraft:stone_brick_stairs[facing=south]")
    put(x, 0, -13, "minecraft:stone_brick_stairs[facing=south]")
for z in range(-30, -13):                                        # the road, paved and walled low
    for x in range(-4, 5):
        put(x, -1, z, TRIM if (x * 3 + z) % 5 == 0 else "minecraft:stone_bricks")
    put(-5, 0, z, "minecraft:stone_brick_wall")
    put(5, 0, z, "minecraft:stone_brick_wall")


# ------------------------------------------------------------------ the bronze ancestors

def statue(cx, side):
    """An ancestor in bronze, 10 tall on a carved plinth before a tower, facing the road: a horned helm over a stern face, a great beard
    to his belt, a broad body, a round shield with a gold boss held before him on his outer side, facing the road."""
    pz = -7
    box(cx - 3, 2, pz - 2, cx + 3, 4, pz + 2, lambda x, y, z: CARVE if y == 4 and (x + z) % 2 == 0 else TRIM)   # the plinth
    by = 5
    box(cx - 1, by, pz - 1, cx + 1, by + 1, pz + 1, BRONZE)      # his legs and robe's hem
    box(cx - 2, by + 2, pz - 1, cx + 2, by + 5, pz + 1, BRONZE)  # his body, broad
    box(cx - 1, by + 1, pz - 2, cx + 1, by + 5, pz - 2, BRONZE_CUT)   # his beard down his chest to his belt
    put(cx, by, pz - 2, BRONZE_CUT)                              # its tip
    box(cx - 1, by + 6, pz - 1, cx + 1, by + 7, pz + 1, BRONZE)  # his head
    put(cx - 1, by + 6, pz - 2, BRONZE_CUT)
    put(cx + 1, by + 6, pz - 2, BRONZE_CUT)
    put(cx, by + 6, pz - 2, BRONZE_HI)                           # the nose over the moustache
    put(cx - 1, by + 7, pz - 2, DARK)                            # eyes in the helm's shadow
    put(cx + 1, by + 7, pz - 2, DARK)
    put(cx, by + 7, pz - 2, BRONZE)
    box(cx - 2, by + 8, pz - 2, cx + 2, by + 8, pz + 1, BRONZE_HI)   # the helm's rim
    box(cx - 1, by + 9, pz - 1, cx + 1, by + 9, pz + 1, BRONZE_HI)   # its dome
    put(cx, by + 10, pz, GOLD)                                   # its crest
    for s in (-1, 1):                                            # the horns: out, then up
        put(cx + 3 * s, by + 8, pz, BRONZE_CUT)
        put(cx + 3 * s, by + 9, pz, BRONZE_CUT)
    ox = cx + 2 * side                                           # his shield before him on the outer side, facing the road
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 3:
                put(ox + dx, by + 3 + dy, pz - 3, BRONZE_CUT if abs(dx) + abs(dy) == 3 else BRONZE)
    put(ox, by + 3, pz - 4, GOLD)                                # its boss


statue(-11, -1)
statue(11, 1)


def ops(origin):
    blocks = [[x, y, z, b] for (x, y, z), b in sorted(B.items())]
    return [{"op": "blocks", "origin": list(origin), "blocks": blocks}]


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(ops([0, 0, 0]), f)
    xs = [k[0] for k in B]
    ys = [k[1] for k in B]
    zs = [k[2] for k in B]
    print(f"{len(B)} cells, x {min(xs)}..{max(xs)}, y {min(ys)}..{max(ys)}, z {min(zs)}..{max(zs)}")
    if len(sys.argv) >= 5 and sys.argv[1] == "--build":
        sys.path.insert(0, os.path.join(HERE, "..", "..", "devbridge", "tools"))
        import devbridge
        o = [int(v) for v in sys.argv[2:5]]
        r = devbridge.call("build", ops=ops(o))
        print(json.dumps(r)[:300])


if __name__ == "__main__":
    main()
