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
    print("ok")


if __name__ == "__main__":
    main()
