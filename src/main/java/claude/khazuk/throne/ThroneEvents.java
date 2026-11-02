package claude.khazuk.throne;

import claude.khazuk.Khazuk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** The Throne of Power's part in a fight: the rider's blow sets its bearers kicking, a wrong to the rider goes in the Book, a death settles it. */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class ThroneEvents {

    private ThroneEvents() {
    }

    /** Last, so a mod that cancels the blow (a claim, a no-PvP rule) stops the kick too. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    static void attack(AttackEntityEvent e) {
        if (!e.isCanceled() && !e.getEntity().level().isClientSide() && e.getEntity().getVehicle() instanceof ThroneOfPower t && e.getTarget() instanceof LivingEntity target) {
            t.kick(target);
        }
    }

    @SubscribeEvent
    static void hurt(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof Player p && p.getVehicle() instanceof ThroneOfPower t && e.getSource().getEntity() instanceof LivingEntity who
                && who != p) {
            t.wrong(who);
        }
    }

    @SubscribeEvent
    static void death(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (ThroneOfPower.live > 0 && dead.level() instanceof ServerLevel level) {
            for (ThroneOfPower t : level.getEntitiesOfClass(ThroneOfPower.class, new AABB(dead.blockPosition()).inflate(64))) {
                t.settle(dead);
            }
        }
    }
}
