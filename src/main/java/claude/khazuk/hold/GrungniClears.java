package claude.khazuk.hold;

import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomShoring;
import claude.settlementcore.room.Siting;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementSpawns;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Grungni clears his gate's ground (the user, 2026-11-06: "shouldnt it break stuff out of the way, and fill hollow ground"). The Hold Gate
 * founded is the god's own work, so the land's small refusals are his to mend, not the god's to walk round: before the engine sites the gate
 * (Settlement Core's Siting, unchanged), over the site and {@value #MARGIN} round it,
 * <ul>
 * <li>what stands on the ground (a player's walls and roofs, fences, torches, trees) is broken, and drops where it stood;</li>
 * <li>water, ice and lava lying on it are turned to stone (a lake's edge becomes a stone dam round the gate);</li>
 * <li>hollow ground (caves, ravines, a dungeon's air) under it is filled with stone, and ground falling away deeper than the plinth reaches
 * ({@link Siting#SKIRT_MAX}) is raised to it.</li>
 * </ul>
 * Then the engine sites it as ever; where it still refuses at a spot of that kind, the ground round the spot is mended too, a few times over.
 * Kept: anything with a block entity (a chest, a spawner: another's hoard), other settlements' buildings and hearts, and what the engine
 * refuses for other reasons (too steep, another building, unloaded land). If the gate can't be sited in the end, every block is put back as
 * it was and nothing drops.
 */
public final class GrungniClears {

    /** How far round the gate's box the ground is mended (its berm, landing and the ramp's foot). */
    public static final int MARGIN = 5;
    /** Spots the engine still refuses, mended one round at a time, at most. */
    public static final int ROUNDS = 16;
    /** How far round a refused spot the ground is mended. */
    public static final int SPOT_REACH = 3;
    private static final int TALL = 128;
    /** The most ground is raised under a column, past the plinth's own reach. */
    public static final int RAISE_MAX = 32;

    private final ServerLevel level;
    private final PlanPlacement pl;
    private final int floor;
    private final List<BoundingBox> keepOut = new ArrayList<>();
    /** Every block changed, as it was, in the order first changed. */
    private final Long2ObjectLinkedOpenHashMap<BlockState> was = new Long2ObjectLinkedOpenHashMap<>();
    private final BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();

    private GrungniClears(ServerLevel level, PlanPlacement pl) {
        this.level = level;
        this.pl = pl;
        this.floor = pl.door().getY() - 1;
        for (Settlement s : SettlementSpawns.loaded(level)) {
            if (s.getBlockPos().distManhattan(pl.door()) > 512) {
                continue;
            }
            for (Building b : s.buildings()) {
                keepOut.add(b.box().inflatedBy(1));
            }
            BlockPos o = s.getBlockPos();
            keepOut.add(new BoundingBox(o.getX() - 3, o.getY() - 2, o.getZ() - 3, o.getX() + 3, o.getY() + 4, o.getZ() + 3));
        }
    }

    /** Why the engine still refuses the mended site (null: it sites it). Then everything is already put back. */
    private Siting.@Nullable Refusal refusal;

    public Siting.@Nullable Refusal refusal() {
        return refusal;
    }

    /** How many blocks Grungni changed. */
    public int changed() {
        return was.size();
    }

    /**
     * Mends the ground for the gate at {@code pl} until the engine sites it, or puts it all back ({@link #refusal()} says why). Sited, the
     * caller {@link #keep()}s it once the gate is begun (what was broken drops), or {@link #putBack()}s it.
     */
    public static GrungniClears clear(ServerLevel level, PlanPlacement pl) {
        GrungniClears g = new GrungniClears(level, pl);
        BoundingBox box = pl.box();
        for (int z = box.minZ() - MARGIN; z <= box.maxZ() + MARGIN; z++) {
            for (int x = box.minX() - MARGIN; x <= box.maxX() + MARGIN; x++) {
                g.column(x, z);
            }
        }
        Siting.Result r = Siting.plan(level, pl, RoomShoring.Warren.alone(), true);
        for (int round = 0; round < ROUNDS && r instanceof Siting.Refusal no && mends(no.why()); round++) {
            int before = g.was.size();
            for (int dz = -SPOT_REACH; dz <= SPOT_REACH; dz++) {
                for (int dx = -SPOT_REACH; dx <= SPOT_REACH; dx++) {
                    g.column(no.spot().getX() + dx, no.spot().getZ() + dz);
                }
            }
            if (g.was.size() == before) {
                break;                                           // nothing there he could mend
            }
            r = Siting.plan(level, pl, RoomShoring.Warren.alone(), true);
        }
        if (r instanceof Siting.Refusal no) {
            g.putBack();
            g.refusal = no;
        }
        return g;
    }

    /**
     * Whether the engine's refusal is one Grungni mends (what stands on the ground, water, lava and hollows under it): the held plan's
     * outline shows the gate's site clear of these (KhazukClient).
     */
    public static boolean mends(String why) {
        return why.startsWith("Something stands at") || why.startsWith("Something is in the ground") || why.startsWith("The ground is hollow")
                || why.startsWith("The ground falls away") || why.startsWith("The ground under the ramp is hollow")
                || why.startsWith("The water at") || why.startsWith("Water at") || why.startsWith("The lava at") || why.startsWith("Lava at")
                || why.startsWith("The tree at") || why.startsWith("The logs at");
    }

    /** One column: things off the ground, water and lava to stone, the ground raised to the plinth's reach, its hollows filled. */
    private void column(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;                                              // the engine refuses unloaded land itself
        }
        int min = level.getMinY();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int y = top;
        int laid = Integer.MIN_VALUE;                            // the highest stone laid in place of water
        for (; y > min && top - y < TALL; y--) {
            BlockState s = state(x, y, z);
            if (Siting.ground(s)) {
                break;
            }
            if (s.isAir() || s.hasBlockEntity()) {
                continue;
            }
            if (!s.getFluidState().isEmpty() || s.is(BlockTags.ICE)) {
                if (set(x, y, z, stone(y))) {
                    laid = Math.max(laid, y);
                }
            } else {
                set(x, y, z, Blocks.AIR.defaultBlockState());     // a thing on the ground, a tree, a plant: off it
            }
        }
        if (!Siting.ground(state(x, y, z)) && laid == Integer.MIN_VALUE) {
            return;                                              // no ground within reach (the void, a chasm): nothing to build up from
        }
        int ground = Math.max(y, laid);
        int reach = floor - 1 - Siting.SKIRT_MAX;
        if (reach - ground > RAISE_MAX) {
            return;                                              // a gorge too deep to raise: the engine says so
        }
        for (int yy = ground + 1; yy <= reach; yy++) {           // fallen away past the plinth's reach: raised to it
            fill(x, yy, z);
        }
        ground = Math.max(ground, reach);
        for (int yy = ground; yy >= Math.min(ground, floor) - 3 && yy > min; yy--) {
            fill(x, yy, z);                                      // hollows under it: filled
        }
    }

    private void fill(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (!Siting.ground(s) && !s.hasBlockEntity()) {
            set(x, y, z, stone(y));
        }
    }

    private static BlockState stone(int y) {
        return (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    private BlockState state(int x, int y, int z) {
        return level.getBlockState(cur.set(x, y, z));
    }

    /** Sets a block quietly (no neighbour updates: water held, nothing falls), keeping what it was. False where it is kept out. */
    private boolean set(int x, int y, int z, BlockState to) {
        cur.set(x, y, z);
        for (BoundingBox k : keepOut) {
            if (k.isInside(cur)) {
                return false;
            }
        }
        BlockState from = level.getBlockState(cur);
        if (from == to) {
            return false;
        }
        was.putIfAbsent(cur.asLong(), from);
        level.setBlock(cur, to, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    /** Every block changed put back as it was, nothing dropped. */
    public void putBack() {
        var it = was.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            level.setBlock(cur.set(e.getLongKey()), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        was.clear();
    }

    /** The gate begun: what was broken drops where it stood (a fluid or air, nothing; a plant, its seeds as when broken). */
    public void keep() {
        var it = was.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            BlockState s = e.getValue();
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.is(BlockTags.ICE)) {
                Block.dropResources(s, level, BlockPos.of(e.getLongKey()));
            }
        }
        was.clear();
    }
}
