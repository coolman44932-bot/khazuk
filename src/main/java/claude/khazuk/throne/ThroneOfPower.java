package claude.khazuk.throne;

import claude.khazuk.KRegistry;
import claude.khazuk.ShrineBlockEntity;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Thorgrim's Throne of Power, the god's own mount (the user: "his throne would be first, but as a rideable for me"; the detail session's
 * model, handoffs/throne_of_power_2026-11-01_done.md). Four Throne Bearers carry it; its owner sits in Thorgrim's seat and steers it as a
 * horse is steered, walking or, sprinting, at a run. Left alone it kneels and rests; sneak-used by its owner it folds back into its item.
 * <ul>
 * <li><b>The bearers kick what the rider hits</b> ({@value #KICK_DAMAGE} more, thrown back), the front one on the target's side, landing on
 * the clip's tick 5; twice as hard on the Book's grudge.</li>
 * <li><b>The Book of Grudges:</b> whoever hurts the rider is written in it; when they die, the grudge is struck out, and the rider is
 * healed and strengthened ({@link #settle}).</li>
 * <li><b>The runes</b> glow while the rider has a rune's blessing: Resistance in his own hold's land (the throne gives it there), Strength
 * from a settled grudge, or either from anything else.</li>
 * </ul>
 * It never dies for good: broken, it drops its item. Neither it nor its rider takes fall damage.
 */
public class ThroneOfPower extends PathfinderMob implements GeoEntity {

    public static final float KICK_DAMAGE = 8f;
    /** Ground speed at a walk and a run, as getRiddenSpeed's attribute value (about 3 and 5.5 blocks a second). */
    static final float WALK_SPEED = 0.07f, RUN_SPEED = 0.13f;
    /** The clips' own ground speeds at 1x (their planted boots), blocks a second. */
    static final double WALK_CLIP = 1.125, RUN_CLIP = 2.25;
    static final int KICK_TICKS = 12, KICK_IMPACT = 5;

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.throne_of_power.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.throne_of_power.walk");
    static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.throne_of_power.run");
    static final RawAnimation SET_DOWN = RawAnimation.begin().thenPlay("animation.throne_of_power.kneel").thenLoop("animation.throne_of_power.rest");
    static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.throne_of_power.rise").thenLoop("animation.throne_of_power.idle");
    static final RawAnimation KICK_L = RawAnimation.begin().thenPlay("animation.throne_of_power.kick_l");
    static final RawAnimation KICK_R = RawAnimation.begin().thenPlay("animation.throne_of_power.kick_r");
    static final RawAnimation WRITTEN = RawAnimation.begin().thenPlay("animation.throne_of_power.grudge_written");
    static final RawAnimation SETTLED = RawAnimation.begin().thenPlay("animation.throne_of_power.grudge_settled");
    static final RawAnimation RUNES = RawAnimation.begin().thenLoop("animation.throne_of_power.runes_lit");

    private static final EntityDataAccessor<Boolean> RUNES_LIT = SynchedEntityData.defineId(ThroneOfPower.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID owner;
    /** Who the Book holds a grudge against. */
    @Nullable private UUID grudge;
    @Nullable private String grudgeName;
    /** The kick under way: its target, and ticks since it began (-1: none). */
    @Nullable private LivingEntity kickAt;
    private int kickTick = -1;
    /** Client: ticks the rise still has to play before the main clip may walk. */
    private int rising;
    private boolean wasRidden;

    public ThroneOfPower(EntityType<? extends ThroneOfPower> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RUNES_LIT, false);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public UUID grudge() {
        return grudge;
    }

    public boolean runesLit() {
        return entityData.get(RUNES_LIT);
    }

    // ------------------------------------------------------------------ riding

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (owner != null && !owner.equals(player.getUUID())) {
            return InteractionResult.PASS;                       // only its god sits in it
        }
        if (isVehicle()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            ItemStack item = new ItemStack(KRegistry.THRONE_ITEM.get());
            if (!player.getInventory().add(item)) {
                spawnAtLocation((ServerLevel) level(), item);
            }
            discard();
            return InteractionResult.SUCCESS;
        }
        if (owner == null) {
            owner = player.getUUID();
        }
        player.setYRot(getYRot());
        player.setXRot(getXRot());
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rot = new Vec2(controller.getXRot() * 0.5f, controller.getYRot());
        setRot(rot.y, rot.x);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float forward = controller.zza;
        if (forward < 0f) {
            forward *= 0.4f;                                     // the bearers back up slowly
        }
        return new Vec3(controller.xxa * 0.3f, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return controller.isSprinting() ? RUN_SPEED : WALK_SPEED;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity l) {
            l.yBodyRot = yBodyRot;
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = new Vec3(1.6, 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);
        return position().add(side);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    // ------------------------------------------------------------------ the bearers' kick, the Book, the runes

    /** The rider has struck {@code target}: the bearer on its side kicks it, landing {@value #KICK_IMPACT} ticks on. */
    public void kick(LivingEntity target) {
        if (kickTick >= 0 && kickTick < KICK_TICKS || target == this || hasPassenger(target)) {
            return;
        }
        Vec3 to = target.position().subtract(position());
        Vec3 right = new Vec3(-1, 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);   // +x in the model is the bearers' right
        boolean leftSide = to.dot(right) < 0;
        kickAt = target;
        kickTick = 0;
        triggerAnim("attack", leftSide ? "kick_l" : "kick_r");
    }

    /** {@code who} hurt the rider: written in the Book (a new grudge replaces an older one). */
    public void wrong(LivingEntity who) {
        if (who == this || who.getUUID().equals(grudge) || hasPassenger(who)) {
            return;
        }
        grudge = who.getUUID();
        grudgeName = who.getName().getString();
        triggerAnim("book", "grudge_written");
        if (getControllingPassenger() instanceof Player p) {
            p.sendSystemMessage(Component.literal("A grudge is written against " + grudgeName + ".").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** The Book's grudge has died: struck out, and the rider healed and strengthened. */
    public void settle(LivingEntity dead) {
        if (!dead.getUUID().equals(grudge)) {
            return;
        }
        grudge = null;
        triggerAnim("book", "grudge_settled");
        if (getControllingPassenger() instanceof Player p) {
            p.heal(6f);
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 20, 0));
            p.sendSystemMessage(Component.literal("The grudge against " + grudgeName + " is settled.").withStyle(ChatFormatting.GOLD));
        }
        grudgeName = null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            boolean ridden = isVehicle();                        // client: the rise plays out before the bearers may walk
            if (ridden != wasRidden) {
                wasRidden = ridden;
                rising = ridden ? 18 : 0;
            } else if (rising > 0) {
                rising--;
            }
            return;
        }
        if (kickTick >= 0) {
            kickTick++;
            if (kickTick == KICK_IMPACT && kickAt != null && kickAt.isAlive() && kickAt.distanceTo(this) < 4.0f) {
                float dmg = kickAt.getUUID().equals(grudge) ? KICK_DAMAGE * 2 : KICK_DAMAGE;
                kickAt.hurtServer(level, damageSources().mobAttack(this), dmg);
                Vec3 away = kickAt.position().subtract(position()).normalize();
                kickAt.knockback(1.4, -away.x, -away.z);
            }
            if (kickTick >= KICK_TICKS) {
                kickTick = -1;
                kickAt = null;
            }
        }
        if (tickCount % 20 == 0) {
            boolean lit = false;
            if (getControllingPassenger() instanceof ServerPlayer p) {
                ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(p, blockPosition());
                if (hold != null && hold.inSettlement(blockPosition())) {
                    p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, true));   // the hold's runes guard their god
                }
                lit = p.hasEffect(MobEffects.RESISTANCE) || p.hasEffect(MobEffects.STRENGTH);
            }
            entityData.set(RUNES_LIT, lit);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        spawnAtLocation(level, new ItemStack(KRegistry.THRONE_ITEM.get()));   // broken, never lost
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.storeNullable("grudge", UUIDUtil.CODEC, grudge);
        out.storeNullable("grudge_name", com.mojang.serialization.Codec.STRING, grudgeName);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        grudge = in.read("grudge", UUIDUtil.CODEC).orElse(null);
        grudgeName = in.read("grudge_name", com.mojang.serialization.Codec.STRING).orElse(null);
    }

    // ------------------------------------------------------------------ its clips

    /** Ground speed from the last tick's move, blocks a second (the same on the rider's client and everyone else's). */
    double groundSpeed() {
        double dx = getX() - xo, dz = getZ() - zo;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ThroneOfPower>("main", 4, test -> {
            ThroneOfPower t = test.animatable();
            if (!t.isVehicle()) {
                test.setControllerSpeed(1f);
                return test.setAndContinue(SET_DOWN);
            }
            if (t.rising > 0) {
                test.setControllerSpeed(1f);
                return test.setAndContinue(RISE);
            }
            double v = t.groundSpeed();
            if (v > 0.15) {
                boolean run = v > 2.0;
                test.setControllerSpeed((float) Mth.clamp(v / (run ? RUN_CLIP : WALK_CLIP), 0.5, 3.0));
                return test.setAndContinue(run ? RUN : WALK);
            }
            test.setControllerSpeed(1f);
            return test.setAndContinue(IDLE);
        }));
        // later controllers overwrite only the bones their clips key: the kicking bearer, the Book, the rune plates
        controllers.add(new AnimationController<ThroneOfPower>("attack", 0, test -> PlayState.STOP)
                .triggerableAnim("kick_l", KICK_L).triggerableAnim("kick_r", KICK_R));
        controllers.add(new AnimationController<ThroneOfPower>("book", 0, test -> PlayState.STOP)
                .triggerableAnim("grudge_written", WRITTEN).triggerableAnim("grudge_settled", SETTLED));
        controllers.add(new AnimationController<ThroneOfPower>("runes", 4,
                test -> test.animatable().runesLit() ? test.setAndContinue(RUNES) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
