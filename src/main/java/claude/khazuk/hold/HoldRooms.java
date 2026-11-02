package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import claude.settlementcore.room.Rooms;

import java.util.List;
import java.util.Set;

/**
 * The hold's buildings and what each is for, registered with the engine as raised buildings (their templates in
 * data/khazuk/structure/room/, door on the x = 0 face at y 1, facing west; y 0 the ground layer):
 * <ul>
 * <li>{@link #GATE}: the Hold Gate (tools/gen_hold_gate.py --template; the user's picture of a karak's gate): the Miners raise its
 * gatehouse from the Ancestors' Plan, then the god raises the mountain it is cut into ({@link HoldCrag}). The dug hold meets its
 * passage from behind (D1).</li>
 * </ul>
 */
public final class HoldRooms {

    public static final Rooms.Function GATE = Rooms.Function.of("DAWI_HOLD_GATE");

    public static final List<Rooms.Room> ALL = List.of(
            new Rooms.Room("dawi_hold_gate", "Hold Gate", 40, GATE,
                    "A karak's gate cut into the mountain: ancestors carved on its towers, bronze guardians at its door, a road up to it.",
                    32, 43, 31, 0, 1, 15, List.of(), List.of(), List.of()));

    private HoldRooms() {
    }

    /** The hold's buildings, raised ones (the dug halls come in D1's next step), under the mod's namespace. */
    public static void register() {
        Rooms.register(Khazuk.MODID, new Rooms.Catalog(List.of(), List.of(), List.of(), Set.of(), 0, ALL));
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
