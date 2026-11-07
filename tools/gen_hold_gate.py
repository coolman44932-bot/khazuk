"""Da Hold Gate (Khazuk! D1; the user's picture, 2026-11-01: "i want it the entrances to look like actual holds"): a karak's gate cut
into a crag. A tall gatehouse of dressed stone with a dark doorway in a stepped frame under a rune medallion; on its two towers, high
up, great ancestor faces carved deep (a domed bronze helm with a brim, a brow over shadowed eyes, a nose standing out, a moustache and a
braided beard flaring to a row of gold beads); a carved panel and a frieze over the door, battlements between the towers; two bronze
guardians standing on plinths before it, resting on great hammers; braziers at the door, lanterns along the walled road; and the
mountain's rock all round in strata and ledges, so it reads as a hold even on flat ground. The doorway opens on a passage that goes
down into the hold.

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
# --template: the raised building the Miners put up (Settlement Core's surface builder): no stair below the ground (a raised building
# has no cellar: the dug hold meets the passage from behind), no road past the steps, and a crag narrow enough for the builder's 64-column
# site. Written turned, as the engine's templates are: the door on the x = 0 face, facing west; y 0 the ground layer.
RAISED = "--template" in sys.argv


ROCK = set()      # the cells whose last word is the mountain's rock (the crag the god raises, not the Miners' masonry)


def put(x, y, z, block):
    B[(x, y, z)] = block
    ROCK.discard((x, y, z))


def rock_put(x, y, z, block):
    put(x, y, z, block)
    ROCK.add((x, y, z))


def box(x0, y0, z0, x1, y1, z1, block):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, block(x, y, z) if callable(block) else block)


def clear(x0, y0, z0, x1, y1, z1):
    box(x0, y0, z0, x1, y1, z1, "minecraft:air")


# ------------------------------------------------------------------ palettes

STRATA = ["minecraft:stone", "minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:stone", "minecraft:deepslate",
          "minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:stone"]
BAND = 4          # the strata's thickness


def rock(x, y, z):
    """The mountain's rock in strata: each band its own stone, a little of its neighbours' in it; cobble and moss at the foot."""
    if y < 2:
        return "minecraft:mossy_cobblestone" if R.random() < 0.4 else "minecraft:cobblestone"
    tilt = (x // 7) % 2                                          # the bands step up and down a little across the face
    band = (y + tilt) // BAND
    n = R.random()
    if n < 0.80:
        return STRATA[band % len(STRATA)]
    if n < 0.90:
        return STRATA[(band + 1) % len(STRATA)]
    return "minecraft:cobblestone" if n < 0.95 else "minecraft:gravel" if y > 30 else "minecraft:stone"


def wall(x, y, z):
    """The gatehouse's dressed stone: pale bricks in courses, every fifth course a band of polished andesite; a few cracked and mossy."""
    if y % 5 == 0:
        return "minecraft:polished_andesite"
    n = R.random()
    if n < 0.06:
        return "minecraft:cracked_stone_bricks"
    if n < (0.16 if y < 6 else 0.04):
        return "minecraft:mossy_stone_bricks"
    return "minecraft:stone_bricks"


# bronze: exposed copper (brown-orange) for the mass, weathered (green patina) in the hollows, the bright and the cut for highlights
BRONZE, PATINA, BRONZE_HI = "minecraft:waxed_exposed_copper", "minecraft:waxed_weathered_copper", "minecraft:waxed_copper_block"
BRONZE_CUT, PATINA_CUT = "minecraft:waxed_exposed_cut_copper", "minecraft:waxed_weathered_cut_copper"
GOLD, TRIM, CARVE, DARK = "minecraft:gold_block", "minecraft:polished_andesite", "minecraft:chiseled_stone_bricks", "minecraft:polished_blackstone"
TUFF_PAT, TUFF_POL, SMOOTH = "minecraft:chiseled_tuff_bricks", "minecraft:polished_tuff", "minecraft:smooth_stone"
BEARD, BEARD_LINE = "minecraft:calcite", "minecraft:polished_diorite"

