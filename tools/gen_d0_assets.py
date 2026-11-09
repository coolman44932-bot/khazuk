"""D0's look for the Ancestor Shrine until it is modelled: dark dwarf stone, banded in gold, a gold rune on each face. Deterministic.

    python tools/gen_d0_assets.py
"""
import os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "khazuk", "textures", "block", "ancestor_shrine.png")

STONE, STONE_D, STONE_L = (92, 88, 84), (62, 59, 56), (120, 115, 108)
GOLD, GOLD_D, GOLD_L = (214, 168, 52), (150, 108, 30), (250, 214, 110)


def elite_eggs():
    """0.14: the Ironbreaker's egg (the Miner's, recoloured gromril blue-steel) and the Hammerer's (gold over steel). Their item files."""
    src = Image.open(os.path.join(RES, "assets", "khazuk", "textures", "item", "miner_spawn_egg.png")).convert("RGBA")
    for kind, tint in (("ironbreaker", (0.8, 0.92, 1.18)), ("hammerer", (1.22, 0.98, 0.62)),
                       ("troll_slayer", (1.3, 0.75, 0.45)), ("giant_slayer", (1.35, 0.62, 0.38)), ("dragon_slayer", (1.4, 0.5, 0.3)),
                       ("engineer_slayer", (1.25, 0.85, 0.5)), ("slayer_mech", (1.15, 0.8, 0.55))):   # 0.14.2: crest orange, copper   # 0.14.2: crest orange
        eg = Image.new("RGBA", src.size)
        for y in range(src.size[1]):
            for x in range(src.size[0]):
                r_, g_, b_, a_ = src.getpixel((x, y))
                lum = (r_ + g_ + b_) / 3
                eg.putpixel((x, y), (0, 0, 0, 0) if a_ == 0 else tuple(min(255, int(lum * f + 15)) for f in tint) + (a_,))
        eg.save(os.path.join(RES, "assets", "khazuk", "textures", "item", kind + "_spawn_egg.png"))
        out(f"assets/khazuk/items/{kind}_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"khazuk:item/{kind}_spawn_egg"}})
        out(f"assets/khazuk/models/item/{kind}_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"khazuk:item/{kind}_spawn_egg"}})


def main():
    r = random.Random(7)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = STONE
            if y in (0, 15) or x in (0, 15):
                c = STONE_D                                      # the block's dressed edge
            elif (y % 5 == 0) or (x + (y // 5) * 3) % 8 == 0:
                c = STONE_D                                      # the courses' joints
            f = 0.9 + 0.2 * r.random()
            im.putpixel((x, y), tuple(int(v * f) for v in c) + (255,))
    for x in range(1, 15):                                       # gold bands top and bottom
        for y in (2, 13):
            im.putpixel((x, y), GOLD + (255,))
        im.putpixel((x, 3), GOLD_D + (255,))
        im.putpixel((x, 12), GOLD_D + (255,))
    rune = [(7, 5), (8, 5), (7, 6), (8, 6), (6, 7), (9, 7), (5, 8), (10, 8), (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10)]
    for x, y in rune:                                            # an ancestor rune, Grungni's hammer-mark
        im.putpixel((x, y), GOLD_L + (255,) if y < 8 else GOLD + (255,))
    im.save(TEX)
    plan_item()
    throne_item()
    book_item()
    runesmith_egg()
    anvil_items()
    god_items()
    elite_eggs()
    print("ok")


RES = os.path.join(HERE, "..", "src", "main", "resources")


def out(rel, obj):
    import json
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline=chr(10)) as f:
        json.dump(obj, f, indent=2)
        f.write(chr(10))


PLANS = {
    # id: (name, the gold drawing (10 wide, 7 tall at 3,5), the shapeless recipe beside the paper)
    "plan_keep": ("King's Throne Room", ["g.g.gg.g.g", "gggggggggg", "g.g....g.g", "g.g.gg.g.g", "g.g.gg.g.g", "g.g....g.g", "gggggggggg"],
                  ["minecraft:stone_bricks", "minecraft:iron_ingot", "minecraft:gold_ingot"]),
    "plan_hold_gate": ("Hold Gate", ["..gggggg..", ".g......g.", ".g.gggg.g.", ".g.g..g.g.", ".g.g..g.g.", ".g.g..g.g.", "gg.g..g.gg"],
                       ["minecraft:stone_bricks", "minecraft:gold_ingot"]),
    "plan_great_hall": ("Great Hall", ["gggggggggg", "g.g....g.g", "g.g.gg.g.g", "g.g....g.g", "g.g....g.g", "g.g.gg.g.g", "gggggggggg"],
                        ["minecraft:deepslate_bricks", "minecraft:gold_ingot"]),
    "plan_barracks": ("Barracks", ["gggggggggg", "g........g", "g.gg..gg.g", "g........g", "g.gg..gg.g", "g........g", "gggggggggg"],
                      ["minecraft:stone_bricks", "minecraft:iron_ingot"]),
    "plan_mine": ("Mine", ["...gggg...", "..g....g..", ".g..gg..g.", "....gg....", "....gg....", "....gg....", "....gg...."],
                  ["minecraft:stone_bricks", "minecraft:rail"]),
    "plan_brewery": ("Brewery", ["..gggggg..", ".g......g.", ".gggggggg.", ".g......g.", ".gggggggg.", ".g......g.", "..gggggg.."],
                     ["minecraft:stone_bricks", "minecraft:barrel"]),
    "plan_runic_forge": ("Runic Forge", ["...gg.....", "..g..g....", ".gggggggg.", "...gggg...", "...gggg...", "..gg..gg..", ".gggggggg."],
                         ["minecraft:stone_bricks", "minecraft:anvil"]),
    "plan_slayer_shrine": ("Shrine of Grimnir", ["g........g", ".g..gg..g.", "..g.gg.g..", "...gggg...", "..g.gg.g..", ".g..gg..g.", "g...gg...g"],
                           ["minecraft:stone_bricks", "minecraft:iron_axe", "minecraft:gold_ingot"]),
    "plan_engineers_guild": ("Engineers' Guild", ["...gggg...", "..g....g..", ".g.gggg.g.", ".g.g..g.g.", ".g.gggg.g.", "..g....g..", "...gggg..."],
                             ["minecraft:stone_bricks", "minecraft:copper_ingot", "minecraft:gold_ingot"]),
    "plan_road": ("Hold Road", ["..........", "gggggggggg", "..........", "g.g.g.g.gg", "..........", "gggggggggg", ".........."],
                  ["minecraft:deepslate_bricks", "minecraft:lantern"]),
    "plan_road_crossing": ("Road Crossing", ["...g..g...", "...g..g...", "ggggggggg.", "....gg....", "ggggggggg.", "...g..g...", "...g..g..."],
                           ["minecraft:deepslate_bricks", "minecraft:lantern", "minecraft:compass"]),
    "plan_road_descent": ("Road Descent", ["gg........", "..g.......", "...gg.....", ".....g....", "......gg..", "........g.", ".........g"],
                          ["minecraft:deepslate_brick_stairs", "minecraft:lantern"]),
    "plan_treasury": ("Treasury", ["....gg....", "...gggg...", "..gggggg..", "....gg....", ".gg.gg.gg.", "gggggggggg", "gggggggggg"],
                      ["minecraft:stone_bricks", "minecraft:gold_ingot", "minecraft:iron_ingot"]),
}


def plan_item():
    """The Ancestors' Plans: a stone tablet with the building drawn on it in gold, its corners chipped (the gate, the Great Hall's
    pillars, the barracks' bunks, the mine's pick). Their item files, recipes and names too."""
    import json
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    for pid, (name, drawing, needs) in PLANS.items():
        r = random.Random(13)
        im = Image.new("RGBA", (16, 16))
        for y in range(2, 15):
            for x in range(2, 14):
                if (x, y) in ((2, 2), (13, 2), (2, 14), (13, 14)):
                    continue
                edge = x in (2, 13) or y in (2, 14)
                c = STONE_D if edge else STONE_L
                f = 0.9 + 0.2 * r.random()
                im.putpixel((x, y), tuple(int(v * f) for v in c) + (255,))
        for dy, row in enumerate(drawing):
            for dx, ch in enumerate(row):
                if ch == "g":
                    im.putpixel((3 + dx, 5 + dy), (GOLD if dy else GOLD_L) + (255,))
        im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", pid + ".png"))
        out(f"assets/khazuk/items/{pid}.json", {"model": {"type": "minecraft:model", "model": f"khazuk:item/{pid}"}})
        out(f"assets/khazuk/models/item/{pid}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"khazuk:item/{pid}"}})
        out(f"data/khazuk/recipe/{pid}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                              "ingredients": needs + ["minecraft:paper"],
                                              "result": {"id": f"khazuk:{pid}", "count": 1}})
        d[f"item.khazuk.{pid}"] = "Ancestors' Plan: " + name
    out("assets/khazuk/lang/en_us.json", d)


def book_item():
    """The Dammaz Kron: a thick book bound in red leather, gold corners and a gold clasp, an ancestor rune on its cover. Its item files,
    recipe (a book, a gold ingot, an iron ingot) and name."""
    import json
    r = random.Random(23)
    im = Image.new("RGBA", (16, 16))
    RED, RED_D, PAGE = (138, 28, 26), (92, 16, 16), (232, 220, 190)
    def px(x, y, c):
        f = 0.92 + 0.16 * r.random()
        im.putpixel((x, y), tuple(min(255, int(v * f)) for v in c) + (255,))
    for y in range(2, 14):
        for x in range(3, 13):
            px(x, y, RED_D if x in (3, 12) or y in (2, 13) else RED)
    for y in range(3, 13):                                      # the pages' edge on the right
        px(13, y, PAGE)
    px(14, 4, PAGE)
    px(14, 11, PAGE)
    for x, y in ((3, 2), (4, 2), (3, 3), (11, 2), (12, 2), (12, 3), (3, 12), (3, 13), (4, 13), (12, 12), (11, 13), (12, 13)):
        px(x, y, GOLD)                                           # gold corners
    for y in range(6, 10):                                      # the clasp
        px(13, y, GOLD_D)
        px(14, y, GOLD)
    for x, y in ((7, 5), (8, 5), (6, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
        px(x, y, GOLD_L)                                         # an ancestor rune
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "dammaz_kron.png"))
    out("assets/khazuk/items/dammaz_kron.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/dammaz_kron"}})
    out("assets/khazuk/models/item/dammaz_kron.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/dammaz_kron"}})
    out("data/khazuk/recipe/dammaz_kron.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                               "ingredients": ["minecraft:book", "minecraft:gold_ingot", "minecraft:iron_ingot"],
                                               "result": {"id": "khazuk:dammaz_kron", "count": 1}})
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    d["item.khazuk.dammaz_kron"] = "Dammaz Kron, the Book of Grudges"
    out("assets/khazuk/lang/en_us.json", d)


