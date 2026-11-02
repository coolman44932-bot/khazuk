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
 * <li>{@link #GATE}: the Hold Gate (tools/gen_hold_gate.py --template; the user's picture of a karak's gate), raised: the Miners raise its
 * gatehouse from the Ancestors' Plan, then the god raises the mountain it is cut into ({@link HoldCrag}).</li>
 * <li>Dug (tools/gen_halls.py; the engine's dig side, Works#dig): the {@link #GREAT_HALL}, the {@link #BARRACKS} and the mine (the
 * engine's Ore Mine: its Miners drive drifts out from its three drift mouths after ore, into its Ore Carts). Each is dug off a wall of
 * the hold's open space (the gate's passage, a hall), its door where the plan is aimed.</li>
 * </ul>
 */
public final class HoldRooms {

    public static final Rooms.Function GATE = Rooms.Function.of("DAWI_HOLD_GATE");
    public static final Rooms.Function GREAT_HALL = Rooms.Function.of("DAWI_GREAT_HALL");
    public static final Rooms.Function BARRACKS = Rooms.Function.of("DAWI_BARRACKS");

    public static final List<Rooms.Room> RAISED = List.of(
            new Rooms.Room("dawi_hold_gate", "Hold Gate", 40, GATE,
                    "A karak's gate cut into the mountain: ancestors carved on its towers, bronze guardians at its door, a road up to it.",
                    32, 43, 31, 0, 1, 15, List.of(), List.of(), List.of()));

    public static final List<Rooms.Room> DUG = List.of(
            new Rooms.Room("dawi_great_hall", "Great Hall", 30, GREAT_HALL,
                    "The hold's heart under the mountain: pillars banded in gold, a long hearth between the tables, the god's seat on the dais.",
                    27, 13, 21, 0, 1, 10, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_barracks", "Barracks", 15, BARRACKS,
                    "Bunks for the hold's warriors, their anvils and grindstones, and a brazier to sit by.",
                    17, 8, 13, 0, 1, 6, List.of(), List.of(), List.of()),
            new Rooms.Room("dawi_mine", "Mine", 20, Rooms.Function.OREMINE,
                    "Timber props and rails: the Miners drive drifts out from its three mouths after ore, and fill its Ore Carts.",
                    15, 8, 15, 0, 1, 7, List.of(), List.of(), List.of(
                            new Rooms.Station("drift", 14, 1, 7, Direction.EAST),
                            new Rooms.Station("drift", 7, 1, 0, Direction.NORTH),
                            new Rooms.Station("drift", 7, 1, 14, Direction.SOUTH))));

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
