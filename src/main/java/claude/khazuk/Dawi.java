package claude.khazuk;

import claude.settlementcore.defence.Muster;
import claude.settlementcore.room.Clearance;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.unit.CrewGoal;
import claude.settlementcore.unit.SettlerMob;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * What both the Dwarfs' units are (the template a race's own unit base copies; the Great Plan's LizardmanUnit is the full-sized
 * one): bound to a {@link ShrineBlockEntity}, crewing its rooms with the engine's {@link CrewGoal}, fighting what the engine calls a foe,
 * and going into and out of the world by sinking into the ground ({@value #SINK_TICKS} ticks, unhurt meanwhile). Its owner's right-click
 * cycles its standing order (work, follow, hold). It is never broken and never turns rogue: courage is the race's to add.
 */
public abstract class Dawi extends SettlerMob implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Dawi.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DIGGING = SynchedEntityData.defineId(Dawi.class, EntityDataSerializers.BOOLEAN);
    /** >0 rising (ticks left), <0 sinking, 0 neither. */
    private static final EntityDataAccessor<Integer> DATA_SINK = SynchedEntityData.defineId(Dawi.class, EntityDataSerializers.INT);

    public static final int SINK_TICKS = 16;
    /** Ticks a unit sent home has to walk to its Shrine before it sinks where it stands. */
    public static final int HOMEWARD_TICKS = 200;
    /** How far from its Shrine it walks home; further, it sinks at once. */
    public static final double WALK_HOME = 32;

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.khazuk.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.khazuk.walk");
    static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.khazuk.work");
    /** A blow (a melee swing): once, rest to rest, the blow landing at 0.25 s. */
    static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.khazuk.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos heartPos;
    @Nullable private BlockPos crewRoom;
    @Nullable private BlockPos guardPos;
    @Nullable private Vec3 riseAt;
    private boolean homeward;
    private long homeBy;

    protected Dawi(EntityType<? extends Dawi> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder baseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_MODE, Mode.WORK.ordinal());
        b.define(DATA_DIGGING, false);
        b.define(DATA_SINK, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DawiGoals.Homeward(this));
        goalSelector.addGoal(2, new CrewGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(4, new DawiGoals.FollowOwner(this));
        goalSelector.addGoal(4, new DawiGoals.Hold(this));
        goalSelector.addGoal(5, new CrewGoal.Idle(this));
        goalSelector.addGoal(6, new DawiGoals.StayNearHeart(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Dawi.class).setAlertOthers(Dawi.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (m, level) -> m instanceof Mob mob && Muster.foe(mob)));
    }

    // ------------------------------------------------------------------ its Shrine

    @Override
    public @Nullable ShrineBlockEntity settlement() {
        return heartPos != null && level() instanceof ServerLevel sl && sl.isLoaded(heartPos)
                && sl.getBlockEntity(heartPos) instanceof ShrineBlockEntity h ? h : null;
    }

    @Override
    public void bindTo(Settlement settlement) {
        heartPos = settlement.getBlockPos();
    }

    public @Nullable BlockPos heartPos() {
        return heartPos;
    }

    // ------------------------------------------------------------------ orders

    @Override
    public Mode getMode() {
        return Mode.values()[Math.floorMod(entityData.get(DATA_MODE), Mode.values().length)];
    }

    @Override
    public void setMode(Mode m) {
        entityData.set(DATA_MODE, m.ordinal());
        guardPos = m == Mode.GUARD ? blockPosition() : null;
    }

    public @Nullable BlockPos guardPos() {
        return guardPos;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ShrineBlockEntity h = settlement();
        if (hand != InteractionHand.MAIN_HAND || h == null || !player.getUUID().equals(h.owner()) || isCrew()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide()) {
            setMode(getMode().next());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(getName().getString() + ": " + getMode().order()));
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ what the engine asks of it

    @Override
    public float tempo() {
        return (0.9f + Math.floorMod(getUUID().hashCode(), 31) / 100f) * (aleUp ? ALE_TEMPO : 1f);
    }

    @Override
    public int animLag() {
        return 2 + Math.floorMod(getUUID().hashCode() >> 5, 7);
    }

    @Override
    public boolean isCrew() {
        return crewRoom != null;
    }

    @Override
    public void makeCrew(BlockPos room) {
        crewRoom = room;
        setMode(Mode.WORK);
    }

    @Override
    public @Nullable BlockPos crewRoom() {
        return crewRoom;
    }

    @Override
    public ItemStack carried() {
        return isCrew() ? getMainHandItem() : ItemStack.EMPTY;
    }

    @Override
    public void carry(ItemStack load) {
        setItemSlot(EquipmentSlot.MAINHAND, load.isEmpty() ? ItemStack.EMPTY : load);
        setDropChance(EquipmentSlot.MAINHAND, load.isEmpty() ? 0f : 2f);
    }

    @Override
    public boolean isDigging() {
        return entityData.get(DATA_DIGGING);
    }

    @Override
    public void setDigging(boolean d) {
        entityData.set(DATA_DIGGING, d);
    }

    @Override
    public boolean isBroken() {
        return false;
    }

    @Override
    public boolean isRogue() {
        return false;
    }

    @Override
    public void hearten(float amount) {
    }

    @Override
    public String runningGoals() {
        return goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                .map(g -> g.getGoal().getClass().getSimpleName()).collect(Collectors.joining(", "));
    }

    /** A crew settler is never saved: its Shrine counts it and puts it back when it is gone from sight. */
    @Override
    public boolean shouldBeSaved() {
        return !isCrew() && super.shouldBeSaved();
    }

    // ------------------------------------------------------------------ into and out of the ground

    @Override
    public boolean isBurrowing() {
        return entityData.get(DATA_SINK) != 0;
    }

    /** Ticks of the sink left: >0 rising, <0 sinking (synced: the model sinks and rises with it). */
    public int sinkPhase() {
        return entityData.get(DATA_SINK);
    }

    @Override
    public void emergeAt(double x, double y, double z) {
        riseAt = null;
        teleportTo(x, y, z);
        getNavigation().stop();
        entityData.set(DATA_SINK, SINK_TICKS);
    }

    @Override
    public void burrowTo(double x, double y, double z) {
        riseAt = new Vec3(x, y, z);
        sink();
    }

    private void sink() {
        setTarget(null);
        getNavigation().stop();
        entityData.set(DATA_SINK, -SINK_TICKS);
    }

    /** Home: it walks to its Shrine first ({@link DawiGoals.Homeward}), then sinks into the ground beside it. Far off, it sinks at once. */
    @Override
    public void goHome() {
        if (homeward) {
            return;
        }
        homeward = true;
        homeBy = level().getGameTime() + HOMEWARD_TICKS;
        setTarget(null);
        setDigging(false);
        ShrineBlockEntity h = level().isClientSide() ? null : settlement();
        if (h == null || distanceToSqr(Vec3.atCenterOf(h.getBlockPos())) > WALK_HOME * WALK_HOME) {
            sink();
        }
    }

    @Override
    public boolean isHomeward() {
        return homeward;
    }

    long homeBy() {
        return homeBy;
    }

    /** At its Shrine (or out of time): down it goes. */
    void sinkHome() {
        if (entityData.get(DATA_SINK) >= 0) {
            sink();
        }
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide() && isBurrowing()) {
            tickSink();
            return;
        }
        super.aiStep();
        if (!level().isClientSide()) {
            mend();
            if (tickCount % 40 == 13) {
                drink();
                wearRunes();
            }
        }
    }

    private void tickSink() {
        setDeltaMovement(Vec3.ZERO);
        int s = entityData.get(DATA_SINK);
        boolean rising = s > 0;
        if (level() instanceof ServerLevel sl && (tickCount & 3) == 0) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.2, getZ(), 2, 0.3, 0.1, 0.3, 0.01);
        }
        s += rising ? -1 : 1;
        entityData.set(DATA_SINK, s);
        if (s != 0) {
            return;
        }
        if (rising) {
            if (level() instanceof ServerLevel sl) {
                Vec3 clear = Clearance.clearOf(sl, this);         // built in while it rose: beside it, not in it
                if (clear != null) {
                    setPos(clear.x, clear.y, clear.z);
                }
            }
        } else if (homeward) {
            ShrineBlockEntity h = settlement();
            if (h != null) {
                h.absorb(this);
            }
            discard();
        } else if (riseAt != null) {
            emergeAt(riseAt.x, riseAt.y, riseAt.z);
        }
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return isBurrowing() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    // ------------------------------------------------------------------ Dawi grit (D2)

    /**
     * Stubborn (the army book's Dwarfs hold where others run): below {@value #STUBBORN_BELOW} of his health a Dwarf takes only
     * {@value #STUBBORN_TAKEN} of a blow. /kill and the void get past it.
     */
    public static final float STUBBORN_BELOW = 0.4f, STUBBORN_TAKEN = 0.75f;
    /** Out of the fight ({@value #CALM_TICKS} ticks unhurt, no foe), he mends {@value #HEAL} health every {@value #HEAL_TICKS} ticks. */
    public static final int CALM_TICKS = 200, HEAL_TICKS = 20;
    public static final float HEAL = 1f;
    private int lastHurtTick = -10000;

    public boolean isStubborn() {
        return getHealth() < getMaxHealth() * STUBBORN_BELOW || claude.khazuk.god.AncestorPowers.furious(this);   // D7: no fear in Grimnir's Fury
    }

    /** What he takes of a blow of {@code amount}: all of it, or less when he's stubborn. A Warrior's shield comes before this. */
    protected float taken(DamageSource source, float amount) {
        if (wardRunes > 0 && (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO))) {
            amount *= (float) (1 - claude.khazuk.hold.Runes.WARD_CUT * wardRunes);   // D5: the hold's Rune of Warding
        }
        return isStubborn() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount * STUBBORN_TAKEN : amount;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, taken(source, amount));
        if (hurt) {
            lastHurtTick = tickCount;
        }
        return hurt;
    }

    /** Out of the fight he mends (called each server tick). */
    private void mend() {
        if (!isAlive() || getTarget() != null || tickCount - lastHurtTick < CALM_TICKS || tickCount % HEAL_TICKS != 0
                || getHealth() >= getMaxHealth()) {
            return;
        }
        heal(HEAL);
    }

    // ------------------------------------------------------------------ ale (D3)

    /** With ale in the hold's barrels (hold/Brewhouse) a Dwarf hits {@value #ALE_DAMAGE} harder, walks a tenth faster and works a quarter faster. */
    public static final double ALE_DAMAGE = 2, ALE_SPEED = 0.1;
    public static final float ALE_TEMPO = 1.25f;
    static final net.minecraft.resources.Identifier ALE = net.minecraft.resources.Identifier.fromNamespaceAndPath(Khazuk.MODID, "ale");
    static final net.minecraft.resources.Identifier GRUDGE = net.minecraft.resources.Identifier.fromNamespaceAndPath(Khazuk.MODID, "grudgebearer");
    private boolean aleUp;

    /** Whether he has ale in him now (refreshed every 2 s from his Shrine's barrels). */
    public boolean aleUp() {
        return aleUp;
    }

    // ------------------------------------------------------------------ the hold's runes (D5)

    static final net.minecraft.resources.Identifier RUNE_IRON = net.minecraft.resources.Identifier.fromNamespaceAndPath(Khazuk.MODID, "hold_rune_iron");
    static final net.minecraft.resources.Identifier RUNE_CLEAVING = net.minecraft.resources.Identifier.fromNamespaceAndPath(Khazuk.MODID, "hold_rune_cleaving");
    private int ironRunes, cleaveRunes, wardRunes;

    /** The hold's runes on him: its Runes of Iron and Cleaving as armour and blows, its Rune of Warding kept for {@link #taken}. */
    void wearRunes() {
        ShrineBlockEntity h = settlement();
        var r = h == null ? null : h.runes();
        int iron = r == null ? 0 : r.level(claude.khazuk.hold.Runes.Kind.IRON), cleave = r == null ? 0 : r.level(claude.khazuk.hold.Runes.Kind.CLEAVING);
        wardRunes = r == null ? 0 : r.level(claude.khazuk.hold.Runes.Kind.WARDING);
        if (iron != ironRunes) {
            ironRunes = iron;
            rune(Attributes.ARMOR, RUNE_IRON, iron * claude.khazuk.hold.Runes.IRON_ARMOR);
        }
        if (cleave != cleaveRunes) {
            cleaveRunes = cleave;
            rune(Attributes.ATTACK_DAMAGE, RUNE_CLEAVING, cleave * claude.khazuk.hold.Runes.CLEAVE_DAMAGE);
        }
    }

    private void rune(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, net.minecraft.resources.Identifier id, double amount) {
        var a = getAttribute(attr);
        if (a == null) {
            return;
        }
        if (amount <= 0) {
            a.removeModifier(id);
        } else {
            a.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ------------------------------------------------------------------ Grimnir's Fury (D7)

    static final net.minecraft.resources.Identifier FURY = net.minecraft.resources.Identifier.fromNamespaceAndPath(Khazuk.MODID, "grimnirs_fury");
    private boolean furyUp;

    /** Whether Grimnir's Fury is on him now. */
    public boolean furyUp() {
        return furyUp;
    }

    /** Grimnir's Fury on him or spent (AncestorPowers): half again as hard a blow, a quarter faster, unmoved by blows; on and off with it. */
    public void feelFury() {
        boolean up = claude.khazuk.god.AncestorPowers.furious(this);
        if (up == furyUp) {
            return;
        }
        furyUp = up;
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        var spd = getAttribute(Attributes.MOVEMENT_SPEED);
        var kb = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (up) {
            if (dmg != null) {
                dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FURY, claude.khazuk.god.AncestorPowers.FURY_DAMAGE,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (spd != null) {
                spd.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FURY, claude.khazuk.god.AncestorPowers.FURY_SPEED,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            if (kb != null) {
                kb.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FURY, claude.khazuk.god.AncestorPowers.FURY_STEADY,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            for (var a : new net.minecraft.world.entity.ai.attributes.AttributeInstance[]{dmg, spd, kb}) {
                if (a != null) {
                    a.removeModifier(FURY);
                }
            }
        }
    }

    /** His Shrine's barrels: ale in him while they hold any, the bonuses on and off with it. */
    void drink() {
        ShrineBlockEntity h = settlement();
        boolean up = h != null && h.brew().aleUp();
        if (up == aleUp) {
            return;
        }
        aleUp = up;
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        var spd = getAttribute(Attributes.MOVEMENT_SPEED);
        if (up) {
            if (dmg != null) {
                dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ALE, ALE_DAMAGE,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
            if (spd != null) {
                spd.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ALE, ALE_SPEED,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        } else {
            if (dmg != null) {
                dmg.removeModifier(ALE);
            }
            if (spd != null) {
                spd.removeModifier(ALE);
            }
        }
    }

    /**
     * A Dwarf never turns on his own god, whatever the god does to him (the review of 88e429a: a Marksman's sure shot made a god's stray blow
     * deadly); anyone else he may.
     */
    @Override
    public void setTarget(@org.jspecify.annotations.Nullable LivingEntity target) {
        ShrineBlockEntity h = target instanceof Player ? settlement() : null;
        if (h != null && target.getUUID().equals(h.owner())) {
            return;
        }
        super.setTarget(target);
    }

    /** Blows that landed and played the attack clip, for the tests. */
    public int attacksForTests;

    /** A blow that lands plays the attack clip on every client, whole: a swing's 6 ticks would cut it short (the review of 737e467). */
    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        ShrineBlockEntity h = settlement();
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean bears = h != null && dmg != null && h.grudges().bears(target);
        if (bears) {                                             // D4: a grudgebearer strikes a foe of a settled grudge's kind harder
            dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(GRUDGE, claude.khazuk.hold.Grudges.BEARER_PERCENT / 100.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean hit;
        try {
            hit = super.doHurtTarget(level, target);
        } finally {
            if (bears) {
                dmg.removeModifier(GRUDGE);
            }
        }
        if (hit) {
            triggerAnim("attack", "attack");
            attacksForTests++;
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        ShrineBlockEntity h = settlement();
        if (h != null) {
            h.removePerson(getUUID());
            h.works().forget(getUUID());
            net.minecraft.world.entity.Entity killer = source.getEntity();
            if (killer != null && killer != this && !(killer instanceof Dawi d && d.settlement() == h) && !killer.getUUID().equals(h.owner())
                    && level() instanceof ServerLevel sl) {
                h.grudges().wronged(sl, h, killer, "slew " + getName().getString());   // D4: written in the Dammaz Kron
            }
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ saves

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("heart", BlockPos.CODEC, heartPos);
        out.storeNullable("guard_pos", BlockPos.CODEC, guardPos);
        out.putInt("mode", getMode().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        heartPos = in.read("heart", BlockPos.CODEC).orElse(null);
        entityData.set(DATA_MODE, in.getIntOr("mode", Mode.WORK.ordinal()));
        guardPos = in.read("guard_pos", BlockPos.CODEC).orElse(null);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // ------------------------------------------------------------------ its look

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<Dawi>("main", 4, test -> {
            Dawi u = test.animatable();
            if (u.isDigging()) {
                return test.setAndContinue(WORK);
            }
            return test.setAndContinue(test.isMoving() ? WALK : IDLE);
        }));
        // A blow, on a controller of its own after main: stopped until the server triggers it, it plays the clip once over the loops (the
        // later controller wins the bones it keys: the right arm, the body), then finishes and stops drawing, and main shows through.
        // On main itself a trigger silences the predicate for good (GeckoLib 5.5.2 never clears it when the clip ends: the review of a4fb33b).
        controllers.add(new AnimationController<Dawi>("attack", 2, test -> PlayState.STOP).triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
