"""D0's look for the Ancestor Shrine until it is modelled: dark dwarf stone, banded in gold, a gold rune on each face. Deterministic.

    python tools/gen_d0_assets.py
"""
import os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "khazuk", "textures", "block", "ancestor_shrine.png")

STONE, STONE_D, STONE_L = (92, 88, 84), (62, 59, 56), (120, 115, 108)
GOLD, GOLD_D, GOLD_L = (214, 168, 52), (150, 108, 30), (250, 214, 110)


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
    print("ok")


RES = os.path.join(HERE, "..", "src", "main", "resources")


def out(rel, obj):
    import json
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline=chr(10)) as f:
        json.dump(obj, f, indent=2)
        f.write(chr(10))


def plan_item():
    """The Ancestors' Plan: a stone tablet with a gold gate drawn on it, its corners chipped. Its item files, recipe and name too."""
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
    gate = ["..gggggg..", ".g......g.", ".g.gggg.g.", ".g.g..g.g.", ".g.g..g.g.", ".g.g..g.g.", "gg.g..g.gg"]
    for dy, row in enumerate(gate):
        for dx, ch in enumerate(row):
            if ch == "g":
                im.putpixel((3 + dx, 5 + dy), (GOLD if dy else GOLD_L) + (255,))
    im.save(os.path.join(RES, "assets", "khazuk", "textures", "item", "plan_hold_gate.png"))
    out("assets/khazuk/items/plan_hold_gate.json", {"model": {"type": "minecraft:model", "model": "khazuk:item/plan_hold_gate"}})
    out("assets/khazuk/models/item/plan_hold_gate.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "khazuk:item/plan_hold_gate"}})
    out("data/khazuk/recipe/plan_hold_gate.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                   "ingredients": ["minecraft:stone_bricks", "minecraft:gold_ingot", "minecraft:paper"],
                                                   "result": {"id": "khazuk:plan_hold_gate", "count": 1}})
    import json
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    with open(lang, encoding="utf-8") as f:
        d = json.load(f)
    d["item.khazuk.plan_hold_gate"] = "Ancestors' Plan: Hold Gate"
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


if __name__ == "__main__":
    main()
