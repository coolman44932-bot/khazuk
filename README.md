# Khazuk!

The Dwarfs, as a race mod on [Settlement Core](https://github.com/coolman44932-bot/settlementcore). You are their Ancestor Gods
(Grungni, Valaya and Grimnir): wake the Dawi from the deep rock, and they dig a hold into the mountain round your Ancestor Shrine, mine
its gold, brew its ale, keep the Book of Grudges and fight for it.

- **Minecraft 26.1.2**, NeoForge 26.1.2.125, GeckoLib 5.5.2, Java 25.
- Needs **Settlement Core 0.5.5** and **GeckoLib** (both required).
- Mod id `khazuk`, package `claude.khazuk`. MIT licence (`LICENSE`).

## In the game

- **The waking:** strike a gold ingot on deepslate below y 0 and the first Dawi step out of the stone. They name their hold and hand
  you the Ancestor Shrine and a founding kit: the Plans of the Hold Gate, the Great Hall and the mine, 90 gold and bread. Set the
  Shrine down and its first Miners and Warriors come up. Later Shrines are crafted, for a second hold and a third.
- **The hold:** Ancestors' Plans for the Hold Gate, the Keep, the Great Hall, the mine, the barracks, the brewery, the treasury and the
  Runic Forge. Miners dig the halls out of the rock; the gatehouse and walls are raised on the surface. The Keep and the halls make room
  for more Dawi up at once.
- **Gold, ale and runes:** gold is mined and paid for plans and units, ale brewed from wheat makes the hold hit harder, walk faster
  and work faster, a Treasury makes gold draw gold, and the Runesmith strikes runes into its kit.
- **The Book of Grudges (Dammaz Kron):** what kills a Dwarf, breaks the hold or steals its gold is written down. Settle a grudge for
  gold, ale and runes, and the hold hits that foe harder from then on.
- **The units:** Miners, Dwarf Warriors, Quarrellers, Thunderers, Longbeards (with great weapons too), the Runesmith and the Runelord
  with the Anvil of Doom. Warriors with enough kills become Longbeards. Every unit rolls its own look: beards, helms and colours, as
  in Total War: WARHAMMER III.
- **The god's powers,** paid from the hold's devotion (prayer at the Shrine, ale, Runesmiths and settled grudges): Grungni's Hammer,
  Valaya's Ward and Grimnir's Fury. Ride Thorgrim's **Throne of Power** for cheaper powers.

## Build and test

```bash
gradlew.bat build                                  # build/libs/khazuk-<v>.jar
gradlew.bat runGameTestServer                      # every Khazuk! GameTest, then the server stops
gradlew.bat runGameTestServer -Ponly=<batch,test>  # one batch, or tests by name
gradlew.bat runClient                              # a dev client with the Dwarfs and the engine
```

The engine is the jar in `libs/`, pinned by `libs/settlementcore.pin` (its sha256), so every commit builds against the engine it was
tested with; `-PcoreJar=<path>` swaps in another. The models are made in Blockbench from scripts, and the generators in `tools/` make
the halls' and the gate's structures and the mod's data. `docs/plan.md` is the design and the order things were built in.

GameTests register only in dev runs and on the GameTest server; a player's game has none of them.
