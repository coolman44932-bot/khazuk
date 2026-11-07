package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HallSeal;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rune-sealed hall doors (the user, 2026-11-06: "digging a hall would kind of mess up the look of the room, there should be a place in the
 * room to dig into"): the Hold Gate's Shrine hall is built with an arch in each side wall, sealed; a dug hall's plan aimed anywhere on a
 * seal is dug through it, its door at the seal's bottom middle, running the way it faces; a gate built before the arches gets them fitted
 * where its wall is still the old brick.
 */
@GameTestHolder(Khazuk.MODID)
public class HallSealGameTests {

    /** The gate's template holds two sealed arches, 3 x 3 each, facing out of the Shrine hall either side. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void theGatesShrineHallIsBuiltWithTwoSealedArches(GameTestHelper h) {
        int north = 0, south = 0;
        for (RoomTemplates.Entry e : RoomTemplates.get(h.getLevel(), "dawi_hold_gate")) {
            if (e.state().getBlock() instanceof HallSeal) {
                Direction f = e.state().getValue(HallSeal.FACING);
                if (f == Direction.NORTH && e.local().getZ() == 9) {
                    north++;
                } else if (f == Direction.SOUTH && e.local().getZ() == 21) {
                    south++;
                }
            }
        }
        h.assertTrue(north == 9 && south == 9, "the gate's sealed arches: " + north + " facing north in its west wall, " + south + " south in its east");
        h.succeed();
    }

    /** Aimed at any block of a 3 x 3 seal, a hall's door goes to its bottom middle, running the way it faces, whichever way the god looks. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void aPlanAimedAtASealIsDugThroughItsMiddle(GameTestHelper h) {
        var level = h.getLevel();
        BlockState seal = KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.EAST);
        for (int z = 4; z <= 6; z++) {
            for (int y = 2; y <= 4; y++) {
                h.setBlock(new BlockPos(10, y, z), seal);
            }
        }
        Rooms.Room mine = HoldRooms.room("dawi_mine");
        for (BlockPos hit : new BlockPos[]{new BlockPos(10, 4, 4), new BlockPos(10, 2, 6), new BlockPos(10, 3, 5)}) {
            PlanPlacement pl = AncestorPlan.aimedAt(level, mine, h.absolutePos(hit), h.absolutePos(hit).getY() + 7, Direction.NORTH);
            h.assertTrue(pl.door().equals(h.absolutePos(new BlockPos(10, 2, 5))) && pl.facing() == Direction.EAST,
                    "aimed at " + hit + ": the door at " + h.relativePos(pl.door()) + " facing " + pl.facing() + ", not the seal's bottom middle facing east");
        }
        BlockState two = KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.WEST);
        h.setBlock(new BlockPos(3, 2, 10), two);                 // two side by side: one door, whichever is aimed at (the review of 6e0b170)
        h.setBlock(new BlockPos(3, 2, 11), two);
        BlockPos a = AncestorPlan.aimedAt(level, mine, h.absolutePos(new BlockPos(3, 2, 10)), 0, Direction.WEST).door();
        BlockPos b = AncestorPlan.aimedAt(level, mine, h.absolutePos(new BlockPos(3, 2, 11)), 0, Direction.WEST).door();
        h.assertTrue(a.equals(b), "a 2-wide seal gives two doors: " + h.relativePos(a) + " and " + h.relativePos(b));
        h.setBlock(new BlockPos(12, 2, 9), KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.SOUTH));
        PlanPlacement one = AncestorPlan.aimedAt(level, mine, h.absolutePos(new BlockPos(12, 2, 9)), 0, Direction.WEST);
        h.assertTrue(one.door().equals(h.absolutePos(new BlockPos(12, 2, 9))) && one.facing() == Direction.SOUTH,
                "a single crafted seal isn't its own door: " + h.relativePos(one.door()) + " " + one.facing());
        h.succeed();
    }

    /**
     * A gate built before its arches gets them fitted, facing out of its Shrine hall, where the wall is still the old brick; a wall cell
     * someone changed is left as it is.
     */
    @GameTest(template = "field", timeoutTicks = 20, batch = "khazuk")
    public static void aGateBuiltBeforeItsArchesGetsThemFitted(GameTestHelper h) {
        fitted(h, new BlockPos(4, 2, 36), Direction.EAST);
    }

    /** The same, the gate turned (running south: the seals' facings turned with it; the review of 6e0b170: east turns nothing). */
    @GameTest(template = "field", timeoutTicks = 20, batch = "khazuk")
    public static void aTurnedGateGetsItsArchesFittedTurned(GameTestHelper h) {
        fitted(h, new BlockPos(36, 2, 4), Direction.SOUTH);
    }

    private static void fitted(GameTestHelper h, BlockPos ground, Direction runs) {
        var level = h.getLevel();
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(ground), runs);
        for (RoomTemplates.Entry e : RoomTemplates.get(level, gate.id())) {
            if (e.local().getX() >= 26 && e.local().getX() <= 30 && e.local().getY() >= 3 && e.local().getY() <= 7
                    && (e.local().getZ() == 9 || e.local().getZ() == 21)) {
                level.setBlock(pl.world(e.local()), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);   // the old wall
            }
        }
        BlockPos changed = pl.world(new BlockPos(28, 4, 21));
        level.setBlock(changed, Blocks.GLASS.defaultBlockState(), 2);
        h.assertTrue(HoldCrag.fitSeals(level, pl), "the arches' land wasn't loaded");
        BlockPos hall = pl.world(new BlockPos(28, 4, 15));
        for (int z : new int[]{9, 21}) {
            BlockPos s = pl.world(new BlockPos(28, 3, z));
            BlockState st = level.getBlockState(s);
            h.assertTrue(st.getBlock() instanceof HallSeal, "no seal fitted at template (28, 3, " + z + "): " + st);
            Direction out = Direction.getApproximateNearest(s.getX() - hall.getX(), 0, s.getZ() - hall.getZ());
            h.assertTrue(st.getValue(HallSeal.FACING) == out, "the seal at (28, 3, " + z + ") faces " + st.getValue(HallSeal.FACING)
                    + ", not out of the Shrine hall (" + out + ")");
        }
        h.assertTrue(level.getBlockState(changed).is(Blocks.GLASS), "a wall cell someone changed was overwritten");
        h.assertTrue(level.getBlockState(pl.world(new BlockPos(28, 7, 21))).is(KRegistry.GILDED_STONE.get()), "the arch's keystone isn't in");
        h.succeed();
    }
}
