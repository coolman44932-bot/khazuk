package claude.khazuk.engine;

import claude.khazuk.Khazuk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/** The Ancestor Engine's part outside itself: a god its burst seat threw clear lands unhurt. */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class EngineEvents {

    private EngineEvents() {
    }

    @SubscribeEvent
    static void fall(LivingFallEvent e) {
        if (!e.getEntity().level().isClientSide() && AncestorEngine.landsUnhurt(e.getEntity())) {
            e.setCanceled(true);
        }
    }
}
