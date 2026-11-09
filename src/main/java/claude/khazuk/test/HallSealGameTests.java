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

    /** The gate's template holds four sealed arches, 3 x 3 each, two facing out of the great Shrine hall either side (2026-11-09). */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void theGatesShrineHallIsBuiltWithTwoSealedArches(GameTestHelper h) {
        int north = 0, south = 0;
        for (RoomTemplates.Entry e : RoomTemplates.get(h.getLevel(), "dawi_hold_gate")) {
            if (e.state().getBlock() instanceof HallSeal) {
                Direction f = e.state().getValue(HallSeal.FACING);
                if (f == Direction.NORTH && e.local().getZ() == 4) {
                    north++;
                } else if (f == Direction.SOUTH && e.local().getZ() == 26) {
                    south++;
                }
            }
        }
        h.assertTrue(north == 18 && south == 18, "the gate's sealed arches: " + north + " seals facing north in its west wall, " + south
                + " south in its east (18 each: two arches)");
        h.succeed();
    }

    /** The Great Hall is built with a sealed arch in each side wall too, so the hold branches hall to hall. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void theGreatHallIsBuiltWithTwoSealedArches(GameTestHelper h) {
        int north = 0, south = 0;
        Rooms.Room gh = HoldRooms.room("dawi_grand_hall");
        for (RoomTemplates.Entry e : RoomTemplates.get(h.getLevel(), gh.id())) {
            if (e.state().getBlock() instanceof HallSeal) {
                Direction f = e.state().getValue(HallSeal.FACING);
                if (f == Direction.NORTH && e.local().getZ() == 0) {
                    north++;
                } else if (f == Direction.SOUTH && e.local().getZ() == gh.sizeZ() - 1) {
                    south++;
                }
            }
        }
        h.assertTrue(north == 9 && south == 9, "the Great Hall's sealed arches: " + north + " in its north wall, " + south + " in its south");
        h.succeed();
    }

    /**
     * The runtime walls guard (AncestorPlan#cutsInto): a Great Hall dug through the gate's seal cuts none of it; a hall aimed back through
     * the gate's passage at its gatehouse is refused, naming the gate.
     */
    @GameTest(template = "field", timeoutTicks = 20, batch = "khazuk")
    public static void aHallMayNotCutIntoAnother(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(new BlockPos(64, 3, 64), KRegistry.SHRINE.get().defaultBlockState());
        if (!(level.getBlockEntity(h.absolutePos(new BlockPos(64, 3, 64))) instanceof claude.khazuk.ShrineBlockEntity hold)) {
            throw h.assertionException("no Shrine");
        }
        hold.claim(h.makeMockServerPlayerInLevel(), "Karak Test");
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        PlanPlacement g = AncestorPlan.placement(gate, h.absolutePos(new BlockPos(4, 2, 36)), Direction.EAST);
        hold.addBuilding(new claude.settlementcore.settlement.Building(gate.id(), g.box(), g.door(), g.facing()), false);
        BlockPos seal = g.world(new BlockPos(29, 3, 26));            // the great hall's back seal in its east wall, its bottom middle
        Direction runs = RoomTemplates.get(level, gate.id()).stream().filter(e -> e.local().equals(new BlockPos(29, 3, 26))).findFirst()
                .orElseThrow().state().rotate(g.rotation()).getValue(HallSeal.FACING);
        PlanPlacement through = PlanPlacement.compute(HoldRooms.room("dawi_grand_hall"), seal, runs);
        String ok = AncestorPlan.cutsInto(hold, level, through);
        h.assertTrue(ok == null, "a Great Hall through the gate's own seal was refused: " + ok);
        PlanPlacement back = PlanPlacement.compute(HoldRooms.room("dawi_barracks"), g.world(new BlockPos(20, 3, 15)), Direction.WEST);
        String no = AncestorPlan.cutsInto(hold, level, back);
        h.assertTrue(no != null && no.contains("Hold Gate"), "a hall cut back through the gatehouse wasn't refused: " + no);
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
            for (int mid : new int[]{21, 29}) {                  // the great hall's two arches a side (their middles x' 21 and 29)
                if (Math.abs(e.local().getX() - mid) <= 2 && e.local().getY() >= 3 && e.local().getY() <= 7
                        && (e.local().getZ() == 4 || e.local().getZ() == 26)) {
                    level.setBlock(pl.world(e.local()), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);   // the old wall
                }
            }
        }
        BlockPos changed = pl.world(new BlockPos(29, 4, 26));
        level.setBlock(changed, Blocks.GLASS.defaultBlockState(), 2);
        h.assertTrue(HoldCrag.fitSeals(level, pl), "the arches' land wasn't loaded");
        for (int x : new int[]{21, 29}) {
            BlockPos hall = pl.world(new BlockPos(x, 4, 15));    // the hall's middle, level with the arch
            for (int z : new int[]{4, 26}) {
                BlockPos s = pl.world(new BlockPos(x, 3, z));
                BlockState st = level.getBlockState(s);
                h.assertTrue(st.getBlock() instanceof HallSeal, "no seal fitted at template (" + x + ", 3, " + z + "): " + st);
                Direction out = Direction.getApproximateNearest(s.getX() - hall.getX(), 0, s.getZ() - hall.getZ());
                h.assertTrue(st.getValue(HallSeal.FACING) == out, "the seal at (" + x + ", 3, " + z + ") faces " + st.getValue(HallSeal.FACING)
                        + ", not out of the Shrine hall (" + out + ")");
            }
        }
        h.assertTrue(level.getBlockState(changed).is(Blocks.GLASS), "a wall cell someone changed was overwritten");
        h.assertTrue(level.getBlockState(pl.world(new BlockPos(29, 7, 26))).is(KRegistry.GILDED_STONE.get()), "the arch's keystone isn't in");
        h.succeed();
    }
}
