package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.food.Fare;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hold Gate (D1; the user's picture of a karak's gate): its template is whole and its door open; set out from the Ancestors' Plan on
 * flat ground in a hold's land it's paid in gold and its site begun; raised, it stands as its template has it and the hold has it; then
 * the god raises its mountain round it, the passage left open, and a rise saved half-way carries on. The field is 72 x 60 x 72, dirt at
 * y 2 over stone, air above (data/khazuk/structure/field.nbt).
 */
@GameTestHolder(Khazuk.MODID)
public class HoldGateGameTests {

    /** The Shrine behind where the gate goes, inside the land it needs. */
    private static final BlockPos SHRINE = new BlockPos(40, 3, 36);
    /** The gate's door ground: it runs east from here, its door toward the west. */
    private static final BlockPos DOOR_GROUND = new BlockPos(4, 2, 36);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SHRINE));
        hold.addFood(200, Fare.COOKED, "the test");
        return hold;
    }

    /** The template is read whole: its size the room's, its door cell open, its gold (the faces' beads, the medallion, the rune) all there. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theHoldGatesTemplateIsWhole(GameTestHelper h) {
        Rooms.Room room = HoldRooms.room("dawi_hold_gate");
        List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), room.id());
        h.assertTrue(cells.size() == room.sizeX() * room.sizeY() * room.sizeZ(), "the template has " + cells.size() + " cells, not "
                + room.sizeX() * room.sizeY() * room.sizeZ());
        int gold = 0;
        boolean doorOpen = false;
        for (RoomTemplates.Entry e : cells) {
            if (e.state().is(KRegistry.GILDED_STONE.get())) {
                gold++;
            }
            if (e.local().equals(new BlockPos(room.doorX(), room.doorY(), room.doorZ())) && e.state().isAir()) {
                doorOpen = true;
            }
        }
        h.assertTrue(doorOpen, "the template's door cell isn't open");
        h.assertTrue(gold >= 30, "the template has only " + gold + " gold blocks");
        h.succeed();
    }

    /**
     * Set out from the Ancestors' Plan in the hold's land, the gate costs its gold and its site begins; raised (all at once, as a finished
     * site), it stands: its door open, the guardians' bronze and the faces' gold in place, and the hold has a Hold Gate.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void theHoldGateIsSetOutAndRaised(GameTestHelper h) {
        ShrineBlockEntity hold = hold(h);
        hold.addCurrency(50, "the test");
        Rooms.Room room = HoldRooms.room("dawi_hold_gate");
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.setOut(hold, h.getLevel(), room, h.absolutePos(DOOR_GROUND), Direction.EAST, false, c -> told.add(c.getString())),
                "the gate was refused: " + told);
        h.assertTrue(hold.currency() == 10 && hold.works().sites().size() == 1, "after the plan: gold " + hold.currency() + " (not 10), "
                + hold.works().sites().size() + " site(s)");
        hold.works().sites().forEach(site -> site.job().finishAll(h.getLevel()));
        hold.works().second(h.getLevel(), 101);
        h.assertTrue(hold.works().sites().isEmpty() && hold.has(HoldRooms.GATE), "the gate wasn't handed over: " + hold.works().sites().size()
                + " site(s)");
        h.assertBlockPresent(Blocks.AIR, DOOR_GROUND.above(3).east(14));    // the doorway, up on the terrace, 14 in from the steps
        int gold = 0, bronze = 0;
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(4, 2, 10)), h.absolutePos(new BlockPos(35, 55, 62)))) {
            var s = h.getLevel().getBlockState(p);
            if (s.is(KRegistry.GILDED_STONE.get())) {
                gold++;
            } else if (s.is(Blocks.WAXED_EXPOSED_COPPER)) {
                bronze++;
            }
        }
        h.assertTrue(gold >= 30 && bronze >= 40, "the raised gate has " + gold + " gold and " + bronze + " bronze");

        // the mountain: a rise saved half-way carries on where it stood, then the rest; the passage stays open through it
        BlockPos door = h.absolutePos(DOOR_GROUND.above());
        int before = solid(h);
        h.assertTrue(hold.crags().second(h.getLevel(), hold, 1000), "the mountain didn't begin to rise");
        CompoundTag saved = new CompoundTag();
        hold.crags().save(saved);
        HoldCrag again = new HoldCrag();
        again.load(saved);
        h.assertTrue(again.raised(door) == 1000 && !again.risen(h.getLevel(), door), "a half-risen mountain reads back at "
                + again.raised(door) + " cells, not 1000");
        h.assertTrue(hold.crags().takeDirty() && !hold.crags().takeDirty(), "a second's rise wasn't marked for saving (once)");
        int halfway = solid(h);
        h.assertTrue(halfway - before >= 900 && halfway - before <= 1000, "a 1000-cell second raised " + (halfway - before));
        int seconds = 0;
        while (hold.crags().second(h.getLevel(), hold, HoldCrag.PER_SECOND) && seconds < 100) {
            seconds++;
        }
        h.assertTrue(hold.crags().risen(h.getLevel(), door), "the mountain hasn't finished after " + seconds + " seconds");
        h.assertTrue(hold.crags().takeDirty(), "the last step of the rise wasn't marked for saving (a restart would raise the summit again)");
        int risen = solid(h) - before;
        h.assertTrue(risen >= 33000, "the mountain is only " + risen + " blocks");   // 33574 cells since the Shrine's hall (0.13.0)
        h.assertBlockPresent(Blocks.AIR, DOOR_GROUND.above(3).east(14));
        h.assertBlockPresent(Blocks.AIR, DOOR_GROUND.above(3).east(24));   // 10 into the passage, through the mountain
        h.assertTrue(!hold.crags().second(h.getLevel(), hold, HoldCrag.PER_SECOND) && !hold.crags().takeDirty(),
                "a risen mountain rose again, or a second with nothing to do was marked for saving");
        // a gate whose mountain would cover the Shrine is refused: the Shrine just behind the gatehouse
        var pl = AncestorPlan.placement(room, h.absolutePos(DOOR_GROUND), Direction.EAST);
        h.assertTrue(HoldCrag.buries(h.getLevel(), pl, h.absolutePos(new BlockPos(30, 3, 36))), "a Shrine inside the mountain isn't buried");
        h.assertTrue(!HoldCrag.buries(h.getLevel(), pl, h.absolutePos(SHRINE)), "the Shrine 5 behind the mountain counts as buried");
        h.succeed();
    }

    /** The field's solid blocks above its ground, round the gate. */
    private static int solid(GameTestHelper h) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 3, 0)), h.absolutePos(new BlockPos(71, 59, 71)))) {
            if (!h.getLevel().getBlockState(p).isAir()) {
                n++;
            }
        }
        return n;
    }
}
