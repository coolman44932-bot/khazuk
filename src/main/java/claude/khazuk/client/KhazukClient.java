package claude.khazuk.client;

import claude.settlementcore.client.HordeGeoModel;
import claude.settlementcore.client.HordeLod;
import claude.settlementcore.client.HordeRenderer;
import claude.khazuk.Warrior;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The Dwarfs' look: its two units drawn by the engine's crowd renderer (HordeRenderer: far ones throttled, simplified and kept),
 * on D2's Blockbench figures (block bench/bbsrc/khazuk_build.js: the candle-crowned Miner, the horned Warrior with axe and round shield);
 * the engine's caches cleared when resources reload, as every race does.
 */
@EventBusSubscriber(modid = Khazuk.MODID, value = Dist.CLIENT)
public final class KhazukClient {

    private KhazukClient() {
    }

    private static boolean outlining;
    /** The last outline's plan, where it was aimed and when: the whole site is planned again only when the aim moves, or every second. */
    private static String outlinedFor = "";
    private static long outlinedAt;

    /**
     * Holding an Ancestors' Plan and looking at the ground: the building's outline where it would go, and the ground's own refusal if any.
     * A hold's buildings are big (the gate's site is some 60 columns square), so the plan is made again only when the aim or the facing
     * moves, or a second has passed (the ground may have changed), not every tick (the review of settlementcore 270e90c).
     */
    @SubscribeEvent
    static void planOutline(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.player.getMainHandItem().getItem() instanceof claude.khazuk.hold.AncestorPlan plan
                && plan.room() != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            String key = plan.room().id() + " " + hit.getBlockPos().asLong() + " " + mc.player.getDirection() + " " + mc.player.getBlockY();
            long now = mc.level.getGameTime();
            if (!outlining || !key.equals(outlinedFor) || now - outlinedAt >= 20) {
                var pl = claude.khazuk.hold.AncestorPlan.aimed(plan.room(), hit.getBlockPos(), mc.player.getBlockY(), mc.player.getDirection());
                claude.settlementcore.client.PlanOutline.show(claude.settlementcore.room.Rooms.isRaised(plan.room().id())
                        ? claude.settlementcore.room.Siting.preview(mc.level, pl)
                        : new claude.settlementcore.room.Siting.Preview(pl, null, java.util.List.of()));   // a dug hall: its box in the rock
                outlinedFor = key;
                outlinedAt = now;
                outlining = true;
            }
        } else if (outlining) {
            claude.settlementcore.client.PlanOutline.clear();
            outlining = false;
        }
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(KRegistry.MINER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.MINER, 0.35f));
        e.registerEntityRenderer(KRegistry.WARRIOR.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.WARRIOR, 0.45f));
        e.registerEntityRenderer(KRegistry.THRONE.get(), ThroneRenderer::new);
        e.registerEntityRenderer(KRegistry.RUNESMITH.get(), RunesmithRenderer::new);
        e.registerEntityRenderer(KRegistry.RUNELORD.get(), RunelordRenderer::new);
        e.registerEntityRenderer(KRegistry.QUARRELLER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.QUARRELLER));
        e.registerEntityRenderer(KRegistry.THUNDERER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.THUNDERER));
        e.registerEntityRenderer(KRegistry.LONGBEARD.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.LONGBEARD));
        e.registerEntityRenderer(KRegistry.LONGBEARD_GREAT.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.LONGBEARD_GREAT));
        e.registerEntityRenderer(KRegistry.IRONBREAKER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.IRONBREAKER, 0.45f));
        e.registerEntityRenderer(KRegistry.HAMMERER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.HAMMERER, 0.45f));
        e.registerEntityRenderer(KRegistry.CINDERBLAST.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        e.registerEntityRenderer(KRegistry.TROLL_SLAYER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.TROLL_SLAYER, 0.45f));
        e.registerEntityRenderer(KRegistry.GIANT_SLAYER.get(), ctx -> new UnitRenderer<>(ctx, claude.khazuk.Looks.GIANT_SLAYER, 0.45f));
        e.registerEntityRenderer(KRegistry.DRAGON_SLAYER.get(), DragonSlayerRenderer::new);
        e.registerEntityRenderer(KRegistry.ANVIL_OF_DOOM.get(), AnvilRenderer::new);
        e.registerEntityRenderer(KRegistry.GRUNGNIS_HAMMER_STRIKE.get(), HammerRenderer::new);
        e.registerBlockEntityRenderer(KRegistry.SHRINE_ENTITY.get(), ShrineRenderer::new);   // the statue of the three Ancestors
    }

    @SubscribeEvent
    static void reload(AddClientReloadListenersEvent e) {
        e.addListener(Identifier.fromNamespaceAndPath(Khazuk.MODID, "horde_caches"), (ResourceManagerReloadListener) rm -> HordeLod.clearCaches());
    }

    static final class RunesmithModel extends HordeGeoModel<claude.khazuk.Runesmith> {
        RunesmithModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "runesmith"), true);
        }
    }

    static final class RunesmithRenderer extends HordeRenderer<claude.khazuk.Runesmith, LivingEntityRenderState> {
        RunesmithRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new RunesmithModel());
            withRenderLayer(com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer::new);   // his runes, the detail session's glowmask
            this.shadowRadius = 0.45f;
        }
    }

    /** The Dragon Slayer: the hold's Slayer hero, one look (a showpiece; on a fast stand-in until the detail session's is in). */
    static final class DragonSlayerModel extends HordeGeoModel<claude.khazuk.Slayer> {
        DragonSlayerModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "dragon_slayer"), true);
        }
    }

    static final class DragonSlayerRenderer extends HordeRenderer<claude.khazuk.Slayer, LivingEntityRenderState> {
        DragonSlayerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DragonSlayerModel());
            this.shadowRadius = 0.5f;
        }
    }

    static final class RunelordModel extends HordeGeoModel<claude.khazuk.Runelord> {
        RunelordModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "runelord"), true);
        }
    }

    static final class RunelordRenderer extends HordeRenderer<claude.khazuk.Runelord, LivingEntityRenderState> {
        RunelordRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new RunelordModel());
            withRenderLayer(com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer::new);
            this.shadowRadius = 0.5f;
        }
    }

    /** A Dwarf's rolled look (claude.khazuk.Looks), packed, in his render state. */
    static final com.geckolib.constant.dataticket.DataTicket<Integer> LOOK = com.geckolib.constant.dataticket.DataTicket.create("khazuk_look", Integer.class);

    /**
     * The rank-and-file on their fast models, each Dwarf in his own look (0.12, TWW3's unit variants): his palette's texture, and of each
     * group of parts only the one he rolled shown (the Warboss's way: the others' bones skipped every frame). The look comes from his UUID
     * (Looks#roll), so nothing is synced; a far Dwarf's kept model is his own, so it keeps his look.
     */
    static final class UnitModel<T extends claude.khazuk.Dawi> extends HordeGeoModel<T> {
        final claude.khazuk.Looks.Kind kind;
        private final Identifier[] textures = new Identifier[claude.khazuk.Looks.PALETTES];
        /** The bones each packed look hides, worked out once (the render thread only; a few dozen looks a kind). */
        private final java.util.Map<Integer, java.util.List<String>> hidden = new java.util.HashMap<>();

        UnitModel(claude.khazuk.Looks.Kind kind) {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, kind.id()), true);
            this.kind = kind;
            for (int p = 0; p < textures.length; p++) {
                textures[p] = claude.khazuk.Looks.texture(kind, p);
            }
        }

        @Override
        public void addAdditionalStateData(T d, @org.jspecify.annotations.Nullable Object related, com.geckolib.renderer.base.GeoRenderState state) {
            super.addAdditionalStateData(d, related, state);
            state.addGeckolibData(LOOK, claude.khazuk.Looks.pack(claude.khazuk.Looks.roll(kind, d.getUUID())));
        }

        @Override
        public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState state) {
            return textures[claude.khazuk.Looks.palette(state.getOrDefaultGeckolibData(LOOK, 0))];
        }

        @Override
        public <R extends LivingEntityRenderState & com.geckolib.renderer.base.GeoRenderState> void customAnimations(
                com.geckolib.renderer.base.RenderPassInfo<R> info, com.geckolib.renderer.base.BoneSnapshots snapshots) {
            super.customAnimations(info, snapshots);
            for (String bone : hidden.computeIfAbsent(info.getOrDefaultGeckolibData(LOOK, 0), packed -> claude.khazuk.Looks.hidden(kind, packed))) {
                snapshots.ifPresent(bone, b -> b.skipRender(true).skipChildrenRender(true));
            }
        }
    }

    static final class UnitRenderer<T extends claude.khazuk.Dawi> extends HordeRenderer<T, LivingEntityRenderState> {
        UnitRenderer(EntityRendererProvider.Context ctx, claude.khazuk.Looks.Kind kind) {
            this(ctx, kind, 0.4f);
        }

        UnitRenderer(EntityRendererProvider.Context ctx, claude.khazuk.Looks.Kind kind, float shadow) {
            super(ctx, new UnitModel<>(kind));
            this.shadowRadius = shadow;
        }
    }


}
