package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.AfterBatch;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hold dug in REAL generated underground (the templates are cuts of a seed-20261101 world through devbridge's {@code cut}: caves,
 * aquifers, lava, ores and deepslate as the world generator made them; on a flat floor a dig never meets any of that):
 * <ul>
 * <li>{@code khazuk:realrock_deep} (48 x 41 x 48, from y -48 to -8: mostly deepslate, caves, 1341 water, 59 lava, ores);</li>
 * <li>{@code khazuk:realrock_shallow} (48 x 33 x 48, from y -8 to 24: over half of it one open cavern, 8882 water): the hold dug BESIDE an
 * open cavern, where the walls guard and the flooding matter most.</li>
 * </ul>
 * The cut's outermost shell is closed with deepslate as the test starts (the rock that went on round it in the world: its caves and water
 * would otherwise open into the test world's air and pour out of it). Then the hold's spot is found by scanning the rock: a chamber for the
 * Shrine with the Mine dug east of it and the Barracks west, placed where their boxes hold the most solid rock (in the shallow cut, among the
 * spots with the cavern within 2 of their walls). A survival god's chamber is carved there and walled where it opened on a cave; the
 * Shrine set down for him (the server's path: a carved Hold Gate is 43 high and neither cut is, so the founding itself is the field run's,
 * SurvivalHoldGameTests); then by his own hands: the gold and bread handed over, both plans used on the chamber's walls (the digs paid),
 * the hold fed. Both halls must be dug out, closed by their own walls (no cave, cavern or aquifer reached but through the doorway or the
 * Mine's drift mouths) and dry (no water or lava standing or flowing in them or in the chamber), and stay so a minute on.
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazukrock", time = 6000, daylightCycle = BatchWorld.Rule.OFF, keepInventory = BatchWorld.Rule.OFF,
        weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
public class RealRockGameTests {

    static final String TAG = "rock";
    public static final int FEED_EVERY = 2400, FEED_BREAD = 32, GOLD = 40;
    /** Both halls dug within this from their order; then a minute's watch. */
    public static final int HALLS_BUDGET = 30000, WATCH = 1200, TIMEOUT = 36000;

    @GameTest(template = "realrock_deep", timeoutTicks = TIMEOUT, batch = "khazukrock")
    public static void aHoldDugInDeepRealRockIsWalledAndDry(GameTestHelper h) {
        run(h, false);
    }

    @GameTest(template = "realrock_shallow", timeoutTicks = TIMEOUT, batch = "khazukrock")
    public static void aHoldDugBesideAnOpenCavernIsWalledAndDry(GameTestHelper h) {
        run(h, true);
    }

    @AfterBatch(batch = "khazukrock")
    public static void runEnds(ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
    }

    /** The chamber, the Mine east of it and the Barracks west, all test-relative; and how solid their rock was. */
    record Site(BlockPos centre, PlanPlacement mine, PlanPlacement barracks, double solid, int caveBeside) {
    }

    private static void run(GameTestHelper h, boolean besideCave) {
        ServerLevel level = h.getLevel();
        AABB rb = h.getRelativeBounds();
        int sx = (int) rb.getXsize(), sy = (int) rb.getYsize(), sz = (int) rb.getZsize();
        seal(h, sx, sy, sz);
        Site site = site(h, sx, sy, sz, besideCave);
        h.assertTrue(site != null, "no spot in the " + sx + " x " + sy + " x " + sz + " cut takes the chamber, the Mine and the Barracks"
                + (besideCave ? " beside its cavern" : ""));
        BlockPos c = site.centre();
        Khazuk.LOG.info("KHAZUK {}: {} the chamber at {}, the Mine's box {}% rock, the cavern's cells within 2 of the halls {}", TAG,
                besideCave ? "shallow:" : "deep:", c.toShortString(), Math.round(site.solid() * 100), site.caveBeside());
        // the god's chamber: 5 x 3 x 5, walled with deepslate where it opened on a cave (and its two doors' wall cells solid)
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 3, 3))) {
            BlockPos a = h.absolutePos(p);
            boolean inner = Math.abs(p.getX() - c.getX()) <= 2 && Math.abs(p.getZ() - c.getZ()) <= 2 && p.getY() >= c.getY() && p.getY() <= c.getY() + 2;
            if (inner) {
                level.setBlock(a, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (SurvivalPlay.open(level, a) || !level.getFluidState(a).isEmpty()) {
                level.setBlock(a, rock, Block.UPDATE_CLIENTS);
            }
        }
        BlockPos shrineAt = c.offset(0, 0, -2);
        SurvivalGod god = SurvivalGod.join(h, c);
        god.give(Items.GOLD_INGOT, GOLD).give(Items.BREAD, FEED_BREAD).give(Items.COOKED_BEEF, 16)
                .give(KRegistry.PLAN_MINE.get(), 1).give(KRegistry.PLAN_BARRACKS.get(), 1);
        ShrineBlockEntity hold = SurvivalPlay.shrineFor(h, god, shrineAt, true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(TIMEOUT);
        int gold = SurvivalPlay.handAll(h, god, shrineAt, Items.GOLD_INGOT);
        SurvivalPlay.handAll(h, god, shrineAt, Items.BREAD);
        int before = hold.currency();
        dig(h, god, hold, site.mine(), KRegistry.PLAN_MINE.get(), c.offset(1, 0, 0));
        dig(h, god, hold, site.barracks(), KRegistry.PLAN_BARRACKS.get(), c.offset(-1, 0, 0));
        int paid = before - hold.currency();
        h.assertTrue(paid == HoldRooms.room("dawi_mine").cost() + HoldRooms.room("dawi_barracks").cost(), "the two digs cost " + paid + " gold");
        god.moveTo(c);
        SurvivalPlay.line(h, TAG, hold, (besideCave ? "shallow" : "deep") + ": " + gold + " gold handed, the Mine and the Barracks ordered (" + paid + " gold)");

        PlanPlacement mineAbs = PlanPlacement.compute(site.mine().room(), h.absolutePos(site.mine().door()), h.getAbsoluteDirection(site.mine().facing()));
        PlanPlacement barAbs = PlanPlacement.compute(site.barracks().room(), h.absolutePos(site.barracks().door()), h.getAbsoluteDirection(site.barracks().facing()));
        BoundingBox chamber = BoundingBox.fromCorners(h.absolutePos(c.offset(-2, 0, -2)), h.absolutePos(c.offset(2, 2, 2)));
        Map<String, Long> at = new LinkedHashMap<>();
        long ordered = h.getTick();
        long[] nextFeed = {ordered + FEED_EVERY};
        SurvivalGod.Job[] eating = {null};
        h.onEachTick(() -> {
            if (god.hasLeft()) {
                return;
            }
            long now = h.getTick();
            if (eating[0] != null && (eating[0].isDone() || eating[0].failed())) {
                eating[0] = null;
            }
            if (eating[0] == null && god.food() <= 12 && god.count(Items.COOKED_BEEF) > 0) {
                eating[0] = god.hold(Items.COOKED_BEEF).eat();
            }
            if (eating[0] == null && now >= nextFeed[0]) {
                god.give(Items.BREAD, FEED_BREAD);
                god.moveTo(c);
                SurvivalPlay.handAll(h, god, shrineAt, Items.BREAD);
                nextFeed[0] = now + FEED_EVERY;
            }
            step(h, hold, at, "mine", hold.has(Rooms.Function.OREMINE), "the Mine dug out");
            step(h, hold, at, "barracks", hold.has(HoldRooms.BARRACKS), "the Barracks dug out");
            if (now % 1200 == 0) {
                SurvivalPlay.line(h, TAG, hold, "a minute's look: digs " + SurvivalPlay.digs(hold) + ", fluid in the chamber "
                        + SurvivalPlay.fluids(level, chamber, 3));
            }
            boolean both = at.containsKey("mine") && at.containsKey("barracks");
            h.assertTrue(both || now - ordered < HALLS_BUDGET, "the halls weren't dug in " + HALLS_BUDGET + " ticks of real rock: digs "
                    + SurvivalPlay.digs(hold) + ", crew " + hold.works().crewCount() + "; " + at);
            if (!both) {
                return;
            }
            if (!at.containsKey("checked")) {
                check(h, level, hold, mineAbs, barAbs, chamber, "as they were dug out");
                at.put("checked", now);
                return;
            }
            if (now - at.get("checked") >= WATCH) {
                check(h, level, hold, mineAbs, barAbs, chamber, "a minute after");
                SurvivalPlay.line(h, TAG, hold, "the run's end: " + at);
                level.getServer().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }

    private static void check(GameTestHelper h, ServerLevel level, ShrineBlockEntity hold, PlanPlacement mine, PlanPlacement barracks,
                              BoundingBox chamber, String when) {
        String m = SurvivalPlay.leak(level, mine, SurvivalPlay.mouths(mine)), b = SurvivalPlay.leak(level, barracks, List.of());
        h.assertTrue(m == null, "the Mine " + when + " isn't closed by its own walls: " + m);
        h.assertTrue(b == null, "the Barracks " + when + " isn't closed by its own walls: " + b);
        for (var e : Map.of("the Mine", mine.box(), "the Barracks", barracks.box(), "the god's chamber", chamber).entrySet()) {
            List<String> wet = SurvivalPlay.fluids(level, e.getValue(), 6);
            h.assertTrue(wet.isEmpty(), e.getKey() + " " + when + " has water or lava in it: " + wet);
        }
        SurvivalPlay.line(h, TAG, hold, "both halls walled and dry " + when);
    }

    /** The god stands at {@code stand} before the chamber's wall and uses the plan on the wall at his feet: the hall runs on from there. */
    private static void dig(GameTestHelper h, SurvivalGod god, ShrineBlockEntity hold, PlanPlacement p, Item plan, BlockPos stand) {
        god.moveTo(stand);
        god.hold(plan).useOn(p.door(), h.getAbsoluteDirection(p.facing().getOpposite()));
        BlockPos door = h.absolutePos(p.door());
        h.assertTrue(hold.works().digs().stream().anyMatch(d -> d.job().door.equals(door)), "the " + p.room().displayName() + "'s plan on the wall at "
                + p.door() + " began no dig: told " + SurvivalPlay.last(god, 3));
    }

    private static void step(GameTestHelper h, ShrineBlockEntity hold, Map<String, Long> at, String key, boolean done, String what) {
        if (done && !at.containsKey(key)) {
            at.put(key, h.getTick());
            SurvivalPlay.line(h, TAG, hold, what);
        }
    }

    // ------------------------------------------------------------------ the cut, closed and read

    /** The cut's outermost shell: air and fluid there become deepslate, as the rock round it in the world was. */
    private static void seal(GameTestHelper h, int sx, int sy, int sz) {
        ServerLevel level = h.getLevel();
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, sx - 1, sy - 1, sz - 1)) {
            if (p.getX() != 0 && p.getX() != sx - 1 && p.getY() != 0 && p.getY() != sy - 1 && p.getZ() != 0 && p.getZ() != sz - 1) {
                continue;
            }
            BlockPos a = h.absolutePos(p);
            if (SurvivalPlay.open(level, a) || !level.getFluidState(a).isEmpty()) {
                level.setBlock(a, rock, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * The best spot: the chamber's middle {@code c} (its floor under {@code c}), the Mine's door in its east wall at {@code c + (3, 0, 0)}, the
     * Barracks' in its west wall at {@code c - (3, 0, 0)}; every box inside the cut's inner part, scored by the solid rock in the two halls'
     * boxes and the chamber's. {@code besideCave}: only among the spots with at least 40 open cells (the cavern, a cave, water) within 2 of the
     * halls' walls; if none has that many, the one with the most.
     */
    private static Site site(GameTestHelper h, int sx, int sy, int sz, boolean besideCave) {
        ServerLevel level = h.getLevel();
        int[][][] solid = new int[sx + 1][sy + 1][sz + 1];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockPos a = h.absolutePos(new BlockPos(x, y, z));
                    boolean s = !SurvivalPlay.open(level, a) && level.getFluidState(a).isEmpty();
                    solid[x + 1][y + 1][z + 1] = (s ? 1 : 0) + solid[x][y + 1][z + 1] + solid[x + 1][y][z + 1] + solid[x + 1][y + 1][z]
                            - solid[x][y][z + 1] - solid[x][y + 1][z] - solid[x + 1][y][z] + solid[x][y][z];
                }
            }
        }
        Rooms.Room mine = HoldRooms.room("dawi_mine"), barracks = HoldRooms.room("dawi_barracks");
        BoundingBox inner = new BoundingBox(1, 1, 1, sx - 2, sy - 2, sz - 2);
        List<Site> all = new ArrayList<>();
        for (int x = 1; x < sx - 1; x++) {
            for (int z = 1; z < sz - 1; z++) {
                for (int y = 2; y < sy - 4; y++) {
                    BlockPos c = new BlockPos(x, y, z);
                    PlanPlacement m = PlanPlacement.compute(mine, c.offset(3, 0, 0), Direction.EAST);
                    PlanPlacement b = PlanPlacement.compute(barracks, c.offset(-3, 0, 0), Direction.WEST);
                    BoundingBox ch = BoundingBox.fromCorners(c.offset(-3, -1, -3), c.offset(3, 3, 3));
                    if (!SurvivalPlay.inside(inner, m.box()) || !SurvivalPlay.inside(inner, b.box()) || !SurvivalPlay.inside(inner, ch)) {
                        continue;
                    }
                    int vol = volume(m.box()) + volume(b.box()) + volume(ch);
                    double s = (count(solid, m.box()) + count(solid, b.box()) + count(solid, ch)) / (double) vol;
                    int ring = 0;
                    for (BoundingBox box : List.of(m.box(), b.box())) {
                        BoundingBox out = clip(box.inflatedBy(2), sx, sy, sz);
                        ring += (volume(out) - count(solid, out)) - (volume(box) - count(solid, box));
                    }
                    all.add(new Site(c, m, b, s, ring));
                }
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        if (besideCave) {
            List<Site> beside = all.stream().filter(s -> s.caveBeside() >= 40).toList();
            if (beside.isEmpty()) {
                return all.stream().max(java.util.Comparator.comparingInt(Site::caveBeside)).orElse(null);
            }
            all = beside;
        }
        return all.stream().max(java.util.Comparator.comparingDouble(Site::solid)).orElse(null);
    }

    private static BoundingBox clip(BoundingBox b, int sx, int sy, int sz) {
        return new BoundingBox(Math.max(0, b.minX()), Math.max(0, b.minY()), Math.max(0, b.minZ()), Math.min(sx - 1, b.maxX()),
                Math.min(sy - 1, b.maxY()), Math.min(sz - 1, b.maxZ()));
    }

    private static int volume(BoundingBox b) {
        return b.getXSpan() * b.getYSpan() * b.getZSpan();
    }

    /** Solid cells in {@code b} (test-relative), from the prefix sums. */
    private static int count(int[][][] s, BoundingBox b) {
        int x0 = b.minX(), y0 = b.minY(), z0 = b.minZ(), x1 = b.maxX() + 1, y1 = b.maxY() + 1, z1 = b.maxZ() + 1;
        return s[x1][y1][z1] - s[x0][y1][z1] - s[x1][y0][z1] - s[x1][y1][z0] + s[x0][y0][z1] + s[x0][y1][z0] + s[x1][y0][z0] - s[x0][y0][z0];
    }
}