def runesmith_egg():
    """The Runesmith's spawn egg: the Miner's egg recoloured, steel and soul-fire blue. Its item files and name, and his entity's."""
    import json
    src = Image.open(os.path.join(RES, "assets", "khazuk", "textures", "item", "miner_spawn_egg.png")).convert("RGBA")
    im = Image.new("RGBA", src.size)
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = src.getpixel((x, y))
            if a == 0:
                im.putpixel((x, y), (0, 0, 0, 0))
                continue
            lum = (r + g + b) / 3
            im.putpixel((x, y), (int(lum * 0.55), int(lum * 0.8), min(255, int(lum * 1.25 + 30)), a))   # steel, gone soul-blue
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "runesmith_spawn_egg.png"))
    out("assets/khazuk/items/runesmith_spawn_egg.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/runesmith_spawn_egg"}})
    out("assets/khazuk/models/item/runesmith_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/runesmith_spawn_egg"}})
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    d["entity.khazuk.runesmith"] = "Runesmith"
    d["item.khazuk.runesmith_spawn_egg"] = "Runesmith Spawn Egg"
    out("assets/khazuk/lang/en_us.json", d)


def anvil_items():
    """D5b: the Runelord's spawn egg (the Miner's, recoloured gold and white) and the Anvil of Doom's item (a dark anvil on a gold-edged
    step, runes glowing blue on its face). Their item files and names, and the Runelord's and the Anvil's entities'."""
    import json
    src = Image.open(os.path.join(RES, "assets", "khazuk", "textures", "item", "miner_spawn_egg.png")).convert("RGBA")
    im = Image.new("RGBA", src.size)
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = src.getpixel((x, y))
            lum = (r + g + b) / 3
            im.putpixel((x, y), (0, 0, 0, 0) if a == 0 else (min(255, int(lum * 1.3 + 20)), min(255, int(lum * 1.05 + 10)), int(lum * 0.5), a))
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "runelord_spawn_egg.png"))
    for kind, tint in (("quarreller", (0.75, 0.85, 0.55)), ("thunderer", (1.1, 0.75, 0.45)),      # D6: olive and copper
                       ("longbeard", (1.15, 1.1, 1.05)), ("longbeard_great", (1.0, 0.95, 1.2))):     # 0.11: grey-white beards
        eg = Image.new("RGBA", src.size)
        for y in range(src.size[1]):
            for x in range(src.size[0]):
                r_, g_, b_, a_ = src.getpixel((x, y))
                lum = (r_ + g_ + b_) / 3
                eg.putpixel((x, y), (0, 0, 0, 0) if a_ == 0 else tuple(min(255, int(lum * f + 15)) for f in tint) + (a_,))
        eg.save(os.path.join(RES, "assets", "khazuk", "textures", "item", kind + "_spawn_egg.png"))
        out(f"assets/khazuk/items/{kind}_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"khazuk:item/{kind}_spawn_egg"}})
        out(f"assets/khazuk/models/item/{kind}_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"khazuk:item/{kind}_spawn_egg"}})
    out("assets/khazuk/items/runelord_spawn_egg.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/runelord_spawn_egg"}})
    out("assets/khazuk/models/item/runelord_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/runelord_spawn_egg"}})
    r = random.Random(29)
    im = Image.new("RGBA", (16, 16))
    IRON, IRON_D, RUNE = (70, 76, 82), (44, 48, 52), (98, 230, 255)
    def px(x, y, c):
        f = 0.92 + 0.16 * r.random()
        im.putpixel((x, y), tuple(min(255, int(v * f)) for v in c) + (255,))
    for x in range(1, 15):                                       # the stone step, gold-edged
        for y in (13, 14):
            px(x, y, STONE if y == 14 else STONE_L)
        px(x, 12, GOLD)
    for y in range(9, 12):                                      # the anvil's foot and waist
        for x in range(5, 11):
            px(x, y, IRON_D)
    for y in range(5, 9):                                       # its top, the horn out to the left
        for x in range(3, 14):
            px(x, y, IRON)
    for x in range(0, 3):
        px(x, 6, IRON)
        px(x, 7, IRON_D)
    for x in (5, 7, 9, 11):                                     # runes on its face
        px(x, 6, RUNE)
    px(8, 5, RUNE)
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "anvil_of_doom.png"))
    out("assets/khazuk/items/anvil_of_doom.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/anvil_of_doom"}})
    out("assets/khazuk/models/item/anvil_of_doom.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/anvil_of_doom"}})
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    d["entity.khazuk.runelord"] = "Runelord"
    d["item.khazuk.runelord_spawn_egg"] = "Runelord Spawn Egg"
    d["entity.khazuk.anvil_of_doom"] = "Anvil of Doom"
    d["entity.khazuk.quarreller"] = "Quarreller"
    d["item.khazuk.quarreller_spawn_egg"] = "Quarreller Spawn Egg"
    d["entity.khazuk.thunderer"] = "Thunderer"
    d["item.khazuk.thunderer_spawn_egg"] = "Thunderer Spawn Egg"
    d["entity.khazuk.longbeard"] = "Longbeard"
    d["item.khazuk.longbeard_spawn_egg"] = "Longbeard Spawn Egg"
    d["entity.khazuk.longbeard_great"] = "Longbeard (Great Weapon)"
    d["item.khazuk.longbeard_great_spawn_egg"] = "Longbeard (Great Weapon) Spawn Egg"
    d["item.khazuk.anvil_of_doom"] = "Anvil of Doom"
    out("assets/khazuk/lang/en_us.json", d)


