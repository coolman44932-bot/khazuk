package claude.khazuk.test;

import claude.khazuk.Ironbreaker;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Ranks;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.BuildingBook;
import claude.settlementcore.settlement.Founding;
import claude.settlementcore.test.AfterBatch;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import claude.settlementcore.work.MineWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hold played by a survival god from his first gold to his first Longbeards, every step of it his hands (the client's packets through
 * the kit's SurvivalGod, so reach, mining time, survival placing and the mod's own click handlers all apply): the faked runs
 * (SurvivalRunGameTests, UndergroundRunGameTests) made real.
 *
 * <ul>
 * <li>khazuksurvival, the long run, in the field filled with deep rock: he mines his way in with an iron pickaxe, strikes a gold ingot on
 * the deep rock (Waking: a right-click), and the kit lands in his pack (the Shrine, the plans, 90 gold, 16 bread); he uses the Hold Gate's
 * plan on the rock and Grungni carves it (Founding, its own clock); he walks its passage and sets the Shrine on the dais from his hotbar
 * (ShrineBlock takes root there only); he hands the Shrine every ingot and loaf he carries (a stack a click); he uses the Great Hall's and
 * the Mine's plans on the Shrine hall's sealed arches (AncestorPlan#useOn: the dig, paid out of the hold's gold as the plan pays it); he
 * crafts the Barracks' plan at a crafting table he sets down himself, and once the Great Hall is dug, uses it on one of the Great Hall's
 * own seals (65 gold for the three, as the old run paid). He brings the hold a stack of bread every two minutes, eats his own beef when he
 * is hungry, and waits for the Quarrellers, Thunderers, Longbeards, the Ironbreaker and the first ore gold, with the old run's
 * "KHAZUK run:" pacing lines and its budgets. Then no dug hall may be open to the rock round it.</li>
 * <li>khazuksurvivalgod, quick: the waking spends the struck ingot and hands over the kit; a Shrine set down off a gate's dais doesn't
 * take root; an unwoken god's Hold Gate plan founds nothing.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazuksurvival", time = 6000, daylightCycle = BatchWorld.Rule.OFF, keepInventory = BatchWorld.Rule.OFF,
        weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
@BatchWorld(batch = "khazuksurvivalgod", time = 6000, daylightCycle = BatchWorld.Rule.OFF, keepInventory = BatchWorld.Rule.OFF,
        weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
public class SurvivalHoldGameTests {

    static final String TAG = "run";
    /** The gate's door ground (its door one up, running east): its box x 4..37, y 2..44, z 29..59. The Great Hall north, the Mine south. */
    static final BlockPos DOOR_GROUND = new BlockPos(4, 2, 44);
    /** Where he comes in: a bubble at x 0..1, the rock he mines at x 2, the pocket before the gate's door at x 3. */
    static final BlockPos START = new BlockPos(1, 3, 44), ROCK = new BlockPos(2, 3, 44), STRIKE = new BlockPos(1, 3, 46), BEFORE_DOOR = new BlockPos(3, 3, 44);
    public static final int FEED_EVERY = 2400, FEED_BREAD = 32;
    /** Budgets in ticks (the old run's, plus the carve and the Barracks dug after the Great Hall): past one, the run has stalled. */
    public static final int WAKE_BUDGET = 400, CARVE_BUDGET = 6000, HALLS_BUDGET = 40000, BARRACKS_BUDGET = 12000, RANKS_BUDGET = 2400;
    public static final int TIMEOUT = 64000;

    @GameTest(template = "field", timeoutTicks = TIMEOUT, batch = "khazuksurvival")
    public static void aSurvivalGodPlaysAHoldFromHisGoldToHisLongbeards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.getLevel().getServer().tickRateManager().requestGameToSprint(TIMEOUT);
        // the rock: the field deep rock from its ground up, as the underground run had it
        SurvivalPlay.fill(h, new BlockPos(0, 3, 0), new BlockPos(71, 58, 71), Blocks.DEEPSLATE.defaultBlockState());
        SurvivalPlay.fill(h, new BlockPos(0, 3, 43), new BlockPos(1, 5, 45), Blocks.AIR.defaultBlockState());   // where he stands
        SurvivalPlay.fill(h, new BlockPos(3, 3, 43), new BlockPos(3, 5, 45), Blocks.AIR.defaultBlockState());   // before the gate's door
        h.assertTrue(Waking.deepRock(h.getBlockState(STRIKE), h.absolutePos(STRIKE)), "the rock he strikes isn't deep rock at y "
                + h.absolutePos(STRIKE).getY() + " (the Dawi wake below y " + Waking.DEEP_Y + ")");

        SurvivalGod god = SurvivalGod.join(h, START);
        god.give(Items.IRON_PICKAXE, 1).give(Items.GOLD_INGOT, 2).give(Items.COOKED_BEEF, 16);
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(DOOR_GROUND), Direction.EAST);
        BlockPos heart = h.relativePos(Founding.heartSpot(pl));
        BoundingBox bounds = SurvivalPlay.bounds(h);

        Run r = new Run();
        // the strike: a gold ingot on the deep rock, a right-click
        god.hold(Items.GOLD_INGOT).useOn(STRIKE, Direction.NORTH);
        h.assertTrue(Waking.waking(god.player().getUUID()) && god.wasTold("The deep rock answers"), "striking the deep rock woke nothing: told "
                + SurvivalPlay.last(god, 3));
        r.mark(h, "strike", null, "the god strikes his gold on the deep rock");
        r.job = god.hold(Items.IRON_PICKAXE).mine(ROCK);

        h.onEachTick(() -> {
            if (god.hasLeft()) {
                return;
            }
            long now = h.getTick();
            ShrineBlockEntity hold = r.hold;
            // ---- getting in, and the waking
            if (!r.at.containsKey("mined")) {
                if (r.job != null && r.job.isDone()) {
                    if (!r.at.containsKey("mined1")) {
                        r.at.put("mined1", now);
                        r.job = god.mine(ROCK.above());
                    } else {
                        r.mark(h, "mined", null, "he has mined his way in (" + r.job.status() + ")");
                        god.moveTo(BEFORE_DOOR);
                    }
                }
            }
            if (!r.at.containsKey("woke")) {
                if (Waking.god(god.player()).woke()) {
                    int gold = god.count(Items.GOLD_INGOT);
                    h.assertTrue(gold == 2 - 1 + Waking.KIT_GOLD, "the waking left him " + gold + " gold: the struck ingot spent, " + Waking.KIT_GOLD
                            + " from the kit (" + god.contents() + ")");
                    for (Item kit : List.of(KRegistry.SHRINE_ITEM.get(), KRegistry.PLAN_HOLD_GATE.get(), KRegistry.PLAN_GREAT_HALL.get(),
                            KRegistry.PLAN_MINE.get(), KRegistry.DAMMAZ_KRON.get())) {
                        h.assertTrue(god.count(kit) == 1, "the kit has no " + kit + ": " + god.contents());
                    }
                    h.assertTrue(god.count(Items.BREAD) == Waking.KIT_BREAD, "the kit's bread: " + god.count(Items.BREAD));
                    r.mark(h, "woke", null, "the Dawi wake: the kit in the god's pack");
                } else {
                    h.assertTrue(now - r.at.get("strike") < WAKE_BUDGET, "the Dawi never woke: told " + SurvivalPlay.last(god, 3));
                }
                return;
            }
            if (!r.at.containsKey("mined")) {
                return;
            }
            // ---- the founding: the Hold Gate's plan on the rock before him, and Grungni carves it
            if (!r.at.containsKey("founding")) {
                god.hold(KRegistry.PLAN_HOLD_GATE.get()).useOn(DOOR_GROUND, Direction.UP);
                h.assertTrue(god.wasTold("Grungni carves") && Founding.of(level).rising(pl.door()), "the Hold Gate's plan carved nothing: told "
                        + SurvivalPlay.last(god, 3));
                r.mark(h, "founding", null, "the Hold Gate's plan used on the rock: Grungni carves the gate");
                return;
            }
            if (!r.at.containsKey("carved")) {
                if (Founding.of(level).rising(pl.door())) {
                    h.assertTrue(now - r.at.get("founding") < CARVE_BUDGET, "the gate isn't carved " + CARVE_BUDGET + " ticks after its plan");
                    return;
                }
                r.mark(h, "carved", null, "the gate is carved");
                return;
            }
            // ---- the Shrine on the dais, from his hotbar
            if (hold == null) {
                BlockPos dais = heart.below();
                r.shrineStand = SurvivalPlay.standNear(h, dais, 4.0, heart);
                god.moveTo(r.shrineStand);
                BlockPos placed = god.hold(KRegistry.SHRINE_ITEM.get()).placeFromHotbar(dais, Direction.UP);
                h.assertTrue(placed.equals(heart), "the Shrine went to " + placed + ", not the dais spot " + heart + "; told " + SurvivalPlay.last(god, 3));
                if (!(level.getBlockEntity(h.absolutePos(heart)) instanceof ShrineBlockEntity s)) {
                    throw h.assertionException("no Shrine stands on the dais: " + h.getBlockState(heart));
                }
                h.assertTrue(god.player().getUUID().equals(s.owner()) && s.has(HoldRooms.GATE), "the Shrine isn't the god's hold of its gate: owner "
                        + s.owner() + ", rooms " + s.buildings());
                r.hold = s;
                r.mark(h, "shrine", s, "the Shrine set on the dais from the hotbar");
                return;
            }
            // ---- his gold and bread, and the first two halls
            if (!r.at.containsKey("ordered")) {
                if (now < r.at.get("shrine") + 2) {
                    return;
                }
                int gold = SurvivalPlay.handAll(h, god, heart, Items.GOLD_INGOT);
                int bread = SurvivalPlay.handAll(h, god, heart, Items.BREAD);
                h.assertTrue(gold == 2 - 1 + Waking.KIT_GOLD && bread == Waking.KIT_BREAD, "he handed " + gold + " gold and " + bread + " bread");
                r.goldBefore = hold.currency();
                List<SurvivalPlay.Arch> arches = SurvivalPlay.arches(level, pl.box());
                r.great = choose(h, hold, HoldRooms.room("dawi_grand_hall"), arches, bounds, List.of());
                h.assertTrue(r.great != null, "no sealed arch of the Shrine hall takes the Great Hall: " + arches);
                r.mine = choose(h, hold, HoldRooms.room("dawi_mine"), arches, bounds, List.of(r.great.box()));
                h.assertTrue(r.mine != null, "no sealed arch of the Shrine hall takes the Mine beside the Great Hall: " + arches);
                BoundingBox g = r.great.box();                   // gold veins in the Great Hall's rock: the dig strikes them
                for (int i = 0; i < 5; i++) {
                    BlockPos v = new BlockPos(g.minX() + 3 + i * (g.getXSpan() - 6) / 5, g.minY() + 4 + i % 3 * 3, g.minZ() + 3 + (i * 7) % Math.max(1, g.getZSpan() - 6));
                    level.setBlock(v, Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    r.veins.add(v);
                }
                order(h, god, hold, r.great, KRegistry.PLAN_GREAT_HALL.get(), heart);
                order(h, god, hold, r.mine, KRegistry.PLAN_MINE.get(), heart);
                int paid = r.goldBefore - hold.currency();
                h.assertTrue(paid == 50, "the Great Hall and the Mine cost " + paid + " gold, not 50: told " + SurvivalPlay.last(god, 4));
                // the Barracks' plan, crafted at a table he sets down by the Shrine
                god.moveTo(r.shrineStand);
                BlockPos tableAt = SurvivalPlay.standNear(h, r.shrineStand, 3.0, heart, heart.above(), r.shrineStand);
                god.give(Items.CRAFTING_TABLE, 1);
                BlockPos table = god.hold(Items.CRAFTING_TABLE).placeFromHotbar(tableAt.below(), Direction.UP);
                god.give(Items.POLISHED_DEEPSLATE, 1).give(Items.IRON_INGOT, 1).give(Items.COBBLED_DEEPSLATE, 1);
                ItemStack plan = god.craft(table, Items.POLISHED_DEEPSLATE, Items.IRON_INGOT, Items.COBBLED_DEEPSLATE);
                h.assertTrue(plan.is(KRegistry.PLAN_BARRACKS.get()) && god.count(KRegistry.PLAN_BARRACKS.get()) == 1, "the table made " + plan
                        + ", not the Barracks' plan (" + god.contents() + ")");
                r.nextFeed = now + FEED_EVERY;
                r.mark(h, "ordered", hold, "his " + gold + " gold and " + bread + " bread handed over; the Great Hall and the Mine ordered through the"
                        + " sealed arches (" + paid + " gold); the Barracks' plan crafted at his table");
                return;
            }
            // ---- the long wait: his own food, the hold's bread, the Barracks once the Great Hall is dug
            if (r.eating != null && (r.eating.isDone() || r.eating.failed())) {
                r.eating = null;
            }
            if (r.eating == null && god.food() <= 12 && god.count(Items.COOKED_BEEF) > 0) {
                r.eating = god.hold(Items.COOKED_BEEF).eat();
            }
            if (r.eating == null && now >= r.nextFeed) {
                god.give(Items.BREAD, FEED_BREAD);               // his farm's bread, carried down
                god.moveTo(r.shrineStand);
                SurvivalPlay.handAll(h, god, heart, Items.BREAD);
                r.nextFeed = now + FEED_EVERY;
            }
            r.step(h, "great_hall", hold.has(HoldRooms.GREAT_HALL), "the Great Hall dug out");
            r.step(h, "mine", !hold.works().all(MineWork.class).isEmpty(), "the Mine dug out, its drifts open");
            if (r.at.containsKey("great_hall") && !r.at.containsKey("barracks_ordered") && r.eating == null) {
                Building gh = hold.buildings().stream().filter(b -> b.room().equals("dawi_grand_hall")).findFirst().orElse(null);
                PlanPlacement ghp = gh == null ? null : BuildingBook.placementOf(gh);
                h.assertTrue(ghp != null, "the Great Hall is dug but the hold has no placement for it: " + hold.buildings());
                List<SurvivalPlay.Arch> arches = SurvivalPlay.arches(level, ghp.box());
                List<BoundingBox> taken = new ArrayList<>(List.of(pl.box(), r.mine.box()));
                r.barracks = choose(h, hold, HoldRooms.room("dawi_barracks"), arches, bounds, taken);
                h.assertTrue(r.barracks != null, "no sealed arch of the Great Hall takes the Barracks inside the field: " + arches);
                int before = hold.currency();
                order(h, god, hold, r.barracks, KRegistry.PLAN_BARRACKS.get(), heart);
                int paid = before - hold.currency();
                h.assertTrue(paid == 15, "the Barracks cost " + paid + " gold, not 15: told " + SurvivalPlay.last(god, 3));
                god.moveTo(r.shrineStand);
                r.mark(h, "barracks_ordered", hold, "the Barracks ordered through a seal of the Great Hall (" + paid + " gold; " + (r.goldBefore - before + paid)
                        + " for the three)");
            }
            r.step(h, "barracks", hold.has(HoldRooms.BARRACKS), "the Barracks dug out");
            r.step(h, "first_marksman", Ranks.count(level, hold, Quarreller.class) + Ranks.count(level, hold, Thunderer.class) > 0,
                    "the first Quarreller or Thunderer up");
            r.step(h, "first_longbeard", Ranks.count(level, hold, KRegistry.LONGBEARD.get()) + Ranks.count(level, hold, KRegistry.LONGBEARD_GREAT.get()) > 0,
                    "the first Longbeard up");
            r.step(h, "first_ironbreaker", Ranks.count(level, hold, Ironbreaker.class) > 0, "the first Ironbreaker up, posted in the Mine");
            r.step(h, "two_each_ranks", Ranks.count(level, hold, Quarreller.class) >= 2 && Ranks.count(level, hold, Thunderer.class) >= 2,
                    "two Quarrellers and two Thunderers up");
            boolean struck = r.veins.stream().anyMatch(v -> !level.getBlockState(v).is(Blocks.DEEPSLATE_GOLD_ORE));
            r.step(h, "ore_gold", struck && hold.currency() > r.goldBefore - 65, "the first gold dug out of the veins");
            if (now % 1200 == 0) {
                SurvivalPlay.line(h, TAG, hold, "a minute's look: digs " + SurvivalPlay.digs(hold) + ", the god's food " + god.food() + ", health "
                        + god.health());
            }
            long ordered = r.at.get("ordered");
            boolean two = r.at.containsKey("great_hall") && r.at.containsKey("mine");
            h.assertTrue(two || now - ordered < HALLS_BUDGET, "the Great Hall and the Mine weren't dug in " + HALLS_BUDGET + " ticks: digs "
                    + SurvivalPlay.digs(hold) + "; " + r.at);
            if (r.at.containsKey("barracks_ordered")) {
                h.assertTrue(r.at.containsKey("barracks") || now - r.at.get("barracks_ordered") < BARRACKS_BUDGET, "the Barracks wasn't dug "
                        + BARRACKS_BUDGET + " ticks after its order: digs " + SurvivalPlay.digs(hold));
            }
            if (r.at.containsKey("barracks")) {
                h.assertTrue(r.at.containsKey("first_marksman") || now < r.at.get("barracks") + RANKS_BUDGET, "no missile troops " + RANKS_BUDGET
                        + " ticks after the Barracks: food " + hold.food());
            }
            if (r.at.containsKey("great_hall")) {
                h.assertTrue(r.at.containsKey("first_longbeard") || now < r.at.get("great_hall") + RANKS_BUDGET, "no Longbeard " + RANKS_BUDGET
                        + " ticks after the Great Hall: food " + hold.food());
            }
            if (r.at.containsKey("mine") && hold.has(Rooms.Function.OREMINE)) {
                r.at.putIfAbsent("mine_done", now);
                h.assertTrue(r.at.containsKey("first_ironbreaker") || now < r.at.get("mine_done") + RANKS_BUDGET, "no Ironbreaker " + RANKS_BUDGET
                        + " ticks after the Mine: food " + hold.food());
            }
            if (two && r.at.containsKey("barracks") && r.at.containsKey("first_marksman") && r.at.containsKey("first_longbeard")
                    && r.at.containsKey("first_ironbreaker") && r.at.containsKey("ore_gold")) {
                for (PlanPlacement dug : List.of(r.great, r.mine, r.barracks)) {
                    String leak = SurvivalPlay.leak(level, dug, dug == r.mine ? SurvivalPlay.mouths(dug) : List.of());
                    h.assertTrue(leak == null, "the " + dug.room().displayName() + " isn't closed by its own walls: " + leak);
                }
                SurvivalPlay.line(h, TAG, hold, "the run's end: " + r.at);
                level.getServer().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }

    /** What the run has done, and when. */
    private static final class Run {
        final Map<String, Long> at = new LinkedHashMap<>();
        final List<BlockPos> veins = new ArrayList<>();
        @Nullable SurvivalGod.Job job, eating;
        @Nullable ShrineBlockEntity hold;
        @Nullable BlockPos shrineStand;
        @Nullable PlanPlacement great, mine, barracks;
        int goldBefore;
        long nextFeed;

        void mark(GameTestHelper h, String key, @Nullable ShrineBlockEntity hold, String what) {
            at.put(key, h.getTick());
            SurvivalPlay.line(h, TAG, hold, what);
        }

        void step(GameTestHelper h, String key, boolean done, String what) {
            if (done && !at.containsKey(key)) {
                mark(h, key, hold, what);
            }
        }
    }

    /**
     * The first sealed arch a hall of {@code room} can be dug through: inside the test's ground, clear of {@code taken}, cutting into no hall
     * of the hold (AncestorPlan#cutsInto, the walls guard's runtime half). Null if none.
     */
    static @Nullable PlanPlacement choose(GameTestHelper h, ShrineBlockEntity hold, Rooms.Room room, List<SurvivalPlay.Arch> arches,
                                          BoundingBox bounds, List<BoundingBox> taken) {
        for (SurvivalPlay.Arch a : arches) {
            PlanPlacement p = PlanPlacement.compute(room, a.anchor(), a.runs());
            if (!SurvivalPlay.inside(bounds, p.box()) || taken.stream().anyMatch(t -> t.intersects(p.box()))
                    || hold.works().digs().stream().anyMatch(d -> d.box().intersects(p.box()))
                    || AncestorPlan.cutsInto(hold, h.getLevel(), p) != null) {
                continue;
            }
            return p;
        }
        return null;
    }

    /** The god walks to the arch, takes the plan in hand and uses it on the seal (its face toward him): the Miners' dig, paid. */
    static void order(GameTestHelper h, SurvivalGod god, ShrineBlockEntity hold, PlanPlacement p, Item plan, BlockPos heart) {
        BlockPos seal = h.relativePos(p.door().above());
        god.moveTo(SurvivalPlay.standNear(h, seal, 4.0, heart));
        god.hold(plan).useOn(seal, p.facing().getOpposite());
        h.assertTrue(hold.works().digs().stream().anyMatch(d -> d.job().door.equals(p.door())), "the " + p.room().displayName()
                + "'s plan on the seal at " + seal + " began no dig: told " + SurvivalPlay.last(god, 3));
    }

    /** The sprint ends with the batch, whether the run passed, failed or timed out. */
    @AfterBatch(batch = "khazuksurvival")
    public static void runEnds(ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
    }

    // ------------------------------------------------------------------ khazuksurvivalgod: the quick ones

    /** A room in the digsite's stone, the deep rock he strikes in its east wall (the digsite stands below y 0). */
    private static void room(GameTestHelper h) {
        SurvivalPlay.fill(h, new BlockPos(2, 2, 2), new BlockPos(8, 4, 6), Blocks.AIR.defaultBlockState());
        h.setBlock(new BlockPos(9, 2, 4), Blocks.DEEPSLATE);
        h.setBlock(new BlockPos(9, 3, 4), Blocks.DEEPSLATE);
    }

    /**
     * The waking, by hand: one gold ingot struck on the deep rock (a right-click), the rock cracks for its time with him beside it, and the
     * Dawi hand over the kit: the struck ingot is spent, the kit's 90 gold, 16 bread, the Shrine and the plans in his pack, the hold named.
     */
    @GameTest(template = "digsite", timeoutTicks = 300, batch = "khazuksurvivalgod")
    public static void theGoldHeStrikesWakesTheDawiAndTheKitLandsInHisPack(GameTestHelper h) {
        room(h);
        BlockPos rock = new BlockPos(9, 2, 4);
        h.assertTrue(Waking.deepRock(h.getBlockState(rock), h.absolutePos(rock)), "the digsite's rock isn't deep rock at y " + h.absolutePos(rock).getY());
        SurvivalGod god = SurvivalGod.join(h, new BlockPos(7, 2, 4));
        god.give(Items.GOLD_INGOT, 1);
        god.hold(Items.GOLD_INGOT).useOn(rock, Direction.WEST);
        h.assertTrue(Waking.waking(god.player().getUUID()), "the strike began no waking: told " + god.chat());
        h.assertTrue(god.count(Items.GOLD_INGOT) == 1, "the struck ingot went at the strike, not at the waking");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Waking.god(god.player()).woke(), "the Dawi haven't woken"))
                .thenExecute(() -> {
                    h.assertTrue(god.count(Items.GOLD_INGOT) == Waking.KIT_GOLD, "he has " + god.count(Items.GOLD_INGOT) + " gold: the struck ingot spent, "
                            + Waking.KIT_GOLD + " from the kit");
                    h.assertTrue(god.count(Items.BREAD) == Waking.KIT_BREAD && god.count(KRegistry.SHRINE_ITEM.get()) == 1
                            && god.count(KRegistry.PLAN_HOLD_GATE.get()) == 1, "the kit: " + god.contents());
                    h.assertTrue(god.wasTold("The rock splits"), "he wasn't told the Dawi stepped out: " + god.chat());
                    h.assertTrue(Waking.god(god.player()).holdName().startsWith("Karak "), "the hold has no name: " + Waking.god(god.player()));
                })
                .thenSucceed();
    }

    /** A woken god's Shrine set down from his hotbar on a plain floor doesn't take root (only a Hold Gate's dais takes it): he keeps it. */
    @GameTest(template = "digsite", timeoutTicks = 60, batch = "khazuksurvivalgod")
    public static void aShrineOffAGatesDaisDoesntTakeRoot(GameTestHelper h) {
        room(h);
        SurvivalGod god = SurvivalGod.join(h, new BlockPos(3, 2, 4));
        Waking.setGod(god.player(), new Waking.God(true, "Karak Test", 0, false));
        god.give(KRegistry.SHRINE_ITEM.get(), 1).hold(KRegistry.SHRINE_ITEM.get());
        god.useOn(new BlockPos(5, 1, 4), Direction.UP);
        h.assertTrue(h.getBlockState(new BlockPos(5, 2, 4)).isAir(), "the Shrine took root on a plain floor: " + h.getBlockState(new BlockPos(5, 2, 4)));
        h.assertTrue(god.count(KRegistry.SHRINE_ITEM.get()) == 1, "the refused Shrine was used up: " + god.contents());
        h.assertTrue(god.wasTold("A Shrine stands only on the dais"), "he wasn't told why: " + god.chat());
        h.succeed();
    }

    /** The Hold Gate's plan in the hands of a god who hasn't woken the Dawi founds nothing, and says why. */
    @GameTest(template = "digsite", timeoutTicks = 60, batch = "khazuksurvivalgod")
    public static void anUnwokenGodsGatePlanFoundsNothing(GameTestHelper h) {
        room(h);
        SurvivalGod god = SurvivalGod.join(h, new BlockPos(3, 2, 4));
        god.give(KRegistry.PLAN_HOLD_GATE.get(), 1).hold(KRegistry.PLAN_HOLD_GATE.get());
        int halls = Founding.of(h.getLevel()).count();
        god.useOn(new BlockPos(6, 1, 4), Direction.UP);
        h.assertTrue(Founding.of(h.getLevel()).count() == halls, "an unwoken god's plan began a founding");
        h.assertTrue(god.wasTold("Only the Dawi's god founds a hold"), "he wasn't told why: " + god.chat());
        h.succeed();
    }
}
