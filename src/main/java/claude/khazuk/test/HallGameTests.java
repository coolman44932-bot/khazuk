package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.food.Fare;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.work.MineWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The hold's dug halls (D1; tools/gen_halls.py, the engine's dig side): their templates are whole, their doors open and the mine's barrels
 * named; a hall aimed at a wall is dug off it at your feet's level; the Miners dig the barracks out of solid rock, cell by cell, and the
 * hold has it; a dug mine is the engine's Ore Mine, its three drift mouths ready. In the digsite (solid stone, 44 x 20 x 44), a pocket
 * cut for the Shrine.
 */
@GameTestHolder(Khazuk.MODID)
public class HallGameTests {

    /** The pocket's floor (stone under it) and the Shrine in it. */
    private static final BlockPos POCKET = new BlockPos(8, 4, 20);
    private static final BlockPos SHRINE = POCKET.offset(1, 1, 0);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(POCKET.offset(-2, 1, -3), POCKET.offset(3, 3, 3))) {
            h.setBlock(p, Blocks.AIR);                           // a pocket 6 x 3 x 7 in the rock
        }
        h.setBlock(SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SHRINE));
        hold.addFood(200, Fare.COOKED, "the test");
        return hold;
    }

    /** Each hall's template is read whole: its size the room's, its doorway open; the mine's Ore Carts and Stone Bins named. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theHallsTemplatesAreWhole(GameTestHelper h) {
        for (Rooms.Room room : HoldRooms.DUG) {
            List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), room.id());
            h.assertTrue(cells.size() == room.sizeX() * room.sizeY() * room.sizeZ(), room.id() + "'s template has " + cells.size() + " cells");
            h.assertTrue(!Rooms.isRaised(room.id()), room.id() + " is registered raised, not dug");
            boolean open = false;
            int carts = 0, bins = 0;
            for (RoomTemplates.Entry e : cells) {
                if (e.local().equals(new BlockPos(room.doorX(), room.doorY(), room.doorZ()))) {
                    open = e.state().isAir();
                }
                String name = e.nbt() == null ? "" : e.nbt().getStringOr("CustomName", "");
                carts += name.contains("Ore Cart") ? 1 : 0;
                bins += name.contains("Stone Bin") ? 1 : 0;
            }
            h.assertTrue(open, room.id() + "'s door cell isn't open");
            if (room.function() == Rooms.Function.OREMINE) {
                h.assertTrue(carts == 2 && bins == 2, "the mine has " + carts + " Ore Carts and " + bins + " Stone Bins, not 2 and 2");
            }
        }
        h.succeed();
    }

    /** A hall's plan aimed at a wall cuts its door in that wall at the feet's level, the hall running on away from whoever aims it. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aHallIsAimedAtAWallAtYourFeet(GameTestHelper h) {
        Rooms.Room hall = HoldRooms.room("dawi_great_hall");
        BlockPos wall = new BlockPos(100, 70, 50);
        PlanPlacement pl = AncestorPlan.aimed(hall, wall, 64, Direction.EAST);
        h.assertTrue(pl.door().equals(new BlockPos(100, 64, 50)), "the door is at " + pl.door() + ", not the wall at the feet's level");
        h.assertTrue(pl.box().minX() == 100 && pl.box().maxX() == 126 && pl.box().minY() == 63, "the hall runs " + pl.box() + ", not east from the door");
        h.succeed();
    }

    /**
     * The Miners dig the barracks out of solid rock off the Shrine's pocket: a digger's batch at a time, the hall's own stone, bunks and
     * anvils set as they go; dug out, it's handed over and the hold has it, its bunks in and its space open.
     */
    @GameTest(template = "digsite", timeoutTicks = 8000, batch = "khazuk")
    public static void theMinersDigOutTheBarracks(GameTestHelper h) {
        ShrineBlockEntity hold = hold(h);
        hold.addToReserve(6, 0);
        Rooms.Room room = HoldRooms.room("dawi_barracks");
        BlockPos door = h.absolutePos(POCKET.offset(4, 1, 0));   // the pocket's east wall, at its floor's level
        PlanPlacement pl = PlanPlacement.compute(room, door, Direction.EAST);
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.dig(hold, h.getLevel(), pl, true, c -> told.add(c.getString())), "the dig was refused: " + told);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(hold.works().digs().isEmpty(), "still digging: "
                        + hold.works().digs().stream().map(d -> Math.round(d.job().progress() * 100) + "%").toList() + ", "
                        + h.getLevel().getEntitiesOfClass(Miner.class, new net.minecraft.world.phys.AABB(door).inflate(30)).size() + " Miners near"))
                .thenExecute(() -> {
                    long took = h.getTick();
                    Khazuk.LOG.info("The Miners dug out the barracks (1768 cells) in {} ticks", took);
                    h.assertTrue(took <= 3000, "the barracks took " + took + " ticks to dig, over 150 s");
                    boolean has = false;
                    for (Building b : hold.buildings()) {
                        has |= b.room().equals(room.id());
                    }
                    h.assertTrue(has, "the hold hasn't the barracks");
                    h.assertTrue(h.getLevel().getBlockState(pl.world(new BlockPos(8, 3, 6))).isAir(), "the barracks' middle is still rock");
                    h.assertTrue(h.getLevel().getBlockState(pl.world(new BlockPos(3, 2, 1))).is(Blocks.RED_WOOL), "a bunk's bedding isn't in");
                })
                .thenSucceed();
    }

    /** A dug mine is the engine's Ore Mine: handed over, the hold's works have its MineWork, its three drift mouths. */
    @GameTest(template = "digsite", timeoutTicks = 100, batch = "khazuk")
    public static void aDugMineIsAnOreMine(GameTestHelper h) {
        ShrineBlockEntity hold = hold(h);
        Rooms.Room room = HoldRooms.room("dawi_mine");
        BlockPos door = h.absolutePos(POCKET.offset(4, 1, 0));
        PlanPlacement pl = PlanPlacement.compute(room, door, Direction.EAST);
        h.assertTrue(AncestorPlan.dig(hold, h.getLevel(), pl, true, c -> {
        }), "the mine was refused");
        hold.works().digs().forEach(d -> d.job().finishAll(h.getLevel()));
        hold.works().second(h.getLevel(), 1);
        hold.works().second(h.getLevel(), 2);
        List<MineWork> mines = hold.works().all(MineWork.class);
        h.assertTrue(hold.works().digs().isEmpty() && mines.size() == 1, "after the dig: " + hold.works().digs().size() + " dig(s), "
                + mines.size() + " Ore Mine(s)");
        h.assertTrue(mines.get(0).mouths().size() == 3, "the mine has " + mines.get(0).mouths().size() + " drift mouths, not 3");
        h.assertTrue(mines.get(0).carts(h.getLevel()).size() == 2, "the mine's Ore Carts weren't found");
        h.succeed();
    }
}