def throne_item():
    """Thorgrim's Throne of Power folded up: a gold throne on a dark dais, a red cushion, the arch's point. Its item files, recipe and names."""
    r = random.Random(17)
    im = Image.new("RGBA", (16, 16))
    RED = (150, 30, 28)
    def px(x, y, c):
        f = 0.92 + 0.16 * r.random()
        im.putpixel((x, y), tuple(min(255, int(v * f)) for v in c) + (255,))
    for y in range(12, 15):                                      # the dais the bearers carry
        for x in range(2, 14):
            px(x, y, STONE_D if y == 14 else STONE)
    for x in range(3, 13):
        px(x, 11, GOLD_D)
    for y in range(2, 11):                                       # the back and its arch, stepped to a point
        w = min(4, max(0, y - 2) // 2 + 1)
        for x in range(8 - w, 8 + w):
            px(x, y, GOLD_L if x == 8 - w else GOLD)
    for y in range(5, 11):                                       # the pillars either side
        px(3, y, GOLD_D)
        px(12, y, GOLD_D)
    for x in range(5, 11):                                       # the cushion
        px(x, 9, RED)
        px(x, 10, RED)
    px(7, 5, (120, 230, 255))                                    # a rune
    px(8, 5, (120, 230, 255))
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "throne_of_power.png"))
    out("assets/khazuk/items/throne_of_power.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/throne_of_power"}})
    out("assets/khazuk/models/item/throne_of_power.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/throne_of_power"}})
    out("data/khazuk/recipe/throne_of_power.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                                    "pattern": ["GBG", "GGG", "I I"],
                                                    "key": {"G": "minecraft:gold_block", "B": "minecraft:book", "I": "minecraft:iron_block"},
                                                    "result": {"id": "khazuk:throne_of_power", "count": 1}})
    import json
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    d["entity.khazuk.throne_of_power"] = "Throne of Power"
    d["item.khazuk.throne_of_power"] = "Thorgrim's Throne of Power"
    out("assets/khazuk/lang/en_us.json", d)




