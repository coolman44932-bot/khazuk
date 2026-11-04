package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Longbeard;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Ranks;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.work.MineWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One hold played the survival way from the start (the user, 2026-11-04: "do the one long test"), timed: the god strikes his gold on the
 * deep rock and wakes the Dawi; the kit's Shrine set down brings the first Dawi; he hands the Shrine the kit's bread and its gold and orders
 * the Great Hall, the Barracks and the Mine dug, paid out of the kit (65 of its 90); the Miners dig them out of solid rock, striking the
 * gold veins in the Great Hall's rock; the Barracks raises Quarrellers and Thunderers, and the Great Hall Longbeards. He keeps the Shrine fed
 * as a player with a farm would: 32 bread every two minutes. Every step's tick, with the hold's food, gold and people, is logged as
 * "KHAZUK run:" lines for the pacing; the test fails only where the run stalls (a step not reached in its budget).
 *
 * <p>In the digsite (solid stone 44 x 20 x 44): a corridor carved down its west side and along its south, the Shrine in the corridor, the
 * Great Hall and the Barracks dug east off it, the Mine north off the south corridor. 26.1's mock player is always creative, so the digs are
 * ordered through AncestorPlan#dig with free=false: what a survival god's plan does.
 */
@GameTestHolder(Khazuk.MODID)
public class SurvivalRunGameTests {

    private static final BlockPos ROCK = new BlockPos(2, 5, 6), SHRINE = new BlockPos(2, 5, 20);
    private static final BlockPos HALL_DOOR = new BlockPos(4, 5, 11), BARRACKS_DOOR = new BlockPos(4, 5, 30), MINE_DOOR = new BlockPos(29, 5, 39);
    /** Gold veins in the Great Hall's rock (the dig strikes them: the hold's gold). */
    private static final List<BlockPos> VEINS = List.of(new BlockPos(15, 8, 8), new BlockPos(20, 10, 15), new BlockPos(25, 6, 5),
            new BlockPos(10, 12, 18), new BlockPos(28, 9, 12));
    public static final int FEED_EVERY = 2400, FEED_BREAD = 32;
    /** Budgets, in ticks from the strike: past one, the run has stalled. */
    public static final int WAKE_BUDGET = 400, HALLS_BUDGET = 30000, RANKS_BUDGET = 2400;

    private static void line(GameTestHelper h, ShrineBlockEntity hold, String step) {
        Khazuk.LOG.info("KHAZUK run: {} at tick {} ({} s): food {}, gold {}, people {} up and {}+{} in the deeps", step, h.getTick(), h.getTick() / 20,
                hold == null ? "-" : hold.food(), hold == null ? "-" : hold.currency(), hold == null ? "-" : hold.peopleCount(),
                hold == null ? "-" : hold.reserveWorkers(), hold == null ? "-" : hold.reserveSoldiers());
    }

