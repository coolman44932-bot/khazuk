package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import claude.settlementcore.room.Rooms;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The hold's buildings and what each is for, registered with the engine (their templates in data/khazuk/structure/room/, door on the
 * x = 0 face at y 1, facing west; y 0 the floor):
 * <ul>
 * <li>{@link #GATE}: the Hold Gate (tools/gen_hold_gate.py --template --write; the user's picture of a karak's gate), since 0.13.0 the
 * hold's founding hall (the user, 2026-11-04: the hold goes behind its gate, not before it): raised by the god before the hold exists
 * (the engine's Founding), its passage running into the Shrine's hall, where the Shrine is set on the dais; the hold is born owning the
 * gate, and the god raises the mountain it is cut into round it ({@link HoldCrag}). In a hold founded before (on a Keep), its plan sets
 * it out for the Miners to raise, as before.</li>
 * <li>{@link #KEEP}: the hold's keep. Since 0.13.0 the King's Throne Room, dug off the hold's halls (tools/gen_halls.py), where
 * Thorgrim's Throne of Power stands; the old surface Keep (tools/gen_keep.py, "dawi_keep") stays known for the holds founded on one, and
 * no plan sets one out any more.</li>
 * <li>Dug (tools/gen_halls.py; the engine's dig side, Works#dig): the {@link #GREAT_HALL}, the {@link #BARRACKS} and the mine (the
 * engine's Ore Mine: its Miners drive drifts out from its three drift mouths after ore, into its Ore Carts). Each is dug off a wall of
 * the hold's open space (the gate's passage, a hall), its door where the plan is aimed.</li>
 * </ul>
 */
public final class HoldRooms {

    public static final Rooms.Function GATE = Rooms.Function.of("DAWI_HOLD_GATE");
    public static final Rooms.Function GREAT_HALL = Rooms.Function.of("DAWI_GREAT_HALL");
    public static final Rooms.Function BARRACKS = Rooms.Function.of("DAWI_BARRACKS");
    public static final Rooms.Function KEEP = Rooms.Function.of("DAWI_KEEP");
    /** D3: the hold brews its ale here (hold/Brewhouse). */
    public static final Rooms.Function BREWERY = Rooms.Function.of("DAWI_BREWERY");
    /** D3: the hold's hoard: more gold from every ore, and the hoard grows (hold/Brewhouse). */
    public static final Rooms.Function TREASURY = Rooms.Function.of("DAWI_TREASURY");
    /** 0.14.2: the Shrine of Grimnir, where the Slayers take their oath (hold/Ranks raises Troll Slayers). */
    public static final Rooms.Function SLAYER_SHRINE = Rooms.Function.of("DAWI_SLAYER_SHRINE");
    /** 0.14.2: the Engineers' Guild: Engineer Slayers and the Slayer mech (hold/Ranks); the war machines' home to come. */
    public static final Rooms.Function ENGINEERS_GUILD = Rooms.Function.of("DAWI_ENGINEERS_GUILD");
    /** D5: the Runesmith's hall, where the hold's runes are struck (hold/Runes). */
    public static final Rooms.Function RUNIC_FORGE = Rooms.Function.of("DAWI_RUNIC_FORGE");

    public static final List<Rooms.Room> RAISED = List.of(
            new Rooms.Room("dawi_hold_gate", "Hold Gate", 40, GATE,
                    "A karak's gate cut into the mountain: ancestors carved on its towers, bronze guardians at its door, and down its passage the "
                            + "Shrine's hall, where the Shrine stands on its dais.",
                    32, 43, 31, 0, 1, 15, List.of(), List.of(), List.of(
                            new Rooms.Station(claude.settlementcore.settlement.Founding.HEART, 28, 4, 15, Direction.WEST))),
            new Rooms.Room("dawi_keep", "Keep", 0, KEEP,                 // the old founding hall (before 0.13.0): known, never set out
                    "The hold's old founding: a stone keep in a walled yard, its gate shut against foes, the Shrine on the dais inside.",
                    25, 14, 25, 0, 1, 12, List.of(), List.of(), List.of(
                            new Rooms.Station(claude.settlementcore.settlement.Founding.HEART, 14, 1, 12, Direction.WEST))));

    public static final List<Rooms.Room> DUG = List.of(
            new Rooms.Room("dawi_great_hall", "Great Hall", 30, GREAT_HALL,     // the first Great Hall (before 0.13.0): known for the halls dug
                    "The hold's heart under the mountain: pillars banded in gold, a long hearth between the tables, the god's seat on the dais.",
                    27, 13, 21, 0, 1, 10, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_grand_hall", "Great Hall", 30, GREAT_HALL,     // 0.13.0: the Great Hall rebuilt grand, under its own id so the
                    // halls dug before keep their own box and template (the review of 31991f2); the Great Hall's plan digs this one
                    "The hold's heart under the mountain: ancestor kings carved on gold-banded pillars, a stepped vault, a long hearth between "
                            + "the feasting tables, and the high seat on the dais under the Ancestors' face.",
                    33, 17, 25, 0, 1, 12, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_throne_room", "King's Throne Room", 25, KEEP,  // 0.13.0: the hold's keep, dug (it was a surface Keep)
                    "Eight ancestor kings in their niches, banners and braziers, and a red runner to the dais where Thorgrim's Throne of Power stands.",
                    19, 13, 17, 0, 1, 8, List.of(), List.of(), List.of(new Rooms.Station("throne", 15, 3, 8, Direction.WEST))),
            new Rooms.Room("dawi_barracks", "Barracks", 15, BARRACKS,
                    "Bunks for the hold's warriors, their grindstones and smithing tables, and a brazier to sit by.",
                    17, 8, 13, 0, 1, 6, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_mine", "Mine", 20, Rooms.Function.OREMINE,
                    "Timber props and rails: the Miners drive drifts out from its three mouths after ore, and fill its Ore Carts.",
                    15, 8, 15, 0, 1, 7, List.of(), List.of(), List.of(
                            new Rooms.Station("drift", 14, 1, 7, Direction.EAST),
                            new Rooms.Station("drift", 7, 1, 0, Direction.NORTH),
                            new Rooms.Station("drift", 7, 1, 14, Direction.SOUTH))),
            new Rooms.Room("dawi_brewery", "Brewery", 15, BREWERY,
                    "Copper mash tuns over fires, a still, barrels racked to the vault: the hold's ale, brewed from the wheat brought to the Shrine.",
                    15, 8, 13, 0, 1, 6, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_treasury", "Treasury", 25, TREASURY,
                    "The hoard behind a strong door: heaps of gold under an ancestor's eye. Every ore pays more, and gold draws gold.",
                    13, 8, 11, 0, 1, 5, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_slayer_shrine", "Shrine of Grimnir", 25, SLAYER_SHRINE,   // 0.14.2: where the Slayers take their oath
                    "Grimnir in blackstone with an axe in each fist and a crest of fire, an oath altar between braziers, fire under iron in the floor, "
                            + "skulls and orange banners: the hold's Troll Slayers swear their oath here.",
                    17, 10, 15, 0, 1, 7, List.of(), List.of(), List.of(new Rooms.Station("oath", 9, 1, 7, Direction.EAST))),
            new Rooms.Room("dawi_engineers_guild", "Engineers' Guild", 30, ENGINEERS_GUILD,   // 0.14.2: its door 5 wide for the mech
                    "A gantry and hoist over a copper assembly bay, boilers on blast furnaces, a workbench of anvils and blueprints, forges under "
                            + "copper hoods and a firing range: the Engineer Slayers work here, and the Slayer mech is built in the bay.",
                    19, 10, 15, 0, 1, 7, List.of(), List.of(), List.of(new Rooms.Station("bay", 12, 1, 7, Direction.WEST),
                            new Rooms.Station("bench", 13, 1, 2, Direction.NORTH))),
            new Rooms.Room("dawi_runic_forge", "Runic Forge", 30, RUNIC_FORGE,
                    "The Runesmith's hall: the rune anvil in a ring of soul fire, the forge's lava behind bars, runes cut into the walls.",
                    15, 9, 13, 0, 1, 6, List.of(), List.of(), List.of(new Rooms.Station("anvil", 9, 1, 6, Direction.WEST))));

    public static final List<Rooms.Room> ALL = all();

    private static List<Rooms.Room> all() {
        List<Rooms.Room> out = new ArrayList<>(RAISED);
        out.addAll(DUG);
        return List.copyOf(out);
    }

    private HoldRooms() {
    }

    /** The hold's buildings under the mod's namespace: the dug ones as the plans' rooms, the raised ones as raised. */
    public static void register() {
        Rooms.register(Khazuk.MODID, new Rooms.Catalog(DUG, List.of(), List.of(), Set.of(), 0, RAISED));
    }

    /** The building of this id, or null. */
    public static Rooms.Room room(String id) {
        for (Rooms.Room r : ALL) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }
}
