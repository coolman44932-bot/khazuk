package claude.khazuk.hold;

import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementSpawns;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The rune anvil (D5, hold/Runes): an anvil in one of its god's Runic Forges is the Runesmith's, not a repair bench. Used by its god: with a
 * weapon or a piece of armour in hand, that kit gets its rune; with nothing, the hold's chosen rune is struck; sneaking, the next of the
 * hold's runes is chosen (its level and price told). Anyone else, or an anvil anywhere else, is a plain anvil.
 */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class RuneEvents {

    private RuneEvents() {
    }

    @SubscribeEvent
    static void anvil(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)
                || !level.getBlockState(e.getPos()).is(BlockTags.ANVIL)) {
            return;
        }
        ShrineBlockEntity hold = forgeOf(level, e);
        if (hold == null) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;                                              // one use a click: the offhand's event only keeps the anvil's own screen shut
        }
        Runes runes = hold.runes();
        if (p.isSecondaryUseActive()) {
            Runes.Kind k = runes.cycle();
            int has = runes.level(k);
            p.sendSystemMessage(Component.literal("The hold's " + k.title() + ": " + (has == 0 ? "none yet" : "I".repeat(has))
                    + (has >= Runes.MAX ? ", as many as it will hold." : "; the next costs " + Runes.cost(k, has) + " gold. Use the anvil with nothing in hand to strike it."))
                    .withStyle(ChatFormatting.AQUA));
            return;
        }
        p.sendSystemMessage(runes.begin(hold, p.getItemInHand(e.getHand())));
    }

    /** The hold whose Runic Forge holds the anvil, if its god is the one using it. */
    static ShrineBlockEntity forgeOf(ServerLevel level, PlayerInteractEvent.RightClickBlock e) {
        for (Settlement s : SettlementSpawns.loaded(level)) {
            if (s instanceof ShrineBlockEntity h && e.getEntity().getUUID().equals(h.owner())) {
                for (Building b : h.buildings()) {
                    if ("dawi_runic_forge".equals(b.room()) && b.box().isInside(e.getPos())) {
                        return h;
                    }
                }
            }
        }
        return null;
    }
}
