package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hold Gates carved out of the rock (Settlement Core's Founding#beginCarved), by door, per level: decided once, when the god used the
 * plan down in the rock, and kept, so no mountain rises round one later (HoldCrag), however near the surface its top came (the review of
 * 5c1b27e: read off the rock over a 43-tall gate, a gate carved from a shallow cave read as a surface one, and its mountain filled the cave).
 */
public final class CarvedGates extends SavedData {

    static final Codec<CarvedGates> CODEC = Codec.LONG.listOf().xmap(CarvedGates::new, c -> new ArrayList<>(c.doors));
    static final SavedDataType<CarvedGates> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Khazuk.MODID, "carved_gates"),
            CarvedGates::new, CODEC);

    private final LongOpenHashSet doors;

    CarvedGates() {
        this(List.of());
    }

    CarvedGates(List<Long> doors) {
        this.doors = new LongOpenHashSet(doors);
    }

    public static CarvedGates of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(BlockPos door) {
        if (doors.add(door.asLong())) {
            setDirty();
        }
    }

    public boolean has(BlockPos door) {
        return doors.contains(door.asLong());
    }

    public void remove(BlockPos door) {
        if (doors.remove(door.asLong())) {
            setDirty();
        }
    }
}
