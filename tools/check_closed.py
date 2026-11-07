"""The walls guard (the user, 2026-11-06: "you should have some kind of thing protecting from making wall less rooms"): every hall's open
space, flooded from its door through its template's air, must stay inside its own walls. Air reaching the box's edge anywhere but the door's
own face is a hole: a room standing on the rock (or the mountain) to close it, which a cave, a carve or a neighbour's dig would open. The
Hold Gate's mountain cells inside its box count as walls (they are raised round it, carved or not: HoldCrag).

    python tools/check_closed.py            every template under data/khazuk/structure/room: OK, or where it leaks
Exit 1 on a leak. WallsGameTests runs the same flood in game (everyHallIsClosedByItsOwnWalls).
"""
import json, os, re, sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
sys.path.insert(0, os.path.join(ROOT, "..", "tools"))
from structgen import nbtio  # noqa: E402

ROOMS = os.path.join(ROOT, "src", "main", "java", "claude", "khazuk", "hold", "HoldRooms.java")
TEMPLATES = os.path.join(ROOT, "src", "main", "resources", "data", "khazuk", "structure", "room")
CRAG = os.path.join(ROOT, "src", "main", "resources", "data", "khazuk", "hold", "hold_gate_crag.json")


# The gate's front is the outdoors (or the cave it was carved from): its terrace, its guardians, its road, all open by design, up to the
# facade's face (template x 13: gen_hold_gate.py's z -1). Behind it, the gate must close itself.
OPEN_FRONT = {"dawi_hold_gate": 13}
# Where a room's inside is flooded from, if not its door: the gate's from its Shrine's spot (its door is out on the terrace).
START = {"dawi_hold_gate": (28, 4, 15)}
# Rooms no plan builds any more: the old surface Keep (before 0.13.0), only known for the holds founded on one.
LEGACY = {"dawi_keep"}
# Openings made on purpose: the Mine's three drift mouths (1 wide, 2 high, the middle of each face but the door's), where its Miners drive
# the galleries on into the ore (OREMINE work).
DRIFTS = {"dawi_mine": {(7, 1, 14), (7, 2, 14), (7, 1, 0), (7, 2, 0), (14, 1, 7), (14, 2, 7)}}


def rooms():
    src = open(ROOMS, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'new Rooms\.Room\("(\w+)",.*?"\s*,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),', src, re.S):
        out[m.group(1)] = tuple(int(g) for g in m.groups()[1:])
    return out


def template(path):
    t = nbtio.plain(nbtio.read(path))
    size = t["size"]
    pal = [p["Name"] for p in t["palette"]]
    cells = {}
    for b in t["blocks"]:
        cells[tuple(b["pos"])] = pal[b["state"]]
    return size, cells


def leaks(rid, dims, size, cells):
    sx, sy, sz, dx, dy, dz = dims
    solid_extra = set()
    if rid == "dawi_hold_gate" and os.path.exists(CRAG):
        for c in json.load(open(CRAG))["cells"]:
            solid_extra.add((c[0], c[1], c[2]))

    def open_(p):
        b = cells.get(p, "minecraft:air")
        return (b.endswith(":air") or b.endswith("_door") or b.endswith("fence_gate")) and p not in solid_extra             and p[0] >= OPEN_FRONT.get(rid, 0)                    # a gate's inside is flooded from behind its facade: the outdoors kept out

    start = START.get(rid, (dx, dy, dz))
    if not open_(start):
        return [f"its door cell {start} is not open: {cells.get(start)}"]
    seen = {start}
    q = deque([start])
    out = []
    while q:
        x, y, z = q.popleft()
        front = abs(z - dz) <= 3                                  # the door's own face: the way in
        edge = x == size[0] - 1 or y == 0 or y == size[1] - 1 or z == 0 or z == size[2] - 1 or (x == 0 and not front)
        if edge and (x, y, z) not in DRIFTS.get(rid, ()) and len(out) < 8:
            out.append((x, y, z))
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if 0 <= n[0] < size[0] and 0 <= n[1] < size[1] and 0 <= n[2] < size[2] and n not in seen and open_(n):
                seen.add(n)
                q.append(n)
    return out


def main():
    bad = 0
    rs = rooms()
    for f in sorted(os.listdir(TEMPLATES)):
        rid = f[:-4]
        if rid in LEGACY:
            print(f"--   {rid}: legacy, no plan builds it")
            continue
        if rid not in rs:
            print(f"{rid}: not in HoldRooms (skipped)")
            continue
        size, cells = template(os.path.join(TEMPLATES, f))
        found = leaks(rid, rs[rid], size, cells)
        if found:
            bad += 1
            print(f"LEAK {rid} {size}: open to its box's edge at {found}")
        else:
            print(f"OK   {rid} {size}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
