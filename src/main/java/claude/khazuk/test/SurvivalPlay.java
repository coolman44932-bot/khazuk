package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.HallSeal;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.test.SurvivalGod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the survival tests share (SurvivalHoldGameTests, RealRockGameTests, WalkAwayGameTests, GodDeathGameTests): where a survival god can
 * stand to reach a block, handing the Shrine all he carries of a thing, the seals of a hall, whether a dug hall is closed by its own walls
 * IN THE WORLD (the walls guard's rule, WallsGameTests, but over what was really dug, caves and all), and the fluids in a hall.
 */
final class SurvivalPlay {

    private SurvivalPlay() {
    }

    // ------------------------------------------------------------------ the world

    static boolean open(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** Sets every cell from {@code lo} to {@code hi} (test-relative, corners included) to {@code state}, quietly (no neighbour updates). */
    static void fill(GameTestHelper h, BlockPos lo, BlockPos hi, BlockState state) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(lo), b = h.absolutePos(hi);
        for (BlockPos p : BlockPos.betweenClosed(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The test's structure as a block box (absolute). */
    static BoundingBox bounds(GameTestHelper h) {
        AABB b = h.getBounds();
        return new BoundingBox((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ),
                (int) Math.ceil(b.maxX) - 1, (int) Math.ceil(b.maxY) - 1, (int) Math.ceil(b.maxZ) - 1);
    }

    static boolean inside(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX() && inner.minY() >= outer.minY() && inner.minZ() >= outer.minZ()
                && inner.maxX() <= outer.maxX() && inner.maxY() <= outer.maxY() && inner.maxZ() <= outer.maxZ();
    }

    // ------------------------------------------------------------------ the god

    /**
     * A cell where the god can stand (feet and head clear, solid under him, no fluid) and reach {@code target} (test-relative): his eyes
     * 1.2 to {@code maxEye} blocks from its middle, the nearest to 2.5 taken. Never one of {@code avoid}. Fails the test when there is none.
     */
    static BlockPos standNear(GameTestHelper h, BlockPos target, double maxEye, BlockPos... avoid) {
        ServerLevel level = h.getLevel();
        BlockPos t = h.absolutePos(target);
        Set<BlockPos> no = new HashSet<>();
        for (BlockPos a : avoid) {
            no.add(h.absolutePos(a));
        }
        Vec3 c = Vec3.atCenterOf(t);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(t.offset(-4, -3, -4), t.offset(4, 2, 4))) {
            if (p.equals(t) || no.contains(p) || !open(level, p) || !open(level, p.above()) || open(level, p.below())
                    || !level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) {
                continue;
            }
            double d = new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5).distanceTo(c);
            if (d < 1.2 || d > maxEye) {
                continue;
            }
            double score = Math.abs(d - 2.5);
            if (score < bestScore) {
                bestScore = score;
                best = p.immutable();
            }
        }
        if (best == null) {
            throw h.assertionException("nowhere for the god to stand within " + maxEye + " of " + target + " (" + level.getBlockState(t) + ")");
        }
        return h.relativePos(best);
    }

    /** The god hands the Shrine at {@code shrine} every {@code item} he carries, a stack a click (its block's own use). How many. */
    static int handAll(GameTestHelper h, SurvivalGod god, BlockPos shrine, Item item) {
        int handed = 0;
        for (int guard = 0; guard < 40 && god.count(item) > 0; guard++) {
            int before = god.count(item);
            god.hold(item).useOn(shrine, Direction.UP);
            int after = god.count(item);
            h.assertTrue(after < before, "the Shrine at " + shrine + " took none of the god's " + item + " (" + before + " carried; held "
                    + god.held() + "; told " + last(god, 3) + ")");
            handed += before - after;
        }
        return handed;
    }

    /** The last {@code n} lines the god was told. */
    static List<String> last(SurvivalGod god, int n) {
        List<String> c = god.chat();
        return c.subList(Math.max(0, c.size() - n), c.size());
    }

    /**
     * A Shrine set down for {@code god} at {@code at} by the server's own path (its block set, then ShrineBlock#setPlacedBy as for a player's
     * placing): not a survival placing, which takes root only on a Hold Gate's dais (SurvivalHoldGameTests proves that one). {@code woke}:
     * the god is the Dawi's, his first Shrine (its first Dawi come up: 3 Miners and a Warrior in its deeps).
     */
    static ShrineBlockEntity shrineFor(GameTestHelper h, SurvivalGod god, BlockPos at, boolean woke) {
        ServerPlayer p = god.player();
        if (woke) {
            Waking.setGod(p, new Waking.God(true, "Karak " + p.getGameProfile().name(), 0, false));
        }
        BlockState s = KRegistry.SHRINE.get().defaultBlockState();
        h.setBlock(at, s);
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), h.absolutePos(at), s, p, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(at)) instanceof ShrineBlockEntity hold)) {
            throw h.assertionException("no Shrine's block entity at " + at);
        }
        h.assertTrue(p.getUUID().equals(hold.owner()), "the Shrine isn't the god's: owner " + hold.owner());
        // A block entity set down is registered with the loaded settlements (SettlementSpawns, which ownedAt searches) by its onLoad a tick
        // later, or by its first second. A player can't use a plan the same tick he places a Shrine (a client's clicks are several ticks
        // apart), so this puts the tests where he already is, with the hold registered, rather than waiting a tick in every caller.
        claude.settlementcore.settlement.SettlementSpawns.add(h.getLevel(), hold);
        h.assertTrue(ShrineBlockEntity.ownedAt(p, h.absolutePos(at)) == hold, "the god's hold isn't found among the loaded ones");
        return hold;
    }

    /** One pacing line, as the old runs wrote them: "KHAZUK <tag>: step at tick (s): food, gold, people up and in the deeps". */
    static void line(GameTestHelper h, String tag, @Nullable ShrineBlockEntity hold, String step) {
        Khazuk.LOG.info("KHAZUK {}: {} at tick {} ({} s): food {}, gold {}, people {} up and {}+{} in the deeps", tag, step, h.getTick(), h.getTick() / 20,
                hold == null ? "-" : hold.food(), hold == null ? "-" : hold.currency(), hold == null ? "-" : hold.peopleCount(),
                hold == null ? "-" : hold.reserveWorkers(), hold == null ? "-" : hold.reserveSoldiers());
    }

    static String digs(ShrineBlockEntity hold) {
        return hold.works().digs().stream().map(d -> d.room().id() + " " + Math.round(d.job().progress() * 100) + "% ("
                + d.job().finishedCount() + "/" + d.job().taskCount() + ")").toList().toString();
    }

    // ------------------------------------------------------------------ seals

    /** A rune-sealed hall door: the bottom middle of its arch (where a hall's door goes), the way a hall dug through it runs, and a block of it. */
    record Arch(BlockPos anchor, Direction runs, BlockPos block) {
    }

    /** Every sealed arch in {@code box} (absolute), one each. */
    static List<Arch> arches(ServerLevel level, BoundingBox box) {
        Map<BlockPos, Arch> out = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).getBlock() instanceof HallSeal) {
                BlockPos a = HallSeal.anchor(level, p);
                if (a != null && !out.containsKey(a)) {
                    out.put(a.immutable(), new Arch(a.immutable(), HallSeal.runs(level, p), a.above().immutable()));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------ the walls guard, in the world

    /**
     * Where a dug hall at {@code pl} is open to the world round it, or null when its own walls close it: its open space (no collision, so
     * fluids count as open), flooded from inside its door within its box, may reach out of its box only through its doorway (its door's face,
     * up to 3 either side of the door) or within 2 of one of {@code mouths} (the Mine's drift mouths, which its Miners drive on into the rock).
     * A cave, an aquifer or the god's pocket reached any other way is a hole: a hall leaning on the rock to close it.
     */
    static @Nullable String leak(ServerLevel level, PlanPlacement pl, List<BlockPos> mouths) {
        BoundingBox box = pl.box();
        Direction f = pl.facing();
        Direction side = f.getClockWise();
        BlockPos door = pl.door();
        BlockPos start = null;
        for (int k = 1; k <= 4 && start == null; k++) {
            for (int up = 0; up <= 2 && start == null; up++) {
                BlockPos c = door.relative(f, k).above(up);
                if (box.isInside(c) && open(level, c)) {
                    start = c;
                }
            }
        }
        if (start == null) {
            return "nothing open inside its door at " + door.toShortString();
        }
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!open(level, n)) {
                    continue;
                }
                if (box.isInside(n)) {
                    if (seen.add(n)) {
                        q.add(n);
                    }
                    continue;
                }
                int along = (p.getX() - door.getX()) * f.getStepX() + (p.getZ() - door.getZ()) * f.getStepZ();
                int across = (p.getX() - door.getX()) * side.getStepX() + (p.getZ() - door.getZ()) * side.getStepZ();
                if (along == 0 && Math.abs(across) <= 3 && d == f.getOpposite()) {
                    continue;                                    // its doorway, out into the space it was dug from
                }
                boolean mouth = false;
                for (BlockPos m : mouths) {
                    mouth |= m.distSqr(p) <= 4.5;
                }
                if (!mouth) {
                    return "open to the world at " + n.toShortString() + " (" + level.getBlockState(n).getBlock() + "), out of "
                            + p.toShortString() + " going " + d;
                }
            }
        }
        return null;
    }

    /** The Mine's drift mouths in the world (its "drift" stations, each and the cell over it). */
    static List<BlockPos> mouths(PlanPlacement pl) {
        List<BlockPos> out = new ArrayList<>();
        for (Rooms.Station s : pl.room().stations()) {
            if ("drift".equals(s.role())) {
                BlockPos w = pl.world(new BlockPos(s.x(), s.y(), s.z()));
                out.add(w);
                out.add(w.above());
            }
        }
        return out;
    }

    /** The fluid cells in {@code box} (absolute) that a hall's template leaves open: water or lava standing or flowing in the hold. A few named. */
    static List<String> fluids(ServerLevel level, BoundingBox box, int name) {
        List<String> out = new ArrayList<>();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {   // water or lava itself, not a waterlogged block
                n++;
                if (out.size() < name) {
                    out.add(p.toShortString() + " " + s.getBlock());
                }
            }
        }
        if (n > out.size()) {
            out.add("... " + n + " in all");
        }
        return out;
    }
}
