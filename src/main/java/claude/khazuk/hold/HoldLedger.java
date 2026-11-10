package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.ledger.LedgerMenu;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementSpawns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The hold's ledger (the user, 2026-11-10: "you know how skaven have that one ledger thing", "i need something to access everything i
 * have in my chests"): the engine's Ledger (settlementcore ledger/LedgerMenu, the Under-Empire's Hoard-Ledger), one screen onto every
 * chest, barrel and shulker box that stands in the hold's halls and roads, the Storeroom's bays first, to search, take from and put
 * into. Its god opens it two ways:
 * <ul>
 * <li><b>The Storeroom's lectern</b> (the storekeeper's ledger the hall is dug with, or any lectern he sets in a Storeroom): used, it
 * opens the hold's ledger, and stays open while he stands by it.</li>
 * <li><b>The Hold Ledger</b>, a book he carries (this item): used anywhere, it opens the ledger of the hold whose land he stands in,
 * else his nearest, and stays open while it is in his hand. The Dawi carry what he takes through the hold's roads (the engine wakes
 * the stores' chunks).</li>
 * </ul>
 * Anyone else's lectern is a lectern, and the book in another's hand is blank.
 */
public class HoldLedger extends Item {

    /** How far from the Storeroom's lectern its god may step before the ledger shuts. */
    static final double LECTERN_REACH = 8;

    public HoldLedger(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(sp, sp.blockPosition());
        if (hold == null) {
            sp.sendSystemMessage(Component.literal("The Hold Ledger's pages are blank: you have no hold to keep its stores.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        LedgerMenu.open(sp, hold, p -> p.getMainHandItem().getItem() instanceof HoldLedger || p.getOffhandItem().getItem() instanceof HoldLedger);
        return InteractionResult.SUCCESS;
    }

    /** The hold whose Storeroom the lectern at {@code at} stands in, if {@code p} is its god; else null. */
    @Nullable
    public static ShrineBlockEntity storeroomOf(ServerLevel level, ServerPlayer p, BlockPos at) {
        if (!level.getBlockState(at).is(Blocks.LECTERN)) {
            return null;
        }
        for (Settlement s : SettlementSpawns.loaded(level)) {
            if (s instanceof ShrineBlockEntity h && p.getUUID().equals(h.owner())) {
                for (Building b : h.buildings()) {
                    if ("dawi_storeroom".equals(b.room()) && b.box().isInside(at)) {
                        return h;
                    }
                }
            }
        }
        return null;
    }

    /** The Storeroom's lectern, used by the hold's god: the ledger, not a book stand. */
    @EventBusSubscriber(modid = Khazuk.MODID)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        static void lectern(PlayerInteractEvent.RightClickBlock e) {
            if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            BlockPos at = e.getPos().immutable();
            ShrineBlockEntity hold = storeroomOf(level, p, at);
            if (hold == null) {
                return;
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (e.getHand() != InteractionHand.MAIN_HAND) {
                return;                                          // one use a click: the offhand's event only keeps a book off the lectern
            }
            LedgerMenu.open(p, hold, who -> who.level() == level && level.getBlockState(at).is(Blocks.LECTERN)
                    && who.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= LECTERN_REACH * LECTERN_REACH);
        }
    }
}
