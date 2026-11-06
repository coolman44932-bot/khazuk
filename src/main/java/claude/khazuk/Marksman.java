package claude.khazuk;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * D6: the hold's missile troops (Quarreller, Thunderer), the hold's own guns for its walls. A Marksman shoots what his hold's foes are from
 * up to {@link #range()} blocks: a shot ({@link #shoot}) with its trail of particles, the blow his own; close in (within {@value #CLOSE}
 * blocks) he fights with his hand weapon as any Dwarf. He needs to see his mark, unless his hold is walled and the mark is outside its walls:
 * then he shoots over the battlements, as the hold's walls are built for (the engine's walls shut their gates at foes and its defenders man
 * them; without guns the defenders could only wait). Raised by the hold's Barracks (hold/Ranks).
 */
public abstract class Marksman extends Dawi {

    /** Within this many blocks he fights hand to hand. */
    public static final double CLOSE = 2.5;

    /** Shots that hit, for the tests. */
    public int shotsForTests;

    protected Marksman(EntityType<? extends Marksman> type, Level level) {
        super(type, level);
    }

    /** How far he shoots. */
    public abstract double range();

    /** Ticks between his shots. */
    public abstract int reload();

    /** What one shot does. */
    public abstract float shotDamage();

    /** What one shot does to {@code mark} (a Thunderer's bites through armour). */
    public float shotDamageOn(LivingEntity mark) {
        return shotDamage();
    }

    /**
     * What a shot at {@code mark} does now: his shot, with the hold's ale and its Runes of Cleaving in it as in any Dwarf's blow, Grimnir's
     * Fury, and the grudge it bears (the bug hunt of 2d0b04c: shots had only the Fury).
     */
    public float shotOn(LivingEntity mark) {
        float shot = (float) (shotDamageOn(mark) + (aleUp() ? ALE_DAMAGE : 0) + cleaveRunes() * claude.khazuk.hold.Runes.CLEAVE_DAMAGE);
        shot *= furyUp() ? claude.khazuk.god.AncestorPowers.FURY_SHOT : 1f;   // D7: Grimnir's Fury
        if (bearsGrudgeAgainst(mark)) {
            shot *= 1 + claude.khazuk.hold.Grudges.BEARER_PERCENT / 100f;
        }
        return shot;
    }

    /** The trail along a shot, and the sound of it. */
    protected abstract ParticleOptions trail();

    protected abstract SoundEvent report();

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new Shoot(this));
        // marks found as he shoots them: unseen ones too, when his walled hold's walls stand between (the review of 88e429a: Dawi's own target
        // goal must see its mark, so a foe behind the wall was never his)
        targetSelector.addGoal(2, new Mark(this));
    }

    /**
     * Marks as he shoots them: any foe {@link #canShoot} allows. Vanilla's targeting checks the line of sight itself whatever its goal says (the
     * re-review of 3948034: mustSee only governs giving up), so it's told not to; canShoot asks for the line of sight unless the mark is
     * outside his walled hold's walls.
     */
    static final class Mark extends net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<net.minecraft.world.entity.Mob> {
        Mark(Marksman m) {
            super(m, net.minecraft.world.entity.Mob.class, 10, false, false,
                    (t, level) -> t instanceof net.minecraft.world.entity.Mob mob && claude.settlementcore.defence.Muster.foe(mob) && m.canShoot(mob));
            targetConditions.ignoreLineOfSight();
        }
    }

    /** Whether he can put a shot on {@code mark}: in range, and seen, or outside his walled hold's walls (over the battlements). */
    public boolean canShoot(LivingEntity mark) {
        double d2 = distanceToSqr(mark);
        if (d2 > range() * range() || d2 < CLOSE * CLOSE) {
            return false;
        }
        if (getSensing().hasLineOfSight(mark)) {
            return true;
        }
        ShrineBlockEntity h = settlement();
        return h != null && h.walled() && h.insideWalls(blockPosition()) && !h.insideWalls(mark.blockPosition());
    }

    /** A shot at {@code mark}: it hits (Dwarf engineering), its trail drawn from his weapon to the mark, the blow his. */
    public void shoot(ServerLevel level, LivingEntity mark) {
        Vec3 from = getEyePosition().add(getLookAngle().scale(0.6)), to = mark.getBoundingBox().getCenter();
        Vec3 step = to.subtract(from);
        int n = Math.max(4, (int) (step.length() * 2));
        for (int i = 1; i <= n; i++) {
            Vec3 p = from.add(step.scale(i / (double) n));
            level.sendParticles(trail(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, getX(), getY(), getZ(), report(), getSoundSource(), 1.0f, 0.9f + getRandom().nextFloat() * 0.2f);
        triggerAnim("attack", "attack");
        foeShotAt();                                             // the engine's word for a ranged blow: he is getting at his foe
        float shot = shotOn(mark);
        if (mark.hurtServer(level, level.damageSources().mobProjectile(this, this), shot)) {
            shotsForTests++;
            setLastHurtMob(mark);
        }
    }

    /** Shooting: when his mark is one he can shoot, he stands, faces it and shoots every {@link #reload()} ticks; closer, the melee goal takes him. */
    static final class Shoot extends Goal {
        private final Marksman m;
        private int cooldown;

        Shoot(Marksman m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive() && !m.isBurrowing() && m.canShoot(t);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            m.getNavigation().stop();
            cooldown = Math.max(cooldown, 10);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) {
                return;
            }
            m.getLookControl().setLookAt(t, 30f, 30f);
            m.getNavigation().stop();
            if (--cooldown <= 0 && m.level() instanceof ServerLevel sl) {
                m.shoot(sl, t);
                cooldown = m.furyUp() ? Math.round(m.reload() * claude.khazuk.god.AncestorPowers.FURY_RELOAD) : m.reload();
            }
        }
    }
}
