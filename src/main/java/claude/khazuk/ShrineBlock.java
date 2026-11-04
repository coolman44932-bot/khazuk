package claude.khazuk;

import claude.settlementcore.food.Fare;
import claude.settlementcore.settlement.SettlementBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Dwarfs' Shrine: a settlement where it stands. Placed, it is its placer's; used with gold (an ingot or a stack of them) it is paid,
 * with food it is fed; used with an empty hand it says how it stands.
 */
public class ShrineBlock extends BaseEntityBlock {
    public static final MapCodec<ShrineBlock> CODEC = simpleCodec(ShrineBlock::new);

    public ShrineBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShrineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, KRegistry.SHRINE_ENTITY.get(),
                (l, p, s, be) -> SettlementBlockEntity.serverTick(l, p, s, be));
    }

    /** The Shrine takes root only for a god who has woken the Dawi (god/Waking); a creative god may set one anywhere. */
    @Override
    public @Nullable BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        // the server's word only: the client hasn't the god's state (the attachment isn't synced), so it doesn't guess a refusal
        if (p != null && !ctx.getLevel().isClientSide() && !mayRoot(p.isCreative(), claude.khazuk.god.Waking.god(p))) {
            p.sendSystemMessage(Component.literal("The Shrine takes root only for the Dawi's god. Wake them first: strike a gold ingot on the deep rock "
                    + "(deepslate, below y " + claude.khazuk.god.Waking.DEEP_Y + ").").withStyle(ChatFormatting.RED));
            return null;
        }
        if (p != null && !p.isCreative() && ctx.getLevel() instanceof net.minecraft.server.level.ServerLevel sl
                && !claude.settlementcore.settlement.Founding.of(sl).spot(ctx.getClickedPos(), p.getUUID())) {
            p.sendSystemMessage(Component.literal("A Shrine stands only on the dais of its Keep. Found the hold first: set the Ancestors' Plan of the "
                    + "Keep down (stone bricks, an iron and a gold ingot, paper), or use it beside an old Shrine, and when the Keep stands, set the "
                    + "Shrine on the dais inside.").withStyle(ChatFormatting.RED));
            return null;
        }
        return super.getStateForPlacement(ctx);
    }

    /** Whether a Shrine takes root for a player: a creative one always, else only the god who woke the Dawi. */
    public static boolean mayRoot(boolean creative, claude.khazuk.god.Waking.God god) {
        return creative || god.woke();
    }

    /**
     * Set down, the Shrine is its god's hold, under the hold's name (god/Waking#nameFor). The first one set down after the waking brings up
     * the Dawi who woke, once: {@value claude.khazuk.god.Waking#FIRST_MINERS} Miners and {@value claude.khazuk.god.Waking#FIRST_WARRIORS}
     * Warrior in its deeps from the start.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (by instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof ShrineBlockEntity heart) {
            var god = claude.khazuk.god.Waking.god(p);
            heart.claim(p, claude.khazuk.god.Waking.nameFor(p, pos));
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                var keep = claude.settlementcore.settlement.Founding.of(sl).claim(pos, p.getUUID());
                if (keep != null) {
                    heart.addBuilding(keep, true);              // set on its Keep's dais: the hold is born owning the Keep
                }
            }
            boolean first = god.woke() && !god.firstUp();
            claude.khazuk.god.Waking.setGod(p, new claude.khazuk.god.Waking.God(god.woke(), god.holdName(), god.holds() + 1, god.firstUp() || first));
            if (first) {
                heart.addToReserve(claude.khazuk.god.Waking.FIRST_MINERS, claude.khazuk.god.Waking.FIRST_WARRIORS);
                heart.tellOwner(heart.settlementName() + ": the first Dawi come up out of the stone to their Shrine. Feed the hold, and more follow.",
                        ChatFormatting.GOLD);
            } else {
                heart.tellOwner(heart.settlementName() + ": the Ancestors are watching. Feed the hold, and the Dawi come up from the deeps.", ChatFormatting.GOLD);
            }
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ShrineBlockEntity heart) || stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (stack.is(heart.currencyItem())) {
            heart.addCurrency(stack.getCount(), "the Shrine's owner");
            stack.setCount(0);
            return InteractionResult.SUCCESS;
        }
        int malt = stack.is(net.minecraft.world.item.Items.WHEAT) ? claude.khazuk.hold.Brewhouse.MALT_PER_WHEAT
                : stack.is(net.minecraft.world.item.Items.HAY_BLOCK) ? claude.khazuk.hold.Brewhouse.MALT_PER_HAY : 0;
        if (malt > 0) {                                          // D3: wheat for the brewery
            stack.shrink(heart.brew().takeMalt(malt, stack.getCount()));
            player.sendSystemMessage(Component.literal(heart.settlementName() + ": " + heart.brew().malt() + " malt for the brewery"
                    + (heart.has(claude.khazuk.hold.HoldRooms.BREWERY) ? "." : ". Dig a Brewery (the Ancestors' Plan) to brew it into ale."))
                    .withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        int each = Fare.nutrition(stack);
        if (each > 0) {
            heart.addFood(heart.foodValue(each) * stack.getCount(), Fare.grade(stack), "the Shrine's owner");
            stack.setCount(0);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player.isSecondaryUseActive() && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof ShrineBlockEntity heart && sp.getUUID().equals(heart.owner())) {
            claude.khazuk.god.Waking.askRename(sp, heart);      // sneak-used by its god: name the hold anew
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ShrineBlockEntity heart) {
            player.sendSystemMessage(Component.literal(heart.settlementName() + ": " + heart.peopleCount() + " up, " + heart.reserveWorkers()
                    + " Miners and " + heart.reserveSoldiers() + " Warriors in the deeps, food " + heart.food() + ", gold " + heart.currency()
                    + ", ale " + heart.brew().ale() + " (malt " + heart.brew().malt() + ")")
                    .withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }
}
