"""The hold's dug halls: D1's first ones behind the Hold Gate (the Great Hall, the barracks and the mine) and D3's (the brewery and the
treasury), as room templates the engine's dig side digs out (Works#dig): the Miners cut the rock and set the hall's own stone, pillars,
lamps and tables as they go.

Every template is in the engine's frame: x away from the door (the door on the x = 0 face, at y 1), y up (y 0 the floor), z across.
Each hall is lined in dressed stone (its walls, floor and vault are part of it), so it reads as a dwarf hall whatever rock it is cut in.

    python tools/gen_halls.py                       the block maps and their sizes
    python tools/gen_halls.py --at x y z            and build each in the running bridge game, side by side from x y z, for saving
                              [--only a,b]          (only those halls)
    python tools/gen_halls.py --write dawi_brewery dawi_treasury
                                                    write those templates straight to data/khazuk/structure/room (no game needed: every
                                                    cell of the box in 26.1's data version, as the bridge's structure_save writes it)
"""
import json, os, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))

AIR = "minecraft:air"
WALL = "minecraft:deepslate_bricks"
WALL_B = "minecraft:cracked_deepslate_bricks"
TILE = "minecraft:deepslate_tiles"
POL = "minecraft:polished_deepslate"
CHIS = "minecraft:chiseled_deepslate"
TRIM = "minecraft:polished_andesite"
CARVE = "minecraft:chiseled_stone_bricks"
SB = "minecraft:stone_bricks"
GOLD = "minecraft:gold_block"
BRONZE = "minecraft:waxed_exposed_copper"
TUFF = "minecraft:chiseled_tuff_bricks"
TUFF_P = "minecraft:polished_tuff"
BEAM_X = "minecraft:stripped_dark_oak_log[axis=x]"
BEAM_Z = "minecraft:stripped_dark_oak_log[axis=z]"
PLANK = "minecraft:dark_oak_planks"
LANTERN = "minecraft:lantern[hanging=true]"
CHAIN = "minecraft:iron_chain[axis=y]"
FIRE = "minecraft:campfire[lit=true]"
DARK = "minecraft:polished_blackstone"


class Hall:
    def __init__(self, sx, sy, sz, door_z, door_w, door_h, seed):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.door_z, self.door_w, self.door_h = door_z, door_w, door_h
        self.r = random.Random(seed)
        self.b = {}
        self.stations = []
        for x in range(sx):
            for y in range(sy):
                for z in range(sz):
                    self.b[(x, y, z)] = AIR

    def put(self, x, y, z, s):
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.b[(x, y, z)] = s

    def box(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, s(x, y, z) if callable(s) else s)

    def wall(self, x, y, z):
        """Dressed deepslate in courses, every fourth course a band of polished andesite, a few bricks cracked."""
        if y % 4 == 0:
            return TRIM
        return WALL_B if self.r.random() < 0.08 else WALL

    def shell(self):
        """Floor, four walls and the vault, the doorway open in the x = 0 face with a stepped frame round it."""
        sx, sy, sz = self.sx, self.sy, self.sz
        self.box(0, 0, 0, sx - 1, 0, sz - 1, lambda x, y, z: TILE if (x + z) % 2 else POL)
        for x in range(sx):
            for y in range(1, sy):
                self.put(x, y, 0, self.wall(x, y, 0))
                self.put(x, y, sz - 1, self.wall(x, y, sz - 1))
        for z in range(sz):
            for y in range(1, sy):
                self.put(0, y, z, self.wall(0, y, z))
                self.put(sx - 1, y, z, self.wall(sx - 1, y, z))
        self.box(0, sy - 1, 0, sx - 1, sy - 1, sz - 1, TILE)       # the vault
        h = self.door_w // 2
        d = self.door_z
        self.box(0, 1, d - h, 0, self.door_h, d + h, AIR)
        for y in range(1, self.door_h + 2):                          # the frame: polished pillars either side, a carved lintel
            self.put(1, y, d - h - 1, CHIS if y % 3 == 0 else POL)
            self.put(1, y, d + h + 1, CHIS if y % 3 == 0 else POL)
        self.box(1, self.door_h + 1, d - h - 1, 1, self.door_h + 1, d + h + 1, CARVE)
        self.put(1, self.door_h + 1, d, GOLD)

    def lamp(self, x, z, drop):
        """A lantern hung from the vault on a chain {@code drop} long."""
        top = self.sy - 2
        for i in range(drop):
            self.put(x, top - i, z, CHAIN)
        self.put(x, top - drop, z, LANTERN)

    def pillar(self, x, z, top):
        """A square pillar 2 x 2, polished at its foot, a gold band at two thirds, a carved capital."""
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(1, top + 1):
                    s = WALL
                    if y == 1:
                        s = POL
                    elif y == top:
                        s = CARVE
                    elif y == (2 * top) // 3:
                        s = GOLD if (dx + dz) % 2 == 0 else BRONZE
                    elif y % 4 == 0:
                        s = TRIM
                    self.put(x + dx, y, z + dz, s)

    def face(self, x, z, y0):
        """An ancestor's face carved into the back wall (x the wall's inner face, z its middle): a bronze helm, a stone brow and
        nose, a calcite beard in braids ending in gold beads."""
        for dz in range(-3, 4):
            self.put(x, y0 + 7, z + dz, BRONZE)                          # the helm's brim
            if abs(dz) <= 2:
                self.put(x, y0 + 8, z + dz, BRONZE)
        self.put(x, y0 + 9, z, GOLD)                                     # its crest
        for dz in (-2, -1, 1, 2):
            self.put(x, y0 + 6, z + dz, CARVE)                           # the brow
        self.put(x, y0 + 5, z - 1, DARK)                                 # the eyes in shadow
        self.put(x, y0 + 5, z + 1, DARK)
        self.put(x, y0 + 5, z, SB)                                       # the nose
        self.put(x, y0 + 4, z, SB)
        for dz in range(-3, 4):
            self.put(x, y0 + 3, z + dz, "minecraft:calcite")             # the moustache
        for dz in range(-2, 3):
            for y in range(y0, y0 + 3):
                self.put(x, y, z + dz, "minecraft:calcite" if (dz + y) % 2 else "minecraft:polished_diorite")
        for dz in (-2, 0, 2):
            self.put(x, y0 - 1, z + dz, GOLD)                            # the beads

    def cells(self):
        return [[x, y, z, s] for (x, y, z), s in sorted(self.b.items())]