# ------------------------------------------------------------------ the mountain: strata, ledges, cliffs

W = 26 if RAISED else 30
DEEP = 18
GH = 15           # the gatehouse's half-width
for x in range(-W, W + 1):
    edge = abs(x) / W
    rough = min(1.0, abs(x) / 12)
    top = int(48 - 22 * edge ** 2 + rough * (3 * math.sin(x * 0.55) + 2 * math.sin(x * 1.6 + 1)))
    top = top - top % 3 + (2 if (x // 5) % 2 else 0)            # stepped: ledges every few blocks
    for z in range(0, DEEP):
        h = top - max(0, z - 12)
        for y in range(0, h):
            rock_put(x, y, z, rock(x, y, z))
    if abs(x) > GH:                                              # the shoulders: cliffs stepping forward either side of the gatehouse
        reach = int(2 + 5 * (abs(x) - GH) / (W - GH))
        for z in range(-reach, 0):
            # the cliff's face steps back as it rises: a ledge at every other band, the overhang of the harder band above it
            lift = (-z) * 6 + (3 if (abs(x) + z) % 4 == 0 else 0)
            for y in range(0, max(0, int(top * 0.9) - lift)):
                rock_put(x, y, z, rock(x, y, z))
# moss on the ledges' tops and the crag's crown, and the odd tuft of moss carpet
for (x, y, z), b in list(B.items()):
    if b != "minecraft:air" and (x, y + 1, z) not in B and R.random() < 0.45:
        rock_put(x, y, z, "minecraft:moss_block")
        if R.random() < 0.15:
            rock_put(x, y + 1, z, "minecraft:moss_carpet")

# ------------------------------------------------------------------ the gatehouse

TW, TOP, MID = 9, 38, 30           # the towers' width, their top, the middle's top
box(-GH, 0, -1, GH, TOP, 1, wall)                                # the facade, three thick: the Miners' masonry
for x in range(-GH, GH + 1):                                     # behind it the mountain's own rock, which the god raises
    for y in range(0, TOP + 1):
        for z in range(2, 9):
            rock_put(x, y, z, rock(x, y, z))
clear(-GH + TW, MID + 1, -1, GH - TW, TOP + 3, 3)                # the middle stands lower, set between the towers
for x in (-GH, -GH + TW - 1, GH - TW + 1, GH):                   # the towers' edges: pilasters standing proud
    box(x, 0, -2, x, TOP, -2, TRIM)
for x0 in (-GH, GH - TW + 1):
    box(x0, TOP, -2, x0 + TW - 1, TOP, -2, TRIM)                 # their cornice
    box(x0, TOP + 1, -1, x0 + TW - 1, TOP + 1, -1, CARVE)
    for x in range(x0, x0 + TW):                                 # their crenels
        if (x - x0) % 2 == 0:
            put(x, TOP + 2, -1, CARVE)
box(-GH, 0, -2, GH, 0, -2, TRIM)                                 # the foot's plinth course
box(-GH, 1, -2, GH, 1, -2, SMOOTH)
# the middle's battlements, gilt spikes on every other merlon, a walk behind
box(-GH + TW, MID, -1, GH - TW, MID, -1, CARVE)
for x in range(-GH + TW, GH - TW + 1):
    if x % 2 == 0:
        put(x, MID + 1, -1, CARVE)
        if x % 4 == 0:
            put(x, MID + 2, -1, "minecraft:waxed_lightning_rod")
box(-GH + TW, MID, 0, GH - TW, MID, 3, "minecraft:stone_bricks")
# the frieze under them: a zigzag of polished tuff in the patterned stone, edged in polished andesite
for x in range(-GH + TW, GH - TW + 1):
    put(x, MID - 1, -2, TRIM)
    put(x, MID - 4, -2, TRIM)
    for dy in (0, 1):
        put(x, MID - 3 + dy, -2, TUFF_POL if (x + dy) % 2 == 0 else TUFF_PAT)

# the door: 5 wide, 9 tall, its top corners cut (a dwarf's gate is squared, not arched)
DOOR_Y, DH = 2, 9
clear(-2, DOOR_Y, -4, 2, DOOR_Y + DH - 1, 14)
clear(-1, DOOR_Y + DH, -4, 1, DOOR_Y + DH, 14)
# its frame: bands each a step prouder than the last, stepped to a point over the door
for band, block, z in ((3, TUFF_PAT, -2), (4, TUFF_POL, -3), (5, TRIM, -3), (6, TUFF_POL, -2)):
    for y in range(DOOR_Y, DOOR_Y + DH + 1):
        put(-band, y, z, block)
        put(band, y, z, block)
    for i in range(band + 1):
        put(-band + i, DOOR_Y + DH + i, z, block)
        put(band - i, DOOR_Y + DH + i, z, block)
# the rune medallion in the frame's peak: a bronze disc round a gold rune, standing out from the frame
MY = DOOR_Y + DH + 2
for dx in range(-1, 2):
    for dy in range(-1, 2):
        put(dx, MY + dy, -4, BRONZE_CUT if (dx == 0) != (dy == 0) else BRONZE)
put(0, MY, -5, GOLD)
for dx, dy in ((-2, 0), (2, 0), (0, 2), (0, -2)):
    put(dx, MY + dy, -4, BRONZE_HI)
# the carved panel over it all: a frame of chiseled stone round the hold's great rune in gold, under the frieze
PY0, PY1 = DOOR_Y + DH + 7, MID - 6
box(-4, PY0, -2, 4, PY1, -2, CARVE)
box(-3, PY0 + 1, -2, 3, PY1 - 1, -2, TUFF_POL)
for dy, row in enumerate(["ggggg", "ggggg", "..g..", "..g..", ".ggg."]):    # Grungni's hammer: its head, the haft, the pommel
    for i, ch in enumerate(row):
        if ch == "g" and PY1 - 1 - dy >= PY0 + 1:
            put(i - 2, PY1 - 1 - dy, -3, GOLD)
# carved lozenges down the face beside the door (the dwarf diamond)
for side in (-1, 1):
    cx = side * 8
    for i, dy in enumerate(range(3, 10)):
        half = 2 - abs(3 - i) if abs(3 - i) <= 2 else 0
        for dx in range(-half, half + 1):
            put(cx + dx, DOOR_Y + dy, -2, TUFF_PAT if abs(dx) == half else CARVE)

# the passage behind: dark deepslate, lanterns, and a stair going down into the hold
box(-3, DOOR_Y - 1, -1, 3, DOOR_Y + DH + 1, 16, "minecraft:deepslate_bricks")
clear(-2, DOOR_Y, -1, 2, DOOR_Y + DH - 1, 16)
clear(-1, DOOR_Y + DH, -1, 1, DOOR_Y + DH, 16)
box(-2, DOOR_Y - 1, -1, 2, DOOR_Y - 1, 7, "minecraft:polished_deepslate")
for i, z in enumerate(range(8, 17)):
    if RAISED:
        box(-2, DOOR_Y - 1, z, 2, DOOR_Y - 1, z, "minecraft:polished_deepslate")   # the passage runs on, level, to the hold's way in
        continue
    box(-2, DOOR_Y - 2 - i, z, 2, DOOR_Y - 2 - i, z, "minecraft:polished_deepslate")
    clear(-2, DOOR_Y - 1 - i, z, 2, DOOR_Y + DH - 1, z)
    for x in range(-2, 3):
        put(x, DOOR_Y - 1 - i, z, "minecraft:deepslate_brick_stairs[facing=north]")
for z in (2, 6):
    put(0, DOOR_Y + DH, z, "minecraft:iron_chain")
    put(0, DOOR_Y + DH - 1, z, "minecraft:lantern[hanging=true]")

# ------------------------------------------------------------------ the Shrine's hall at the passage's end (the user, 2026-11-04: the
# hold goes behind its gate, not before it): the passage opens into a hall 11 wide and 11 high, and on a dais at its far end, under Grungni's
# rune, stands the Ancestor Shrine. The hold is founded here: its halls are dug off this hall's walls and the passage's.
SHRINE_HALL = RAISED
# a hall seal's FACING in the template's frame (x' = z + 14, z' = x + GH): local +x is the template's +z', south
# its middle: z 14, so the widest hall dug through it (the Great Hall, 25 across) clears the gatehouse's back wall (z 1)
SEAL_Z = 14
SEAL_FACING = {True: "khazuk:hall_seal[facing=south]", False: "khazuk:hall_seal[facing=north]"}
HEART_LOCAL = (0, DOOR_Y + 1, 14)                                # the Shrine's spot: on the dais, in the hall's middle
if SHRINE_HALL:
    HZ0, HZ1, HX, HTOP = 9, 16, 5, DOOR_Y + 10                   # the hall: z 9..16, x -5..5, its floor at DOOR_Y - 1, its roof over HTOP
    box(-HX - 1, DOOR_Y - 1, HZ0, HX + 1, HTOP + 1, HZ1 + 1, "minecraft:deepslate_bricks")
    clear(-HX, DOOR_Y, HZ0, HX, HTOP, HZ1)
    box(-HX, DOOR_Y - 1, HZ0, HX, DOOR_Y - 1, HZ1,
        lambda x, y, z: "minecraft:deepslate_tiles" if (x + z) % 2 else "minecraft:polished_deepslate")
    for x in range(-1, 2):                                       # a runner of gold-edged tiles from the passage to the dais
        for z in range(HZ0, 12):
            put(x, DOOR_Y - 1, z, "minecraft:chiseled_deepslate" if x == 0 else "minecraft:polished_blackstone")
    for px in (-HX, HX):                                         # four pillars, banded in gold and bronze
        for pz in (HZ0 + 1, HZ1):
            for y in range(DOOR_Y, HTOP + 1):
                band = {DOOR_Y: CARVE, DOOR_Y + 4: GOLD, DOOR_Y + 5: BRONZE, HTOP: CARVE}
                put(px, y, pz, band.get(y, "minecraft:polished_deepslate"))
    # the dais: a step up from the hall, the Shrine on it, braziers on its front corners
    box(-3, DOOR_Y, 13, 3, DOOR_Y, HZ1, "minecraft:polished_deepslate")
    for x in range(-3, 4):
        put(x, DOOR_Y, 12, "minecraft:deepslate_brick_stairs[facing=south]")
    for x in (-3, 3):
        put(x, DOOR_Y + 1, 13, CARVE)
        put(x, DOOR_Y + 2, 13, BRONZE_CUT)
        put(x, DOOR_Y + 3, 13, "minecraft:campfire[lit=true]")
    put(*HEART_LOCAL, "minecraft:air")
    # Grungni's rune on the wall behind the Shrine: a tuff panel framed in chiselled stone, the hammer in gold standing out of it
    box(-3, DOOR_Y + 3, HZ1 + 1, 3, DOOR_Y + 9, HZ1 + 1, CARVE)
    box(-2, DOOR_Y + 4, HZ1 + 1, 2, DOOR_Y + 8, HZ1 + 1, TUFF_POL)
    for dy, row in enumerate(["ggggg", "ggggg", "..g..", "..g..", ".ggg."]):
        for i, ch in enumerate(row):
            if ch == "g":                                        # set in the wall, flush: the statue's answers (the ward's dome)
                put(i - 2, DOOR_Y + 8 - dy, HZ1 + 1, GOLD)       # reach 2.5 round the Shrine (the detail session's clearances)
    # the hall doors (the user, 2026-11-06: "there should be a place in the room to dig into"): an arch in each side wall between the
    # pillars, framed in carved stone under a gold keystone, sealed with rune-carved doors until a plan digs a hall through it (hold/HallSeal,
    # its FACING the way the hall runs, written in the template's own frame: SEAL_FACING)
    for sx in (-HX - 1, HX + 1):
        for z in range(SEAL_Z - 2, SEAL_Z + 3):
            for y in range(DOOR_Y, DOOR_Y + 4):
                put(sx, y, z, CARVE)
        for z in range(SEAL_Z - 1, SEAL_Z + 2):
            for y in range(DOOR_Y, DOOR_Y + 3):
                put(sx, y, z, SEAL_FACING[sx > 0])
        put(sx, DOOR_Y + 4, SEAL_Z, GOLD)
    for x, z in ((-3, 11), (3, 11), (-3, 15), (3, 15)):         # lanterns on chains from the roof
        put(x, HTOP, z, "minecraft:iron_chain")
        put(x, HTOP - 1, z, "minecraft:lantern[hanging=true]")

# ------------------------------------------------------------------ the ancestor faces, carved deep high on the towers


def face(x0, top):
    """An ancestor's face, 9 wide, carved 2-4 blocks deep: each row a run of blocks at a depth, so the helm's dome, its brim, the brow,
    the nose and the beard's braids stand out and cast shadow. z -2 is the relief's back, against the tower at -1."""
    c = x0 + 4

    def run(y, half, z0, z1, block):
        for x in range(c - half, c + half + 1):
            for z in range(z1, z0 + 1):
                put(x, y, z, block(x) if callable(block) else block)

    y = top
    run(y, 1, -2, -2, BRONZE), put(c, y, -3, GOLD)               # the dome's crown, the crest's tip
    y -= 1
    run(y, 2, -2, -3, BRONZE), put(c, y, -4, GOLD)               # the dome swelling, the crest down its middle
    y -= 1
    run(y, 3, -2, -3, BRONZE), run(y, 2, -4, -4, BRONZE_HI), put(c, y, -4, GOLD)
    y -= 1
    run(y, 4, -2, -3, lambda x: PATINA if abs(x - c) == 4 else BRONZE), run(y, 3, -4, -4, BRONZE_HI), put(c, y, -4, GOLD)
    y -= 1
    run(y, 4, -2, -4, lambda x: PATINA if abs(x - c) == 4 else BRONZE), put(c, y, -4, GOLD)
    y -= 1
    run(y, 4, -2, -5, BRONZE_CUT)                                # the brim: a ledge over the eyes
    y -= 1
    run(y, 3, -2, -3, TRIM)                                      # the brow, heavy
    put(c - 2, y, -4, TRIM), put(c + 2, y, -4, TRIM)
    y -= 1
    run(y, 3, -2, -2, SMOOTH)                                    # the eyes, deep under the brow, and the nose's bridge
    put(c - 2, y, -2, DARK), put(c + 2, y, -2, DARK)
    put(c, y, -3, TRIM), put(c, y, -4, TRIM)
    y -= 1
    run(y, 3, -2, -2, SMOOTH)                                    # the cheeks; the nose stands furthest out
    put(c, y, -3, TRIM), put(c, y, -4, TRIM), put(c, y, -5, TRIM)
    y -= 1
    run(y, 4, -2, -3, BEARD_LINE)                                # the moustache, sweeping out to the sides
    put(c - 4, y - 1, -3, BEARD_LINE), put(c + 4, y - 1, -3, BEARD_LINE)
    put(c, y, -4, BEARD_LINE)
    for i in range(5):                                           # the beard: a solid mass two deep, flaring, three braids out of it
        y -= 1
        half = 3 + (1 if i >= 1 else 0)
        run(y, half, -2, -3, BEARD)
        for bx in (-3, 0, 3):
            put(c + bx, y, -4, BEARD_LINE if i % 2 == 1 else BEARD)   # the braid, plaited every other block
    y -= 1
    run(y, 4, -2, -2, BEARD)
    for bx in (-3, 0, 3):                                        # the beads at the braids' tips: gold, bronze between
        put(c + bx, y, -4, GOLD)
        put(c + bx, y, -3, BRONZE_HI)


for x0 in (-GH, GH - TW + 1):
    face(x0, TOP - 2)
    for y in range(3, TOP - 18):                                 # the tower's face under the beard: two sunk panel lines
        put(x0 + 1, y, -2, TRIM)
        put(x0 + TW - 2, y, -2, TRIM)

# ------------------------------------------------------------------ the terrace, braziers, steps and the road

box(-15, 0, -11, 15, 1, -3, lambda x, y, z: TRIM if (x + z) % 7 == 0 else "minecraft:stone_bricks")
for x in range(-15, 16):
    put(x, 2, -11, "minecraft:stone_brick_wall" if abs(x) > 4 else "minecraft:air")
for z in range(-11, -2):
    put(-15, 2, z, "minecraft:stone_brick_wall")
    put(15, 2, z, "minecraft:stone_brick_wall")
for side in (-1, 1):                                             # braziers by the door: a bronze bowl of fire on a carved pillar
    bx = side * 5
    put(bx, 2, -6, CARVE)
    put(bx, 3, -6, BRONZE_CUT)
    put(bx, 4, -6, "minecraft:campfire[lit=true]")
for x in range(-4, 5):                                           # the steps up to it, the road's width
    put(x, 1, -12, "minecraft:stone_brick_stairs[facing=south]")
    put(x, 0, -13, "minecraft:stone_brick_stairs[facing=south]")
for z in range(-14 if RAISED else -34, -13):                     # the road, paved and walled low, lanterns on posts along it
    for x in range(-4, 5):
        put(x, -1, z, TRIM if (x * 3 + z) % 5 == 0 else "minecraft:stone_bricks")
    for wx in (-5, 5):
        put(wx, 0, z, "minecraft:stone_brick_wall")
        if z % 6 == 0:
            put(wx, 0, z, CARVE)
            put(wx, 1, z, "minecraft:stone_brick_wall")
            put(wx, 2, z, "minecraft:lantern")


# ------------------------------------------------------------------ the bronze guardians

def guardian(cx, side):
    """An ancestor guardian in bronze, 13 tall on a carved plinth before a tower, facing the road: standing broad and solemn in a long
    robe, both hands resting on the haft of a great hammer grounded before his feet; broad pauldrons, a beard to his belt, a horned helm
    with a brim over shadowed eyes; a small round shield on his outer arm; patina in the hollows."""
    pz = -7
    box(cx - 3, 2, pz - 4, cx + 3, 4, pz + 2, lambda x, y, z: CARVE if y == 4 and (x + z) % 2 == 0 else TRIM)   # the plinth
    by = 5
    box(cx - 2, by, pz - 1, cx + 2, by + 4, pz + 1, BRONZE)      # the long robe
    box(cx - 2, by, pz + 1, cx + 2, by + 8, pz + 1, PATINA)      # shadowed behind
    put(cx - 1, by, pz - 2, PATINA_CUT), put(cx + 1, by, pz - 2, PATINA_CUT)   # the boots' toes
    box(cx - 2, by + 5, pz - 1, cx + 2, by + 8, pz, BRONZE)      # the body
    box(cx - 3, by + 8, pz - 1, cx + 3, by + 8, pz + 1, BRONZE_HI)   # broad pauldrons
    box(cx - 1, by + 4, pz - 2, cx + 1, by + 8, pz - 2, BRONZE_CUT)  # the beard down his chest
    put(cx, by + 3, pz - 2, BRONZE_CUT)                          # its point
    for s in (-1, 1):                                            # his arms down his sides, to his hands on the haft
        box(cx + 3 * s, by + 5, pz - 1, cx + 3 * s, by + 7, pz, BRONZE)
        put(cx + 2 * s, by + 5, pz - 2, BRONZE)
        put(cx + s, by + 5, pz - 3, BRONZE_HI)
    for y in range(by + 1, by + 7):                              # the hammer's haft, upright before him
        put(cx, y, pz - 3, "minecraft:stripped_dark_oak_log")
    box(cx - 1, by, pz - 3, cx + 1, by, pz - 3, PATINA)          # its head on the ground, bronze
    put(cx, by, pz - 4, BRONZE_CUT)
    box(cx - 1, by + 9, pz - 1, cx + 1, by + 10, pz + 1, BRONZE)  # his head
    put(cx - 1, by + 9, pz - 2, BRONZE_CUT), put(cx + 1, by + 9, pz - 2, BRONZE_CUT)   # the moustache
    put(cx, by + 9, pz - 2, BRONZE_HI)                           # the nose
    put(cx - 1, by + 10, pz - 2, PATINA), put(cx + 1, by + 10, pz - 2, PATINA)       # the eyes, in the helm's shadow
    put(cx, by + 10, pz - 2, BRONZE)
    box(cx - 2, by + 11, pz - 2, cx + 2, by + 11, pz + 1, BRONZE_CUT)   # the helm's brim
    box(cx - 1, by + 12, pz - 1, cx + 1, by + 12, pz + 1, BRONZE_HI)    # its dome
    put(cx, by + 13, pz, GOLD)                                   # its crest
    for s in (-1, 1):                                            # the horns: out, then up
        put(cx + 3 * s, by + 11, pz, BRONZE_CUT)
        put(cx + 3 * s, by + 12, pz, BRONZE_CUT)
        put(cx + 3 * s, by + 13, pz, BRONZE_HI)
    ox = cx + 4 * side                                           # the shield on his outer arm: a small round one, a gold boss
    for dy, dz in ((5, 0), (6, -1), (6, 0), (6, 1), (7, 0)):
        put(ox, by + dy, pz + dz, BRONZE if (dy, dz) != (6, 0) else GOLD)


guardian(-11, -1)
guardian(11, 1)


FACING_TURN = {"south": "east", "north": "west", "east": "north", "west": "south"}   # local -> template: x' = z, z' = x


CRAG_OUT = os.path.join(HERE, "..", "src", "main", "resources", "data", "khazuk", "hold", "hold_gate_crag.json")


def turn(b):
    if b.startswith("khazuk:hall_seal"):
        return b                                                 # written in the template's frame already
    for k, v in FACING_TURN.items():
        if f"facing={k}" in b:
            return b.replace(f"facing={k}", f"facing={v}")
    return b


def template_cells():
    """The Miners' building, turned as the engine's templates are (x' = z + 14, z' = x + GH, y' = y + 1): the gatehouse, its terrace,
    the guardians and the passage, every cell of its box (air too) but the mountain's rock, which the god raises round it once it stands
    (crag_cells). The ground layer (y' 0) is paving before the gate, plain stone under it. The door at (0, 1, GH). Returns (cells, size)."""
    z0 = -14
    sx = 17 - z0 + 1
    sz = 2 * GH + 1
    sy = max(y for (x, y, z), b in B.items() if (x, y, z) not in ROCK and abs(x) <= GH) + 2
    cells = {}
    for x in range(sx):
        for z in range(sz):
            cells[(x, 0, z)] = "minecraft:stone_bricks" if x < 12 else "minecraft:stone"
            for y in range(1, sy):
                cells[(x, y, z)] = "minecraft:air"
    for (x, y, z), b in B.items():
        if y < -1 or (x, y, z) in ROCK or abs(x) > GH or z < z0 or z > 17:
            continue
        cells[(z - z0, y + 1, x + GH)] = turn(b)
    return cells, (sx, sy, sz)


def crag_cells():
    """The crag, in the template's frame (z' runs past the box either side), lowest first: [x', y', z', block]."""
    out = []
    for (x, y, z) in ROCK:
        if y >= -1:
            out.append([z + 14, y + 1, x + GH, B[(x, y, z)]])
    out.sort(key=lambda c: (c[1], c[0], c[2]))
    return out


def write_crag():
    cells = crag_cells()
    palette = sorted({c[3] for c in cells})
    idx = {b: i for i, b in enumerate(palette)}
    os.makedirs(os.path.dirname(CRAG_OUT), exist_ok=True)
    with open(CRAG_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"palette": palette, "cells": [[x, y, z, idx[b]] for x, y, z, b in cells]}, f, separators=(",", ":"))
    print(f"crag: {len(cells)} cells -> {os.path.relpath(CRAG_OUT)}")


TEMPLATE_OUT = os.path.join(HERE, "..", "src", "main", "resources", "data", "khazuk", "structure", "room", "dawi_hold_gate.nbt")
DATA_VERSION = 4790                                              # 26.1.2's, as the bridge's structure_save writes it


def _state(name, props):
    return name + ("[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]" if props else "")


def _split(s):
    if "[" not in s:
        return s, {}
    name, rest = s.split("[", 1)
    return name, dict(kv.split("=") for kv in rest.rstrip("]").split(","))


GILDING = {"minecraft:gold_block": "khazuk:gilded_stone", "minecraft:raw_gold_block": "khazuk:hoard_gold"}


def write_template(cells, size):
    """The gatehouse's template straight to its .nbt (no game needed), every cell of its box. A cell whose block the shipped template
    already has, its properties the game's full set of what this script names, keeps the shipped state (the game's own wall and stair
    shapes), so a change here rewrites only what it changes."""
    sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "structgen"))
    import nbtio

    def val(t):
        return t.value if hasattr(t, "value") else t

    had = {}
    if os.path.exists(TEMPLATE_OUT):
        root = val(nbtio.read(TEMPLATE_OUT))
        pal = []
        for e in val(root["palette"]):
            e = val(e)
            props = {k: val(v) for k, v in val(e["Properties"]).items()} if "Properties" in e else {}
            pal.append((val(e["Name"]), props))
        for b in val(root["blocks"]):
            b = val(b)
            had[tuple(val(i) for i in val(b["pos"]))] = pal[val(b["state"])]
    out, kept = {}, 0
    for pos, s in cells.items():
        name, props = _split(s)
        if ":" not in name:
            name = "minecraft:" + name
        old = had.get(pos)
        if old and old[0] == name and all(old[1].get(k) == v for k, v in props.items()):
            out[pos] = old
            kept += 1
        else:
            out[pos] = (name, props)
    order = sorted(out, key=lambda p: (p[1], p[2], p[0]))
    palette, index, blocks = [], {}, []
    for p in order:
        name, props = out[p]
        key = _state(name, props)
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": nbtio.String(GILDING.get(name, name))}   # the gate's gold is Khazuk!'s gilding, not money (the bug hunt of 2d0b04c)
            if props:
                entry["Properties"] = nbtio.Compound({k: nbtio.String(v) for k, v in sorted(props.items())})
            palette.append(nbtio.Compound(entry))
        blocks.append(nbtio.Compound({"pos": nbtio.List([nbtio.Int(p[0]), nbtio.Int(p[1]), nbtio.Int(p[2])]), "state": nbtio.Int(index[key])}))
    root = nbtio.Compound({
        "DataVersion": nbtio.Int(DATA_VERSION),
        "size": nbtio.List([nbtio.Int(size[0]), nbtio.Int(size[1]), nbtio.Int(size[2])]),
        "palette": nbtio.List(palette, nbtio.COMPOUND),
        "blocks": nbtio.List(blocks, nbtio.COMPOUND),
        "entities": nbtio.List([], nbtio.COMPOUND),
    })
    nbtio.write(TEMPLATE_OUT, root)
    print(f"template -> {os.path.relpath(TEMPLATE_OUT)}: {len(out)} cells, {kept} kept as shipped, {len(out) - kept} new")


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
    if RAISED:
        cells, size = template_cells()
        hx, hy, hz = HEART_LOCAL
        print(f"template {size}, door at (0, 1, {GH}), heart station at ({hz + 14}, {hy + 1}, {hx + GH})")
        write_crag()
        if "--write" in sys.argv:
            write_template(cells, size)
        if "--at" in sys.argv:
            i = sys.argv.index("--at")
            o = [int(v) for v in sys.argv[i + 1:i + 4]]
            sys.path.insert(0, os.path.join(HERE, "..", "..", "devbridge", "tools"))
            import devbridge
            r = devbridge.call("build", ops=[{"op": "blocks", "origin": o, "blocks": [[x, y, z, b] for (x, y, z), b in sorted(cells.items())]}])
            print(json.dumps(r)[:200], "size", size)
        return
    if len(sys.argv) >= 5 and sys.argv[1] == "--build":
        sys.path.insert(0, os.path.join(HERE, "..", "..", "devbridge", "tools"))
        import devbridge
        o = [int(v) for v in sys.argv[2:5]]
        r = devbridge.call("build", ops=ops(o))
        print(json.dumps(r)[:300])


if __name__ == "__main__":
    main()
