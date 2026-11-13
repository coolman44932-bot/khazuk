package claude.khazuk.test;

import claude.khazuk.Ironbreaker;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HallSeal;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Ranks;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.BuildingBook;
import claude.settlementcore.settlement.Founding;
import claude.settlementcore.test.AfterBatch;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import claude.settlementcore.test.TestPos;
import claude.settlementcore.work.MineWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The survival hold (SurvivalHoldGameTests' khazuksurvival run, step for step) played in REAL generated rock: {@code khazuk:realrock_hold}, an
 * 80 x 52 x 80 cut of a seed-20261101 world's deep underground (world y -58 to -7 at the cut: deepslate and tuff, caves, gravel, 1425 water,
 * a lava lake at its bottom, ores, a skeleton dungeon), cut by devbridge with its air kept and no entities. On a deepslate-filled field the
 * carve and the digs never meet a cave; here they are sited to meet as many as they can.
 *
 * <ol>
 * <li>The cut's outermost shell is closed with deepslate where it is air or fluid (RealRockGameTests#seal), so its caves and water don't open
 * into the test world.</li>
 * <li>The site: the Hold Gate placed as the god places it ({@link AncestorPlan#placement}, running east), the Great Hall and the Mine through
 * the Shrine hall's seals, the Barracks through one of the Great Hall's, all predicted from the room templates before anything is carved
 * (their sealed arches, and AncestorPlan#cutsInto's rule over the templates). Every box must fit inside the shell, none within 5 of a block
 * entity (the dungeon's spawner and chests: the carve refuses one in its box, and a live spawner's skeletons are a different test), the
 * gate's door ground and the god's floor solid, the god under rock (AncestorPlan#underRock). Among those, the site with the most cave,
 * water and lava cells within 3 of the four boxes' faces, either side: real play digs next to caves, and that is what is tested. On
 * devbridge's cut (read off its file, 2026-11-13) six sites fit, all with the gate's floor on the lava lake's level (template y 3); the
 * best is the door ground at (10, 3, 53), the Great Hall north, the Mine south, the Barracks west in an open cavern, a cave with a lava
 * fall and standing water on the plane just before the gate's front.</li>
 * <li>The god joins in a pocket carved before the gate's door (walled with deepslate where it opened on a cave), with an iron pickaxe, 2 gold
 * and beef; strikes gold on the pocket's deep rock; uses the Hold Gate's plan on the rock before him; Grungni carves it. Then as the field
 * run: the Shrine on the dais, gold and bread handed over, the Great Hall and the Mine through the seals, the Barracks' plan crafted and
 * dug through a seal of the Great Hall, a stack of bread every 2400 ticks, his own beef, the "KHAZUK rockhold:" lines and the budgets.</li>
 * <li>At the end, and again {@value #WATCH} ticks later: the three halls closed by their own walls (SurvivalPlay#leak); the carved gate's
 * box closed but for its door and the two arches the halls were dug through; no water or lava in any hall's box, or in the gate's
 * passage, forecourt and Shrine hall (what is open inside its box, reached from its door). Any failure names the room, the block, and the
 * nearest cell that was a cave, water or lava as the test began.</li>
 * </ol>
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazukrockhold", time = 6000, daylightCycle = BatchWorld.Rule.OFF, keepInventory = BatchWorld.Rule.OFF,
        weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
public class RealRockHoldGameTests {

    static final String TAG = "rockhold";
    /** The cut as devbridge made it (only for the messages: the test reads the plot's real size). */
    static final int CUT_X = 80, CUT_Y = 52, CUT_Z = 80;
    /** Boxes stay this far inside the cut (1: inside its sealed shell). */
    static final int MARGIN = 1;
    /** How near a box's faces a cave counts toward a site's score, and how far from a box a block entity must be. */
    static final int NEAR = 3, KEEP_FROM_BLOCK_ENTITY = 5;
    public static final int FEED_EVERY = SurvivalHoldGameTests.FEED_EVERY, FEED_BREAD = SurvivalHoldGameTests.FEED_BREAD;
    /** The field run's budgets, but the halls' cut to fit the timeout. */
    public static final int WAKE_BUDGET = SurvivalHoldGameTests.WAKE_BUDGET, CARVE_BUDGET = SurvivalHoldGameTests.CARVE_BUDGET,
            HALLS_BUDGET = 30000, BARRACKS_BUDGET = SurvivalHoldGameTests.BARRACKS_BUDGET, RANKS_BUDGET = SurvivalHoldGameTests.RANKS_BUDGET;
    public static final int WATCH = 1200;
    /** 40 minutes of ticks at 20 a second (the sprint runs faster): the field run took ~14000. */
    public static final int TIMEOUT = 48000;

    @GameTest(template = "realrock_hold", timeoutTicks = TIMEOUT, batch = "khazukrockhold")
    public static void aSurvivalGodCarvesAndDigsAHoldInRealRockWalledAndDry(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AABB rb = h.getRelativeBounds();
        int sx = (int) rb.getXsize(), sy = (int) rb.getYsize(), sz = (int) rb.getZsize();
        RealRockGameTests.seal(h, sx, sy, sz);
        Rock rock = Rock.read(level, SurvivalPlay.bounds(h));
        BoundingBox inner = new BoundingBox(rock.box.minX() + MARGIN, rock.box.minY() + MARGIN, rock.box.minZ() + MARGIN,
                rock.box.maxX() - MARGIN, rock.box.maxY() - MARGIN, rock.box.maxZ() - MARGIN);
        Direction east = h.getAbsoluteDirection(Direction.EAST);
        Site site = site(h, rock, inner, east);
        h.assertTrue(site != null, "no site in the " + sx + " x " + sy + " x " + sz + " cut (devbridge's is " + CUT_X + " x " + CUT_Y + " x " + CUT_Z
                + ") takes the Hold Gate, the Great Hall, the Mine and the Barracks: " + Site.why);
        PlanPlacement pl = site.gate();
        Khazuk.LOG.info("KHAZUK {}: the site: the gate's door ground {} (absolute {}), running {}; {} sites fitted. Cave/water/lava cells within {} of "
                        + "the faces: gate {}, Great Hall {}, Mine {}, Barracks {} ({} in all: {} water, {} lava); open in the boxes: gate {}, Great Hall {}, "
                        + "Mine {}, Barracks {}. Boxes: gate {}, Great Hall {} (via {}), Mine {} (via {}), Barracks {} (via {})", TAG,
                TestPos.relative(h, site.ground()).toShortString(), site.ground().toShortString(), east, Site.fitted, NEAR,
                site.near()[0], site.near()[1], site.near()[2], site.near()[3], site.score(), site.water(), site.lava(),
                rock.open(pl.box()), rock.open(site.great().box()), rock.open(site.mine().box()), rock.open(site.barracks().box()),
                pl.box(), site.great().box(), site.great().door().toShortString(), site.mine().box(), site.mine().door().toShortString(),
                site.barracks().box(), site.barracks().door().toShortString());

        // ---- the god's pocket before the door: its inside cut, its shell walled with deepslate where it opened on a cave or held water
        Direction side = east.getClockWise();
        BlockPos groundAbs = site.ground(), feetAbs = groundAbs.relative(east.getOpposite()).above();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        for (int along = -3; along <= -1; along++) {
            for (int up = 0; up <= 4; up++) {
                for (int across = -2; across <= 2; across++) {
                    BlockPos a = groundAbs.relative(east, along).relative(side, across).above(up);
                    boolean in = along >= -2 && up >= 1 && up <= 3 && Math.abs(across) <= 1;
                    if (in) {
                        level.setBlock(a, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (SurvivalPlay.open(level, a) || !level.getFluidState(a).isEmpty()) {
                        level.setBlock(a, deepslate, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        BlockPos strikeAbs = feetAbs.relative(side, 2);       // the pocket's wall beside him, as the field run's STRIKE
        if (!Waking.deepRock(level.getBlockState(strikeAbs), strikeAbs)) {
            Khazuk.LOG.info("KHAZUK {}: the pocket's wall at {} is {}, not deep rock: deepslate set there to strike on", TAG, strikeAbs.toShortString(),
                    level.getBlockState(strikeAbs));
            level.setBlock(strikeAbs, deepslate, Block.UPDATE_CLIENTS);
        }
        h.assertTrue(Waking.deepRock(level.getBlockState(strikeAbs), strikeAbs), "the rock he strikes isn't deep rock at world y " + strikeAbs.getY()
                + " (the Dawi wake below y " + Waking.DEEP_Y + "; the cut stands from world y " + rock.box.minY() + " to " + rock.box.maxY() + ")");
        h.assertTrue(AncestorPlan.underRock(level, feetAbs), "the god's pocket at " + feetAbs.toShortString() + " isn't under rock: the gate would be "
                + "raised, not carved");
        BlockPos feet = TestPos.relative(h, feetAbs), ground = TestPos.relative(h, groundAbs), strike = TestPos.relative(h, strikeAbs);

        SurvivalGod god = SurvivalGod.join(h, feet);
        god.give(Items.IRON_PICKAXE, 1).give(Items.GOLD_INGOT, 2).give(Items.COOKED_BEEF, 16);
        level.getServer().tickRateManager().requestGameToSprint(TIMEOUT);
        BlockPos heart = TestPos.relative(h, Founding.heartSpot(pl));
        Run r = new Run(rock, site, pl);

        god.hold(Items.GOLD_INGOT).useOn(strike, side.getOpposite());
        h.assertTrue(Waking.waking(god.player().getUUID()) && god.wasTold("The deep rock answers"), "striking the deep rock woke nothing: told "
                + SurvivalPlay.last(god, 3));
        r.mark(h, "strike", null, "the god strikes his gold on the deep rock at " + strikeAbs.toShortString());

        h.onEachTick(() -> {
            if (god.hasLeft()) {
                return;
            }
            long now = h.getTick();
            ShrineBlockEntity hold = r.hold;
            h.assertTrue(now < TIMEOUT - 100, "the run stalled: " + r.at + "; digs " + (hold == null ? "-" : SurvivalPlay.digs(hold))
                    + "; told " + SurvivalPlay.last(god, 4));
            // ---- the waking
            if (!r.at.containsKey("woke")) {
                if (Waking.god(god.player()).woke()) {
                    int gold = god.count(Items.GOLD_INGOT);
                    h.assertTrue(gold == 2 - 1 + Waking.KIT_GOLD, "the waking left him " + gold + " gold (" + god.contents() + ")");
                    for (Item kit : List.of(KRegistry.SHRINE_ITEM.get(), KRegistry.PLAN_HOLD_GATE.get(), KRegistry.PLAN_GREAT_HALL.get(),
                            KRegistry.PLAN_MINE.get(), KRegistry.DAMMAZ_KRON.get())) {
                        h.assertTrue(god.count(kit) == 1, "the kit has no " + kit + ": " + god.contents());
                    }
                    r.mark(h, "woke", null, "the Dawi wake: the kit in the god's pack");
                } else {
                    h.assertTrue(now - r.at.get("strike") < WAKE_BUDGET, "the Dawi never woke: told " + SurvivalPlay.last(god, 3));
                }
                return;
            }
            // ---- the founding: the Hold Gate's plan on the rock before him, and Grungni carves it out of the real rock
            if (!r.at.containsKey("founding")) {
                god.hold(KRegistry.PLAN_HOLD_GATE.get()).useOn(ground, Direction.UP);
                h.assertTrue(god.player().getDirection() == east, "the god faces " + god.player().getDirection() + " as he uses the Hold Gate's plan, not "
                        + east);
                h.assertTrue(god.wasTold("Grungni carves") && Founding.of(level).rising(pl.door()), "the Hold Gate's plan carved nothing: told "
                        + SurvivalPlay.last(god, 3));
                r.mark(h, "founding", null, "the Hold Gate's plan used on the rock: Grungni carves the gate out of real rock");
                return;
            }
            if (!r.at.containsKey("carved")) {
                if (Founding.of(level).rising(pl.door())) {
                    h.assertTrue(now - r.at.get("founding") < CARVE_BUDGET, "the gate isn't carved " + CARVE_BUDGET + " ticks after its plan");
                    return;
                }
                r.mark(h, "carved", null, "the gate is carved; fluid in its box " + SurvivalPlay.fluids(level, pl.box(), 4));
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
            // ---- his gold and bread, and the first two halls through the Shrine hall's seals
            if (!r.at.containsKey("ordered")) {
                if (now < r.at.get("shrine") + 2) {
                    return;
                }
                int gold = SurvivalPlay.handAll(h, god, heart, Items.GOLD_INGOT);
                int bread = SurvivalPlay.handAll(h, god, heart, Items.BREAD);
                h.assertTrue(gold == 2 - 1 + Waking.KIT_GOLD && bread == Waking.KIT_BREAD, "he handed " + gold + " gold and " + bread + " bread");
                r.goldBefore = hold.currency();
                List<SurvivalPlay.Arch> arches = SurvivalPlay.arches(level, pl.box());
                r.great = SurvivalHoldGameTests.choose(h, hold, HoldRooms.room("dawi_grand_hall"), first(arches, site.great()), inner, List.of());
                h.assertTrue(r.great != null, "no sealed arch of the Shrine hall takes the Great Hall inside the cut: " + arches);
                r.mine = SurvivalHoldGameTests.choose(h, hold, HoldRooms.room("dawi_mine"), first(arches, site.mine()), inner, List.of(r.great.box()));
                h.assertTrue(r.mine != null, "no sealed arch of the Shrine hall takes the Mine beside the Great Hall: " + arches);
                predicted(h, "Great Hall", r.great, site.great());
                predicted(h, "Mine", r.mine, site.mine());
                BoundingBox g = r.great.box();                   // gold veins in the Great Hall's rock, as the field run has them
                for (int i = 0; i < 5; i++) {
                    BlockPos v = new BlockPos(g.minX() + 3 + i * (g.getXSpan() - 6) / 5, g.minY() + 4 + i % 3 * 3, g.minZ() + 3 + (i * 7) % Math.max(1, g.getZSpan() - 6));
                    level.setBlock(v, Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    r.veins.add(v);
                }
                SurvivalHoldGameTests.order(h, god, hold, r.great, KRegistry.PLAN_GREAT_HALL.get(), heart);
                SurvivalHoldGameTests.order(h, god, hold, r.mine, KRegistry.PLAN_MINE.get(), heart);
                int paid = r.goldBefore - hold.currency();
                h.assertTrue(paid == 50, "the Great Hall and the Mine cost " + paid + " gold, not 50: told " + SurvivalPlay.last(god, 4));
                god.moveTo(r.shrineStand);
                BlockPos tableAt = SurvivalPlay.standNear(h, r.shrineStand, 3.0, heart, heart.above(), r.shrineStand);
                god.give(Items.CRAFTING_TABLE, 1);
                BlockPos table = god.hold(Items.CRAFTING_TABLE).placeFromHotbar(tableAt.below(), Direction.UP);
                god.give(Items.POLISHED_DEEPSLATE, 1).give(Items.IRON_INGOT, 1).give(Items.COBBLED_DEEPSLATE, 1);
                ItemStack plan = god.craft(table, Items.POLISHED_DEEPSLATE, Items.IRON_INGOT, Items.COBBLED_DEEPSLATE);
                h.assertTrue(plan.is(KRegistry.PLAN_BARRACKS.get()) && god.count(KRegistry.PLAN_BARRACKS.get()) == 1, "the table made " + plan
                        + ", not the Barracks' plan (" + god.contents() + ")");
                r.nextFeed = now + FEED_EVERY;
                r.mark(h, "ordered", hold, "his " + gold + " gold and " + bread + " bread handed over; the Great Hall (" + r.great.box() + ") and the Mine ("
                        + r.mine.box() + ") ordered through the sealed arches (" + paid + " gold); the Barracks' plan crafted at his table");
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
                god.give(Items.BREAD, FEED_BREAD);
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
                r.barracks = SurvivalHoldGameTests.choose(h, hold, HoldRooms.room("dawi_barracks"), first(arches, site.barracks()), inner, taken);
                h.assertTrue(r.barracks != null, "no sealed arch of the Great Hall takes the Barracks inside the cut: " + arches);
                predicted(h, "Barracks", r.barracks, site.barracks());
                int before = hold.currency();
                SurvivalHoldGameTests.order(h, god, hold, r.barracks, KRegistry.PLAN_BARRACKS.get(), heart);
                int paid = before - hold.currency();
                h.assertTrue(paid == 15, "the Barracks cost " + paid + " gold, not 15: told " + SurvivalPlay.last(god, 3));
                god.moveTo(r.shrineStand);
                r.mark(h, "barracks_ordered", hold, "the Barracks (" + r.barracks.box() + ") ordered through a seal of the Great Hall (" + paid + " gold)");
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
                        + god.health() + ", fluid in the gate's box " + SurvivalPlay.fluids(level, pl.box(), 3));
            }
            long ordered = r.at.get("ordered");
            boolean two = r.at.containsKey("great_hall") && r.at.containsKey("mine");
            h.assertTrue(two || now - ordered < HALLS_BUDGET, "the Great Hall and the Mine weren't dug in " + HALLS_BUDGET + " ticks of real rock: digs "
                    + SurvivalPlay.digs(hold) + ", crew " + hold.works().crewCount() + "; " + r.at);
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
            boolean all = two && r.at.containsKey("barracks") && r.at.containsKey("first_marksman") && r.at.containsKey("first_longbeard")
                    && r.at.containsKey("first_ironbreaker") && r.at.containsKey("ore_gold");
            if (!all) {
                return;
            }
            // ---- walled and dry, as dug and a minute on
            if (!r.at.containsKey("checked")) {
                check(h, r, "as the hold stands");
                r.mark(h, "checked", hold, "every hall walled by its own walls and dry, the gate closed");
                return;
            }
            if (now - r.at.get("checked") >= WATCH) {
                check(h, r, WATCH + " ticks later");
                SurvivalPlay.line(h, TAG, hold, "the run's end: " + r.at);
                level.getServer().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }

    /** The sprint ends with the batch, whether the run passed, failed or timed out. */
    @AfterBatch(batch = "khazukrockhold")
    public static void runEnds(ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
    }

    // ------------------------------------------------------------------ the run

    private static final class Run {
        final Rock rock;
        final Site site;
        final PlanPlacement gate;
        final Map<String, Long> at = new LinkedHashMap<>();
        final List<BlockPos> veins = new ArrayList<>();
        @Nullable SurvivalGod.Job eating;
        @Nullable ShrineBlockEntity hold;
        @Nullable BlockPos shrineStand;
        @Nullable PlanPlacement great, mine, barracks;
        int goldBefore;
        long nextFeed;

        Run(Rock rock, Site site, PlanPlacement gate) {
            this.rock = rock;
            this.site = site;
            this.gate = gate;
        }

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

    /** {@code arches} with the one the site was chosen for first (SurvivalHoldGameTests#choose takes the first that fits). */
    private static List<SurvivalPlay.Arch> first(List<SurvivalPlay.Arch> arches, PlanPlacement planned) {
        List<SurvivalPlay.Arch> out = new ArrayList<>(arches);
        out.sort((a, b) -> Boolean.compare(!a.anchor().equals(planned.door()), !b.anchor().equals(planned.door())));
        return out;
    }

    private static void predicted(GameTestHelper h, String hall, PlanPlacement got, PlanPlacement planned) {
        if (!got.door().equals(planned.door()) || got.facing() != planned.facing()) {
            Khazuk.LOG.warn("KHAZUK {}: the {} went through the arch at {} running {}, not the one the site was chosen for ({} running {}): the "
                    + "cave counts logged for it are not its own", TAG, hall, got.door().toShortString(), got.facing(), planned.door().toShortString(),
                    planned.facing());
        }
    }

    /**
     * The walls and the water, all four rooms. Every problem is gathered before the test fails (a run is 40 minutes of the laptop's time):
     * each names its room, the block, and the nearest cell that was a cave, water or lava when the test began.
     */
    private static void check(GameTestHelper h, Run r, String when) {
        ServerLevel level = h.getLevel();
        List<String> wrong = new ArrayList<>();
        for (PlanPlacement dug : List.of(r.great, r.mine, r.barracks)) {
            List<BlockPos> mouths = dug == r.mine ? SurvivalPlay.mouths(dug) : List.of();
            List<PlanPlacement> joined = dug == r.great ? List.of(r.barracks) : List.of();
            String leak = SurvivalPlay.leak(level, dug, mouths, joined);
            if (leak != null) {
                Flood f = flood(level, dug, mouths, joined, false);
                wrong.add("the " + dug.room().displayName() + " isn't closed by its own walls: " + leak + "; " + holes(level, r, f, Set.of())
                        + " (its box " + dug.box() + ", door " + dug.door().toShortString() + " running " + dug.facing() + ")");
            }
            List<BlockPos> wet = wet(level, dug.box());
            if (!wet.isEmpty()) {
                wrong.add("the " + dug.room().displayName() + " has water or lava in its box: " + named(level, wet) + "; " + r.rock.nearest(level, wet.get(0)));
            }
        }
        // the gate: its box, but for the halls dug through its arches (their own boxes), closed but for its door and those two arches
        PlanPlacement gate = r.gate;
        Flood f = flood(level, gate, List.of(), List.of(r.great, r.mine), true);
        if (f.why() != null) {
            wrong.add("the carved Hold Gate: " + f.why());
        } else if (!f.holes().isEmpty()) {
            wrong.add("the carved Hold Gate isn't closed by its own walls: " + holes(level, r, f, doorwayPatch(level, gate)) + " (its box " + gate.box()
                    + ", door " + gate.door().toShortString() + " running " + gate.facing() + ")");
        }
        List<BlockPos> wetIn = new ArrayList<>();
        for (BlockPos p : f.inside()) {
            if (level.getBlockState(p).getBlock() instanceof LiquidBlock) {
                wetIn.add(p);
            }
        }
        if (!wetIn.isEmpty()) {
            wrong.add("the Hold Gate's passage, forecourt or Shrine hall has water or lava in it: " + named(level, wetIn) + "; "
                    + r.rock.nearest(level, wetIn.get(0)));
        }
        List<String> sealedIn = SurvivalPlay.fluids(level, gate.box(), 6);
        if (!sealedIn.isEmpty()) {                               // behind its walls, in the rock Grungni filled back: not in the hall, but said
            Khazuk.LOG.warn("KHAZUK {}: water or lava stands in the gate's box behind its walls ({}): {}", TAG, when, sealedIn);
        }
        if (!wrong.isEmpty()) {
            for (String w : wrong) {
                Khazuk.LOG.error("KHAZUK {}: {}: {}", TAG, when, w);
            }
            throw h.assertionException(wrong.size() + " problem(s) " + when + ": " + String.join(" || ", wrong));
        }
        SurvivalPlay.line(h, TAG, r.hold, "walled and dry " + when + ": the gate's open inside " + f.inside().size() + " cells, fluid in its box "
                + sealedIn.size());
    }

    /** The first few holes of {@code f}, which are in {@code patch} (the engine's doorway cells, left unshored), and the nearest tick-0 cave. */
    private static String holes(ServerLevel level, Run r, Flood f, Set<BlockPos> patch) {
        if (f.holes().isEmpty()) {
            return "(the flood found no hole: " + f.why() + ")";
        }
        List<String> out = new ArrayList<>();
        for (Hole x : f.holes().subList(0, Math.min(6, f.holes().size()))) {
            out.add(x.at().toShortString() + " " + level.getBlockState(x.at()).getBlock() + " out of " + x.from().toShortString() + " going " + x.going()
                    + (patch.contains(x.at()) ? " [in RoomShoring#doorways' patch: left unshored by design]" : ""));
        }
        long inPatch = f.holes().stream().filter(x -> patch.contains(x.at())).count();
        return f.holes().size() + " hole cell(s)" + (patch.isEmpty() ? "" : " (" + inPatch + " in the doorway patch)") + ": " + out + "; "
                + r.rock.nearest(level, f.holes().get(0).at());
    }

    private static String named(ServerLevel level, List<BlockPos> cells) {
        return cells.size() + " cells, " + cells.subList(0, Math.min(6, cells.size())).stream()
                .map(p -> p.toShortString() + " " + level.getBlockState(p).getBlock()).toList();
    }

    /** The cells just outside a room's doorways that its shoring leaves as the world has them (RoomShoring#doorways). */
    static Set<BlockPos> doorwayPatch(ServerLevel level, PlanPlacement pl) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet openLocal = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (RoomTemplates.Entry e : RoomTemplates.get(level, pl.room().id())) {
            if (claude.settlementcore.room.RoomShoring.open(e.state())) {
                openLocal.add(e.local().asLong());
            }
        }
        Set<BlockPos> out = new HashSet<>();
        for (long k : claude.settlementcore.room.RoomShoring.doorways(pl, openLocal)) {
            out.add(BlockPos.of(k));
        }
        return out;
    }

    /** The water and lava (the blocks themselves) in {@code box}. */
    private static List<BlockPos> wet(ServerLevel level, BoundingBox box) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).getBlock() instanceof LiquidBlock) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** An open cell outside a room its flood reached by a way that is no opening of the room: where, from where, which way. */
    record Hole(BlockPos at, BlockPos from, Direction going) {
    }

    /** A flood of a room's open space: every hole found (up to {@link #MAX_HOLES}), every open cell it reached, or why it couldn't start. */
    record Flood(List<Hole> holes, Set<BlockPos> inside, @Nullable String why) {
    }

    static final int MAX_HOLES = 400;

    /**
     * SurvivalPlay#leak's flood, going on past a hole to find them all: the room's open space flooded from inside its door within its box may
     * leave it only through its doorway (its door's face, up to 3 either side), within 2 of a mouth, or into a joined hall through that hall's
     * door (its arch). {@code joinedOutside}: the joined halls' boxes are not the room's, even where they overlap its box (the gate: the Great
     * Hall and the Mine are dug from its seals, so its box holds their first rows).
     */
    static Flood flood(ServerLevel level, PlanPlacement pl, List<BlockPos> mouths, List<PlanPlacement> joined, boolean joinedOutside) {
        BoundingBox box = pl.box();
        Direction f = pl.facing();
        Direction side = f.getClockWise();
        BlockPos door = pl.door();
        java.util.function.Predicate<BlockPos> in = c -> box.isInside(c) && !(joinedOutside && joined.stream().anyMatch(j -> j.box().isInside(c)));
        BlockPos start = null;
        for (int k = 1; k <= 4 && start == null; k++) {
            for (int up = 0; up <= 2 && start == null; up++) {
                BlockPos c = door.relative(f, k).above(up);
                if (in.test(c) && SurvivalPlay.open(level, c)) {
                    start = c;
                }
            }
        }
        if (start == null) {
            return new Flood(List.of(), Set.of(), "nothing open inside its door at " + door.toShortString());
        }
        List<Hole> holes = new ArrayList<>();
        Set<BlockPos> holeAt = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!SurvivalPlay.open(level, n)) {
                    continue;
                }
                if (in.test(n)) {
                    if (seen.add(n)) {
                        q.add(n);
                    }
                    continue;
                }
                int along = (p.getX() - door.getX()) * f.getStepX() + (p.getZ() - door.getZ()) * f.getStepZ();
                int across = (p.getX() - door.getX()) * side.getStepX() + (p.getZ() - door.getZ()) * side.getStepZ();
                if (along == 0 && Math.abs(across) <= 3 && d == f.getOpposite()) {
                    continue;                                    // its doorway
                }
                boolean viaArch = false;
                for (PlanPlacement j : joined) {
                    Direction jf = j.facing();
                    Direction jside = jf.getClockWise();
                    BlockPos jd = j.door();
                    int jAlong = (n.getX() - jd.getX()) * jf.getStepX() + (n.getZ() - jd.getZ()) * jf.getStepZ();
                    int jAcross = (n.getX() - jd.getX()) * jside.getStepX() + (n.getZ() - jd.getZ()) * jside.getStepZ();
                    viaArch |= j.box().isInside(n) && (jAlong == 0 || jAlong == 1) && Math.abs(jAcross) <= 3 && d == jf;
                }
                if (viaArch) {
                    continue;
                }
                boolean mouth = false;
                for (BlockPos m : mouths) {
                    mouth |= m.distSqr(p) <= 4.5;
                }
                if (!mouth && holes.size() < MAX_HOLES && holeAt.add(n)) {
                    holes.add(new Hole(n.immutable(), p.immutable(), d));
                }
            }
        }
        return new Flood(holes, seen, null);
    }

    // ------------------------------------------------------------------ the rock as the test found it

    /** The cut as it stood at tick 0 (its shell sealed): every cell solid, open (a cave), water or lava, and prefix sums for counting boxes. */
    static final class Rock {
        static final byte SOLID = 0, OPEN = 1, WATER = 2, LAVA = 3;
        final BoundingBox box;
        final int nx, ny, nz;
        final byte[] kind;
        final int[] open, water, lava, entities;

        private Rock(BoundingBox box) {
            this.box = box;
            nx = box.getXSpan();
            ny = box.getYSpan();
            nz = box.getZSpan();
            kind = new byte[nx * ny * nz];
            int n = (nx + 1) * (ny + 1) * (nz + 1);
            open = new int[n];
            water = new int[n];
            lava = new int[n];
            entities = new int[n];
        }

        static Rock read(ServerLevel level, BoundingBox box) {
            Rock r = new Rock(box);
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int x = 0; x < r.nx; x++) {
                for (int y = 0; y < r.ny; y++) {
                    for (int z = 0; z < r.nz; z++) {
                        at.set(box.minX() + x, box.minY() + y, box.minZ() + z);
                        BlockState s = level.getBlockState(at);
                        FluidState fl = s.getFluidState();
                        byte k = !fl.isEmpty() && (s.getBlock() instanceof LiquidBlock || SurvivalPlay.open(level, at))
                                ? (fl.is(FluidTags.LAVA) ? LAVA : WATER) : SurvivalPlay.open(level, at) ? OPEN : SOLID;
                        r.kind[(x * r.ny + y) * r.nz + z] = k;
                        r.add(r.open, x, y, z, k != SOLID ? 1 : 0);
                        r.add(r.water, x, y, z, k == WATER ? 1 : 0);
                        r.add(r.lava, x, y, z, k == LAVA ? 1 : 0);
                        r.add(r.entities, x, y, z, s.hasBlockEntity() ? 1 : 0);
                    }
                }
            }
            return r;
        }

        private int ix(int x, int y, int z) {
            return (x * (ny + 1) + y) * (nz + 1) + z;
        }

        private void add(int[] s, int x, int y, int z, int v) {
            s[ix(x + 1, y + 1, z + 1)] = v + s[ix(x, y + 1, z + 1)] + s[ix(x + 1, y, z + 1)] + s[ix(x + 1, y + 1, z)] - s[ix(x, y, z + 1)]
                    - s[ix(x, y + 1, z)] - s[ix(x + 1, y, z)] + s[ix(x, y, z)];
        }

        /** Cells of {@code s} in {@code b} (absolute), clipped to the cut. */
        int count(int[] s, BoundingBox b) {
            int x0 = Math.max(0, b.minX() - box.minX()), y0 = Math.max(0, b.minY() - box.minY()), z0 = Math.max(0, b.minZ() - box.minZ());
            int x1 = Math.min(nx - 1, b.maxX() - box.minX()) + 1, y1 = Math.min(ny - 1, b.maxY() - box.minY()) + 1,
                    z1 = Math.min(nz - 1, b.maxZ() - box.minZ()) + 1;
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
                return 0;
            }
            return s[ix(x1, y1, z1)] - s[ix(x0, y1, z1)] - s[ix(x1, y0, z1)] - s[ix(x1, y1, z0)] + s[ix(x0, y0, z1)] + s[ix(x0, y1, z0)]
                    + s[ix(x1, y0, z0)] - s[ix(x0, y0, z0)];
        }

        int open(BoundingBox b) {
            return count(open, b);
        }

        /** Cells of {@code s} within {@code k} of {@code b}'s faces, inside or out. */
        int near(int[] s, BoundingBox b, int k) {
            BoundingBox out = new BoundingBox(b.minX() - k, b.minY() - k, b.minZ() - k, b.maxX() + k, b.maxY() + k, b.maxZ() + k);
            int n = count(s, out);
            if (b.getXSpan() > 2 * k && b.getYSpan() > 2 * k && b.getZSpan() > 2 * k) {
                n -= count(s, new BoundingBox(b.minX() + k, b.minY() + k, b.minZ() + k, b.maxX() - k, b.maxY() - k, b.maxZ() - k));
            }
            return n;
        }

        byte at(BlockPos p) {
            if (!box.isInside(p)) {
                return SOLID;
            }
            return kind[((p.getX() - box.minX()) * ny + p.getY() - box.minY()) * nz + p.getZ() - box.minZ()];
        }

        /** The nearest cell to {@code p} that was a cave, water or lava when the test began, what it was and what it is now. */
        String nearest(ServerLevel level, BlockPos p) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-12, -12, -12), p.offset(12, 12, 12))) {
                if (at(q) != SOLID) {
                    double d = q.distSqr(p);
                    if (d < bd) {
                        bd = d;
                        best = q.immutable();
                    }
                }
            }
            if (best == null) {
                return "no cave, water or lava within 12 of " + p.toShortString() + " when the test began";
            }
            String[] was = {"rock", "a cave's air", "water", "lava"};
            return "the nearest cave cell when the test began: " + best.toShortString() + " (" + was[at(best)] + " then, " + level.getBlockState(best).getBlock()
                    + " now), " + String.format("%.1f", Math.sqrt(bd)) + " from " + p.toShortString();
        }
    }

    // ------------------------------------------------------------------ the site

    /**
     * The chosen site (absolute): the gate's door ground and its placement, the three halls through their arches, the cave/water/lava cells
     * within {@value #NEAR} of each box's faces (gate, Great Hall, Mine, Barracks), and the water and lava among them.
     */
    record Site(BlockPos ground, PlanPlacement gate, PlanPlacement great, PlanPlacement mine, PlanPlacement barracks, int[] near, int score, int water,
                int lava) {
        static String why = "";
        static int fitted;
    }

    /** A hall dug through an arch, as offsets from the gate's door ground: the arch's anchor and the way it runs. */
    record Through(BlockPos anchor, Direction runs) {
        PlanPlacement at(Rooms.Room room, BlockPos ground) {
            return PlanPlacement.compute(room, ground.offset(anchor), runs);
        }
    }

    /** One way to lay the three halls round the gate. */
    record Layout(Through great, Through mine, Through barracks) {
    }

    /**
     * The best site in the cut for the hold, or null (then {@link Site#why} says what stopped every one). The layouts are worked out once,
     * from the room templates, for a gate at one spot, and moved over the cut.
     */
    static @Nullable Site site(GameTestHelper h, Rock rock, BoundingBox inner, Direction east) {
        ServerLevel level = h.getLevel();
        Rooms.Room gateRoom = HoldRooms.room("dawi_hold_gate"), greatRoom = HoldRooms.room("dawi_grand_hall"),
                mineRoom = HoldRooms.room("dawi_mine"), barracksRoom = HoldRooms.room("dawi_barracks");
        BlockPos ref = BlockPos.ZERO;
        PlanPlacement g0 = AncestorPlan.placement(gateRoom, ref, east);
        List<Layout> layouts = new ArrayList<>();
        Map<BlockPos, Direction> gateArches = arches(level, g0);
        for (var a : gateArches.entrySet()) {
            PlanPlacement gh = PlanPlacement.compute(greatRoom, a.getKey(), a.getValue());
            if (cuts(level, g0, gh)) {
                continue;
            }
            for (var b : gateArches.entrySet()) {
                if (b.getKey().equals(a.getKey())) {
                    continue;
                }
                PlanPlacement m = PlanPlacement.compute(mineRoom, b.getKey(), b.getValue());
                if (m.box().intersects(gh.box()) || cuts(level, g0, m)) {
                    continue;
                }
                for (var c : arches(level, gh).entrySet()) {
                    PlanPlacement br = PlanPlacement.compute(barracksRoom, c.getKey(), c.getValue());
                    if (br.box().intersects(g0.box()) || br.box().intersects(m.box()) || cuts(level, gh, br) || cuts(level, g0, br) || cuts(level, m, br)) {
                        continue;
                    }
                    layouts.add(new Layout(new Through(a.getKey(), a.getValue()), new Through(b.getKey(), b.getValue()),
                            new Through(c.getKey(), c.getValue())));
                }
            }
        }
        if (layouts.isEmpty()) {
            Site.why = "no way to lay the Great Hall, the Mine and the Barracks through the gate's arches (" + gateArches + ") cuts into none";
            return null;
        }
        Direction side = east.getClockWise();
        record Candidate(BlockPos ground, Layout layout, int score) {
        }
        List<Candidate> all = new ArrayList<>();
        int[] stopped = new int[3];
        BoundingBox b = rock.box;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockPos ground = new BlockPos(x, y, z);
                    PlanPlacement gate = AncestorPlan.placement(gateRoom, ground, east);
                    if (!SurvivalPlay.inside(inner, gate.box())) {
                        continue;
                    }
                    BlockPos floor = ground.relative(east.getOpposite());
                    BoundingBox pocket = BoundingBox.fromCorners(ground.relative(east, -3).relative(side, -2), ground.relative(east, -1).relative(side, 2).above(4));
                    if (!SurvivalPlay.inside(inner, pocket)) {
                        continue;
                    }
                    if (rock.at(ground) != Rock.SOLID || rock.at(floor) != Rock.SOLID) {
                        stopped[0]++;
                        continue;
                    }
                    for (Layout l : layouts) {
                        List<BoundingBox> boxes = List.of(gate.box(), l.great().at(greatRoom, ground).box(), l.mine().at(mineRoom, ground).box(),
                                l.barracks().at(barracksRoom, ground).box());
                        if (!boxes.stream().allMatch(bx -> SurvivalPlay.inside(inner, bx))) {
                            continue;
                        }
                        if (boxes.stream().anyMatch(bx -> rock.count(rock.entities, bx.inflatedBy(KEEP_FROM_BLOCK_ENTITY)) > 0)) {
                            stopped[1]++;
                            continue;
                        }
                        int score = 0;
                        for (BoundingBox bx : boxes) {
                            score += rock.near(rock.open, bx, NEAR);
                        }
                        all.add(new Candidate(ground, l, score));
                    }
                }
            }
        }
        all.sort((p, q) -> Integer.compare(q.score(), p.score()));
        for (Candidate c : all) {
            BlockPos feet = c.ground().relative(east.getOpposite()).above();
            if (!AncestorPlan.underRock(level, feet)) {
                stopped[2]++;
                continue;
            }
            Site.fitted = all.size();
            PlanPlacement gate = AncestorPlan.placement(gateRoom, c.ground(), east);
            PlanPlacement gh = c.layout().great().at(greatRoom, c.ground()), m = c.layout().mine().at(mineRoom, c.ground()),
                    br = c.layout().barracks().at(barracksRoom, c.ground());
            int[] near = new int[4];
            int water = 0, lava = 0, i = 0;
            for (PlanPlacement p : List.of(gate, gh, m, br)) {
                near[i++] = rock.near(rock.open, p.box(), NEAR);
                water += rock.near(rock.water, p.box(), NEAR);
                lava += rock.near(rock.lava, p.box(), NEAR);
            }
            return new Site(c.ground(), gate, gh, m, br, near, c.score(), water, lava);
        }
        Site.why = layouts.size() + " layouts; " + all.size() + " sites fitted; door ground or the god's floor open at " + stopped[0]
                + ", a block entity within " + KEEP_FROM_BLOCK_ENTITY + " of a box at " + stopped[1] + ", the god not under rock at " + stopped[2];
        return null;
    }

    /** The sealed arches a room at {@code pl} is built with, read off its template (anchor: the arch's bottom middle; the way it runs). */
    static Map<BlockPos, Direction> arches(ServerLevel level, PlanPlacement pl) {
        Map<BlockPos, Direction> seals = new HashMap<>();
        for (RoomTemplates.Entry e : RoomTemplates.get(level, pl.room().id())) {
            if (e.state().getBlock() instanceof HallSeal) {
                seals.put(pl.world(e.local()), e.state().rotate(pl.rotation()).getValue(BlockStateProperties.HORIZONTAL_FACING));
            }
        }
        Map<BlockPos, Direction> out = new LinkedHashMap<>();
        for (var s : seals.entrySet()) {
            Direction f = s.getValue();
            BlockPos p = s.getKey();
            for (int i = 0; i < 8 && f == seals.get(p.below()); i++) {
                p = p.below();
            }
            Direction right = f.getClockWise();
            int rr = 0, l = 0;
            while (rr < 8 && f == seals.get(p.relative(right, rr + 1))) {
                rr++;
            }
            while (l < 8 && f == seals.get(p.relative(right, -(l + 1)))) {
                l++;
            }
            out.putIfAbsent(p.relative(right, Math.floorDiv(rr - l, 2)), f);   // HallSeal#anchor, over the template
        }
        return out;
    }

    /** AncestorPlan#cutsInto over the templates: any of {@code built}'s stone in {@code dug}'s box but for the wall it is dug from. */
    static boolean cuts(ServerLevel level, PlanPlacement built, PlanPlacement dug) {
        if (!built.box().intersects(dug.box())) {
            return false;
        }
        Direction f = dug.facing();
        for (RoomTemplates.Entry e : RoomTemplates.get(level, built.room().id())) {
            if (e.state().isAir()) {
                continue;
            }
            BlockPos p = built.world(e.local());
            int along = (p.getX() - dug.door().getX()) * f.getStepX() + (p.getZ() - dug.door().getZ()) * f.getStepZ();
            if (along != 0 && dug.box().isInside(p)) {
                return true;
            }
        }
        return false;
    }
}