def great_hall_v1():
    """The first Great Hall (0.2.0 to 0.12.3), 27 long, 13 high, 21 wide: two rows of pillars, beams across under the vault on them, a long
    hearth down the middle with tables and benches either side, lanterns on chains, and at the far end a dais under an ancestor's face,
    with a seat of gold for the god. Kept for the record; the user, 2026-11-04: "the great hall doesnt look that great does it" (it read
    as a tavern). great_hall() below replaced it."""
    h = Hall(27, 13, 21, 10, 5, 7, 7)
    h.shell()
    for x in range(5, 23, 5):                                            # two rows of pillars
        h.pillar(x, 4, 11)
        h.pillar(x, 15, 11)
        for z in range(1, 20):                                           # a beam across the hall on each pair
            h.put(x, 11, z, BEAM_Z)
            h.put(x + 1, 11, z, BEAM_Z)
    for z in (4, 16):
        for x in range(1, 26):                                           # and along it over the pillars
            if h.b[(x, 11, z)] == AIR:
                h.put(x, 11, z, BEAM_X)
    for x in range(7, 21):                                               # the hearth: a stone trench, its fires every third block
        h.put(x, 0, 9, DARK)
        h.put(x, 0, 11, DARK)
        h.put(x, 0, 10, FIRE if x % 3 == 0 else "minecraft:ochre_froglight")     # the fires sunk in the floor
        h.put(x, 1, 9, "minecraft:polished_blackstone_slab[type=bottom]")
        h.put(x, 1, 11, "minecraft:polished_blackstone_slab[type=bottom]")
        h.put(x, 1, 10, "minecraft:iron_bars[east=true,west=true]")                # a grate over them: nobody steps in
    for z, bench in ((7, "minecraft:spruce_stairs[facing=north]"), (13, "minecraft:spruce_stairs[facing=south]")):
        for x in range(7, 21):                                           # a long table either side of the hearth, benches beside
            h.put(x, 1, z, "minecraft:dark_oak_fence" if x in (7, 13, 20) else AIR)
            h.put(x, 2, z, "minecraft:dark_oak_slab[type=top]")
            bz = z - 1 if z == 7 else z + 1
            h.put(x, 1, bz, bench)
        for x in range(8, 20, 3):
            h.put(x, 3, z, "minecraft:lantern")                          # candles on the board: lanterns
    for x in range(7, 22, 5):                                            # lanterns down the hall between the beams
        h.lamp(x + 2, 7, 3)
        h.lamp(x + 2, 13, 3)
    # the dais at the far end, two steps up, the god's gold seat under an ancestor's face
    h.box(22, 1, 5, 25, 1, 15, POL)
    h.box(23, 2, 7, 25, 2, 13, CHIS)
    for z in range(5, 16):
        h.put(21, 1, z, "minecraft:polished_deepslate_stairs[facing=west]")
        if 7 <= z <= 13:
            h.put(22, 2, z, "minecraft:polished_deepslate_stairs[facing=west]")
    h.put(24, 3, 10, "minecraft:gold_block")
    h.put(24, 4, 10, "minecraft:gold_block")
    h.put(23, 3, 10, "minecraft:polished_blackstone_stairs[facing=west]")
    h.put(24, 3, 9, "minecraft:gold_block")
    h.put(24, 3, 11, "minecraft:gold_block")
    h.face(25, 10, 5)
    for z in (6, 14):                                                    # braziers either side of the seat, on the dais
        h.put(23, 2, z, FIRE)
    for y in range(2, 10):                                               # banners of runes on the side walls: tuff panels
        for x in range(8, 20, 6):
            for z in (1, 19):
                h.put(x, y, z, TUFF if y % 2 else TUFF_P)
    h.stations = []
    return h


def barracks():
    """The barracks, 17 long, 8 high, 13 wide: bunks two high along both walls, a weapon wall with stonecutters, grindstones and a smithing
    table, barrels of kit, a brazier in the middle and lanterns on chains."""
    h = Hall(17, 8, 13, 6, 3, 4, 11)
    h.shell()
    for x in range(3, 15, 3):                                            # bunks: a frame of dark oak, red wool for the bedding, two high
        for z, side in ((1, 1), (11, -1)):
            for y in (1, 4):
                h.put(x, y, z, "minecraft:dark_oak_slab[type=top]" if y == 4 else "minecraft:dark_oak_planks")
                h.put(x + 1, y, z, "minecraft:dark_oak_slab[type=top]" if y == 4 else "minecraft:dark_oak_planks")
                h.put(x, y + 1, z, "minecraft:red_wool" if y == 1 else "minecraft:red_carpet")
                h.put(x + 1, y + 1, z, "minecraft:red_wool" if y == 1 else "minecraft:red_carpet")
            h.put(x - 1, 1, z, "minecraft:dark_oak_fence")
            h.put(x - 1, 2, z, "minecraft:dark_oak_fence")
            h.put(x - 1, 3, z, "minecraft:dark_oak_fence")
    for z in range(2, 11):                                               # the weapon wall at the far end
        h.put(15, 1, z, ["minecraft:stonecutter[facing=west]", "minecraft:grindstone[face=floor,facing=north]", "minecraft:smithing_table",
                         "minecraft:barrel[facing=up]"][z % 4])
        h.put(15, 3, z, BRONZE if z % 2 else GOLD)
    h.put(8, 0, 6, FIRE)                                                 # the brazier sunk in the floor, a grate over it
    h.put(8, 1, 6, "minecraft:iron_bars")
    h.lamp(5, 6, 2)
    h.lamp(11, 6, 2)
    return h


