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

    // ------------------------------------------------------------------ a hall dug through a seal (the user's save, 2026-11-09)

    /** The gate's door ground (running east from here), and the Shrine behind it: as HoldGateGameTests has them. */
    private static final BlockPos GATE_GROUND = new BlockPos(4, 2, 36);
    private static final BlockPos GATE_SHRINE = new BlockPos(44, 3, 36);

    /** What a built gate and a Mine ordered through its back seal give a test: the hold, the Mine's placement, the seal's bottom middle. */
    private record Dug(claude.khazuk.ShrineBlockEntity hold, PlanPlacement mine, BlockPos seal) {
    }

    /**
     * A Hold Gate raised in the field (free, all at once, handed over), the great Shrine hall's back seal on its east wall (the one the user's Mine was
     * ordered through, template (29, 3, 26)) with rock behind it, and a Mine ordered through that seal as the god's plan does it.
     */
    private static Dug mineThroughTheGatesSeal(GameTestHelper h) {
        return throughTheGatesSeal(h, "dawi_mine");
    }

    /** {@link #mineThroughTheGatesSeal}, the room {@code roomId} ordered through the gate's back seal. */
    private static Dug throughTheGatesSeal(GameTestHelper h, String roomId) {
        var level = h.getLevel();
        h.setBlock(GATE_SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        claude.khazuk.ShrineBlockEntity hold = (claude.khazuk.ShrineBlockEntity) level.getBlockEntity(h.absolutePos(GATE_SHRINE));
        hold.addFood(400, claude.settlementcore.food.Fare.COOKED, "the test");
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        java.util.List<String> told = new java.util.ArrayList<>();
        h.assertTrue(AncestorPlan.setOut(hold, level, gate, h.absolutePos(GATE_GROUND), Direction.EAST, true, c -> told.add(c.getString())),
                "the gate was refused: " + told);
        hold.works().sites().forEach(site -> site.job().finishAll(level));
        hold.works().second(level, 101);
        h.assertTrue(hold.has(HoldRooms.GATE), "the gate wasn't handed over");
        PlanPlacement g = AncestorPlan.placement(gate, h.absolutePos(GATE_GROUND), Direction.EAST);
        BlockPos seal = g.world(new BlockPos(29, 3, 26));
        h.assertTrue(level.getBlockState(seal).getBlock() instanceof HallSeal, "no seal at the gate's back arch: " + level.getBlockState(seal));
        Direction runs = level.getBlockState(seal).getValue(HallSeal.FACING);
        PlanPlacement mine = PlanPlacement.compute(HoldRooms.room(roomId), HallSeal.anchor(level, seal), runs);
        h.assertTrue(HallSeal.arch(level, mine.door()).size() == 9, "the arch is " + HallSeal.arch(level, mine.door()).size() + " seals, not 9");
        for (BlockPos p : BlockPos.betweenClosed(mine.box().minX(), mine.box().minY(), mine.box().minZ(), mine.box().maxX(), mine.box().maxY(), mine.box().maxZ())) {
            int along = (p.getX() - mine.door().getX()) * runs.getStepX() + (p.getZ() - mine.door().getZ()) * runs.getStepZ();
            if (along > 0 && level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);   // the mountain behind the wall
            }
        }
        told.clear();
        h.assertTrue(AncestorPlan.dig(hold, level, mine, true, c -> told.add(c.getString())), "the " + roomId + " through the seal was refused: " + told);
        return new Dug(hold, mine, seal);
    }

    /** The next room ordered through the seal at {@code seal} (an arch of the room before), the rock behind it filled, begun and dug whole. */
    private static PlanPlacement digOnThrough(GameTestHelper h, claude.khazuk.ShrineBlockEntity hold, BlockPos seal, String roomId) {
        var level = h.getLevel();
        Rooms.Room room = HoldRooms.room(roomId);
        PlanPlacement next = AncestorPlan.aimedAt(level, room, seal, 0, Direction.WEST);   // whichever way the god looks
        Direction runs = level.getBlockState(seal).getValue(HallSeal.FACING);
        h.assertTrue(next.door().equals(HallSeal.anchor(level, seal)) && next.facing() == runs, "the " + roomId + " aimed at the seal: its door "
                + h.relativePos(next.door()) + " running " + next.facing() + ", not the arch's bottom middle running " + runs);
        for (BlockPos p : BlockPos.betweenClosed(next.box().minX(), next.box().minY(), next.box().minZ(), next.box().maxX(), next.box().maxY(), next.box().maxZ())) {
            int along = (p.getX() - next.door().getX()) * runs.getStepX() + (p.getZ() - next.door().getZ()) * runs.getStepZ();
            if (along > 0 && level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            }
        }
        java.util.List<String> told = new java.util.ArrayList<>();
        h.assertTrue(AncestorPlan.dig(hold, level, next, true, c -> told.add(c.getString())), "the " + roomId + " through the seal was refused: " + told);
        hold.works().digs().forEach(d -> d.job().finishAll(level));
        hold.works().second(level, 303);
        h.assertTrue(hold.works().digs().isEmpty() && HallSeal.arch(level, next.door()).isEmpty() && level.getBlockState(next.door()).isAir(),
                "the " + roomId + " wasn't dug through its seal: digs " + hold.works().digs().size() + ", the door " + level.getBlockState(next.door()));
        return next;
    }

    /**
     * The hold's roads (the user, 2026-11-09: "why dont we have hallways like skaven do", then "yeah lets do small for now"): a Hold Road dug
     * through the gate's back seal ends in a sealed door of its own, and a Road Crossing is dug on through it; the crossing's own three
     * seals stand, its side ones a door the next plan runs either way through.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aRoadEndsInASealedDoorTheHoldDigsOnThrough(GameTestHelper h) {
        var level = h.getLevel();
        Dug d = throughTheGatesSeal(h, "dawi_road");
        d.hold().works().digs().forEach(w -> w.job().finishAll(level));
        d.hold().works().second(level, 202);
        h.assertTrue(d.hold().has(HoldRooms.ROAD) && HallSeal.arch(level, d.mine().door()).isEmpty(), "the road wasn't dug through the gate's seal");
        BlockPos far = d.mine().world(new BlockPos(14, 2, 3));
        h.assertTrue(level.getBlockState(far).getBlock() instanceof HallSeal && level.getBlockState(far).getValue(HallSeal.FACING) == d.mine().facing()
                && HallSeal.arch(level, HallSeal.anchor(level, far)).size() == 9, "the road's far end isn't a sealed door running on: " + level.getBlockState(far));
        PlanPlacement crossing = digOnThrough(h, d.hold(), far, "dawi_road_crossing");
        int sides = 0;
        for (BlockPos l : new BlockPos[]{new BlockPos(10, 2, 5), new BlockPos(5, 2, 0), new BlockPos(5, 2, 10)}) {
            BlockPos w = crossing.world(l);
            sides += level.getBlockState(w).getBlock() instanceof HallSeal && HallSeal.arch(level, HallSeal.anchor(level, w)).size() == 9 ? 1 : 0;
        }
        h.assertTrue(sides == 3, "the crossing has " + sides + " whole sealed doors, not 3 (ahead and either side)");
        h.succeed();
    }

    /**
     * A Road Descent dug through a sealed door: its stair goes down 8 to a landing whose far end is a sealed door, the next hall's door 8
     * under the one it was dug from. (A sealed door set high in the open field: the gate's floor is too near the field's own for a descent.)
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aRoadDescentGoesDownEightToASealedDoor(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(GATE_SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        claude.khazuk.ShrineBlockEntity hold = (claude.khazuk.ShrineBlockEntity) level.getBlockEntity(h.absolutePos(GATE_SHRINE));
        BlockPos door = h.absolutePos(new BlockPos(30, 20, 36));
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean seal = Math.abs(dz) <= 1 && dy <= 2;
                level.setBlock(door.offset(0, dy, dz), seal ? KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.EAST)
                        : Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        PlanPlacement descent = digOnThrough(h, hold, door.above(), "dawi_road_descent");
        BlockPos foot = descent.world(new BlockPos(16, 2, 3));
        h.assertTrue(level.getBlockState(foot).getBlock() instanceof HallSeal && level.getBlockState(foot).getValue(HallSeal.FACING) == descent.facing()
                && HallSeal.anchor(level, foot).getY() == descent.door().getY() - 8, "the descent's foot isn't a sealed door 8 down: "
                + level.getBlockState(foot) + " at " + h.relativePos(foot) + ", its door at " + h.relativePos(descent.door()));
        h.assertTrue(level.getBlockState(descent.world(new BlockPos(6, 4, 3))).getBlock() instanceof net.minecraft.world.level.block.StairBlock,
                "the descent has no stair half way down: " + level.getBlockState(descent.world(new BlockPos(6, 4, 3))));
        h.succeed();
    }

    /**
     * The user's save (2026-11-09, the hold at -448 -31 4, the Mine ordered through the gate's seal at -449 -32 15): the dig was 55% done and the
     * seal still whole, ten Dawi bunched against it in the hall. The hold's keep-out for a dig (ShrineBlockEntity#keepOutCells) keeps every
     * other building's masonry, and the seals are masonry of the gate (a template cell that is no air): the arch the Mine is dug through was
     * left out of its own dig, so the Mine's doorway cells were never tasks and nobody could cut them. Dug whole, the arch must be gone and
     * the gate's other arch beside it (the Mine's face is wider than its door) must stand.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aMineDugThroughTheGatesSealCutsTheSeal(GameTestHelper h) {
        var level = h.getLevel();
        Dug d = mineThroughTheGatesSeal(h);
        BlockPos otherSeal = AncestorPlan.placement(HoldRooms.room("dawi_hold_gate"), h.absolutePos(GATE_GROUND), Direction.EAST).world(new BlockPos(21, 3, 26));
        h.assertTrue(level.getBlockState(otherSeal).getBlock() instanceof HallSeal, "the gate's other arch isn't built");
        java.util.List<BlockPos> arch = HallSeal.arch(level, d.mine().door());
        var job = d.hold().works().digs().get(0).job();
        job.finishAll(level);
        int standing = 0;
        StringBuilder left = new StringBuilder();
        for (BlockPos p : arch) {
            if (level.getBlockState(p).getBlock() instanceof HallSeal) {
                standing++;
                left.append(h.relativePos(p).toShortString()).append(' ');
            }
        }
        h.assertTrue(standing == 0, "the Mine was dug whole (" + job.finishedCount() + " of " + job.taskCount() + " cells) and " + standing + " of "
                + arch.size() + " seal blocks of its doorway still stand: " + left + "(the door at " + h.relativePos(d.mine().door()) + ", running "
                + d.mine().facing() + ")");
        h.assertTrue(level.getBlockState(otherSeal).getBlock() instanceof HallSeal, "the Mine cut the gate's other arch beside its door: "
                + level.getBlockState(otherSeal));
        h.succeed();
    }

    /**
     * What the user saw: the Mine's Miners follow the dig in through the opened door, not bunch in the hall against the seal while the settlement
     * digs the Mine on its own (its crew's chores shelved as unreachable: DigWork#shelve). A Miner must stand beyond the wall plane, in the Mine.
     */
    @GameTest(template = "field", timeoutTicks = 2400, batch = "khazuk")
    public static void theMinersFollowTheirDigThroughTheGatesSeal(GameTestHelper h) {
        var level = h.getLevel();
        Dug d = mineThroughTheGatesSeal(h);
        d.hold().addToReserve(4, 0);
        Direction runs = d.mine().facing();
        var job = d.hold().works().digs().get(0).job();
        h.startSequence()
                .thenWaitUntil(() -> {
                    int inside = 0;
                    StringBuilder where = new StringBuilder();
                    for (java.util.UUID id : d.hold().works().crewIds()) {
                        var e = level.getEntity(id);
                        if (e == null) {
                            continue;
                        }
                        BlockPos p = e.blockPosition();
                        int along = (p.getX() - d.mine().door().getX()) * runs.getStepX() + (p.getZ() - d.mine().door().getZ()) * runs.getStepZ();
                        inside += along >= 2 && d.mine().box().isInside(p) ? 1 : 0;
                        where.append(h.relativePos(p).toShortString()).append(" (").append(along).append(" along) ");
                    }
                    h.assertTrue(inside > 0, "tick " + h.getTick() + ": no Miner of the crew has followed the dig through the seal: crew " + where
                            + ", dig " + job.finishedCount() + "/" + job.taskCount() + " cells, the door is " + level.getBlockState(d.mine().door()).getBlock()
                            + " at " + h.relativePos(d.mine().door()));
                })
                .thenSucceed();
    }

    /**
     * A dig begun before the keep-out let the seals go (a save from the jar of 2026-11-09 00:31 and before) has its doorway's cells in its saved
     * keep-out for good, and was dug on beyond a whole seal: its hold cuts the arch once the dig has begun.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aSealStandingAtAnOldDigsDoorIsCut(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(GATE_SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        claude.khazuk.ShrineBlockEntity hold = (claude.khazuk.ShrineBlockEntity) level.getBlockEntity(h.absolutePos(GATE_SHRINE));
        BlockState seal = KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.EAST);
        BlockPos door = h.absolutePos(new BlockPos(10, 3, 20));
        PlanPlacement pl = PlanPlacement.compute(HoldRooms.room("dawi_mine"), door, Direction.EAST);
        for (BlockPos p : BlockPos.betweenClosed(pl.box().minX(), pl.box().minY(), pl.box().minZ(), pl.box().maxX(), pl.box().maxY(), pl.box().maxZ())) {
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        java.util.List<String> told = new java.util.ArrayList<>();
        h.assertTrue(AncestorPlan.dig(hold, level, pl, true, c -> told.add(c.getString())), "the dig was refused: " + told);
        hold.works().digs().get(0).job().finishAll(level);       // dug whole, then the old jar's whole arch put back
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(door.offset(0, dy, dz), seal, 2);
            }
        }
        h.assertTrue(HallSeal.arch(level, door).size() == 9, "the arch isn't back");
        int cut = hold.openSealedDoors(level);
        h.assertTrue(cut == 9 && level.getBlockState(door).isAir() && level.getBlockState(door.above(2).north()).isAir(),
                "the old dig's seal: " + cut + " blocks cut (not 9), the door is " + level.getBlockState(door));
        h.succeed();
    }
}
