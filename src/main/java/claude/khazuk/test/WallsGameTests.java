package claude.khazuk.test;

import claude.khazuk.Khazuk;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The walls guard (the user, 2026-11-06: "you should have some kind of thing protecting from making wall less rooms", after a carved Hold
 * Gate stood with holes beside its passage). Every hall's open space, flooded from its door through its template's air, must stay inside
 * its own walls: air reaching its box's edge anywhere but its door's own face is a hole, a room leaning on the rock (or the mountain) to
 * close it, which a cave, a carve or a neighbour's dig opens. The Hold Gate is flooded from its Shrine's spot, behind its facade (before it
 * is the outdoors: its terrace and road), its mountain's cells inside its box counted as walls (raised round it, carved or not). Kept open
 * on purpose: the Mine's three drift mouths. Not checked: the old surface Keep, which no plan builds. tools/check_closed.py runs the same
 * flood offline.
 */
@GameTestHolder(Khazuk.MODID)
public class WallsGameTests {

    /** The gate's facade face, in its template's frame: before it, outdoors. */
    static final int GATE_FRONT = 13;
    static final Map<String, BlockPos> START = Map.of("dawi_hold_gate", new BlockPos(28, 4, 15));
    static final Map<String, Set<BlockPos>> DRIFTS = Map.of("dawi_mine", Set.of(new BlockPos(7, 1, 14), new BlockPos(7, 2, 14),
            new BlockPos(7, 1, 0), new BlockPos(7, 2, 0), new BlockPos(14, 1, 7), new BlockPos(14, 2, 7)));
    static final Set<String> LEGACY = Set.of("dawi_keep");

    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void everyHallIsClosedByItsOwnWalls(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (Rooms.Room r : HoldRooms.ALL) {
            if (LEGACY.contains(r.id())) {
                continue;
            }
            List<RoomTemplates.Entry> t = RoomTemplates.get(h.getLevel(), r.id());
            if (t.isEmpty()) {
                bad.add(r.id() + ": no template");
                continue;
            }
            String leak = leak(h, r, t);
            checked++;
            if (leak != null) {
                bad.add(r.id() + ": " + leak);
            }
        }
        h.assertTrue(bad.isEmpty() && checked >= 10, "halls open to the rock round them (" + checked + " checked): " + bad);
        h.succeed();
    }

    /**
     * The guard catches what it guards against (the review of 6e0b170): a hole knocked in a hall's side wall, and the gate without its
     * mountain's cells inside its box (the holes the user saw beside its passage), are both found.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void theGuardFindsAHoleInAWall(GameTestHelper h) {
        Rooms.Room barracks = HoldRooms.room("dawi_barracks");
        List<RoomTemplates.Entry> t = new ArrayList<>(RoomTemplates.get(h.getLevel(), barracks.id()));
        h.assertTrue(leak(h, barracks, t, true) == null, "the barracks as shipped already leaks");
        int z = barracks.sizeZ() - 1;                            // its side wall: a hole through it at the floor's middle
        BlockPos hole = new BlockPos(barracks.sizeX() / 2, 1, z);
        t.replaceAll(e -> e.local().getX() == hole.getX() && e.local().getY() >= 1 && e.local().getY() <= 2 && e.local().getZ() >= z - 1
                ? new RoomTemplates.Entry(e.local(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null) : e);
        h.assertTrue(leak(h, barracks, t, true) != null, "a hole through the barracks' side wall wasn't found");
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        h.assertTrue(leak(h, gate, RoomTemplates.get(h.getLevel(), gate.id()), false) != null,
                "the gate without its mountain's cells as walls wasn't found open (the holes beside its passage)");
        h.succeed();
    }

    static String leak(GameTestHelper h, Rooms.Room r, List<RoomTemplates.Entry> t) {
        return leak(h, r, t, true);
    }

    /** Where the room's inside reaches its box's edge, or null if its walls close it; {@code crag}: the gate's mountain cells count as walls. */
    static String leak(GameTestHelper h, Rooms.Room r, List<RoomTemplates.Entry> t, boolean crag) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        for (RoomTemplates.Entry e : t) {
            cells.put(e.local(), e.state());
        }
        boolean gate = r.function() == HoldRooms.GATE;
        Set<BlockPos> walls = gate && crag ? HoldCrag.cragCellsInFrame(h.getLevel()) : Set.of();
        int front = gate ? GATE_FRONT : 0;
        int sx = r.sizeX(), sy = r.sizeY(), sz = r.sizeZ();
        BlockPos door = new BlockPos(r.doorX(), r.doorY(), r.doorZ());
        BlockPos start = START.getOrDefault(r.id(), door);
        java.util.function.Predicate<BlockPos> open = p -> {
            BlockState s = cells.get(p);
            boolean air = s == null || s.isAir() || s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock;
            return air && !walls.contains(p) && p.getX() >= front;
        };
        if (!open.test(start)) {
            return "its inside's first cell " + start + " is not open: " + cells.get(start);
        }
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> q = new ArrayDeque<>(List.of(start));
        Set<BlockPos> drifts = DRIFTS.getOrDefault(r.id(), Set.of());
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            boolean doorFace = p.getX() == 0 && Math.abs(p.getZ() - door.getZ()) <= 3;
            boolean edge = p.getX() == sx - 1 || p.getY() == 0 || p.getY() == sy - 1 || p.getZ() == 0 || p.getZ() == sz - 1
                    || (p.getX() == 0 && !doorFace);
            if (edge && !drifts.contains(p)) {
                return "open to its box's edge at " + p;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() >= 0 && n.getX() < sx && n.getY() >= 0 && n.getY() < sy && n.getZ() >= 0 && n.getZ() < sz && seen.add(n) && open.test(n)) {
                    q.add(n);
                }
            }
        }
        return null;
    }
}
