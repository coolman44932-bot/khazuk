"""The hold's ledger sheet: the engine's Ledger screen (settlementcore tools/gen_ledger_gui.py draws it from a palette) in the Dawi's
look, dressed deepslate rimmed and ruled in gold. Writes assets/khazuk/textures/gui/ledger.png (Khazuk's constructor names it in its
LedgerLook).

    python tools/gen_ledger_skin.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "settlementcore", "tools"))
import gen_ledger_gui

DAWI = {
    "panel": "#3a3d44",         # dressed deepslate
    "grain": 0.06,
    "speckle": 0.12,
    "seed": 41,
    "outline": "#0f1012",
    "rim_light": "#e2bd5a",     # gold
    "rim_dark": "#7c5f22",
    "well": "#1b1d21",
    "well_shadow": "#0c0d0f",
    "well_light": "#5d626c",
    "slot": "#26282d",
    "rule": "#c9a24a",
    "stud": "#c9a24a",
    "stud_light": "#f4dc8a",
    "stud_dark": "#6a5220",
    "knob": "#e2bd5a",
    "knob_dull": "#6c6a62",
}

if __name__ == "__main__":
    out = os.path.join(HERE, "..", "src", "main", "resources", "assets", "khazuk", "textures", "gui", "ledger.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    gen_ledger_gui.write(out, DAWI)
    print("wrote", os.path.relpath(out))
