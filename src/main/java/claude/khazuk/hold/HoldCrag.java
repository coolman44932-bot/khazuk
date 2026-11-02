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
 * itself (Siting#mayReplace) are taken: a player's blocks stand. Kept per gate by its door, so a save mid-rise carries on.
 */
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

    /** Whether {@code b} is a Hold Gate. */
    public static boolean gate(Building b) {
        Rooms.Room r = Rooms.get(b.room());
        return r != null && r.function() == HoldRooms.GATE;
    }

    /** Whether the gate at {@code door} has its whole mountain. */
    public boolean risen(ServerLevel level, BlockPos door) {
        return done.getOrDefault(door.asLong(), 0) >= cells(level).size();
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
                if (rising == null && done.getOrDefault(b.door().asLong(), 0) < c.size()) {
                    rising = b;
                }
            }
        }
        done.keySet().removeIf(k -> !gates.contains(k));
        if (rising == null || c.size() == 0) {
            return false;
        }
        PlanPlacement pl = BuildingBook.placementOf(rising);
        if (pl == null) {
            done.put(rising.door().asLong(), c.size());
            return false;
        }
        long key = rising.door().asLong();
        int i = done.getOrDefault(key, 0);
        if (i == 0) {
            hold.tellOwner("Grungni raises the mountain round the " + pl.room().displayName() + "!", ChatFormatting.GOLD);
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pl.box().minX() - 16, pl.box().minY() - 2,
                pl.box().minZ() - 16, pl.box().maxX() + 17, pl.box().maxY() + 12, pl.box().maxZ() + 17));
        int end = Math.min(c.size(), i + budget);
        BlockPos last = null;
        for (; i < end; i++) {
            BlockPos p = pl.world(new BlockPos(c.xs[i], c.ys[i], c.zs[i]));
            if (!level.isLoaded(p)) {
                break;                                           // wait for its chunk
            }
            BlockState s = c.states[i];
            if (s.getBlock() instanceof FallingBlock && level.getBlockState(p.below()).isAir()) {
                s = Blocks.COBBLESTONE.defaultBlockState();       // nothing loose over a hollow
            }
            if (place(level, p, s, near) && c.foot[i]) {
                BlockState fill = s.is(Blocks.MOSS_CARPET) || s.getBlock() instanceof FallingBlock ? Blocks.STONE.defaultBlockState() : s;
                BlockPos q = p.below();
                for (int d = 0; d < FOOTING && level.isLoaded(q) && hollow(level.getBlockState(q)); d++, q = q.below()) {
                    place(level, q, fill, near);
                }
            }
            if ((i & 63) == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                        12, 0.6, 0.3, 0.6, 0.05);
                last = p;
            }
        }
        done.put(key, i);
        if (last != null) {
            level.playSound(null, last, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        }
        if (i >= c.size()) {
            hold.tellOwner("The " + pl.room().displayName() + " stands in its mountain.", ChatFormatting.GOLD);
            return false;
        }
        return true;
    }

    /** What the mountain may take: air, the land and its cover, water; never a block entity. */
    static boolean takes(BlockState s) {
        return !s.hasBlockEntity() && (s.isAir() || s.canBeReplaced() || Siting.mayReplace(s));
    }

    /** What a column's foot fills down through: air, water, plants; it stops on solid ground. */
    static boolean hollow(BlockState s) {
        return !s.hasBlockEntity() && (s.isAir() || s.canBeReplaced());
    }

    private static boolean place(ServerLevel level, BlockPos p, BlockState s, List<LivingEntity> near) {
        if (!takes(level.getBlockState(p))) {
            return false;
        }
        for (LivingEntity e : near) {
            if (!e.isSpectator() && e.getBoundingBox().intersects(new AABB(p))) {
                e.teleportTo(e.getX(), p.getY() + 1.0, e.getZ());  // lifted on the rising rock
            }
        }
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
        return true;
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
    }

    public void load(CompoundTag tag) {
        done.clear();
        long[] doors = tag.getLongArray("crag_doors").orElse(new long[0]);
        int[] counts = tag.getIntArray("crag_done").orElse(new int[0]);
        for (int k = 0; k < Math.min(doors.length, counts.length); k++) {
            done.put(doors[k], counts[k]);
        }
    }
}
