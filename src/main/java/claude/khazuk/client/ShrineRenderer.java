package claude.khazuk.client;

import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.ShrineItem;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * The Ancestor Shrine as the statue of the three Ancestors (the detail session's model, handoffs/ancestor_shrine_2026-11-04_done.md): its
 * geo and clips under {@code geckolib/.../block/}, its 512 sheet under {@code textures/entity/} (kept out of the block atlas), the runes,
 * eyes and effects glowing through GeckoLib's auto-glow layer (its glowmask), cutout as GeckoLib's default. GeoBlockRenderer turns it by
 * the block's FACING (the model faces -Z, NORTH unturned).
 * <ul>
 * <li><b>Size:</b> the user's pick, {@value #SCALE}x the model: Grungni's crest 3.75 x {@value #SCALE} = 4.3 blocks up, the answers'
 * reach 5.0 x {@value #SCALE} = 5.75.</li>
 * <li><b>Bounds:</b> ±{@value #REACH} blocks round the block's centre in x and z, {@value #HEIGHT} up (the handoff's ±2.5 and 5.25 at
 * the model's own size, scaled), so it isn't culled while any of it is on screen.</li>
 * <li><b>The devotion gauge:</b> of the band's runes {@code devotion_1} to {@code devotion_10} (left to right as you face it), the first
 * {@link ShrineBlockEntity#litRunes} are shown, the rest hidden with their {@code _swell} (so only lit runes swell as the hold prays);
 * a hidden rune is a dark groove in the stone.</li>
 * </ul>
 * The item is the same statue, small (GeckoLib's item render, {@link #itemProvider}), its runes unlit as a new Shrine's.
 */
public final class ShrineRenderer extends GeoBlockRenderer<ShrineBlockEntity, BlockEntityRenderState> {

    public static final float SCALE = 1.15f;
    public static final double REACH = 2.9, HEIGHT = 6.1;
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Khazuk.MODID, "textures/entity/ancestor_shrine_statue.png");
    /** The band's runes lit, in the render state. */
    static final DataTicket<Integer> LIT = DataTicket.create("khazuk_shrine_lit_runes", Integer.class);

    /** The statue's geo, clips and sheet, for the block and the item alike. */
    static final class ShrineModel<T extends GeoAnimatable> extends DefaultedBlockGeoModel<T> {
        ShrineModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "ancestor_shrine"));
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            return TEXTURE;
        }
    }

    public ShrineRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, new ShrineModel<>());
        withRenderLayer(AutoGlowingGeoLayer::new);
        withScale(SCALE);
    }

    @Override
    public AABB getRenderBoundingBox(ShrineBlockEntity be) {
        BlockPos p = be.getBlockPos();
        double x = p.getX() + 0.5, z = p.getZ() + 0.5;
        return new AABB(x - REACH, p.getY(), z - REACH, x + REACH, p.getY() + HEIGHT, z + REACH);
    }

    @Override
    public void addRenderData(ShrineBlockEntity be, Void related, BlockEntityRenderState state, float partialTick) {
        super.addRenderData(be, related, state, partialTick);
        state.addGeckolibData(LIT, be.litRunes());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> info, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(info, snapshots);
        gauge(snapshots, info.getOrDefaultGeckolibData(LIT, 0));
    }

    /** The first {@code lit} runes of the band shown, the rest hidden with their swell. */
    static void gauge(BoneSnapshots snapshots, int lit) {
        for (int i = 1; i <= ShrineBlockEntity.RUNES; i++) {
            boolean hide = i > lit;
            snapshots.ifPresent("devotion_" + i, b -> b.skipRender(hide).skipChildrenRender(hide));
            snapshots.ifPresent("devotion_" + i + "_swell", b -> b.skipRender(hide).skipChildrenRender(hide));
        }
    }

    // ------------------------------------------------------------------ the item

    /** The Shrine's item: the statue at the model's own size, the display numbers in models/item/ancestor_shrine.json fitting it. */
    static final class ItemRenderer extends GeoItemRenderer<ShrineItem> {
        ItemRenderer() {
            super(new ShrineModel<ShrineItem>());
            withRenderLayer(AutoGlowingGeoLayer::new);
        }

        @Override
        public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> info, BoneSnapshots snapshots) {
            super.adjustModelBonesForRender(info, snapshots);
            gauge(snapshots, 0);                                 // a Shrine not yet set down has no hold, no devotion
        }
    }

    /** ShrineItem's render provider (GeckoLib asks for it on the client only); the renderer made on first use. */
    public static GeoRenderProvider itemProvider() {
        return new GeoRenderProvider() {
            private ItemRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new ItemRenderer();
                }
                return renderer;
            }
        };
    }
}