def mine():
    """The mine, 15 long, 8 high, 15 wide: timber props and beams, rails to the ore cart, the Ore Cart and the Stone Bin (the engine's
    Ore Mine's barrels), and three drift mouths in the walls (its "drift" stations) where the Miners drive their drifts out after ore."""
    h = Hall(15, 8, 15, 7, 3, 4, 13)
    h.shell()
    for x in (4, 10):                                                    # timber props and the beams on them
        for z in (2, 12):
            for y in range(1, 6):
                h.put(x, y, z, "minecraft:stripped_spruce_log[axis=y]")
        for z in range(1, 14):
            h.put(x, 6, z, "minecraft:stripped_spruce_log[axis=z]")
    for x in range(1, 12):                                               # rails down the middle to the carts
        h.put(x, 1, 7, "minecraft:rail[shape=east_west]")
    h.put(12, 1, 6, 'minecraft:barrel[facing=up]{CustomName:"Ore Cart"}')
    h.put(12, 1, 8, 'minecraft:barrel[facing=up]{CustomName:"Ore Cart"}')
    h.put(12, 1, 5, 'minecraft:barrel[facing=up]{CustomName:"Stone Bin"}')
    h.put(12, 1, 9, 'minecraft:barrel[facing=up]{CustomName:"Stone Bin"}')
    h.put(12, 1, 7, "minecraft:lantern")
    h.lamp(7, 4, 1)
    h.lamp(7, 10, 1)
    # the drift mouths: an opening 1 wide, 2 high in the far wall and each side wall, a station where each drift begins, running outward
    for (x, z, facing) in ((14, 7, "east"), (7, 0, "north"), (7, 14, "south")):
        h.put(x, 1, z, AIR)
        h.put(x, 2, z, AIR)
        h.stations.append(("drift", x, 1, z, facing))
    return h


def brewery():
    """The brewery (D3), 15 long, 8 high, 13 wide: two copper mash tuns over fires, a still of copper with its pipe up into the vault,
    racks of barrels three high along both walls, sacks of grain (hay) by the door, a long tasting table, lanterns on chains. The hold
    brews its ale here from the wheat its god brings to the Shrine."""
    h = Hall(15, 8, 13, 6, 3, 4, 17)
    h.shell()
    for z in (1, 11):                                                    # barrel racks along both walls, three high, oak frames between
        for x in range(2, 13):
            if x % 4 == 1:
                for y in range(1, 4):
                    h.put(x, y, z, "minecraft:stripped_spruce_log[axis=y]")
            else:
                for y in range(1, 4):
                    h.put(x, y, z, "minecraft:barrel[facing=" + ("south" if z == 1 else "north") + "]")
        for x in range(2, 13):
            h.put(x, 4, z, "minecraft:spruce_slab[type=bottom]")
    for (x, z) in ((10, 4), (10, 8)):                                    # the mash tuns: cauldrons of water over fires in a copper ring
        h.put(x, 0, z, FIRE)
        h.put(x, 1, z, "minecraft:water_cauldron[level=3]")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            h.put(x + dx, 1, z + dz, "minecraft:waxed_cut_copper_stairs[facing=" + {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}[(dx, dz)] + ",half=bottom]")
    for y in range(1, 4):                                                # the still at the far end: a copper column, its pipe to the vault
        h.put(13, y, 6, "minecraft:waxed_copper_block" if y < 3 else "minecraft:waxed_exposed_copper")
    for y in range(4, 7):
        h.put(13, y, 6, "minecraft:lightning_rod[facing=up]")
    h.put(12, 1, 6, "minecraft:brewing_stand")
    for z in range(4, 9):                                                # the tasting table down the middle, stools by it
        h.put(6, 1, z, "minecraft:dark_oak_slab[type=top]")
        h.put(5, 1, z, "minecraft:dark_oak_trapdoor[facing=east,half=bottom,open=false]" if z % 2 == 0 else AIR)
    h.put(6, 2, 5, "minecraft:decorated_pot")
    h.put(6, 2, 7, "minecraft:flower_pot")
    for (x, z) in ((2, 3), (2, 9), (3, 3)):                              # sacks of grain by the door
        h.put(x, 1, z, "minecraft:hay_block[axis=y]")
    h.put(2, 2, 3, "minecraft:hay_block[axis=x]")
    h.lamp(4, 6, 2)
    h.lamp(10, 6, 2)
    return h


def treasury():
    """The treasury (D3), 13 long, 8 high, 11 wide: the hold's hoard. Heaps of gold block and raw gold along the walls, a gold-banded
    vault, a strong door's iron bars either side of the doorway, an ancestor's face over a gold dais at the far end, and a strongbox
    (a barrel bound in iron) on it. With one dug, the hold gets more gold out of every ore and its hoard grows."""
    h = Hall(13, 8, 11, 5, 3, 4, 19)
    h.shell()
    for x in range(1, 12):                                               # a gold band round the walls at head height
        for z in (0, 10):
            h.put(x, 4, z, GOLD)
    for z in range(1, 10):
        h.put(12, 4, z, GOLD)
    for y in range(1, 4):                                                # iron bars either side of the doorway: the strong door
        h.put(1, y, 3, "minecraft:iron_bars")
        h.put(1, y, 7, "minecraft:iron_bars")
    heaps = [(3, 1, 2), (4, 1, 1), (5, 1, 1), (3, 1, 9), (4, 1, 9), (7, 1, 1), (8, 1, 1), (8, 1, 9), (9, 1, 9), (7, 1, 9)]
    for i, (x, y, z) in enumerate(heaps):                                # the heaps: gold block piled two high, raw gold on top
        h.put(x, y, z, GOLD)
        if i % 2 == 0:
            h.put(x, y + 1, z, "minecraft:raw_gold_block")
    for z in range(3, 8):                                                # the dais at the far end, gold-edged
        for x in range(9, 12):
            h.put(x, 1, z, CHIS if x == 9 else POL)
        h.put(9, 2, z, "minecraft:gold_block" if z in (3, 7) else AIR)
    h.put(10, 2, 5, "minecraft:barrel[facing=up]")                       # the strongbox
    h.put(10, 3, 5, "minecraft:heavy_weighted_pressure_plate")
    h.put(10, 2, 4, "minecraft:gold_block")
    h.put(10, 2, 6, "minecraft:gold_block")
    h.face(11, 5, 2)
    h.lamp(4, 5, 2)
    h.lamp(8, 5, 2)
    return h


