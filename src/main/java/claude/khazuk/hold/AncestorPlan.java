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
        if (hold == null && claude.settlementcore.settlement.Founding.isFoundingHall(room)) {
            // no hold of theirs here: the Hold Gate's plan founds one (in a hold, it is set out for the Miners, as any building)
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
        String no = claude.settlementcore.settlement.Founding.of(level).begin(level, placement(room, ground, facing), p.getUUID());
        if (no == null) {
            tell.accept(Component.literal("Grungni raises the " + room.displayName() + ". When it stands, walk its passage and set the Shrine on "
                    + "the dais in the hall at its end.").withStyle(ChatFormatting.GOLD));
            return true;
        }
        tell.accept(Component.literal(no).withStyle(ChatFormatting.RED));
        return false;
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