    /** The god uses {@code stack} on the Shrine, as a player does (its block's own useItemOn). */
    private static void hand(GameTestHelper h, ServerPlayer god, BlockPos shrine, ItemStack stack) {
        BlockPos at = h.absolutePos(shrine);
        h.getLevel().getBlockState(at).useItemOn(stack, h.getLevel(), god, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    @GameTest(template = "digsite", timeoutTicks = 36000, batch = "khazuklong")
    public static void aSurvivalHoldFromWakingToLongbeards(GameTestHelper h) {
        h.getLevel().getServer().tickRateManager().requestGameToSprint(36000);   // as fast as the machine goes: a long run, not a slow one
        // the corridors: down the west side, and along the south
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 5, 1), new BlockPos(3, 7, 42))) {
            h.setBlock(p, Blocks.AIR);
        }
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 5, 40), new BlockPos(42, 7, 42))) {
            h.setBlock(p, Blocks.AIR);
        }
        for (BlockPos v : VEINS) {
            h.setBlock(v, Blocks.DEEPSLATE_GOLD_ORE);
        }
        h.setBlock(ROCK, Blocks.DEEPSLATE);
        h.setBlock(ROCK.above(), Blocks.DEEPSLATE);
        BlockPos rock = h.absolutePos(ROCK);
        h.assertTrue(Waking.deepRock(h.getLevel().getBlockState(rock), rock), "the run's rock isn't deep rock at y " + rock.getY());
        @SuppressWarnings("removal")
        ServerPlayer god = h.makeMockServerPlayerInLevel();
        Vec3 stand = h.absoluteVec(Vec3.atBottomCenterOf(ROCK.south(2)));
        god.snapTo(stand.x, stand.y, stand.z);
        Waking.begin(h.getLevel(), god, rock);
        line(h, null, "the god strikes his gold on the deep rock");

        Map<String, Long> at = new LinkedHashMap<>();
        ShrineBlockEntity[] hold = new ShrineBlockEntity[1];
        int[] goldBefore = new int[1];
        List<String> told = new ArrayList<>();
        h.onEachTick(() -> {
            long now = h.getTick();
            ShrineBlockEntity hd = hold[0];
            if (!at.containsKey("woke")) {
                if (Waking.god(god).woke()) {
                    at.put("woke", now);
                    h.assertTrue(god.getInventory().countItem(Items.GOLD_INGOT) >= Waking.KIT_GOLD && god.getInventory().countItem(KRegistry.SHRINE_ITEM.get()) == 1,
                            "the kit: " + god.getInventory().countItem(Items.GOLD_INGOT) + " gold, " + god.getInventory().countItem(KRegistry.SHRINE_ITEM.get()) + " Shrine");
                    line(h, null, "the Dawi wake: the kit in the god's pack");
                } else {
                    h.assertTrue(now < WAKE_BUDGET, "the Dawi never woke");
                }
                return;
            }
            if (hd == null) {
                // the kit's Shrine set down in the corridor (taken from his pack), the first Dawi brought up
                god.getInventory().clearOrCountMatchingItems(s -> s.is(KRegistry.SHRINE_ITEM.get()), 1, god.inventoryMenu.getCraftSlots());
                h.setBlock(SHRINE, KRegistry.SHRINE.get().defaultBlockState());
                KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), h.absolutePos(SHRINE), KRegistry.SHRINE.get().defaultBlockState(), god,
                        new ItemStack(KRegistry.SHRINE_ITEM.get()));
                hold[0] = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SHRINE));
                at.put("shrine", now);
                line(h, hold[0], "the Shrine set down");
                return;
            }
            if (!at.containsKey("ordered") && now >= at.get("shrine") + 2) {
                // the kit's bread and gold handed to the Shrine, and the three halls ordered, paid
                int bread = god.getInventory().countItem(Items.BREAD), gold = god.getInventory().countItem(Items.GOLD_INGOT);
                hand(h, god, SHRINE, new ItemStack(Items.BREAD, bread));
                hand(h, god, SHRINE, new ItemStack(Items.GOLD_INGOT, gold));
                goldBefore[0] = hd.currency();
                order(h, hd, "dawi_great_hall", HALL_DOOR, Direction.EAST, told);
                order(h, hd, "dawi_barracks", BARRACKS_DOOR, Direction.EAST, told);
                order(h, hd, "dawi_mine", MINE_DOOR, Direction.NORTH, told);
                int paid = goldBefore[0] - hd.currency();
                h.assertTrue(paid == 65, "the three halls cost " + paid + " gold, not 65: " + told);
                at.put("ordered", now);
                line(h, hd, "the kit's " + bread + " bread and " + gold + " gold handed over; the Great Hall, Barracks and Mine ordered (" + paid + " gold)");
                return;
            }
            if (!at.containsKey("ordered")) {
                return;
            }
            if ((now - at.get("ordered")) % FEED_EVERY == FEED_EVERY - 1) {
                hand(h, god, SHRINE, new ItemStack(Items.BREAD, FEED_BREAD));   // the god's farm: a stack of bread every two minutes
            }
            step(h, hd, at, "great_hall", hd.has(HoldRooms.GREAT_HALL), "the Great Hall dug out");
            step(h, hd, at, "barracks", hd.has(HoldRooms.BARRACKS), "the Barracks dug out");
            step(h, hd, at, "mine", !hd.works().all(MineWork.class).isEmpty(), "the Mine dug out, its drifts open");
            step(h, hd, at, "first_marksman", Ranks.count(h.getLevel(), hd, Quarreller.class) + Ranks.count(h.getLevel(), hd, Thunderer.class) > 0,
                    "the first Quarreller or Thunderer up");
            step(h, hd, at, "first_longbeard", Ranks.count(h.getLevel(), hd, KRegistry.LONGBEARD.get()) + Ranks.count(h.getLevel(), hd, KRegistry.LONGBEARD_GREAT.get()) > 0,
                    "the first Longbeard up");
            step(h, hd, at, "two_each_ranks", Ranks.count(h.getLevel(), hd, Quarreller.class) >= 2 && Ranks.count(h.getLevel(), hd, Thunderer.class) >= 2,
                    "two Quarrellers and two Thunderers up");
            step(h, hd, at, "ore_gold", hd.currency() > goldBefore[0] - 65, "the first gold dug out of the veins");
            if (now % 1200 == 0) {
                line(h, hd, "a minute's look: digs " + hd.works().digs().stream().map(d -> d.room().id() + " " + Math.round(d.job().progress() * 100) + "%").toList());
            }
            boolean halls = at.containsKey("great_hall") && at.containsKey("barracks") && at.containsKey("mine");
            h.assertTrue(halls || now < HALLS_BUDGET, "the halls weren't all dug in " + HALLS_BUDGET + " ticks: digs " + hd.works().digs().stream()
                    .map(d -> d.room().id() + " " + Math.round(d.job().progress() * 100) + "%").toList() + "; " + at);
            if (at.containsKey("barracks")) {
                h.assertTrue(at.containsKey("first_marksman") || now < at.get("barracks") + RANKS_BUDGET, "no missile troops " + RANKS_BUDGET
                        + " ticks after the Barracks: food " + hd.food());
            }
            if (at.containsKey("great_hall")) {
                h.assertTrue(at.containsKey("first_longbeard") || now < at.get("great_hall") + RANKS_BUDGET, "no Longbeard " + RANKS_BUDGET
                        + " ticks after the Great Hall: food " + hd.food());
            }
            if (halls && at.containsKey("first_marksman") && at.containsKey("first_longbeard") && at.containsKey("ore_gold")) {
                line(h, hd, "the run's end: " + at);
                h.getLevel().getServer().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }

    private static void order(GameTestHelper h, ShrineBlockEntity hold, String id, BlockPos door, Direction facing, List<String> told) {
        Rooms.Room room = HoldRooms.room(id);
        PlanPlacement pl = PlanPlacement.compute(room, h.absolutePos(door), facing);
        h.assertTrue(AncestorPlan.dig(hold, h.getLevel(), pl, false, c -> told.add(c.getString())), id + " was refused: " + told);
    }

    private static void step(GameTestHelper h, ShrineBlockEntity hold, Map<String, Long> at, String key, boolean done, String what) {
        if (done && !at.containsKey(key)) {
            at.put(key, h.getTick());
            line(h, hold, what);
        }
    }
}
