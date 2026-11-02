package claude.khazuk.client;

import claude.khazuk.KRegistry;
import claude.khazuk.throne.ThroneOfPower;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Throne of Power (the detail session's model): its runes, the Book's ink and gold glow through GeckoLib's auto-glow layer. */
final class ThroneRenderer extends GeoEntityRenderer<ThroneOfPower, LivingEntityRenderState> {

    ThroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, KRegistry.THRONE.get());
        withRenderLayer(AutoGlowingGeoLayer::new);
        shadowRadius = 1.4f;
    }
}