def runic_forge():
    """The Runic Forge (D5), 15 long, 9 high, 13 wide: the Runesmith's hall. The rune anvil (its "anvil" station, where runes are struck)
    on a polished dais in the middle, a ring of soul fire round it; the forge at the far end, lava behind iron bars under a copper hood,
    blast furnaces either side; a quench trough of water; runes cut into the walls, lit blue (soul lanterns in chiselled stone); racks of
    hammers and ingots. Its station: the anvil at (9, 1, 6), struck from the door's side."""
    h = Hall(15, 9, 13, 6, 3, 4, 23)
    h.shell()
    for z in range(3, 10):                                               # the forge at the far end: lava in a stone hearth behind bars
        h.put(13, 1, z, "minecraft:blast_furnace[facing=west,lit=true]" if z in (3, 9) else "minecraft:lava_cauldron")
        h.put(13, 2, z, "minecraft:iron_bars" if 4 <= z <= 8 else CHIS)
        h.put(13, 3, z, "minecraft:waxed_cut_copper")
        h.put(12, 4, z, "minecraft:waxed_cut_copper_slab[type=top]")
    for y in range(4, 8):                                                # its chimney up the vault
        h.put(13, y, 6, "minecraft:waxed_copper_block" if y < 7 else "minecraft:waxed_exposed_copper")
    for x in range(8, 11):                                               # the anvil's dais, polished, gold at its corners
        for z in range(5, 8):
            h.put(x, 0, z, POL)
    for (x, z) in ((8, 5), (8, 7), (10, 5), (10, 7)):
        h.put(x, 0, z, GOLD)
    h.put(9, 1, 6, "minecraft:anvil[facing=north]")                     # the rune anvil: its station
    h.stations.append(("anvil", 9, 1, 6, "west"))
    # the Runesmith's work spot, a step up before the anvil: on it a Dwarf's hammer comes down square on the anvil's face (the detail
    # session's handoff, handoffs/runesmith_runelord_2026-11-03_done.md: the anvil's top, 1 block up, is over his shoulder)
    h.put(8, 1, 6, "minecraft:polished_deepslate_slab[type=bottom]")
    for (x, z) in ((7, 4), (7, 8), (11, 4), (11, 8)):                    # soul fire round it, blue: the runes' own light
        h.put(x, 0, z, "minecraft:soul_campfire[lit=true]")
        h.put(x, 1, z, "minecraft:iron_bars")
    for x in range(3, 7):                                                # the quench trough along one wall, racks of ingots along the other
        h.put(x, 1, 1, "minecraft:water_cauldron[level=3]")
        h.put(x, 1, 11, "minecraft:iron_block" if x % 2 else "minecraft:gold_block")
        h.put(x, 2, 11, "minecraft:iron_bars")
    for x in range(3, 12, 2):                                            # runes cut into both long walls, lit blue
        for z in (0, 12):
            h.put(x, 3, z, "minecraft:chiseled_deepslate")
            h.put(x, 4, z, "minecraft:chiseled_deepslate")
    for x in range(3, 12, 4):                                            # soul lanterns hung from the vault along both walls
        for z in (2, 10):
            h.put(x, 7, z, CHAIN)
            h.put(x, 6, z, CHAIN)
            h.put(x, 5, z, "minecraft:soul_lantern[hanging=true]")
    h.put(2, 1, 3, "minecraft:smithing_table")
    h.put(2, 1, 9, "minecraft:grindstone[face=floor,facing=east]")
    h.lamp(5, 6, 2)
    return h


# The grand halls (2026-11-04): the Great Hall rebuilt as a karak's hall (Karaz-a-Karak, TWW3's Dwarf settlements) and the King's
# Throne Room, which replaces the surface Keep. Their own pieces below; the halls above don't use them, so their templates don't change.

CALC = "minecraft:calcite"
DIOR = "minecraft:polished_diorite"
HAFT = "minecraft:stripped_dark_oak_log[axis=y]"
DARK_B = "minecraft:polished_blackstone_bricks"
CHIS_B = "minecraft:chiseled_polished_blackstone"
CU = "minecraft:waxed_cut_copper"
CU_X = "minecraft:waxed_exposed_cut_copper"
POST = "minecraft:polished_blackstone_wall"


def stair(block, facing, half="bottom"):
    """A stair of {@code block} (the name before _stairs) rising toward {@code facing}; half top hangs it under a ceiling."""
    return f"minecraft:{block}_stairs[facing={facing},half={half}]"


# An ancestor king carved 3 wide, row by row from his feet up (each row the same read left to right or right to left), in pale stone
# so he stands out of the dark deepslate round him (carved in the hall's own stone, he didn't read as a figure at all)
KING_ROWS = {
    "plinth": (CHIS, GOLD, CHIS),
    "feet": (SB, DARK, SB),
    "robe": (TRIM, HAFT, TRIM),              # his hammer's haft down the middle, planted before him
    "hands": (BRONZE, GOLD, BRONZE),         # gauntlets folded on the hammer's gold head
    "tip": (TRIM, CALC, TRIM),               # the beard's point
    "beard": (CALC, CALC, CALC),
    "moustache": (DIOR, CALC, DIOR),
    "eyes": (DARK, SB, DARK),                # the eyes in shadow under the helm, the nose between
    "brow": (BRONZE, BRONZE, BRONZE),        # the helm's brim
    "crown": (GOLD, GOLD, GOLD),
    "helm": (BRONZE, GOLD, BRONZE),
}
KING_11 = ["plinth", "feet", "robe", "robe", "hands", "tip", "beard", "moustache", "eyes", "brow", "crown"]
KING_9 = ["plinth", "feet", "robe", "hands", "tip", "beard", "moustache", "eyes", "helm"]
# the same king's sides and back, row by row: robe, belt, mail, a stone head, the helm
KING_SIDE = {"plinth": CHIS, "feet": SB, "robe": TRIM, "hands": BRONZE, "tip": SB, "beard": SB, "moustache": SB, "eyes": SB,
             "brow": BRONZE, "crown": BRONZE, "helm": BRONZE}

# runes cut into the walls, 3 wide and 5 high, top row first: gold in black stone (after the Khazalid runes' straight strokes)
RUNES = [
    ["#.#", "#.#", ".#.", "#.#", "#.#"],
    ["##.", "#.#", "##.", "#.#", "#.#"],
    [".#.", "#.#", ".#.", ".#.", ".#."],
    ["#.#", "###", "#.#", "#.#", "#.#"],
    ["#..", "##.", "#.#", "##.", "#.."],
    [".#.", "#.#", "#.#", "#.#", ".#."],
]


def king(h, face, rows, y0=1):
    """An ancestor king carved into a face: {@code face} the face's three (x, z) cells, {@code rows} KING_11 or KING_9 from y0 up."""
    for j, row in enumerate(rows):
        for (x, z), s in zip(face, KING_ROWS[row]):
            h.put(x, y0 + j, z, s)


def rune(h, cells, y0, i):
    """Rune {@code i} cut into a wall: {@code cells} its three (x, z) columns left to right, its foot at y0."""
    for j, line in enumerate(RUNES[i % len(RUNES)]):
        for (x, z), c in zip(cells, line):
            h.put(x, y0 + 4 - j, z, GOLD if c == "#" else DARK)


