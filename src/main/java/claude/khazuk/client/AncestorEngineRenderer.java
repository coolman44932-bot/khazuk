package claude.khazuk.client;

import claude.khazuk.KRegistry;
import claude.khazuk.engine.AncestorEngine;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The Ancestor Engine (the detail session's model, handoffs/ancestor_engine_2026-11-10_done.md): the mask's eyes, the lamps, the firebox,
 * the boiler's seams and the runes glow through GeckoLib's auto-glow layer. It has no head bone, so nothing looks at anyone.
 */
final class AncestorEngineRenderer extends GeoEntityRenderer<AncestorEngine, LivingEntityRenderState> {

    AncestorEngineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, KRegistry.ANCESTOR_ENGINE.get());
        withRenderLayer(AutoGlowingGeoLayer::new);
        shadowRadius = 1.1f;
    }

    /** The wreck sags where it stands as its burst plays: vanilla's death tip-over would roll it onto its side. */
    @Override
    protected float getDeathMaxRotation(GeoRenderState state) {
        return 0f;
    }
}
