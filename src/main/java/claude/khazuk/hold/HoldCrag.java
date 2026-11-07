package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.room.Siting;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.BuildingBook;
import claude.settlementcore.settlement.SettlementBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The mountain round a Hold Gate. The Miners raise the gatehouse (its facade, terrace, guardians and passage: the room's template); once it
 * stands, the god raises the crag it is cut into, {@value #PER_SECOND} cells a second, the lowest course first, rock pushed up under
 * anyone standing where it comes. Its cells are tools/gen_hold_gate.py's (data/khazuk/hold/hold_gate_crag.json, in the template's frame,
 * running past its box either side); each column's foot is filled down to the ground where the land falls away. Only air and the land
 * itself (Siting#mayReplace) are taken, never a block entity, never inside the hold's other buildings or round its Shrine; a plan whose
 * mountain would cover the Shrine is refused ({@link #buries}). Someone standing where the rock comes is lifted onto it if there is room
 * above, else that cell is left open; a rider or his mount is never moved. Kept per gate by its door, so a save mid-rise carries on.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = Khazuk.MODID)
public final class HoldCrag {

    public static final int PER_SECOND = 1500;
    /** How far a column's foot reaches down to meet lower ground. */
    static final int FOOTING = 16;
    static final Identifier FILE = Identifier.fromNamespaceAndPath(Khazuk.MODID, "hold/hold_gate_crag.json");

    /** The crag's cells, lowest first: {@code xs[i], ys[i], zs[i]} in the template's frame, {@code states[i]}; {@code foot[i]} if the
     *  lowest of its column. */
    record Cells(int[] xs, int[] ys, int[] zs, BlockState[] states, boolean[] foot) {
        int size() {
            return xs.length;
        }
    }

    @Nullable
    private static Cells cells;

    /** Cells raised so far, per gate (its door's position). */
    private final Long2IntMap done = new Long2IntOpenHashMap();
    /** Whether {@link #done} changed since the hold last saved. */
    private boolean dirty;

    /** Cells left open because someone stood there, per gate: tried again each second once the rest is up, for {@value #RETRY_SECONDS} s. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<it.unimi.dsi.fastutil.ints.IntArrayList> waiting = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final Long2IntMap retried = new Long2IntOpenHashMap();
    static final int RETRY_SECONDS = 60;

    /** A new server reads the cells afresh: a datapack may have changed them. */
    @net.neoforged.bus.api.SubscribeEvent
    static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
        cells = null;
        palette = null;
    }

    static Cells cells(ServerLevel level) {
        if (cells == null) {
            cells = read(level);
        }
        return cells;
    }

    private static Cells read(ServerLevel level) {
        Optional<Resource> res = level.getServer().getResourceManager().getResource(FILE);
        if (res.isEmpty()) {
            Khazuk.LOG.error("Khazuk: missing {}", FILE);
            return new Cells(new int[0], new int[0], new int[0], new BlockState[0], new boolean[0]);
        }
        try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            List<BlockState> palette = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("palette")) {
                palette.add(net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                        level.holderLookup(Registries.BLOCK), e.getAsString(), false).blockState());
            }
            JsonArray a = o.getAsJsonArray("cells");
            int n = a.size();
            int[] xs = new int[n], ys = new int[n], zs = new int[n];
            BlockState[] states = new BlockState[n];
            Long2IntMap lowest = new Long2IntOpenHashMap();
            for (int i = 0; i < n; i++) {
                JsonArray c = a.get(i).getAsJsonArray();
                xs[i] = c.get(0).getAsInt();
                ys[i] = c.get(1).getAsInt();
                zs[i] = c.get(2).getAsInt();
                states[i] = palette.get(c.get(3).getAsInt());
                long col = BlockPos.asLong(xs[i], 0, zs[i]);
                if (!lowest.containsKey(col) || ys[lowest.get(col)] > ys[i]) {
                    lowest.put(col, i);
                }
            }
            boolean[] foot = new boolean[n];
            lowest.values().forEach(i -> foot[i] = true);
            return new Cells(xs, ys, zs, states, foot);
        } catch (Exception e) {
            Khazuk.LOG.error("Khazuk: couldn't read {}", FILE, e);
            return new Cells(new int[0], new int[0], new int[0], new BlockState[0], new boolean[0]);
        }
    }

    /**
     * Whether the gate at {@code pl} stands down in the rock (carved, Founding#beginCarved): rock more than {@link AncestorPlan#ROOF} over its
     * own top. No mountain rises round it: it would fill the caves round a deep gate with stone.
     */
    public static boolean carved(ServerLevel level, PlanPlacement pl) {
        BlockPos top = new BlockPos(pl.door().getX(), pl.box().maxY(), pl.door().getZ());
        return AncestorPlan.rockOver(level, top) > AncestorPlan.ROOF;
    }

    /** Whether {@code b} is a Hold Gate. */
    public static boolean gate(Building b) {
        Rooms.Room r = Rooms.get(b.room());
        return r != null && r.function() == HoldRooms.GATE;
    }

    /** Cells raised so far round the gate at {@code door}. */
    public int raised(BlockPos door) {
        return done.getOrDefault(door.asLong(), 0);
    }

    /** True once if the rise moved since the last call: the hold saves then. */
    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** The whole mountain's box in the world for {@code pl} (a gate's placement), its feet included. */
    public static BoundingBox footprint(ServerLevel level, PlanPlacement pl) {
        Cells c = cells(level);
        if (c.size() == 0) {
            return pl.box();
        }
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = 0; i < c.size(); i++) {
            x0 = Math.min(x0, c.xs[i]);
            y0 = Math.min(y0, c.ys[i]);
            z0 = Math.min(z0, c.zs[i]);
            x1 = Math.max(x1, c.xs[i]);
            y1 = Math.max(y1, c.ys[i]);
            z1 = Math.max(z1, c.zs[i]);
        }
        return BoundingBox.fromCorners(pl.world(new BlockPos(x0, y0 - FOOTING, z0)), pl.world(new BlockPos(x1, y1, z1)));
    }

    /** Whether {@code pl}'s mountain would come within 2 of the Shrine at {@code shrine}. */
    public static boolean buries(ServerLevel level, PlanPlacement pl, BlockPos shrine) {
        BoundingBox f = footprint(level, pl).inflatedBy(2);
        return f.isInside(shrine);
    }

    /** Whether the gate at {@code door} has its whole mountain (every cell placed or given up on, none waiting for someone to move). */
    public boolean risen(ServerLevel level, BlockPos door) {
        return done.getOrDefault(door.asLong(), 0) >= cells(level).size() && !waiting.containsKey(door.asLong());
    }

    /**
     * A second's rise for {@code hold}: up to {@code budget} cells of the first gate still short of its mountain. Gates no longer in the
     * hold are forgotten. True if anything is still rising.
     */
    public boolean second(ServerLevel level, SettlementBlockEntity hold, int budget) {
        Cells c = cells(level);
        LongSet gates = new LongOpenHashSet();
        Building rising = null;
        for (Building b : hold.buildings()) {
            if (b.door() != null && gate(b)) {
                gates.add(b.door().asLong());
                if (rising == null && (done.getOrDefault(b.door().asLong(), 0) < c.size() || waiting.containsKey(b.door().asLong()))) {
                    rising = b;
                }
            }
        }
        if (done.keySet().removeIf(k -> !gates.contains(k))) {
            dirty = true;
        }
        waiting.keySet().removeIf(k -> !gates.contains(k));
        if (rising == null || c.size() == 0) {
            return false;
        }
        PlanPlacement pl = BuildingBook.placementOf(rising);
        if (pl == null) {
            done.put(rising.door().asLong(), c.size());
            dirty = true;
            return false;
        }
        long key = rising.door().asLong();
        int i = done.getOrDefault(key, 0);
        if (i == 0 && carved(level, pl)) {
            done.put(key, c.size());                             // carved down in the rock: the rock round it is its mountain
            dirty = true;
            return false;
        }
        if (i == 0) {
            hold.tellOwner("Grungni raises the mountain round the " + pl.room().displayName() + "!", ChatFormatting.GOLD);
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pl.box().minX() - 16, pl.box().minY() - 2 - FOOTING,
                pl.box().minZ() - 16, pl.box().maxX() + 17, pl.box().maxY() + 12, pl.box().maxZ() + 17), e -> !e.isSpectator());
        List<BoundingBox> keepOut = new ArrayList<>();
        for (Building b : hold.buildings()) {
            if (b != rising) {
                keepOut.add(b.box());                            // the hold's other buildings keep their air
            }
        }
        BlockPos shrine = hold.getBlockPos();
        keepOut.add(new BoundingBox(shrine.getX() - 2, shrine.getY() - 1, shrine.getZ() - 2, shrine.getX() + 2, shrine.getY() + 3, shrine.getZ() + 2));
        // every other settlement near, of any race and any god (another of this god's holds too): its rooms and its heart keep their air
        // (the bug hunt of 2d0b04c: the mountain filled a neighbour's halls with stone)
        for (claude.settlementcore.settlement.Settlement other : claude.settlementcore.settlement.SettlementSpawns.loaded(level)) {
            if (other == hold || other.getBlockPos().distManhattan(shrine) > 256) {
                continue;
            }
            for (Building b : other.buildings()) {
                keepOut.add(b.box().inflatedBy(1));
            }
            BlockPos o = other.getBlockPos();
            keepOut.add(new BoundingBox(o.getX() - 3, o.getY() - 1, o.getZ() - 3, o.getX() + 3, o.getY() + 4, o.getZ() + 3));
        }
        own = pl.box();
        int end = Math.min(c.size(), i + budget);
        BlockPos last = null;
        it.unimi.dsi.fastutil.ints.IntArrayList held = waiting.get(key);
        for (; i < end; i++) {
            BlockPos p = pl.world(new BlockPos(c.xs[i], c.ys[i], c.zs[i]));
            if (!level.isLoaded(p)) {
                break;                                           // wait for its chunk
            }
            int r = cell(level, c, i, p, near, keepOut, false);
            if (r == OCCUPIED) {
                if (held == null) {
                    held = new it.unimi.dsi.fastutil.ints.IntArrayList();
                    waiting.put(key, held);
                }
                held.add(i);                                     // someone there: tried again once the rest is up
            }
            if ((i & 63) == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, c.states[i]), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                        12, 0.6, 0.3, 0.6, 0.05);
                last = p;
            }
        }
        if (done.put(key, i) != i) {
            dirty = true;
        }
        if (i >= c.size() && held != null) {
            int tries = retried.merge(key, 1, Integer::sum);
            it.unimi.dsi.fastutil.ints.IntArrayList still = new it.unimi.dsi.fastutil.ints.IntArrayList();
            for (int k = 0; k < held.size(); k++) {
                int j = held.getInt(k);
                BlockPos p = pl.world(new BlockPos(c.xs[j], c.ys[j], c.zs[j]));
                if (level.isLoaded(p) && cell(level, c, j, p, near, keepOut, true) == OCCUPIED) {
                    still.add(j);
                }
            }
            if (still.isEmpty() || tries >= RETRY_SECONDS) {
                waiting.remove(key);                             // all in, or a minute's wait for whoever stands there: let them keep their hollow
                retried.remove(key);
            } else {
                waiting.put(key, still);
            }
        }
        if (last != null) {
            level.playSound(null, last, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        }
        if (i >= c.size() && !waiting.containsKey(key)) {
            hold.tellOwner("The " + pl.room().displayName() + " stands in its mountain.", ChatFormatting.GOLD);
            return false;
        }
        return true;
    }

    static final int PLACED = 1, REFUSED = 0, OCCUPIED = -1;

    /** Cell {@code i} of the mountain at {@code p}, and its column's foot if it is the lowest: {@link #PLACED}, {@link #REFUSED} or {@link #OCCUPIED}. */
    private static int cell(ServerLevel level, Cells c, int i, BlockPos p, List<LivingEntity> near, List<BoundingBox> keepOut, boolean retry) {
        BlockState s = c.states[i];
        if (s.getBlock() instanceof FallingBlock && level.getBlockState(p.below()).isAir()) {
            s = Blocks.COBBLESTONE.defaultBlockState();           // nothing loose over a hollow
        }
        // a retried cell may have the mountain's own stone over it by now: that isn't a roof (the review of cb5d663)
        int r = place(level, p, s, near, keepOut, retry ? Roof.UNDER_OTHERS : Roof.LOOK);
        if (r == PLACED && c.foot[i]) {
            BlockState fill = s.is(Blocks.MOSS_CARPET) || s.getBlock() instanceof FallingBlock ? Blocks.STONE.defaultBlockState() : s;
            BlockPos q = p.below();
            for (int d = 0; d < FOOTING && level.isLoaded(q) && hollow(level.getBlockState(q)); d++, q = q.below()) {
                place(level, q, fill, near, keepOut, Roof.NONE);   // its foot, under the cell just set: no roof to look for (the review of cb5d663)
            }
        }
        return r;
    }

    /** How a cell looks for a built roof over it: not at all (a column's foot), at what stands there, or past the mountain's own stone. */
    enum Roof { NONE, LOOK, UNDER_OTHERS }

    private static java.util.@Nullable Set<net.minecraft.world.level.block.Block> palette;

    /** The blocks the mountain is made of: over a retried cell they are its own, not a roof. Built once a server (the review of 719e3c3). */
    private static java.util.Set<net.minecraft.world.level.block.Block> palette(ServerLevel level) {
        if (palette != null) {
            return palette;
        }
        Cells c = cells(level);
        java.util.Set<net.minecraft.world.level.block.Block> out = new java.util.HashSet<>();
        for (BlockState st : c.states()) {
            out.add(st.getBlock());
        }
        out.add(Blocks.COBBLESTONE);
        out.add(Blocks.STONE);
        palette = java.util.Set.copyOf(out);
        return palette;
    }

    /** What the mountain may take: air, the land and its cover, water; never a block entity. */
    static boolean takes(BlockState s) {
        return !s.hasBlockEntity() && (s.isAir() || s.canBeReplaced() || Siting.mayReplace(s));
    }

    /** What a column's foot fills down through: air, water, plants; it stops on solid ground. */
    static boolean hollow(BlockState s) {
        return !s.hasBlockEntity() && (s.isAir() || s.canBeReplaced());
    }

    /** The rising gate's own box: under its own stone the mountain may fill (it wraps the towers); elsewhere a built roof keeps its air. */
    private static BoundingBox own;

    /** How far up the mountain looks for a roof over an air cell. */
    static final int ROOF_LOOK = 12;

    /**
     * Whether the air at {@code p} is under something someone built (planks, glass, brick: anything not the land, its plants, its trees or
     * water) within {@value #ROOF_LOOK} blocks: a house's room, a hall. The mountain fills a cave, never a room (the bug hunt of 2d0b04c).
     */
    public static boolean roofed(ServerLevel level, BlockPos p) {
        return roofed(level, p, java.util.Set.of());
    }

    /** Whether a retried cell at {@code p} is under a built roof, the mountain's own stone over it not counted. */
    public static boolean roofedPastOwnStone(ServerLevel level, BlockPos p) {
        return roofed(level, p, palette(level));
    }

    /** {@link #roofed}, passing over the blocks in {@code own} (the mountain's own stone). */
    static boolean roofed(ServerLevel level, BlockPos p, java.util.Set<net.minecraft.world.level.block.Block> own) {
        BlockPos.MutableBlockPos q = p.mutable();
        for (int d = 1; d <= ROOF_LOOK; d++) {
            q.move(0, 1, 0);
            if (!level.isLoaded(q)) {
                return false;
            }
            BlockState above = level.getBlockState(q);
            if (above.isAir() || own.contains(above.getBlock())) {
                continue;
            }
            return !(claude.settlementcore.room.Siting.mayReplace(above) || above.is(net.minecraft.tags.BlockTags.LEAVES)
                    || above.is(net.minecraft.tags.BlockTags.LOGS) || !above.getFluidState().isEmpty());
        }
        return false;
    }

    /** One cell of the mountain: not in {@code keepOut}, only where it {@link #takes}, never in a built room, and never on anyone it can't lift. */
    private static int place(ServerLevel level, BlockPos p, BlockState s, List<LivingEntity> near, List<BoundingBox> keepOut, Roof roof) {
        for (BoundingBox b : keepOut) {
            if (b.isInside(p)) {
                return REFUSED;
            }
        }
        BlockState was = level.getBlockState(p);
        if (!takes(was)) {
            return REFUSED;
        }
        if (roof != Roof.NONE && was.isAir() && (own == null || !own.isInside(p))
                && roofed(level, p, roof == Roof.UNDER_OTHERS ? palette(level) : java.util.Set.of())) {
            return REFUSED;
        }
        AABB cell = new AABB(p);
        for (LivingEntity e : near) {
            if (e.getBoundingBox().intersects(cell)) {
                AABB lifted = e.getBoundingBox().move(0, p.getY() + 1.0 - e.getY(), 0);
                if (e.isPassenger() || e.isVehicle() || !level.noCollision(e, lifted)) {
                    return OCCUPIED;                             // left open for now rather than bury or unseat anyone
                }
                e.teleportTo(e.getX(), p.getY() + 1.0, e.getZ());  // lifted on the rising rock
            }
        }
        level.setBlock(p, s, Block.UPDATE_ALL);
        return PLACED;
    }

    public void save(CompoundTag tag) {
        long[] doors = new long[done.size()];
        int[] counts = new int[done.size()];
        int k = 0;
        for (Long2IntMap.Entry e : done.long2IntEntrySet()) {
            doors[k] = e.getLongKey();
            counts[k++] = e.getIntValue();
        }
        tag.putLongArray("crag_doors", doors);
        tag.putIntArray("crag_done", counts);
        net.minecraft.nbt.ListTag wait = new net.minecraft.nbt.ListTag();   // cells someone stood in: still to fill after a reload (the bug hunt)
        for (var e : waiting.long2ObjectEntrySet()) {
            CompoundTag w = new CompoundTag();
            w.putLong("door", e.getLongKey());
            w.putIntArray("cells", e.getValue().toIntArray());
            wait.add(w);
        }
        tag.put("crag_waiting", wait);
    }

    public void load(CompoundTag tag) {
        done.clear();
        long[] doors = tag.getLongArray("crag_doors").orElse(new long[0]);
        int[] counts = tag.getIntArray("crag_done").orElse(new int[0]);
        for (int k = 0; k < Math.min(doors.length, counts.length); k++) {
            done.put(doors[k], counts[k]);
        }
        waiting.clear();
        for (net.minecraft.nbt.Tag t : tag.getListOrEmpty("crag_waiting")) {
            if (t instanceof CompoundTag w) {
                int[] cells = w.getIntArray("cells").orElse(new int[0]);
                if (cells.length > 0) {
                    waiting.put(w.getLongOr("door", 0L), new it.unimi.dsi.fastutil.ints.IntArrayList(cells));
                }
            }
        }
    }
}
