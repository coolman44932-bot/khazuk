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
 * on the test race's Blockbench figures (block bench/bbsrc/testrace_build.js) until D2 models them; the engine's caches cleared when resources reload, as every race does.
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
            String key = plan.room().id() + " " + hit.getBlockPos().asLong() + " " + mc.player.getDirection();
            long now = mc.level.getGameTime();
            if (!outlining || !key.equals(outlinedFor) || now - outlinedAt >= 20) {
                var pl = claude.khazuk.hold.AncestorPlan.placement(plan.room(), hit.getBlockPos(), mc.player.getDirection());
                claude.settlementcore.client.PlanOutline.show(claude.settlementcore.room.Siting.preview(mc.level, pl));
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
        e.registerEntityRenderer(KRegistry.MINER.get(), WorkerRenderer::new);
        e.registerEntityRenderer(KRegistry.WARRIOR.get(), GuardRenderer::new);
        e.registerEntityRenderer(KRegistry.THRONE.get(), ThroneRenderer::new);
    }

    @SubscribeEvent
    static void reload(AddClientReloadListenersEvent e) {
        e.addListener(Identifier.fromNamespaceAndPath(Khazuk.MODID, "horde_caches"), (ResourceManagerReloadListener) rm -> HordeLod.clearCaches());
    }

    static final class WorkerModel extends HordeGeoModel<Miner> {
        WorkerModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "miner"), true);
        }
    }

    static final class GuardModel extends HordeGeoModel<Warrior> {
        GuardModel() {
            super(Identifier.fromNamespaceAndPath(Khazuk.MODID, "dwarf_warrior"), true);
        }
    }

    static final class WorkerRenderer extends HordeRenderer<Miner, LivingEntityRenderState> {
        WorkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WorkerModel());
            this.shadowRadius = 0.35f;
        }
    }

    static final class GuardRenderer extends HordeRenderer<Warrior, LivingEntityRenderState> {
        GuardRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new GuardModel());
            this.shadowRadius = 0.45f;
        }
    }
}