def hang(h, x, z, top, drop, lantern=LANTERN):
    """A lantern on a chain {@code drop} long, the chain's top link at y {@code top} (the open cell under the ceiling)."""
    for i in range(drop):
        h.put(x, top - i, z, CHAIN)
    h.put(x, top - drop, z, lantern)


def chandelier(h, x, z, top, drop):
    """A copper wheel on a chain: a hub and four spokes {@code drop} under the ceiling, a lantern hung under each."""
    for i in range(drop):
        h.put(x, top - i, z, CHAIN)
    y = top - drop
    h.put(x, y, z, CU)
    h.put(x, y - 1, z, LANTERN)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        h.put(x + dx, y, z + dz, CU_X)
        h.put(x + dx, y - 1, z + dz, LANTERN)


def brazier(h, x, y, z):
    """A brazier: a fire on a blackstone post."""
    h.put(x, y, z, POST)
    h.put(x, y + 1, z, FIRE)


def vault(h, top, x0, x1, z0, z1, centre):
    """Fill every column (x, z) of the box from above its open top ({@code top(x, z)}) to the vault with tile, and step the edges:
    where the column toward the middle ({@code centre}, in z) stands higher, the ceiling's lip is an upside-down stair."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t = top(x, z)
            for y in range(t + 1, h.sy - 1):
                h.put(x, y, z, TILE)
            if z != centre:
                inner = z + 1 if z < centre else z - 1
                if top(x, inner) > t and t + 1 < h.sy - 1:
                    h.put(x, t + 1, z, stair("polished_deepslate", "north" if z < centre else "south", "top"))


def great_face(h, x, z, y0):
    """The ancestor's great face on a hall's end wall ({@code x} the wall, {@code z} its middle, {@code y0} the beard's foot): a beard 7
    wide in calcite braids ending in gold beads, a drooping moustache, a heavy brow, a bronze helm with a gold rune, crest and wings."""
    for dz in (-4, 0, 4):
        h.put(x, y0 - 1, z + dz, GOLD)                                   # the beads
    for j, w in enumerate((1, 2, 3, 3, 3)):                              # the beard, widening up to the moustache
        for dz in range(-w, w + 1):
            h.put(x, y0 + j, z + dz, CALC if (dz + j) % 2 else DIOR)
    for dz in (-4, 4):                                                   # the braids at its sides, banded in gold
        for y in range(y0, y0 + 4):
            h.put(x, y, z + dz, GOLD if y == y0 + 1 else CALC)
    for dz in range(-4, 5):
        h.put(x, y0 + 5, z + dz, CALC)                                   # the moustache, its ends drooping
    for dz in (-5, 5):
        h.put(x, y0 + 4, z + dz, CALC)
        h.put(x, y0 + 3, z + dz, DIOR)
    for dz in range(-3, 4):
        for y in (y0 + 6, y0 + 7):
            h.put(x, y, z + dz, SB)                                      # the face
    h.put(x, y0 + 7, z - 2, DARK)                                        # the eyes in shadow
    h.put(x, y0 + 7, z + 2, DARK)
    h.put(x, y0 + 6, z, TRIM)                                            # the nose, standing out of the wall
    h.put(x, y0 + 7, z, TRIM)
    h.put(x - 1, y0 + 6, z, SB)
    for dz in range(-4, 5):
        h.put(x, y0 + 8, z + dz, CARVE)                                  # the brow
        h.put(x, y0 + 10, z + dz, BRONZE)                                # the helm
    for dz in range(-5, 6):
        h.put(x, y0 + 9, z + dz, BRONZE)                                 # its brim
    h.put(x, y0 + 10, z, GOLD)                                           # the rune on it
    for dz in (-1, 0, 1):
        h.put(x, y0 + 11, z + dz, GOLD)                                  # the crest
    h.put(x, y0 + 12, z, GOLD)
    for s in (-1, 1):                                                    # the wings
        h.put(x, y0 + 10, z + 5 * s, GOLD)
        h.put(x, y0 + 11, z + 6 * s, GOLD)
        h.put(x, y0 + 12, z + 6 * s, GOLD)


def statue(h, x0, z0, rows, y0):
    """A free-standing ancestor king 3 x 3 facing west (his face on x = x0), on a plinth to y0 - 1, with bronze pauldrons out to
    either side at his beard's height and a step up to him."""
    for y in range(1, y0):
        for dx in range(-1, 3):
            for dz in range(3):
                h.put(x0 + dx, y, z0 + dz, GOLD if (dx == -1 and dz == 1 and y == y0 - 1) else (CHIS if y == y0 - 1 else POL))
    for dz in range(3):
        h.put(x0 - 2, 1, z0 + dz, stair("polished_deepslate", "east"))
    for j, row in enumerate(rows):
        y = y0 + j
        for dx in range(3):
            for dz in range(3):
                h.put(x0 + dx, y, z0 + dz, KING_SIDE[row])
        if row in ("tip", "beard"):
            for dx in (0, 1):
                h.put(x0 + dx, y, z0 - 1, BRONZE)
                h.put(x0 + dx, y, z0 + 3, BRONZE)
    king(h, [(x0, z0 + dz) for dz in range(3)], rows, y0)


def great_hall():
    """The Great Hall (2026-11-04, rebuilt grand), 33 long, 17 high, 25 wide: a karak's hall, a nave under a stepped vault between two
    arcades of great pillars, each pillar an ancestor king carved in its face (bronze-helmed, calcite-bearded, gold-crowned, his hammer
    planted before him), banded in gold and bronze, a rune frieze over the arches; low aisles behind them, their walls cut with gold runes
    and hung with banners; a long hearth down the nave between two long tables and their benches, under copper chandeliers and red
    banners hung from the vault; and at the far end a high bay, a two-step dais with the god's high seat under the ancestor's great face,
    two ancestor kings standing in its corners, braziers before them."""
    sx, sy, sz, C = 33, 17, 25, 12
    h = Hall(sx, sy, sz, C, 5, 6, 29)
    h.shell()
    PX = (4, 12, 20)                                                     # the pillars' near x; each 3 x 3, arches 5 wide between

    def top(x, z):
        """The highest open cell of column (x, z): the aisles low, the arcades higher, the nave stepped up to the vault, the bay high."""
        if x >= 26:
            return 12 + min(min(z - 1, sz - 2 - z), 3)
        if z <= 3 or z >= 21:
            return 9
        if z <= 6 or z >= 18:
            return 11
        return 13 + min(min(z - 7, 17 - z), 2)

    vault(h, top, 1, sx - 2, 1, sz - 2, C)
    # the floor: the nave in polished stone edged in blackstone, a line of black and gold across it at every pillar pair
    for x in range(1, 26):
        for z in range(7, 18):
            h.put(x, 0, z, DARK_B if z in (7, 17) else POL)
    for px in PX:
        for z in range(7, 18):
            h.put(px + 1, 0, z, GOLD if z in (7, 12, 17) else DARK)
    # the pillars: 3 x 3, a carved base, gold and bronze bands, a carved capital; before each an ancestor king standing out of its face
    # into the nave, broad-shouldered; a banner on its aisle side
    for px in PX:
        for pz, step, back in ((4, 7, 3), (18, 17, 21)):
            for dx in range(3):
                for dz in range(3):
                    for y in range(1, 12):
                        s = WALL
                        if y == 1:
                            s = CHIS
                        elif y in (2, 8):
                            s = GOLD if (dx + dz) % 2 == 0 else BRONZE
                        elif y == 11:
                            s = CARVE
                        elif y == 10:
                            s = BRONZE
                        elif y % 4 == 0:
                            s = TRIM
                        h.put(px + dx, y, pz + dz, s)
            king(h, [(px + i, step) for i in range(3)], KING_11)
            for y in (6, 7):
                h.put(px - 1, y, step, BRONZE)                           # his pauldrons
                h.put(px + 3, y, step, BRONZE)
            h.put(px + 1, 8, back, f"minecraft:red_wall_banner[facing={'north' if step == 7 else 'south'}]")
    # the arches between the pillars (and from the door wall to the first), and the rune frieze over them on the nave's side
    for a, b in [(1, 3)] + [(px + 3, px + 7) for px in PX[:-1]]:
        for z in (4, 5, 6, 18, 19, 20):
            h.put(a, 11, z, stair("polished_deepslate", "west", "top"))
            h.put(b, 11, z, stair("polished_deepslate", "east", "top"))
            for x in range(a + 1, b):
                h.put(x, 11, z, "minecraft:polished_deepslate_slab[type=top]")
    for x in range(1, 26):
        for z in (6, 18):
            h.put(x, 12, z, GOLD if x % 4 == 1 else CHIS)
            h.put(x, 13, z, BRONZE)
    # ribs across the nave's vault at every pillar pair, gold keystones; gold coffers where the chandeliers hang between them
    for px in PX:
        for z in range(7, 18):
            h.put(px + 1, top(px + 1, z), z, GOLD if z == C else CHIS)
    for x in (9, 17):
        h.put(x, sy - 1, C, GOLD)
        chandelier(h, x, C, 15, 4)
    # the aisles: beams across on the pillars, lanterns between, gold runes cut in the walls and banners between the runes
    for px in PX:
        for z in list(range(1, 4)) + list(range(21, 24)):
            h.put(px + 1, 9, z, BEAM_Z)
    for i, a in enumerate((1, 8, 16, 23)):
        for z, hz in ((0, 2), (sz - 1, sz - 3)):
            rune(h, [(a + d, z) for d in range(3)], 3, 2 * i + (z > 0))
            for d in range(3):
                h.put(a + d, 2, z, CHIS)
                h.put(a + d, 8, z, CHIS)
            if a > 1:
                hang(h, a + 1, hz, 9, 1)
    for px in PX:
        h.put(px + 1, 8, 1, "minecraft:red_wall_banner[facing=south]")
        h.put(px + 1, 8, sz - 2, "minecraft:red_wall_banner[facing=north]")
    # the hearth down the nave: embers the length of it under an iron grate in a blackstone trough, three fires blazing in it (a fire
    # in every cell smokes the nave out), gold-capped hearthstones at its ends
    for x in range(8, 21):
        for z in (11, 12, 13):
            h.put(x, 0, z, DARK)
        h.put(x, 1, 11, "minecraft:polished_blackstone_slab[type=bottom]")
        h.put(x, 1, 13, "minecraft:polished_blackstone_slab[type=bottom]")
        if x in (9, 14, 19):
            h.put(x, 1, 12, FIRE)
        else:
            h.put(x, 0, 12, "minecraft:magma_block")
            h.put(x, 1, 12, "minecraft:iron_bars[east=true,west=true]")
    for x in (7, 21):
        for z in (11, 12, 13):
            h.put(x, 0, z, DARK)
            h.put(x, 1, z, CHIS_B if z != C else DARK_B)
        h.put(x, 2, C, GOLD)
    # the long tables either side of it, benches both sides of each, lanterns and candles on them, kegs at their heads
    for z in (9, 15):
        for x in range(7, 22):
            h.put(x, 1, z, "minecraft:dark_oak_slab[type=top]")
            if x in (9, 14, 19):
                h.put(x, 2, z, "minecraft:lantern")
            elif x in (7, 11, 12, 16, 17, 21):
                h.put(x, 2, z, "minecraft:candle[candles=3,lit=true]")
        h.put(6, 1, z, "minecraft:barrel[facing=east]")
        h.put(22, 1, z, "minecraft:barrel[facing=west]")
    for x in range(7, 22):
        h.put(x, 1, 8, "minecraft:spruce_stairs[facing=north]")
        h.put(x, 1, 10, "minecraft:spruce_stairs[facing=south]")
        h.put(x, 1, 14, "minecraft:spruce_stairs[facing=north]")
        h.put(x, 1, 16, "minecraft:spruce_stairs[facing=south]")
    # banners hung off the frieze over every arch, into the nave (wool hung in the nave hid the kings)
    for x in (2, 9, 17):
        h.put(x, 13, 7, "minecraft:red_wall_banner[facing=south]")
        h.put(x, 13, 17, "minecraft:red_wall_banner[facing=north]")
    # the door's side: braziers either side of the way in, runes over the door
    brazier(h, 2, 1, 8)
    brazier(h, 2, 1, 16)
    for z in range(C - 2, C + 3):
        h.put(0, 9, z, GOLD if z % 2 == 0 else CHIS)
    h.put(0, 10, C, GOLD)
    # the bay: where the low aisles meet its height, their end faces banded in bronze and cut with a gold-studded frieze
    for z in list(range(1, 7)) + list(range(18, sz - 1)):
        for y in range(top(25, z) + 1, sy - 1):
            if top(26, z) >= y:
                h.put(25, y, z, BRONZE if y == top(25, z) + 1 else (GOLD if (y + z) % 3 == 0 else CHIS))
    # a rib across its mouth, the dais two steps up, the god's high seat on a third under the ancestor's great face
    for z in range(1, sz - 1):
        h.put(26, top(26, z), z, GOLD if z == C else CHIS)
    for z in range(6, 19):
        h.put(26, 1, z, GOLD if z in (6, 18) else stair("polished_deepslate", "east"))
        for x in range(27, 32):
            h.put(x, 1, z, POL)
    for z in range(7, 18):
        h.put(27, 2, z, GOLD if z in (7, 17) else stair("polished_deepslate", "east"))
        for x in range(28, 32):
            h.put(x, 2, z, CHIS if (x + z) % 2 else POL)
    for x in range(22, 26):                                              # a red runner from the hearth's end up to the steps
        for z in (11, 12, 13):
            h.put(x, 1, z, "minecraft:red_carpet")
    for z in (11, 12, 13):
        h.put(28, 3, z, "minecraft:red_carpet")
        h.put(29, 3, z, stair("polished_blackstone", "east"))
        for x in (30, 31):
            h.put(x, 3, z, CHIS_B)
    h.put(30, 4, C, stair("polished_blackstone", "east"))                # the high seat, its back to the wall, facing the door
    h.put(30, 4, C - 1, GOLD)                                            # its arms
    h.put(30, 4, C + 1, GOLD)
    for y in range(4, 8):                                                # its back: a black panel between gold posts, gold-crested
        h.put(31, y, C - 1, GOLD)
        h.put(31, y, C + 1, GOLD)
        h.put(31, y, C, DARK_B if y < 6 else GOLD)
    h.put(31, 8, C, GOLD)
    great_face(h, sx - 1, C, 4)
    for z in (6, 18):
        h.put(sx - 2, 8, z, "minecraft:red_wall_banner[facing=west]")
    brazier(h, 28, 3, 8)                                                 # braziers on the dais, and at the steps' foot
    brazier(h, 28, 3, 16)
    brazier(h, 25, 1, 8)
    brazier(h, 25, 1, 16)
    statue(h, 28, 2, KING_11, 3)                                         # the two kings in the bay's corners
    statue(h, 28, sz - 5, KING_11, 3)
    for z in (3, sz - 4):
        brazier(h, 25, 1, z)
    for z in (5, sz - 6):                                                # lanterns hung either side of the dais (one over it hid the face)
        hang(h, 29, z, top(29, z), 5)
    h.stations = []
    return h


def throne_room():
    """The King's Throne Room (2026-11-04; it replaces the surface Keep), 19 long, 13 high, 17 wide: a solemn hall under a stepped vault,
    a red runner edged in gold up to a two-step dais at the far end where Thorgrim's Throne of Power stands (its "throne" station, facing
    the door) under the ancestor's face; eight ancestor kings along the walls, banners and braziers between them, gold runes either side
    of the door, a gold band round the walls, two copper chandeliers. Its station: the throne at (15, 3, 8), facing west."""
    sx, sy, sz, C = 19, 13, 17, 8
    h = Hall(sx, sy, sz, C, 3, 5, 31)
    h.shell()

    def top(x, z):
        return 8 + min(min(z - 1, sz - 2 - z), 3)

    vault(h, top, 1, sx - 2, 1, sz - 2, C)
    for x in range(1, sx - 1):                                           # a gold band round the walls under the vault's steps
        h.put(x, 8, 0, GOLD)
        h.put(x, 8, sz - 1, GOLD)
    for z in list(range(1, 4)) + list(range(sz - 4, sz - 1)):
        h.put(0, 8, z, GOLD)
        h.put(sx - 1, 8, z, GOLD)
    for x in (4, 9, 14):                                                 # ribs across the vault, gold keystones
        for z in range(1, sz - 1):
            h.put(x, top(x, z), z, GOLD if z == C else CHIS)
    # the runner: red carpet three wide from the door to the dais, the floor either side of it blackstone inlaid with gold
    for x in range(1, 12):
        for z in (C - 1, C, C + 1):
            h.put(x, 1, z, "minecraft:red_carpet")
        for z in (C - 2, C + 2):
            h.put(x, 0, z, GOLD if x % 2 == 0 else DARK_B)
    # eight ancestor kings along the walls, two deep in their niches, facing the runner; banners and braziers in the gaps between
    for x0 in (2, 6, 10, 14):
        for back, front in ((1, 2), (sz - 2, sz - 3)):
            for dx in range(3):
                for y in range(1, top(x0 + dx, back) + 1):
                    h.put(x0 + dx, y, back, POL if y > 1 else CHIS)
            king(h, [(x0 + dx, front) for dx in range(3)], KING_9)
    for x in (1, 5, 9, 13, 17):
        h.put(x, 7, 1, "minecraft:red_wall_banner[facing=south]")
        h.put(x, 7, sz - 2, "minecraft:red_wall_banner[facing=north]")
    for x in (5, 9, 13):
        brazier(h, x, 1, 1)
        brazier(h, x, 1, sz - 2)
    # runes either side of the door
    for z0, i in ((2, 0), (sz - 5, 3)):
        rune(h, [(0, z0 + d) for d in range(3)], 2, i)
    # the dais: two steps up across the hall's width, a platform 4 deep, black-edged and gold-cornered, the throne's place in the middle
    for z in range(3, sz - 3):
        h.put(12, 1, z, GOLD if z in (3, sz - 4) else stair("polished_deepslate", "east"))
        h.put(13, 1, z, POL)
        h.put(13, 2, z, GOLD if z in (3, sz - 4) else stair("polished_deepslate", "east"))
        for x in range(14, sx - 1):
            h.put(x, 1, z, POL)
            edge = x in (14, sx - 2) or z in (3, sz - 4)
            h.put(x, 2, z, GOLD if (x in (14, sx - 2) and z in (3, sz - 4)) else (CHIS_B if edge else POL))
    h.stations.append(("throne", 15, 3, C, "west"))
    brazier(h, 14, 3, 4)                                                 # braziers either side of the throne
    brazier(h, 14, 3, sz - 5)
    h.face(sx - 1, C, 4)                                                 # the ancestor's face over it
    for z in (4, sz - 5):
        h.put(sx - 2, 9, z, "minecraft:red_wall_banner[facing=west]")
    for x in (5, 10):
        chandelier(h, x, C, 11, 2)
    return h


HALLS = {"dawi_great_hall": great_hall_v1, "dawi_grand_hall": great_hall, "dawi_barracks": barracks, "dawi_mine": mine, "dawi_brewery": brewery, "dawi_treasury": treasury,
         "dawi_runic_forge": runic_forge, "dawi_throne_room": throne_room}
DATA_VERSION = 4790                                                      # 26.1.2's, as the bridge's structure_save writes it

# 26.1.2's own blockstate files (block bench/mc-source-26.1.2): every block a hall uses must have one, and every state it sets must be one
# they name. A few properties change no model, so the files don't name them: those are added here.
BLOCKSTATES = os.path.join(HERE, "..", "..", "mc-source-26.1.2", "minecraft", "assets", "minecraft", "blockstates")
UNMODELLED = {"waterlogged": {"true", "false"}, "signal_fire": {"true", "false"}}
_known_cache = {}


def _known(name):
    """The properties and values 26.1.2's blockstate file for {@code name} names, or None if it has none (no such block)."""
    if name in _known_cache:
        return _known_cache[name]
    ns, _, path = name.partition(":")
    f = os.path.join(BLOCKSTATES, path + ".json")
    props = None
    if ns == "minecraft" and os.path.exists(f):
        with open(f, encoding="utf-8") as fh:
            j = json.load(fh)
        props = {k: set(v) for k, v in UNMODELLED.items()}

        def add(k, v):
            props.setdefault(k, set()).update(str(v).lower().split("|"))

        for key in j.get("variants", {}):
            for kv in filter(None, key.split(",")):
                k, v = kv.split("=")
                add(k, v)

        def when(w):
            for k, v in w.items():
                if k in ("OR", "AND"):
                    for sub in v:
                        when(sub)
                else:
                    add(k, v)

        for part in j.get("multipart", []):
            when(part.get("when", {}))
        if j.get("multipart"):                                           # a multipart names only the states that add a part
            for k, vs in props.items():
                if vs <= {"true"}:
                    vs.add("false")
                elif vs <= {"low", "tall"}:
                    vs.add("none")
        if path.endswith("_wall_banner"):                                # a banner's model is built in: its file names no state
            props["facing"] = {"north", "south", "west", "east"}
        elif path.endswith("_banner"):
            props["rotation"] = {str(i) for i in range(16)}
    _known_cache[name] = props
    return props


def validate(h):
    """Every state the hall sets checked against 26.1.2's blockstate files: the problems found, empty if none."""
    bad = []
    for st in sorted(set(h.b.values())):
        base = st.split("{", 1)[0]
        name, props = base, {}
        if "[" in base:
            name, rest = base.split("[", 1)
            props = dict(kv.split("=") for kv in rest.rstrip("]").split(","))
        known = _known(name)
        if known is None:
            bad.append(f"{st}: no block {name} in 26.1.2")
            continue
        for k, v in props.items():
            if k not in known:
                bad.append(f"{st}: {name} has no property {k}")
            elif v not in known[k]:
                bad.append(f"{st}: {name}'s {k} is never {v} (only {sorted(known[k])})")
    return bad


GILDING = {"minecraft:gold_block": "khazuk:gilded_stone", "minecraft:raw_gold_block": "khazuk:hoard_gold"}


def write(h, path):
    """The hall's template straight to an .nbt file: every cell of its box, as structure_save writes it (gen_keep.py's way)."""
    sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "structgen"))
    import nbtio
    cells = sorted(h.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))
    palette, index, blocks = [], {}, []
    for (x, y, z), st in cells:
        if st not in index:
            index[st] = len(palette)
            name, props = st, {}
            if "[" in st:
                name, rest = st.split("[", 1)
                props = dict(kv.split("=") for kv in rest.rstrip("]").split(","))
            entry = {"Name": nbtio.String(GILDING.get(name, name))}   # the halls' gold is Khazuk!'s gilding, not money (the bug hunt of 2d0b04c)
            if props:
                entry["Properties"] = nbtio.Compound({k: nbtio.String(v) for k, v in props.items()})
            palette.append(nbtio.Compound(entry))
        blocks.append(nbtio.Compound({"pos": nbtio.List([nbtio.Int(x), nbtio.Int(y), nbtio.Int(z)]), "state": nbtio.Int(index[st])}))
    root = nbtio.Compound({
        "DataVersion": nbtio.Int(DATA_VERSION),
        "size": nbtio.List([nbtio.Int(h.sx), nbtio.Int(h.sy), nbtio.Int(h.sz)]),
        "palette": nbtio.List(palette, nbtio.COMPOUND),
        "blocks": nbtio.List(blocks, nbtio.COMPOUND),
        "entities": nbtio.List([], nbtio.COMPOUND),
    })
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    nbtio.write(path, root)


