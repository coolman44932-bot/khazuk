package claude.khazuk;

import claude.settlementcore.defence.Muster;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * An Ironbreaker's Cinderblast bomb (0.14): thrown in an arc, it bursts where it lands (on a foe or the ground) in fire and smoke. Every foe
 * of the hold within {@value #RADIUS} blocks takes {@value #DAMAGE} (less towards the edge, never under {@value #EDGE} of it) and burns for
 * {@value #BURN_SECONDS} s; so does the foe its thrower fights, whatever it is. The hold's Dawi, its god and anyone else are never touched,
 * and no block is broken. Drawn as a fire charge in flight.
 */
public class Cinderblast extends ThrowableItemProjectile {

    public static final float DAMAGE = 9f, EDGE = 0.5f;
    public static final double RADIUS = 2.5;
    public static final int BURN_SECONDS = 4;

    /** What the last burst struck, for the tests. */
    public int struckForTests = -1;

    public Cinderblast(EntityType<? extends Cinderblast> type, Level level) {
        super(type, level);
    }

    public Cinderblast(Level level, LivingEntity thrower) {
        super(KRegistry.CINDERBLAST.get(), thrower, level, new ItemStack(Items.FIRE_CHARGE));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    /** It flies past the hold's own (and its god): a Dwarf's bomb never bursts on a Dwarf. */
    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !spared(e);
    }

    /** Whether the burst leaves {@code e} be: the hold's Dawi, its god, and any player but the one the thrower fights. */
    boolean spared(Entity e) {
        if (e instanceof Dawi) {
            return true;
        }
        Entity thrower = getOwner();
        LivingEntity fought = thrower instanceof Mob m ? m.getTarget() : null;
        if (e == fought) {
            return false;
        }
        if (e instanceof Player) {
            return true;
        }
        return !(e instanceof Mob m && Muster.foe(m));
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel sl) {
            burst(sl);
            discard();
        }
    }

    /** The burst, where it is now. How many it struck. */
    public int burst(ServerLevel level) {
        double x = getX(), y = getY(), z = getZ();
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 30, 1.0, 0.4, 1.0, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 12, 0.8, 0.4, 0.8, 0.02);
        level.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 5, 0.5, 0.2, 0.5, 0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8f, 1.3f + random.nextFloat() * 0.2f);
        Entity thrower = getOwner();
        var src = level.damageSources().thrown(this, thrower);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x, y, z, x, y, z).inflate(RADIUS + 1),
                e -> e.isAlive() && !spared(e))) {
            double d = Math.sqrt(e.distanceToSqr(x, Math.max(e.getY(), Math.min(y, e.getY() + e.getBbHeight())), z));
            if (d > RADIUS + e.getBbWidth() / 2) {
                continue;
            }
            float f = (float) Math.max(EDGE, 1 - d / (RADIUS * 2));
            if (e.hurtServer(level, src, DAMAGE * f)) {
                n++;
            }
            e.igniteForSeconds(BURN_SECONDS);
        }
        struckForTests = n;
        return n;
    }
}
