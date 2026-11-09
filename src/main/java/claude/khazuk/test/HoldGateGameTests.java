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
    private static final BlockPos SHRINE = new BlockPos(44, 3, 36);   // 44 since the great Shrine hall's deeper mountain (it ends at x 39)
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
        hold.widening().second(h.getLevel(), hold);
        h.assertTrue(hold.widening().wide(h.absolutePos(DOOR_GROUND.above())) && hold.works().digs().isEmpty(),
                "a gate raised with the great hall isn't marked wide, or a widening was begun for it: " + hold.works().digs().size());
        h.succeed();
    }

    /**
     * A Hold Gate set out over hollow ground is begun, not refused (the user, 2026-11-08: "wherever they are gonna build at so i dont get the
     * message for hollow ground"): a cave 1 and 2 under the gate's floor, under its passage and its tower, and water 1 under it. The engine
     * alone refuses that site for hollow ground; the hold's Miners take it, every one of those cells is a seal of their plan, and raised,
     * each is solid ground again.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aHoldGateSetOutOverACaveIsBegunAndItsMinersFillIt(GameTestHelper h) {
        ShrineBlockEntity hold = hold(h);
        hold.addCurrency(50, "the test");
        Rooms.Room room = HoldRooms.room("dawi_hold_gate");
        List<BlockPos> hollow = new ArrayList<>();
        for (int x = 12; x <= 16; x++) {
            for (int z = 34; z <= 38; z++) {
                for (int y = 0; y <= 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    hollow.add(h.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        BlockPos wet = new BlockPos(24, 1, 30);
        h.setBlock(wet, Blocks.WATER.defaultBlockState());
        hollow.add(h.absolutePos(wet));
        var pl = AncestorPlan.placement(room, h.absolutePos(DOOR_GROUND), Direction.EAST);
        var alone = claude.settlementcore.room.Siting.plan(h.getLevel(), pl, claude.settlementcore.room.RoomShoring.Warren.alone(), true);
        h.assertTrue(alone instanceof claude.settlementcore.room.Siting.Refusal no && no.why().startsWith("The ground is hollow"),
                "the test needs a site the engine alone refuses for hollow ground: " + alone);
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.setOut(hold, h.getLevel(), room, h.absolutePos(DOOR_GROUND), Direction.EAST, false, c -> told.add(c.getString())),
                "the gate over the cave was refused: " + told);
        h.assertTrue(hold.works().sites().size() == 1, hold.works().sites().size() + " site(s) begun");
        var site = hold.works().sites().iterator().next();
        h.assertTrue(site.job() instanceof claude.settlementcore.room.RaiseJob, "the gate's site is " + site.job());
        site.job().finishAll(h.getLevel());
        for (BlockPos c : hollow) {
            var s = h.getLevel().getBlockState(c);
            h.assertTrue(claude.settlementcore.room.Siting.ground(s), "the hollow at " + c.toShortString() + " is " + s + " once the gate is raised");
        }
        h.succeed();
    }

    /**
     * The Miners widen an old Hold Gate's small Shrine hall (the user, 2026-11-09: "the hold gate is too small no space to do anything"; his
     * pick: "Miners widen it"). A gate raised with the great hall, then made old: the great hall's every cell stone but the old small hall
     * (11 by 8 at the passage's end) and the passage. In a second the hold begins the widening dig (free), its door in the old hall's back
     * wall; the gate isn't marked wide yet. Dug and handed over, the next second folds it into the gate: no widening room is left, the gate is
     * marked wide, its mountain rises again from the bottom, and the great hall is there: its open floor where the old wall's rock stood, a
     * pillar's gold band, its four sealed doors, the dais round the Shrine's spot. A gate marked wide begins no dig.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void anOldGatesShrineHallIsWidenedByItsMiners(GameTestHelper h) {
        ShrineBlockEntity hold = hold(h);
        hold.addCurrency(50, "the test");
        Rooms.Room room = HoldRooms.room("dawi_hold_gate");
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.setOut(hold, h.getLevel(), room, h.absolutePos(DOOR_GROUND), Direction.EAST, true, c -> told.add(c.getString())),
                "the gate was refused: " + told);
        hold.works().sites().forEach(site -> site.job().finishAll(h.getLevel()));
        hold.works().second(h.getLevel(), 101);
        var gate = AncestorPlan.placement(room, h.absolutePos(DOOR_GROUND), Direction.EAST);
        h.assertTrue(hold.has(HoldRooms.GATE), "the gate wasn't handed over");
        // made old: the great hall stone, but for the old small hall (x -5..5, z 9..16, up to 10 over its floor) and the passage (x -2..2)
        var level = h.getLevel();
        for (int x = -11; x <= 11; x++) {
            for (int z = 2; z <= 19; z++) {
                for (int y = 1; y <= 14; y++) {                   // local y: the hall's floor (DOOR_Y - 1 = 1) to its roof (HTOP + 1 = 14)
                    boolean oldHall = Math.abs(x) <= 5 && z >= 9 && z <= 16 && y >= 2 && y <= 12;
                    boolean passage = Math.abs(x) <= 2 && z <= 8 && y >= 2 && y <= 10;
                    BlockPos w = gate.world(local(x, y, z));
                    if (w.equals(hold.getBlockPos())) {
                        continue;
                    }
                    level.setBlock(w, oldHall || passage ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos gateDoor = h.absolutePos(DOOR_GROUND.above());
        var widening = new claude.khazuk.hold.GateWidening();
        widening.second(level, hold);
        h.assertTrue(!widening.wide(gateDoor) && hold.works().digs().size() == 1
                        && hold.works().digs().get(0).room() == HoldRooms.GATE_WIDENING,
                "the old gate's widening wasn't begun: wide " + widening.wide(gateDoor) + ", digs " + hold.works().digs().size());
        widening.second(level, hold);
        h.assertTrue(hold.works().digs().size() == 1, "a second second began another widening: " + hold.works().digs().size());
        hold.works().digs().get(0).job().finishAll(level);
        hold.works().second(level, 202);
        h.assertTrue(hold.works().digs().isEmpty() && hold.has(HoldRooms.WIDENING), "the widening wasn't handed over: digs " + hold.works().digs().size());
        widening.second(level, hold);
        h.assertTrue(!hold.has(HoldRooms.WIDENING) && widening.wide(gateDoor) && hold.crags().raised(gateDoor) == 0 && hold.has(HoldRooms.GATE),
                "folded: a widening room " + hold.has(HoldRooms.WIDENING) + ", wide " + widening.wide(gateDoor) + ", the mountain at "
                        + hold.crags().raised(gateDoor) + " (not 0), the gate " + hold.has(HoldRooms.GATE));
        h.assertTrue(level.getBlockState(gate.world(local(8, 3, 5))).isAir() && level.getBlockState(gate.world(local(-8, 3, 15))).isAir(),
                "the great hall's open floor is still the old wall's rock");
        h.assertTrue(level.getBlockState(gate.world(local(6, 6, 9))).is(KRegistry.GILDED_STONE.get()), "the pillar's gold band is "
                + level.getBlockState(gate.world(local(6, 6, 9))));
        int seals = 0;
        for (int sx : new int[]{-11, 11}) {
            for (int sz : new int[]{7, 15}) {
                seals += level.getBlockState(gate.world(local(sx, 2, sz))).getBlock() instanceof claude.khazuk.hold.HallSeal ? 1 : 0;
            }
        }
        h.assertTrue(seals == 4, "the great hall has " + seals + " sealed doors, not 4");
        h.assertTrue(level.getBlockState(gate.world(local(4, 2, 17))).is(Blocks.POLISHED_DEEPSLATE), "the dais where the old back wall stood is "
                + level.getBlockState(gate.world(local(4, 2, 17))));
        int digs = hold.works().digs().size();
        widening.second(level, hold);
        h.assertTrue(hold.works().digs().size() == digs, "a widened gate began another dig");
        h.succeed();
    }

    /** The gate template's cell for gen_hold_gate.py's local (x across, y up, z into the mountain): x' = z + 14, y' = y + 1, z' = x + 15. */
    private static BlockPos local(int x, int y, int z) {
        return new BlockPos(z + 14, y + 1, x + 15);
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
