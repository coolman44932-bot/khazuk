"""The hold's dug halls: D1's first ones behind the Hold Gate (the Great Hall, the barracks and the mine) and D3's (the brewery and the
treasury), as room templates the engine's dig side digs out (Works#dig): the Miners cut the rock and set the hall's own stone, pillars,
lamps and tables as they go.

Every template is in the engine's frame: x away from the door (the door on the x = 0 face, at y 1), y up (y 0 the floor), z across.
Each hall is lined in dressed stone (its walls, floor and vault are part of it), so it reads as a dwarf hall whatever rock it is cut in.

    python tools/gen_halls.py                       the block maps and their sizes
    python tools/gen_halls.py --at x y z            and build each in the running bridge game, side by side from x y z, for saving
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


def great_hall():
    """The Great Hall, 27 long, 13 high, 21 wide: two rows of pillars, beams across under the vault on them, a long hearth down the
    middle with tables and benches either side, lanterns on chains, and at the far end a dais under an ancestor's face, with a seat
    of gold for the god."""
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


HALLS = {"dawi_great_hall": great_hall, "dawi_barracks": barracks, "dawi_mine": mine, "dawi_brewery": brewery, "dawi_treasury": treasury,
         "dawi_runic_forge": runic_forge}
DATA_VERSION = 4790                                                      # 26.1.2's, as the bridge's structure_save writes it


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
            entry = {"Name": nbtio.String(name)}
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
    for k, h in built.items():
        print(f"{k}: size ({h.sx}, {h.sy}, {h.sz}), door (0, 1, {h.door_z}), stations {h.stations}")
    if "--write" in sys.argv:
        for k in sys.argv[sys.argv.index("--write") + 1:]:
            if k.startswith("--"):
                break
            path = os.path.join(HERE, "..", "src", "main", "resources", "data", "khazuk", "structure", "room", k + ".nbt")
            write(built[k], path)
            print(f"  wrote {os.path.relpath(path)}")
    if "--at" in sys.argv:
        i = sys.argv.index("--at")
        o = [int(v) for v in sys.argv[i + 1:i + 4]]
        sys.path.insert(0, os.path.join(HERE, "..", "..", "devbridge", "tools"))
        import devbridge
        x = o[0]
        for k, h in built.items():
            r = devbridge.call("build", ops=[{"op": "blocks", "origin": [x, o[1], o[2]], "blocks": h.cells()}])
            print(k, "built at", [x, o[1], o[2]], json.dumps(r)[:120])
            print(f"  save: structure_save from=[{x},{o[1]},{o[2]}] to=[{x + h.sx - 1},{o[1] + h.sy - 1},{o[2] + h.sz - 1}] path=khazuk/{k}")
            x += h.sx + 4


if __name__ == "__main__":
    main()
