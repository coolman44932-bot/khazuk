"""The rune-sealed hall door's assets (hold/HallSeal; the user, 2026-11-06: "there should be a place in the room to dig into"): its front
(dark dressed deepslate, a bronze-banded frame, Grungni's door rune inlaid in gold), its sides (plain deepslate bricks), the blockstate
turning its front toward the room (away from FACING, the way the hall runs), its item, loot, pickaxe tag, recipe and name.

    python tools/gen_hall_seal.py
"""
import json, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")


def out(rel, data):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def texture():
    R = random.Random(77)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 44 + R.randint(-6, 6)
            px[x, y] = (v, v, v + 4, 255)
    for y in range(16):                                          # dressed courses, every 4th row a mortar line
        if y % 4 == 3:
            for x in range(16):
                px[x, y] = (30, 30, 34, 255)
    bronze, dark_bronze, gold, gold_hi = (150, 96, 46, 255), (104, 64, 30, 255), (214, 170, 60, 255), (250, 214, 110, 255)
    for i in range(16):                                          # the bronze band round its face
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = bronze
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= i <= 14:
                px[x, y] = dark_bronze
    # Grungni's door rune: a tall arch with a bar across and a mark under it
    rune = ["..gggggg..",
            ".g......g.",
            "g........g",
            "g...gg...g",
            "g..g..g..g",
            "g...gg...g",
            "g........g",
            "gggggggggg",
            "g........g",
            "g...gg...g"]
    for dy, row in enumerate(rune):
        for dx, ch in enumerate(row):
            if ch == "g":
                px[3 + dx, 3 + dy] = gold_hi if dy < 2 or dx in (0, 9) and dy < 4 else gold
    img.save(os.path.join(RES, "assets", "khazuk", "textures", "block", "hall_seal_front.png"))


def main():
    texture()
    out("assets/khazuk/models/block/hall_seal.json", {
        "parent": "minecraft:block/orientable",
        "textures": {"front": "khazuk:block/hall_seal_front", "side": "minecraft:block/deepslate_bricks",
                     "top": "minecraft:block/deepslate_bricks"}})
    # orientable's front faces north; it should face the room, away from FACING (the hall runs on beyond it)
    turns = {"north": 180, "east": 270, "south": 0, "west": 90}
    out("assets/khazuk/blockstates/hall_seal.json", {"variants": {
        f"facing={f}": ({"model": "khazuk:block/hall_seal", "y": y} if y else {"model": "khazuk:block/hall_seal"}) for f, y in turns.items()}})
    out("assets/khazuk/items/hall_seal.json", {"model": {"type": "minecraft:model", "model": "khazuk:block/hall_seal"}})
    out("data/khazuk/loot_table/blocks/hall_seal.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "khazuk:hall_seal"}],
                                              "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "khazuk:blocks/hall_seal"})
    out("data/khazuk/recipe/hall_seal.json", {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": [" D ", "DGD", " D "], "key": {"D": "minecraft:deepslate_bricks", "G": "minecraft:gold_ingot"},
        "result": {"id": "khazuk:hall_seal", "count": 4}})
    tag = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")
    t = json.load(open(tag, encoding="utf-8"))
    if "khazuk:hall_seal" not in t["values"]:
        t["values"].append("khazuk:hall_seal")
    out("data/minecraft/tags/block/mineable/pickaxe.json", t)
    lang = os.path.join(RES, "assets", "khazuk", "lang", "en_us.json")
    d = json.load(open(lang, encoding="utf-8"))
    d["block.khazuk.hall_seal"] = "Rune-Sealed Hall Door"
    out("assets/khazuk/lang/en_us.json", d)
    print("hall seal assets written")


if __name__ == "__main__":
    main()
