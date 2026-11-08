package claude.khazuk;

import claude.settlementcore.defence.Muster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The Slayer mech (0.14.2, the user's: "an open cockpit so you can see the crest, a boiler that runs too hot, and when it breaks he jumps
 * out and keeps fighting"): an engineer-Slayer's walking steam engine, about 3 blocks tall, its pilot's crest up out of the cockpit.
 * <ul>
 * <li><b>The boiler:</b> its heat rises {@value #HEAT_FIGHTING} a second while it fights and {@value #HEAT_BLOW} a blow, and falls
 * {@value #COOL} a second at rest. From {@value #HOT} it runs hot: a third harder ({@value #HOT_DAMAGE}) and quicker. Past the red line
 * ({@value #RED_LINE}) the boiler hurts it, {@value #SCALD} a second, venting steam.</li>
 * <li><b>Its arms:</b> heavy blows that throw foes back; each rings on the foes beside its mark ({@value #SWEEP_SHARE} of it).</li>
 * <li><b>It breaks:</b> the boiler bursts ({@value #BURST} to the foes round it, set burning) and its pilot leaps out a Giant Slayer, on
 * the hold's roll, and fights on.</li>
 * <li>Built by the hold's Engineers' Guild, one to a hold (hold/Ranks). A showpiece is commissioned (handoffs/slayer_mech_2026-11-06.md);
 * it runs on a fast stand-in till then.</li>
 * </ul>
 */
public class SlayerMech extends Dawi {

    public static final double MAX_HEALTH = 140, DAMAGE = 22, ARMOR = 16, TOUGHNESS = 6, SWEEP_REACH = 2.5, BURST_REACH = 4;
    public static final float HEAT_FIGHTING = 4, HEAT_BLOW = 6, COOL = 3, HOT = 70, RED_LINE = 100, MAX_HEAT = 120, SCALD = 2,
            SWEEP_SHARE = 0.3f, BURST = 18f;
    public static final double HOT_DAMAGE = 0.33, HOT_SPEED = 0.2;
    static final Identifier HOT_RUN = Identifier.fromNamespaceAndPath(Khazuk.MODID, "boiler_hot");

    /** Past the red line (synced: its model shudders and vents, the overheat clip). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_OVERHEATED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SlayerMech.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    static final com.geckolib.animation.RawAnimation OVERHEAT = com.geckolib.animation.RawAnimation.begin().thenLoop("animation.khazuk.overheat");
    /** The user's ask: it strides on past the red line, shuddering and venting, its pilot beating on the boiler (a 0.9 s loop at its hot pace). */
    static final com.geckolib.animation.RawAnimation OVERHEAT_WALK = com.geckolib.animation.RawAnimation.begin().thenLoop("animation.khazuk.overheat_walk");
    /** Hot, under the red line: driven at its hot pace (the detail session's hot_walk, handoffs/khazuk_filming_2026-11-08_done.md), white seams, no shudder. */
    static final com.geckolib.animation.RawAnimation HOT_WALK = com.geckolib.animation.RawAnimation.begin().thenLoop("animation.khazuk.hot_walk");
    /** Running hot ({@value #HOT} or more, synced: the hot walk's pace matches the hot run's speed, so its feet don't slide). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_HOT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SlayerMech.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    static final com.geckolib.animation.RawAnimation BURST_CLIP = com.geckolib.animation.RawAnimation.begin().thenPlay("animation.khazuk.burst");
    /** The burst clip's beats (its 20 ticks are vanilla's death): the boiler blows at 0.35 s, the pilot is out at 0.6 s. */
    public static final int BLOWS_AT = 7, PILOT_OUT_AT = 12;

    private float heat;
    private boolean hot, burst, blown, pilotOut;
    /** The pilot who leapt out, for the tests. */
    public Slayer pilotForTests;

    public SlayerMech(EntityType<? extends SlayerMech> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    public boolean isStubborn() {
        return true;                                             // an engine doesn't flinch
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_OVERHEATED, false);
        b.define(DATA_HOT, false);
    }

    /** Running hot, as the clients see it. */
    public boolean hotNow() {
        return entityData.get(DATA_HOT);
    }

    public boolean overheated() {
        return entityData.get(DATA_OVERHEATED);
    }

    /**
     * Its clips (the detail session's showpiece, handoffs/slayer_mech_2026-11-06_done.md): past the red line it shudders and vents, striding
     * (overheat_walk) or standing (overheat); running hot it strides driven (hot_walk, the hot run's pace); else it walks or idles. The blow and the burst are triggered on the attack
     * controller, after main.
     */
    @Override
    public void registerControllers(com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new com.geckolib.animation.AnimationController<SlayerMech>("main", 4, test -> {
            SlayerMech m = test.animatable();
            if (test.isMoving()) {
                return test.setAndContinue(m.overheated() ? OVERHEAT_WALK : m.hotNow() ? HOT_WALK : WALK);
            }
            return test.setAndContinue(m.overheated() ? OVERHEAT : IDLE);
        }));
        controllers.add(new com.geckolib.animation.AnimationController<SlayerMech>("attack", 2, test -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("burst", BURST_CLIP));
    }

    public float heat() {
        return heat;
    }

    public boolean runningHot() {
        return hot;
    }

    /** Tests: the boiler at {@code h}. */
    public void heatForTests(float h) {
        heat = h;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && isAlive() && !isBurrowing() && tickCount % 20 == 3) {
            boil(sl);
        }
    }

    /** The boiler's second: hotter fighting, cooler at rest; hot it runs harder; past the red line it scalds itself and vents. */
    public void boil(ServerLevel level) {
        heat = Math.max(0, Math.min(MAX_HEAT, heat + (getTarget() != null ? HEAT_FIGHTING : -COOL)));
        boolean now = heat >= HOT;
        if (now != hot) {
            hot = now;
            var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
            var spd = getAttribute(Attributes.MOVEMENT_SPEED);
            if (now) {
                if (dmg != null) {
                    dmg.addOrUpdateTransientModifier(new AttributeModifier(HOT_RUN, HOT_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                if (spd != null) {
                    spd.addOrUpdateTransientModifier(new AttributeModifier(HOT_RUN, HOT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
            } else {
                if (dmg != null) {
                    dmg.removeModifier(HOT_RUN);
                }
                if (spd != null) {
                    spd.removeModifier(HOT_RUN);
                }
            }
        }
        if (hot) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.6, getZ(), 3, 0.2, 0.1, 0.2, 0.01);
        }
        if (hotNow() != hot) {
            entityData.set(DATA_HOT, hot);
        }
        if (overheated() != heat >= RED_LINE) {
            entityData.set(DATA_OVERHEATED, heat >= RED_LINE);
        }
        if (heat >= RED_LINE) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2.2, getZ(), 8, 0.5, 0.2, 0.5, 0.05);   // venting
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 0.6f, 0.7f);
            hurtServer(level, level.damageSources().onFire(), SCALD);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            heat = Math.min(MAX_HEAT, heat + HEAT_BLOW);
            float share = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SWEEP_SHARE;
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(target.blockPosition()).inflate(SWEEP_REACH + 1),
                    m -> m != target && m.isAlive() && !(m instanceof Dawi) && Muster.foe(m) && m.distanceToSqr(target) <= SWEEP_REACH * SWEEP_REACH)) {
                m.hurtServer(level, level.damageSources().mobAttack(this), share);
            }
        }
        return hit;
    }

    /** It breaks: the burst clip plays out over vanilla's 20 ticks of death; the boiler blows at {@value #BLOWS_AT}, the pilot leaps out at {@value #PILOT_OUT_AT}. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!burst && isDeadOrDying() && level() instanceof ServerLevel) {   // only a death that happened (a mod may cancel it: the review of a20044f)
            burst = true;
            triggerAnim("attack", "burst");
        }
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level() instanceof ServerLevel sl && burst) {
            if (deathTime >= BLOWS_AT && !blown) {
                blown = true;
                blow(sl);
            }
            if (deathTime >= PILOT_OUT_AT && !pilotOut) {
                pilotOut = true;
                pilotOut(sl);
            }
        }
    }

    /** Whether its pilot has leapt out (the tests). */
    public boolean pilotIsOut() {
        return pilotOut;
    }

    private void blow(ServerLevel level) {
        // one blast at the boiler, behind and up, and a little smoke drifting up from it: the burst clip (the plates blown off, the pilot's
        // vault over the rim, the wreck sagging) shows through it (the detail session's filming, 2026-11-08: the emitter hid all of it)
        net.minecraft.world.phys.Vec3 back = net.minecraft.world.phys.Vec3.directionFromRotation(0, getYRot()).scale(-0.6);
        double bx = getX() + back.x, by = getY() + 1.8, bz = getZ() + back.z;
        level.sendParticles(ParticleTypes.EXPLOSION, bx, by, bz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, bx + back.x, by + 0.4, bz + back.z, 10, 0.3, 0.4, 0.3, 0.04);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 40, 1.2, 0.6, 1.2, 0.06);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.4f, 0.8f);
        var src = level.damageSources().explosion(this, this);
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(BURST_REACH),
                m -> m.isAlive() && !(m instanceof Dawi) && Muster.foe(m) && m.distanceToSqr(this) <= BURST_REACH * BURST_REACH)) {
            m.hurtServer(level, src, BURST);
            m.igniteForSeconds(4);
        }
    }

    private void pilotOut(ServerLevel level) {
        ShrineBlockEntity h = settlementLoading();
        Slayer pilot = KRegistry.GIANT_SLAYER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (pilot == null) {
            return;
        }
        if (h != null) {
            pilot.bindTo(h);
            h.ranks().enlist(pilot);
        }
        pilot.snapTo(getX(), getY() + 1.2, getZ(), getYRot(), 0);
        pilot.setDeltaMovement(getLookAngle().x * 0.4, 0.5, getLookAngle().z * 0.4);   // he leaps out
        pilot.takeOrders(this);
        if (getTarget() != null) {
            pilot.setTarget(getTarget());
        }
        level.addFreshEntity(pilot);
        pilotForTests = pilot;
        if (h != null) {
            h.tellOwner(h.settlementName() + ": the Slayer mech's boiler bursts! Its pilot leaps out of the wreck, axes up, and fights on.",
                    ChatFormatting.GOLD);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("heat", heat);
        out.putBoolean("burst", burst);                          // saved mid-death, it bursts and its pilot leaps out after the reload (the review of a20044f)
        out.putBoolean("blown", blown);
        out.putBoolean("pilot_out", pilotOut);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        heat = in.getFloatOr("heat", 0f);
        burst = in.getBooleanOr("burst", false);
        blown = in.getBooleanOr("blown", false);
        pilotOut = in.getBooleanOr("pilot_out", false);
    }
}
