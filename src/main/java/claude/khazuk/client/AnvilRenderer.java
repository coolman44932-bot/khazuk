package claude.khazuk.client;

import claude.khazuk.KRegistry;
import claude.khazuk.hold.AnvilOfDoom;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Anvil of Doom (the detail session's model): its rune tiles, struck runes, sparks and lightning glow through GeckoLib's auto-glow layer. */
final class AnvilRenderer extends GeoEntityRenderer<AnvilOfDoom, LivingEntityRenderState> {

    AnvilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, KRegistry.ANVIL_OF_DOOM.get());
        withRenderLayer(AutoGlowingGeoLayer::new);
        shadowRadius = 1.5f;
    }
}
