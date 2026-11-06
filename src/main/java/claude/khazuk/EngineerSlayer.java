package claude.khazuk;

import claude.settlementcore.defence.Muster;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An Engineer Slayer (0.14.2, the user's: "Engineer Slayers with guns that sometimes blow up (Malakai-style)"): a Slayer who swore his
 * oath at the workbench, an axe in one hand and a handgun of his own making in the other, goggles up on his crest.
 * <ul>
 * <li><b>The gun:</b> a scatter shot of {@value #SHOT} out to {@value #RANGE} blocks every {@value #RELOAD} ticks; every other foe within
 * {@value #SCATTER_REACH} blocks of his mark takes {@value #SCATTER_SHARE} of it. With the hold's ale, runes, grudges and the Fury in it
 * (Marksman#shotOn), and a Slayer's share on large foes and his death-wish (Slayer#slaying).</li>
 * <li><b>It blows up:</b> one shot in {@value #OVERLOAD_IN} overloads and bursts at the mark: {@value #BURST} to every foe within
 * {@value #BURST_REACH} blocks, set burning; the backlash singes him for {@value #BACKLASH} (never to death: he has a better death in
 * mind), and a Slayer hurt hits the harder.</li>
 * <li>A Slayer: unbreakable, unarmoured, quick; close in, his axe, with a Slayer's share on a large foe.</li>
 * <li>Raised by the hold's Engineers' Guild (hold/Ranks).</li>
 * </ul>
 */
public class EngineerSlayer extends Marksman {

    public static final double MAX_HEALTH = 44, DAMAGE = 15, RANGE = 18, SCATTER_REACH = 2.5, BURST_REACH = 3.5;
    public static final float SHOT = 11f, SCATTER_SHARE = 0.4f, BURST = 16f, BACKLASH = 4f;
    public static final int RELOAD = 50, OVERLOAD_IN = 8, BURN_SECONDS = 4;
    static final Identifier SLAYING = Identifier.fromNamespaceAndPath(Khazuk.MODID, "engineer_slayer");

    /** Shots that overloaded, and foes the scatter caught, for the tests. */
    public int overloadsForTests, scatteredForTests;
    /** Tests: every shot overloads (0: by chance). */
    public int forceOverload;

    public EngineerSlayer(EntityType<? extends EngineerSlayer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FOLLOW_RANGE, RANGE + 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    public double range() {
        return RANGE;
    }

    @Override
    public int reload() {
        return RELOAD;
    }

    @Override
    public float shotDamage() {
        return SHOT;
    }

    /** A Slayer's shot: his share on a large foe and his death-wish in it too. */
    @Override
    public float shotDamageOn(LivingEntity mark) {
        return (float) (SHOT * Slayer.slaying(this, mark, Slayer.TROLL_LARGE));
    }

    @Override
    public boolean isStubborn() {
        return true;
    }

    @Override
    public void shoot(ServerLevel level, LivingEntity mark) {
        Vec3 at = getEyePosition().add(getLookAngle().scale(0.9));
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.2, 0.1, 0.2, 0.02);
        float full = shotOn(mark);
        super.shoot(level, mark);
        scatter(level, mark, full * SCATTER_SHARE);
        if (forceOverload > 0 || getRandom().nextInt(OVERLOAD_IN) == 0) {
            overload(level, mark);
        }
    }

    /** The scatter: the hold's foes beside his mark take a share. How many. */
    int scatter(ServerLevel level, LivingEntity mark, float share) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(mark.blockPosition()).inflate(SCATTER_REACH + 1),
                m -> m != mark && m.isAlive() && !(m instanceof Dawi) && Muster.foe(m) && m.distanceToSqr(mark) <= SCATTER_REACH * SCATTER_REACH)) {
            if (m.hurtServer(level, level.damageSources().mobProjectile(this, this), share)) {
                n++;
            }
        }
        scatteredForTests += n;
        return n;
    }

    /** The gun overloads: a burst at the mark that burns the hold's foes round it, and singes him (never to death). How many it struck. */
    public int overload(ServerLevel level, LivingEntity mark) {
        overloadsForTests++;
        Vec3 c = mark.position();
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 2, 0.4, 0.3, 0.4, 0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 30, 1.2, 0.5, 1.2, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 10, 0.3, 0.3, 0.3, 0.03);   // his own gun smoking
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.0f, 1.1f);
        var src = level.damageSources().explosion(this, this);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mark.blockPosition()).inflate(BURST_REACH + 1),
                e -> e.isAlive() && e.distanceToSqr(c) <= BURST_REACH * BURST_REACH && (e == mark || e instanceof Mob m && !(m instanceof Dawi) && Muster.foe(m)))) {
            e.invulnerableTime = 0;                              // the burst is its own blow, not lost to the shot's hurt cooldown (the review of b04b727)
            if (e.hurtServer(level, src, BURST)) {
                n++;
            }
            e.igniteForSeconds(BURN_SECONDS);
        }
        float singe = Math.min(BACKLASH, getHealth() - 1);   // the backlash: a Slayer hurt is a Slayer the harder
        if (singe > 0) {
            setHealth(getHealth() - singe);
        }
        return n;
    }

    /** Close in, his axe: a Slayer's share on a large foe, and the death-wish. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        var a = getAttribute(Attributes.ATTACK_DAMAGE);
        double m = Slayer.slaying(this, target, Slayer.TROLL_LARGE);
        if (a == null || m <= 1.0001) {
            return super.doHurtTarget(level, target);
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(SLAYING, m - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        try {
            return super.doHurtTarget(level, target);
        } finally {
            a.removeModifier(SLAYING);
        }
    }

    @Override
    protected ParticleOptions trail() {
        return ParticleTypes.SMOKE;
    }

    @Override
    protected SoundEvent report() {
        return SoundEvents.GENERIC_EXPLODE.value();
    }
}
