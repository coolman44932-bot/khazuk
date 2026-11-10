package claude.khazuk.client;

import claude.khazuk.Khazuk;
import claude.khazuk.engine.AncestorEngine;
import claude.khazuk.engine.EngineKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * The Ancestor Engine's keys, the client's side. While its god rides it, the attack and the use key are the engine's (vanilla's own
 * swing, block breaking and item use are held back, but he may still eat and drink), and a new key vents its boiler (V, in the
 * Khazuk! category). The client only tells the server which keys are down or were pressed ({@link EngineKeys}), when that changes:
 * the server decides what they do. The jump and sprint keys go by vanilla's input packet.
 */
@EventBusSubscriber(modid = Khazuk.MODID, value = Dist.CLIENT)
public final class EngineControls {

    private EngineControls() {
    }

    static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Khazuk.MODID, "khazuk"));
    /** Made when the keys are registered, not when the class loads. */
    private static KeyMapping vent;
    /** The keys as last sent, and the presses seen since. */
    private static boolean sentAttack, sentUse, attackPressed, usePressed;

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent e) {
        e.registerCategory(CATEGORY);
        vent = new KeyMapping("key.khazuk.vent", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
        e.register(vent);
    }

    /** The engine this client's player drives, or null. */
    private static AncestorEngine ridden() {
        Player p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof AncestorEngine e && e.getControllingPassenger() == p ? e : null;
    }

    /** Food or drink in a hand: the use key eats, as ever, and the claw waits. */
    private static boolean eats(Player p) {
        return p.getItemInHand(InteractionHand.MAIN_HAND).has(DataComponents.CONSUMABLE) || p.getItemInHand(InteractionHand.OFF_HAND).has(DataComponents.CONSUMABLE);
    }

    /** Vanilla's attack (the swing, and breaking the block looked at) and use are the engine's while he rides it. */
    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if (e.isPickBlock() || ridden() == null) {
            return;
        }
        if (e.isUseItem()) {
            if (eats(Minecraft.getInstance().player)) {
                return;
            }
            usePressed |= !sentUse;                              // fired again every few ticks while held: only a new press counts
        } else {
            attackPressed |= !sentAttack;
        }
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (ridden() == null) {
            sentAttack = sentUse = attackPressed = usePressed = false;   // the server lets every key go when he gets down
            if (vent != null) {
                while (vent.consumeClick()) {
                }
            }
            return;
        }
        boolean attack = mc.options.keyAttack.isDown();
        boolean use = mc.options.keyUse.isDown() && !eats(mc.player);
        boolean vented = false;
        while (vent != null && vent.consumeClick()) {
            vented = true;
        }
        if (attack != sentAttack || use != sentUse || vented || attackPressed || usePressed) {
            ClientPacketDistributor.sendToServer(new EngineKeys(attack, use, vented, attackPressed, usePressed));
        }
        sentAttack = attack;
        sentUse = use;
        attackPressed = usePressed = false;
    }
}
