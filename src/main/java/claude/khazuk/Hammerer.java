package claude.khazuk;

import claude.settlementcore.defence.Muster;
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * A Hammerer (0.14, with the Ironbreakers): the King's bodyguard, sworn to the hold's throne, a great hammer in both hands and no shield.
 * <ul>
 * <li><b>The great hammer:</b> the heaviest blow of the hold's foot ({@value #DAMAGE}), that throws what it strikes back, and rings out:
 * every other foe within {@value #RING_REACH} blocks of the one struck takes {@value #RING_SHARE} of it and is thrown back too.</li>
 * <li><b>Oathsworn:</b> always stubborn (immune to fear): a Hammerer never gives ground.</li>
 * <li><b>The King's Guard:</b> raised by the hold's King's Throne Room ({@value claude.khazuk.hold.Ranks#HAMMERER_EACH} of them, hold/Ranks).
 * While their god walks in the hold they keep at his side; they strike whatever strikes him and any foe of the hold he strikes; and with a Hammerer
 * * within {@value #GUARD_REACH} blocks the god takes {@value #GUARD_TAKEN} of every blow. When he is away they stand guard in the Throne
 * Room. Their god can order them as any Dwarf (follow, hold) and they do as told.</li>
 * </ul>
 * His stats are the stronger of the army book's and TWW3's (the user's rule): the best of the hold's foot with a great weapon.
 */
public class Hammerer extends Warrior {

    public static final double MAX_HEALTH = 50, DAMAGE = 20, ARMOR = 17, TOUGHNESS = 6, KNOCKBACK_RESISTANCE = 0.9, KNOCKBACK = 1.4;
    public static final double RING_REACH = 2.2, GUARD_REACH = 6;
    public static final float RING_SHARE = 0.4f, GUARD_TAKEN = 0.75f;
    /** How long after a blow on (or by) the god his guard still answers it. */
    public static final int ANSWER_TICKS = 100;

    /** Foes the last blow rang out on, for the tests. */
    public int rungForTests;
    /** Where he stands guard while the god is away: the Throne Room (saved). */
    private @Nullable BlockPos post;

    public Hammerer(EntityType<? extends Hammerer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.27)          // a step quicker than a Warrior: he keeps up with his king
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(4, new KingsGuard(this));
        targetSelector.addGoal(1, new AnswerForTheKing(this));
    }

    @Override
    protected boolean hasShield() {
        return false;
    }

    @Override
    public boolean isStubborn() {
        return true;
    }

    public @Nullable BlockPos post() {
        return post;
    }

    public void setPost(@Nullable BlockPos p) {
        post = p == null ? null : p.immutable();
    }

    /** The god, if he is in the hold now (the one the guard keeps by). */
    public @Nullable ServerPlayer kingInHold() {
        ShrineBlockEntity h = settlement();
        ServerPlayer p = h == null ? null : h.ownerPlayer();
        if (p == null || !p.isAlive() || p.level() != level() || p.isSpectator()) {
            return null;
        }
        return h.inSettlement(p.blockPosition()) || p.distanceToSqr(Vec3.atCenterOf(h.getBlockPos())) <= ShrineBlockEntity.RADIUS * ShrineBlockEntity.RADIUS
                ? p : null;
    }

    // ------------------------------------------------------------------ the great hammer

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            rungForTests = ring(level, target);
        }
        return hit;
    }

    /** The blow rings out round {@code struck}: the hold's foes beside it take a share and are thrown back. How many. */
    public int ring(ServerLevel level, Entity struck) {
        float share = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * RING_SHARE;
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(struck.blockPosition()).inflate(RING_REACH + 1),
                m -> m != struck && m.isAlive() && !(m instanceof Dawi) && Muster.foe(m) && m.distanceToSqr(struck) <= RING_REACH * RING_REACH)) {
            if (m.hurtServer(level, level.damageSources().mobAttack(this), share)) {
                Vec3 away = m.position().subtract(position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1e-4) {
                    away = away.normalize();
                    m.knockback(0.6, -away.x, -away.z);
                }
                n++;
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, struck.getX(), struck.getY(0.5), struck.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, struck.getX(), struck.getY(0.5), struck.getZ(), 10, 0.6, 0.3, 0.6, 0.2);
        level.playSound(null, struck.getX(), struck.getY(), struck.getZ(), SoundEvents.ANVIL_LAND, getSoundSource(), 0.35f, 1.4f + getRandom().nextFloat() * 0.2f);
        return n;
    }

    // ------------------------------------------------------------------ saves

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("post", BlockPos.CODEC, post);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        post = in.read("post", BlockPos.CODEC).orElse(null);
    }

    // ------------------------------------------------------------------ the King's Guard

    /** At work (no other order): at the god's side while he walks in the hold, at the Throne Room's post while he is away. */
    static final class KingsGuard extends Goal {
        private final Hammerer u;

        KingsGuard(Hammerer u) {
            this.u = u;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return u.getMode() == SettlerMob.Mode.WORK && !u.isCrew() && u.getTarget() == null && !u.isHomeward() && !u.isBurrowing()
                    && (u.kingInHold() != null || u.post() != null);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            ServerPlayer king = u.kingInHold();
            if (king != null) {
                u.getLookControl().setLookAt(king);
                if (u.distanceToSqr(king) > 9 && u.tickCount % 10 == 0) {
                    u.getNavigation().moveTo(king, 1.2);
                } else if (u.distanceToSqr(king) <= 4) {
                    u.getNavigation().stop();
                }
                return;
            }
            BlockPos p = u.post();
            if (p != null && u.distanceToSqr(Vec3.atBottomCenterOf(p)) > 2.25 && u.tickCount % 20 == 0) {
                u.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            u.getNavigation().stop();
        }
    }

    /** Whatever strikes the god, or whatever he strikes, his guard goes for (if it isn't the hold's own). */
    static final class AnswerForTheKing extends TargetGoal {
        private final Hammerer u;
        private @Nullable LivingEntity mark;

        AnswerForTheKing(Hammerer u) {
            super(u, false);
            this.u = u;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer king = u.kingInHold();
            mark = king == null ? null : markOf(u, king);
            return mark != null && canAttack(mark, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            u.setTarget(mark);
            super.start();
        }
    }

    /**
     * What the guard should answer for {@code king} now: whatever struck him lately, else a foe of the hold's he struck lately (a cow or a
     * villager he swings at is his own business: the review of 2298b06). Never the hold's own, a player or a tame pet.
     */
    static @Nullable LivingEntity markOf(Hammerer u, ServerPlayer king) {
        int now = king.tickCount;
        LivingEntity by = king.getLastHurtByMob();
        if (by != null && by.isAlive() && now - king.getLastHurtByMobTimestamp() < ANSWER_TICKS && answerable(u, by)) {
            return by;
        }
        LivingEntity at = king.getLastHurtMob();
        if (at instanceof Mob m && m.isAlive() && now - king.getLastHurtMobTimestamp() < ANSWER_TICKS && answerable(u, m) && Muster.foe(m)) {
            return at;
        }
        return null;
    }

    private static boolean answerable(Hammerer u, LivingEntity e) {
        return !(e instanceof Dawi) && !(e instanceof net.minecraft.world.entity.player.Player) && e.distanceToSqr(u) <= 24 * 24
                && !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame());
    }

    /** With a Hammerer of his hold beside him, the god takes a quarter less of every blow. /kill and the void get past it. */
    @EventBusSubscriber(modid = Khazuk.MODID)
    public static final class Guard {
        private Guard() {
        }

        @SubscribeEvent
        public static void incoming(LivingIncomingDamageEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }
            if (guarded(p)) {
                e.setAmount(e.getAmount() * GUARD_TAKEN);
            }
        }

        /** Whether a Hammerer of a hold {@code p} is god of stands within {@value #GUARD_REACH} blocks of him. */
        public static boolean guarded(ServerPlayer p) {
            return !p.level().getEntitiesOfClass(Hammerer.class, p.getBoundingBox().inflate(GUARD_REACH), h -> h.isAlive() && !h.isBurrowing()
                    && h.distanceToSqr(p) <= GUARD_REACH * GUARD_REACH && h.settlement() != null && p.getUUID().equals(h.settlement().owner())).isEmpty();
        }
    }
}