def main():
    built = {k: f() for k, f in HALLS.items()}
    bad = {}
    for k, h in built.items():
        print(f"{k}: size ({h.sx}, {h.sy}, {h.sz}), door (0, 1, {h.door_z}) {h.door_w} wide {h.door_h} high, stations {h.stations}")
        bad[k] = validate(h)
        for b in bad[k]:
            print(f"  NOT A 26.1.2 STATE: {b}")
    if "--write" in sys.argv:
        for k in sys.argv[sys.argv.index("--write") + 1:]:
            if k.startswith("--"):
                break
            if bad[k]:
                print(f"  {k} not written: {len(bad[k])} states 26.1.2 doesn't have")
                continue
            path = os.path.join(HERE, "..", "src", "main", "resources", "data", "khazuk", "structure", "room", k + ".nbt")
            write(built[k], path)
            print(f"  wrote {os.path.relpath(path)}")
    if "--at" in sys.argv:
        i = sys.argv.index("--at")
        o = [int(v) for v in sys.argv[i + 1:i + 4]]
        sys.path.insert(0, os.path.join(HERE, "..", "..", "devbridge", "tools"))
        import devbridge
        x = o[0]
        only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else list(built)
        for k, h in built.items():
            if k not in only:
                continue
            r = devbridge.call("build", ops=[{"op": "blocks", "origin": [x, o[1], o[2]], "blocks": h.cells()}])
            print(k, "built at", [x, o[1], o[2]], json.dumps(r)[:120])
            print(f"  save: structure_save from=[{x},{o[1]},{o[2]}] to=[{x + h.sx - 1},{o[1] + h.sy - 1},{o[2] + h.sz - 1}] path=khazuk/{k}")
            x += h.sx + 4


if __name__ == "__main__":
    main()
