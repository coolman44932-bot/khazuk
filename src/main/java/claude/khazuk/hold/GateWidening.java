package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.BuildingBook;
import claude.settlementcore.work.DigWork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The Miners widen an old Hold Gate's Shrine hall (the user, 2026-11-09: "the hold gate is too small no space to do anything", then his
 * pick: "Miners widen it"). A gate raised or carved before the great hall (tools/gen_hold_gate.py: 21 wide, 17 deep, 12 high) has the old
 * small one (11 by 8, its dais filling most of it). Once a second each hold looks at its gates it hasn't marked wide:
 * <ul>
 * <li>one whose old hall's back wall still stands where the great hall's dais is ({@link #DOOR}, solid with the old hall open before it)
 * has the great hall dug out by its Miners: Settlement Core's dig of {@link HoldRooms#GATE_WIDENING} (the great hall's cells alone, in the
 * gate template's frame), free, its door in that wall. The Shrine stays where it stood: the great hall's dais is round its spot;</li>
 * <li>dug, the widening's room is folded into the gate (it is no room of its own), the gate is marked wide, and its mountain rises again
 * from the bottom, so the rock closes over the great hall's deeper back (HoldCrag: only air and the land are taken, never a built room's
 * air);</li>
 * <li>a gate built with the great hall (its back wall's cell open: the dais) is simply marked wide.</li>
 * </ul>
 * A dig the engine refuses (another dig running into it) is asked again each second until it starts.
 */
public final class GateWidening {

    /** The widening dig's door in the gate template's frame: the old hall's back wall beside the dais, at standing height (WIDEN_DOOR). */
    public static final BlockPos DOOR = new BlockPos(31, 4, 19);

    /** Gates (by door) marked wide: built with the great hall, or widened. */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet wide = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private boolean dirty;

    /** A second's look for {@code hold}: folds a dug widening into its gate, begins one for an old gate, marks a new gate wide. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        for (Building b : java.util.List.copyOf(hold.buildings())) {
            if (b.room().equals(HoldRooms.GATE_WIDENING.id())) {
                fold(level, hold, b);
            }
        }
        for (Building b : hold.buildings()) {
            if (b.door() == null || !HoldCrag.gate(b) || wide.contains(b.door().asLong()) || !level.isLoaded(b.door())) {
                continue;
            }
            PlanPlacement gate = BuildingBook.placementOf(b);
            if (gate == null) {
                continue;
            }
            BlockPos door = gate.world(DOOR);
            if (!level.isLoaded(door)) {
                continue;
            }
            if (level.getBlockState(door).getCollisionShape(level, door).isEmpty()) {
                wide.add(b.door().asLong());                     // its dais is there: built with the great hall
                dirty = true;
                continue;
            }
            if (digging(hold, door)) {
                continue;
            }
            String no = hold.works().dig(level, placement(gate), true);
            if (no == null) {
                hold.tellOwner(hold.settlementName() + ": the Miners dig the Hold Gate's Shrine hall out wide: room for the hold's god, two doors"
                        + " in each wall, the Shrine where it stands.", ChatFormatting.GOLD);
            }
        }
    }

    /** The widening's placement for {@code gate}: its frame the gate template's less the great hall's near corner, its door at {@link #DOOR}. */
    public static PlanPlacement placement(PlanPlacement gate) {
        return PlanPlacement.compute(HoldRooms.GATE_WIDENING, gate.world(DOOR), gate.facing());
    }

    /** Whether a widening is already being dug at {@code door}. */
    private static boolean digging(ShrineBlockEntity hold, BlockPos door) {
        for (DigWork d : hold.works().digs()) {
            if (d.room().id().equals(HoldRooms.GATE_WIDENING.id()) && d.box().isInside(door)) {
                return true;
            }
        }
        return false;
    }

    /** The widening dug: off the hold's rooms, its gate marked wide and its mountain risen again over the great hall. */
    private void fold(ServerLevel level, ShrineBlockEntity hold, Building widening) {
        hold.removeBuilding(level, widening);
        Building gate = gateOf(hold, widening);
        if (gate != null) {
            wide.add(gate.door().asLong());
            hold.crags().riseAgain(gate.door());
            hold.tellOwner(hold.settlementName() + ": the Hold Gate's Shrine hall is dug out wide.", ChatFormatting.GOLD);
        }
        dirty = true;
    }

    /** The gate whose widening {@code widening} is (its door at the gate's {@link #DOOR}), or null. */
    private static @Nullable Building gateOf(ShrineBlockEntity hold, Building widening) {
        for (Building b : hold.buildings()) {
            PlanPlacement g = HoldCrag.gate(b) ? BuildingBook.placementOf(b) : null;
            if (g != null && widening.door() != null && g.world(DOOR).equals(widening.door())) {
                return b;
            }
        }
        return null;
    }

    /** Whether the gate at {@code door} is marked wide (the tests). */
    public boolean wide(BlockPos door) {
        return wide.contains(door.asLong());
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void save(CompoundTag tag) {
        tag.putLongArray("gate_wide", wide.toLongArray());
    }

    public void load(CompoundTag tag) {
        wide.clear();
        for (long d : tag.getLongArray("gate_wide").orElse(new long[0])) {
            wide.add(d);
        }
    }
}
