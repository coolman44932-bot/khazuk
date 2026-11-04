package claude.khazuk.client;

import claude.khazuk.KRegistry;
import claude.khazuk.god.GrungniHammer;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Grungni's Hammer: it comes down from far above its box, so it is never culled; its gold light glows through the auto-glow layer. */
final class HammerRenderer extends GeoEntityRenderer<GrungniHammer, EntityRenderState> {

    HammerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, KRegistry.GRUNGNIS_HAMMER_STRIKE.get());
        withRenderLayer(AutoGlowingGeoLayer::new);
        shadowRadius = 0f;
    }

    @Override
    protected boolean affectedByCulling(GrungniHammer hammer) {
        return false;
    }
}
