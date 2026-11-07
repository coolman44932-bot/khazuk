package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Ancestors' Plan: the god's own plan of one of the hold's buildings, drawn in gold on a stone tablet. Never used up: the gold is the
 * price. Held, it outlines where the building would go (KhazukClient).
 * <ul>
 * <li>The Hold Gate, the hold's founding (since 0.13.0): used on the ground where no hold of yours is, the god raises it there, its door
 * toward you and running away from you, and the Shrine goes on the dais at its passage's end ({@link #found}). In a hold of yours (one
 * founded before, on a Keep), it sets the gate out and the Miners raise it (Settlement Core's surface builder).</li>
 * <li>A dug hall (the Great Hall, the barracks, the mine): used on a wall you stand before (the gate's passage, a hall), its door is cut
 * there at your feet's level and the hall runs on into the rock away from you; the Miners dig it out (the engine's dig side).</li>
 * </ul>
 */
public class AncestorPlan extends Item {

    private final String roomId;

    public AncestorPlan(Properties props, String roomId) {
        super(props);
        this.roomId = roomId;
    }

    public Rooms.Room room() {
        return HoldRooms.room(roomId);
    }

    /** Where the building would go if this plan were used on {@code ground} by someone facing {@code facing}: its door toward them. */
    public static PlanPlacement placement(Rooms.Room room, BlockPos ground, Direction facing) {
        return PlanPlacement.onGround(room, ground, facing);
    }

    /**
     * Where the building would go, aimed at {@code hit} by someone standing with their feet at {@code feetY} and facing {@code facing}: a
     * raised one on the ground hit ({@link #placement(Rooms.Room, BlockPos, Direction)}); a dug one with its door in the wall hit, at the
     * feet's level (so the hall's floor is the floor you stand on), running away from you.
     */
    public static PlanPlacement aimed(Rooms.Room room, BlockPos hit, int feetY, Direction facing) {
        if (Rooms.isRaised(room.id())) {
            return placement(room, hit, facing);
        }
        return PlanPlacement.compute(room, new BlockPos(hit.getX(), feetY, hit.getZ()), facing);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || !(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        Rooms.Room room = room();
        ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(p, ctx.getClickedPos());
        if (claude.settlementcore.settlement.Founding.isFoundingHall(room) && (hold == null || !hold.inSettlement(ctx.getClickedPos()))) {
            // not in a hold of theirs (ownedAt is their nearest Shrine, however far: the review of 31991f2): the Hold Gate's plan founds a new
            // hold here. In a hold's land it is set out for the Miners, as any building.
            return found(level, p, room, ctx.getClickedPos(), p.getDirection(), p::sendSystemMessage) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (room == null || hold == null) {
            p.sendSystemMessage(Component.literal("No hold of yours is here. Found one first: set the Ancestors' Plan of the Hold Gate down where the "
                    + "hold should be, and when the gate stands, set the Shrine on the dais at the end of its passage.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!Rooms.isRaised(room.id())) {
            return dig(hold, level, aimed(room, ctx.getClickedPos(), p.getBlockY(), p.getDirection()), p.isCreative(), p::sendSystemMessage)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return setOut(hold, level, room, ctx.getClickedPos(), p.getDirection(), p.isCreative(), p::sendSystemMessage)
                ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * A founding hall's plan (the Hold Gate's) used on {@code ground} by its god {@code p}, facing {@code facing}, where no hold of theirs is:
     * a new hold's founding there. The god raises the gate (the engine's Founding), and when it stands the Shrine goes on the dais at the end
     * of its passage. Only for the Dawi's god (or a creative one). True if begun.
     */
    public static boolean found(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer p, Rooms.Room room, BlockPos ground,
                                Direction facing, java.util.function.Consumer<Component> tell) {
        if (!p.isCreative() && !claude.khazuk.god.Waking.god(p).woke()) {
            tell.accept(Component.literal("Only the Dawi's god founds a hold. Wake them first: strike a gold ingot on the deep rock.").withStyle(ChatFormatting.RED));
            return false;
        }
        PlanPlacement pl = placement(room, ground, facing);
        if (underRock(level, p.blockPosition())) {               // down in the rock: Grungni carves the gate out of it
            String no = claude.settlementcore.settlement.Founding.of(level).beginCarved(level, pl, p.getUUID());
            if (no != null) {
                tell.accept(Component.literal(no).withStyle(ChatFormatting.RED));
                return false;
            }
            CarvedGates.of(level).add(pl.door());               // decided now and kept: no mountain rises round it (HoldCrag)
            int broke = clearOutOfTheWay(level, pl.box());
            tell.accept(Component.literal("Grungni carves the " + room.displayName() + " out of the rock" + (broke > 0 ? ", breaking what was in its way" : "")
                    + ". When it is cut, walk its passage and set the Shrine on the dais in the hall at its end: the rock round it is its mountain.")
                    .withStyle(ChatFormatting.GOLD));
            return true;
        }
        GrungniClears cleared = GrungniClears.clear(level, pl);  // what stands on the ground broken, water dammed, hollows filled
        if (cleared.refusal() != null) {
            tell.accept(Component.literal(cleared.refusal().why()).withStyle(ChatFormatting.RED));
            return false;
        }
        String no = claude.settlementcore.settlement.Founding.of(level).begin(level, pl, p.getUUID());
        if (no != null) {
            cleared.putBack();
        } else {
            cleared.keep();
            CarvedGates.of(level).remove(pl.door());             // a gate once carved here and gone: this one is raised, its mountain with it
        }                                                        // (only once begun: a refused click leaves a carved gate's mark, the review of 5adaf65)
        if (no == null) {
            tell.accept(Component.literal("Grungni raises the " + room.displayName() + ". When it stands, walk its passage and set the Shrine on "
                    + "the dais in the hall at its end.").withStyle(ChatFormatting.GOLD));
            return true;
        }
        tell.accept(Component.literal(no).withStyle(ChatFormatting.RED));
        return false;
    }

    /** Rock (or anything solid) more than this far over the god's head: the plan was used down in a cave or a mine. */
    public static final int ROOF = 4;

    /**
     * Whether the god stands down in the rock (a cave, a mine): more than {@link #ROOF} blocks of the land itself (rock, earth: Settlement
     * Core's Siting#ground) over his head. There the Hold Gate is carved out of the rock (Settlement Core's carved founding), not raised under
     * the sky (the user, 2026-11-06: "dwarves cant build underground?"; the engine sites a raised one from the land's surface down, so down
     * there it read the lakes far overhead). Measured over where the god stands, not the block aimed at (the review of 7f8ce4e); a tree's
     * logs, a roof or a lake over him are not the rock (the review of 5c1b27e).
     */
    public static boolean underRock(Level level, BlockPos feet) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int rock = 0;
        for (int y = feet.getY() + 2; y <= top && rock <= ROOF; y++) {
            if (claude.settlementcore.room.Siting.ground(level.getBlockState(at.set(feet.getX(), y, feet.getZ())))) {
                rock++;
            }
        }
        return rock > ROOF;
    }

    /**
     * Before the gate is carved: what a player put in its box (planks, rails, torches, bricks: anything not the land itself, its water or its
     * air) is broken and drops where it stood, as Grungni does on the surface; the engine's carve would cut it away to nothing. Block
     * entities never reach here (the engine refuses a carve over one). How many were broken.
     */
    static int clearOutOfTheWay(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        int n = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    net.minecraft.world.level.block.state.BlockState s = level.getBlockState(at.set(x, y, z));
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, at) < 0
                            || claude.settlementcore.room.DigJob.natural(s) || claude.settlementcore.room.Siting.ground(s)
                            || claude.settlementcore.room.Siting.cover(s) || s.is(net.minecraft.tags.BlockTags.LOGS)
                            || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.ICE)
                            || s.is(net.minecraft.tags.BlockTags.TERRACOTTA) || s.is(net.neoforged.neoforge.common.Tags.Blocks.SANDSTONE_BLOCKS)
                            || s.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)) {
                        continue;                                // the land, what grows on it, bedrock: the carve's own to cut (the review of a9ef4de)
                    }
                    level.destroyBlock(at.immutable(), true);
                    n++;
                }
            }
        }
        return n;
    }

    /** Begins digging the hall at {@code pl} for {@code hold}: refused (and told why) where the engine won't dig it; else begun, paid. True if begun. */
    public static boolean dig(ShrineBlockEntity hold, ServerLevel level, PlanPlacement pl, boolean free, java.util.function.Consumer<Component> tell) {
        if (pl.box().inflatedBy(1).isInside(hold.getBlockPos())) {
            tell.accept(Component.literal("The " + pl.room().displayName() + " would be dug round the Shrine: aim it further off.").withStyle(ChatFormatting.RED));
            return false;
        }
        String no = hold.works().dig(level, pl, free);
        if (no != null) {
            tell.accept(Component.literal(no).withStyle(ChatFormatting.RED));
            return false;
        }
        tell.accept(Component.literal("The Miners will dig out the " + pl.room().displayName() + (free ? "." : " for " + pl.room().cost() + " gold."))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Sets {@code room} out for {@code hold} on {@code ground}, running toward {@code facing}: refused (and told why) where the engine won't
     *  raise it; else begun, its gold taken by the engine. {@code free}: a creative god pays nothing. True if begun. */
    public static boolean setOut(ShrineBlockEntity hold, ServerLevel level, Rooms.Room room, BlockPos ground, Direction facing, boolean free,
                                 java.util.function.Consumer<Component> tell) {
        PlanPlacement pl = placement(room, ground, facing);
        if (room.function() == HoldRooms.GATE && HoldCrag.buries(level, pl, hold.getBlockPos())) {
            tell.accept(Component.literal("The mountain round the " + room.displayName() + " would bury the Shrine: set it out further away, "
                    + "or facing away from it.").withStyle(ChatFormatting.RED));
            return false;
        }
        String no = hold.works().raise(level, pl, free);
        if (no != null) {
            tell.accept(Component.literal(no).withStyle(ChatFormatting.RED));
            return false;
        }
        tell.accept(Component.literal("The Miners will raise the " + room.displayName() + (free ? "." : " for " + room.cost() + " gold."))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }
}