GOD_ITEMS = {
    # id: (name, the drawing (16 x 16: g gold, G light gold, d dark gold, w wood, W dark wood, r rune light, s steel, f flame, F deep flame),
    #      the shaped recipe, its key)
    "grungnis_hammer": ("Grungni's Hammer", [
        "................",
        "...dddddddd.....",
        "..dGGGGGGGGd....",
        "..dGgrgggrgd....",
        "..dGgggrgggd....",
        "..dGgrgggrgd....",
        "..dggggggggd....",
        "...dddwwddd.....",
        "......wW........",
        ".......wW.......",
        "........wW......",
        ".........wW.....",
        "..........wW....",
        "...........wW...",
        "............dd..",
        "................"], ["GIG", " S ", " S "], {"G": "minecraft:gold_block", "I": "minecraft:iron_block", "S": "minecraft:stick"}),
    "valayas_ward": ("Valaya's Ward", [
        "................",
        ".....dddddd.....",
        "...ddGGGGGGdd...",
        "..dGGggggggGGd..",
        "..dGgsssssssgd..",
        ".dGgss.rr.ssgGd.",
        ".dGgs.rrrr.sgGd.",
        ".dGgsrr..rrsgGd.",
        ".dGgs.rrrr.sgGd.",
        ".dGgss.rr.ssgGd.",
        "..dGgsssssssgd..",
        "..dGGggggggGGd..",
        "...ddGGGGGGdd...",
        ".....dddddd.....",
        "................",
        "................"], ["GIG", "ISI", "GIG"], {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot", "S": "minecraft:shield"}),
    "grimnirs_fury": ("Grimnir's Fury", [
        "........f.......",
        ".......fFf......",
        "......fFFFf.....",
        ".....ssssfFf....",
        "....sssssssF....",
        "...ssssrsssd....",
        "...sssrrrsdw....",
        "...ssssrsdw.....",
        "....sssssdw.....",
        ".....sssdw......",
        ".......dw.......",
        "......dw........",
        ".....gw.........",
        "....wd..........",
        "...gw...........",
        "................"], [" B ", "GAG", " B "], {"B": "minecraft:blaze_powder", "G": "minecraft:gold_ingot", "A": "minecraft:iron_axe"}),
}


def god_items():
    """D7: the Ancestor Gods' three tokens (an icon, the item files, a shaped recipe each), Grungni's Hammer's blow as a damage type with
    its death messages, and the names."""
    import json
    pal = {"g": GOLD, "G": GOLD_L, "d": GOLD_D, "w": (128, 84, 48), "W": (86, 54, 30), "r": (150, 236, 255), "s": (168, 174, 182),
           "f": (255, 170, 40), "F": (230, 70, 20)}
    r = random.Random(41)
    for kind, (name, rows, pattern, key) in GOD_ITEMS.items():
        im = Image.new("RGBA", (16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    f = 1.0 if ch in "rfF" else 0.92 + 0.16 * r.random()
                    im.putpixel((x, y), tuple(min(255, int(v * f)) for v in pal[ch]) + (255,))
        im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", kind + ".png"))
        out(f"assets/khazuk/items/{kind}.json", {"model": {"type": "minecraft:model", "model": f"khazuk:item/{kind}"}})
        out(f"assets/khazuk/models/item/{kind}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"khazuk:item/{kind}"}})
        out(f"data/khazuk/recipe/{kind}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                                               "result": {"id": f"khazuk:{kind}", "count": 1}})
    out("data/khazuk/damage_type/grungnis_hammer.json", {"message_id": "khazuk.grungnis_hammer", "scaling": "when_caused_by_living_non_player",
                                                         "exhaustion": 0.1})
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    for kind, (name, *_rest) in GOD_ITEMS.items():
        d["item.khazuk." + kind] = name
    d["entity.khazuk.grungnis_hammer"] = "Grungni's Hammer"
    d["death.attack.khazuk.grungnis_hammer"] = "%1$s was struck down by Grungni's Hammer"
    d["death.attack.khazuk.grungnis_hammer.player"] = "%1$s was struck down by %2$s's Hammer of Grungni"
    out("assets/khazuk/lang/en_us.json", d)


if __name__ == "__main__":
    main()
