package claude.khazuk.engine;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.settlementcore.defence.Muster;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementSpawns;
import claude.settlementcore.unit.SettlerMob;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ancestor Engine (the user, 2026-11-10: "make a another variant of the mech but for the player"; the detail session's model,
 * handoffs/ancestor_engine_2026-11-10_done.md, its mechanics approved "Yes, as proposed"): the god's own walking steam engine, the
 * master-work the Slayer mech copies. He sets it down from its item, climbs in and drives it as the Throne of Power is driven.
 * <ul>
 * <li><b>The attack key</b> is the great chop ({@value #CHOP} to everything in a box {@value #CHOP_AHEAD} blocks ahead, landing on the
 * clip's tick 5). Held on a block he looks at, the axe digs it like a pick, a block a bite ({@link #digs}).</li>
 * <li><b>The use key</b> is the claw ({@value #CRUSH} to the one nearest it, thrown).</li>
 * <li><b>The jump key, standing</b>, is the stomp ({@value #STOMP} and a shove to everything within {@value #STOMP_REACH} blocks).</li>
 * <li><b>The boiler's heat is his to spend:</b> every blow that lands heats it (0 to {@value #MAX_HEAT}), and the hotter the harder it
 * hits (half as much again at full heat). The vent key empties it in a scalding blast ({@value #VENT_LEAST} to {@value #VENT_FULL},
 * by the heat spent, within {@value #VENT_REACH} blocks). At the red line unvented it shudders (the overheat clips) and fights on at
 * its full bonus: the heat never hurts it or him.</li>
 * <li><b>Broken</b>, the boiler bursts as the Slayer mech's does ({@value #BURST} to the foes round it), the seat throws its god up and
 * clear, unhurt, and the wreck leaves its item.</li>
 * </ul>
 * Never its god's own are struck: him, his Dawi, his pets and allies, or what they ride. Every blow is decided here on the server; the
 * client sends only which keys are down ({@link EngineKeys}).
 */
public class AncestorEngine extends PathfinderMob implements GeoEntity {

    // the Slayer mech is the yardstick (140 health, a blow of 22, 29 running hot, armour 16 and 6, a burst of 18): a god's engine is far above it
    public static final double MAX_HEALTH = 350, ARMOR = 20, TOUGHNESS = 10;
    public static final float CHOP = 30f, CRUSH = 60f, STOMP = 16f, VENT_LEAST = 6f, VENT_FULL = 40f, BURST = 36f;
    public static final float MAX_HEAT = 100f, HEAT_CHOP = 8f, HEAT_CRUSH = 12f, HEAT_STOMP = 10f, HOT_BONUS = 0.5f;
    /** The boiler cools {@value #COOL} a second once {@value #COOL_AFTER} ticks have passed with no blow landed. */
    public static final float COOL = 2.5f;
    public static final int COOL_AFTER = 100;
    public static final double CHOP_AHEAD = 2.0, CHOP_HALF = 1.6, CLAW_AHEAD = 2.2, CLAW_UP = 1.4, CLAW_REACH = 1.6, STOMP_REACH = 3.0,
            VENT_REACH = 4.0, BURST_REACH = 4.0, DIG_REACH = 5.0;
    /** The work clip's loop and its bite (0.6 s into each 0.8 s). */
    public static final int WORK_LOOP = 16, BITE_AT = 12;
    /** The rise clip (0.9 s): climbed into, it heaves up before it walks or strikes. */
    public static final int RISE_TICKS = 18;
    /** The burst clip's beats, as the Slayer mech's: the boiler blows at 0.35 s, the seat's ram is at its top at 0.6 s. */
    public static final int BLOWS_AT = 7, RIDER_OUT_AT = 12;
    /** Thrown clear, its god lands unhurt for this long. */
    public static final int NO_FALL_TICKS = 100;
    /** Parked in its god's hold it mends this much a second. */
    public static final float MEND = 1f;
    /** A block softer than this is broken whatever it is (leaves, glass, plants); a harder one must be a pick's, an axe's or a shovel's. */
    static final float SOFT = 1.0f;
    /** Ground speed at a walk and a run, as getRiddenSpeed's attribute value: the clips' own 2.06 and 3.56 blocks a second (43.2 blocks a second to the 1.0). */
    static final float WALK_SPEED = 0.0477f, RUN_SPEED = 0.0825f;
    static final double WALK_CLIP = 2.06, RUN_CLIP = 3.56, RUN_ABOVE = 2.8;

    /** Its blows: the clip on the attack controller, the tick it lands on, and how long before the next may begin. */
    public enum Blow {
        CHOP("attack", 5, 12), CRUSH("crush", 6, 14), STOMP("stomp", 8, 18), VENT("vent", 3, 18);

        public final String clip;
        public final int lands, ticks;

        Blow(String clip, int lands, int ticks) {
            this.clip = clip;
            this.lands = lands;
            this.ticks = ticks;
        }
    }

    static final RawAnimation REST = RawAnimation.begin().thenLoop("animation.khazuk.rest");
    static final RawAnimation SET_DOWN = RawAnimation.begin().thenPlay("animation.khazuk.kneel").thenLoop("animation.khazuk.rest");
    static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.khazuk.rise").thenLoop("animation.khazuk.idle");
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.khazuk.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.khazuk.walk");
    static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.khazuk.run");
    static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.khazuk.work");
    static final RawAnimation OVERHEAT = RawAnimation.begin().thenLoop("animation.khazuk.overheat");
    static final RawAnimation OVERHEAT_WALK = RawAnimation.begin().thenLoop("animation.khazuk.overheat_walk");
    static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.khazuk.attack");
    static final RawAnimation CRUSH_CLIP = RawAnimation.begin().thenPlay("animation.khazuk.crush");
    static final RawAnimation STOMP_CLIP = RawAnimation.begin().thenPlay("animation.khazuk.stomp");
    static final RawAnimation VENT_CLIP = RawAnimation.begin().thenPlay("animation.khazuk.vent");
    static final RawAnimation BURST_CLIP = RawAnimation.begin().thenPlay("animation.khazuk.burst");
    /** Every clip its controllers ask for (the tests: GeckoLib 5.5 crashes on a missing one). */
    public static final java.util.List<String> CLIPS = java.util.List.of("rest", "rise", "kneel", "idle", "walk", "run", "work", "overheat",
            "overheat_walk", "attack", "crush", "stomp", "vent", "burst");

    /** The boiler's heat, 0 to {@value #MAX_HEAT} (synced: the overheat clips; the rider is told it on his action bar). */
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(AncestorEngine.class, EntityDataSerializers.FLOAT);
    /** Digging (synced: the work clip, and it stands while it digs). */
    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(AncestorEngine.class, EntityDataSerializers.BOOLEAN);
    /** Stomping (synced: the clip keys its legs, so it stands till the foot is down). */
    private static final EntityDataAccessor<Boolean> DATA_ROOTED = SynchedEntityData.defineId(AncestorEngine.class, EntityDataSerializers.BOOLEAN);

    /** Gods thrown clear of a burst engine, and the game time until which they land unhurt (the server's). */
    private static final Map<UUID, Long> THROWN = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID owner;
    /** The rider's keys, as his client last said. */
    private boolean attackDown, useDown, ventAsked;
    /** The attack or the use key was pressed since the engine last answered it (a press swings even at the air; held on nothing, the axe waits). */
    private boolean attackFresh, useFresh;
    /** The blow under way, and ticks since it began. */
    @Nullable private Blow blow;
    private int blowTick;
    private int digTick, sinceBlow;
    /** Ticks its rider has been in it, up to {@value #RISE_TICKS}. */
    private int manned;
    private int toldHeat = -1, toldAt;
    private boolean burst, blown, thrown, ejecting;
    /** Client: ticks the rise still has to play before it may walk; whether it has been manned since this client first saw it. */
    private int rising;
    private boolean wasRidden, everManned;

    public AncestorEngine(EntityType<? extends AncestorEngine> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_HEAT, 0f);
        b.define(DATA_WORKING, false);
        b.define(DATA_ROOTED, false);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public float heat() {
        return entityData.get(DATA_HEAT);
    }

    /** The boiler at {@code h} (the vent, the tests). */
    public void setHeat(float h) {
        float to = Mth.clamp(h, 0f, MAX_HEAT);
        if (to != heat()) {
            entityData.set(DATA_HEAT, to);
        }
    }

    /** At the red line, unvented: it shudders and vents on its own clips, and fights on. */
    public boolean overheated() {
        return heat() >= MAX_HEAT;
    }

    public boolean working() {
        return entityData.get(DATA_WORKING);
    }

    public boolean rooted() {
        return entityData.get(DATA_ROOTED);
    }

    /** How much harder it hits for its heat: 1 cold, {@code 1 + }{@value #HOT_BONUS} at the red line. */
    public float power() {
        return 1f + HOT_BONUS * heat() / MAX_HEAT;
    }

    @Nullable
    public Blow blow() {
        return blow;
    }

    /** No lead drags it off (a Mob is leashable, and a lead is handled before mobInteract: the Throne's review of 2f93cc2). */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ------------------------------------------------------------------ riding (the Throne of Power's way)

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (owner != null && !owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("The Ancestor Engine answers only to its own god.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.PASS;
        }
        if (isVehicle() || !isAlive()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {                     // sneak-used, it is packed back into its item
            ItemStack item = new ItemStack(KRegistry.ANCESTOR_ENGINE_ITEM.get());
            if (!player.getInventory().add(item)) {
                ItemEntity e = spawnAtLocation((ServerLevel) level(), item);
                if (e != null) {
                    e.setUnlimitedLifetime();
                }
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

    /** It stands while it heaves up, while it digs and while it stomps; else it is steered as the Throne is. */
    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        if (rising > 0 || working() || rooted()) {
            return Vec3.ZERO;
        }
        float forward = controller.zza;
        if (forward < 0f) {
            forward *= 0.4f;
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

    /** Thrown out of a burst engine, he leaves from his seat; else off to the left, the right, behind or in front, the first spot he can stand. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (ejecting) {
            return passenger.position();
        }
        for (Vec3 off : new Vec3[]{new Vec3(1.7, 0, 0), new Vec3(-1.7, 0, 0), new Vec3(0, 0, -1.9), new Vec3(0, 0, 2.2)}) {
            Vec3 at = position().add(off.yRot(-getYRot() * Mth.DEG_TO_RAD));
            for (int dy = 0; dy <= 1; dy++) {
                Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), BlockPos.containing(at).above(dy), true);
                if (safe != null) {
                    return safe;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /** Neither it nor its rider takes fall damage (the Throne's rule: the fall isn't passed on to him). */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    // ------------------------------------------------------------------ the rider's keys

    /**
     * The payload's handler: {@code p}'s client says which of his keys are down, and which were pressed since it last said (a click
     * shorter than a tick is still a click). Only the engine's own rider is heard: false for anyone else. What the keys do is decided in
     * {@link #tick}, never by the client.
     */
    public static boolean keysFrom(ServerPlayer p, EngineKeys keys) {
        if (!(p.getVehicle() instanceof AncestorEngine e) || e.getControllingPassenger() != p || !e.isAlive()) {
            return false;
        }
        e.attackFresh |= keys.attackPressed() || keys.attack() && !e.attackDown;
        e.useFresh |= keys.usePressed() || keys.use() && !e.useDown;
        e.attackDown = keys.attack();
        e.useDown = keys.use();
        e.ventAsked |= keys.vent();
        return true;
    }

    /**
     * Whom its blows never touch: itself, its rider, its god's Dawi, pets and allies, his other mounts, a player he may not harm, a stand, and
     * anything one of those rides.
     */
    public boolean spares(@Nullable Player rider, LivingEntity t) {
        if (t == this || t == rider || hasPassenger(t) || !t.isAlive() || t.isSpectator() || t instanceof ArmorStand) {
            return true;
        }
        UUID god = rider != null ? rider.getUUID() : owner;
        if (t instanceof SettlerMob s) {
            Settlement hold = s.settlement();
            if (hold == null ? t instanceof Dawi : god != null && god.equals(hold.owner())) {
                return true;
            }
        }
        if (god != null) {
            if (t instanceof OwnableEntity pet && pet.getOwnerReference() != null && god.equals(pet.getOwnerReference().getUUID())
                    || t instanceof AncestorEngine e && god.equals(e.owner)
                    || t instanceof claude.khazuk.throne.ThroneOfPower th && god.equals(th.owner())
                    || t instanceof claude.khazuk.hold.AnvilOfDoom a && god.equals(a.owner())) {
                return true;
            }
        }
        if (rider != null && (t.isAlliedTo(rider) || rider.isAlliedTo(t) || t instanceof Player other && !rider.canHarmPlayer(other))) {
            return true;
        }
        for (Entity up : t.getPassengers()) {
            if (up instanceof LivingEntity l && spares(rider, l)) {
                return true;                                     // what one of his own rides
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            boolean ridden = isVehicle();                        // client: the rise plays out before it may walk
            if (ridden != wasRidden) {
                wasRidden = ridden;
                rising = ridden ? RISE_TICKS : 0;
                everManned = true;
            } else if (rising > 0) {
                rising--;
            }
            return;
        }
        if (!isAlive()) {
            return;                                              // its burst is tickDeath's
        }
        sinceBlow++;
        if (sinceBlow > COOL_AFTER && tickCount % 20 == 0 && heat() > 0) {
            setHeat(heat() - COOL);
        }
        if (!(getControllingPassenger() instanceof ServerPlayer rider)) {
            unmanned(level);
            return;
        }
        if (manned < RISE_TICKS) {
            manned++;                                            // it heaves up first: a key pressed now is answered when it stands
            ventAsked = false;
            return;
        }
        if (blow != null) {
            blowTick++;
            if (blowTick == blow.lands) {
                land(level, rider, blow);
            }
            if (blowTick >= blow.ticks) {
                blow = null;
                setRooted(false);
            }
        }
        if (blow == null) {
            Input in = rider.getLastClientInput();
            // the vent key is kept until the engine is free to answer it (the review of 3fe3e69: it was wiped every tick, and with the
            // attack key held a blow is under way all but one tick in twelve, so the vent was dead in the fight it is for); a cold
            // boiler's press is spent here too
            boolean vent = ventAsked;
            ventAsked = false;
            if (vent && heat() >= 1f) {
                begin(Blow.VENT);
            } else if (in.jump() && !in.forward() && !in.backward() && !in.left() && !in.right()) {
                begin(Blow.STOMP);
            } else if (useDown || useFresh) {
                begin(Blow.CRUSH);
            } else if (attackDown || attackFresh) {
                chopOrDig(level, rider);
            } else {
                setWorking(false);
            }
        }
        tellHeat(rider);
    }

    /** No one in it: its keys are up, nothing is under way, and parked in its god's hold it mends. */
    private void unmanned(ServerLevel level) {
        attackDown = useDown = ventAsked = attackFresh = useFresh = false;
        blow = null;
        manned = 0;
        toldHeat = -1;
        setWorking(false);
        setRooted(false);
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth() && owner != null) {
            for (Settlement s : SettlementSpawns.loaded(level)) {
                if (owner.equals(s.owner()) && s.inSettlement(blockPosition())) {
                    heal(MEND);
                    break;
                }
            }
        }
    }

    private void begin(Blow b) {
        blow = b;
        blowTick = 0;
        if (b == Blow.CHOP) {
            attackFresh = false;                                 // its own press is answered; one made for the other arm during this blow waits
        } else if (b == Blow.CRUSH) {
            useFresh = false;
        }
        setWorking(false);
        setRooted(b == Blow.STOMP);
        triggerAnim("attack", b.clip);
    }

    private void setWorking(boolean on) {
        if (working() != on) {
            entityData.set(DATA_WORKING, on);
        }
        if (!on) {
            digTick = 0;
        }
    }

    private void setRooted(boolean on) {
        if (rooted() != on) {
            entityData.set(DATA_ROOTED, on);
        }
    }

    private Vec3 ahead() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    private AABB chopBox() {
        Vec3 c = position().add(ahead().scale(CHOP_AHEAD));
        return new AABB(c.x - CHOP_HALF, getY() - 1.0, c.z - CHOP_HALF, c.x + CHOP_HALF, getY() + 3.5, c.z + CHOP_HALF);
    }

    /**
     * The attack key: a foe in the axe's reach, or anything he looks at that isn't his own, is chopped; else, held, the block he looks at
     * is dug, a bite each loop of the work clip; else a fresh press chops the air, and held on nothing the axe waits for a foe.
     */
    private void chopOrDig(ServerLevel level, ServerPlayer rider) {
        // a foe the axe can reach: one out past it (looked at across the cave) doesn't keep the axe from the rock in front of him
        boolean foe = foeInReach(level, rider) || looksAt(level, rider, CHOP_AHEAD + CHOP_HALF) != null;
        if (!attackDown) {
            refused = null;
        }
        BlockPos face = foe || !attackDown ? null : digFace(level, rider);
        if (face != null && face.equals(refused)) {
            face = null;                                         // it would not bite here: not hewn at again until he lets the key go
        }
        if (face == null) {
            setWorking(false);
            if (attackFresh || foe) {
                begin(Blow.CHOP);
            }
            return;
        }
        attackFresh = false;
        if (!working()) {
            setWorking(true);
        }
        digTick++;
        if (digTick == BITE_AT && !bite(level, rider, face)) {
            // a claim or a rule refused it (the review of 3fe3e69: the engine stood rooted, hewing for ever at a block that never broke)
            refused = face.immutable();
            setWorking(false);
            rider.sendOverlayMessage(Component.literal("The axe will not bite here.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (digTick >= WORK_LOOP) {
            digTick = 0;
        }
    }

    private boolean foeInReach(ServerLevel level, ServerPlayer rider) {
        return !level.getEntitiesOfClass(Mob.class, chopBox(), m -> Muster.foe(m) && !spares(rider, m)).isEmpty();
    }

    /** The living thing under the rider's eye within {@code reach}, nearer than any block, that isn't his own. */
    @Nullable
    private LivingEntity looksAt(ServerLevel level, ServerPlayer rider, double reach) {
        Vec3 from = rider.getEyePosition(), to = from.add(rider.getLookAngle().scale(reach));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider));
        double max = wall.getType() == HitResult.Type.MISS ? reach * reach : from.distanceToSqr(wall.getLocation());
        var hit = ProjectileUtil.getEntityHitResult(rider, from, to, new AABB(from, to).inflate(1.0),
                e -> e instanceof LivingEntity l && !spares(rider, l), max);
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }

    /** The block the axe would dig now: the one the rider looks at within {@value #DIG_REACH}, if it {@link #digs}. */
    @Nullable
    private BlockPos digFace(ServerLevel level, ServerPlayer rider) {
        Vec3 from = rider.getEyePosition(), to = from.add(rider.getLookAngle().scale(DIG_REACH));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, rider));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return digs(level, pos, level.getBlockState(pos)) ? pos : null;
    }

    /**
     * Whether the axe digs this block: anything a diamond pick, axe or shovel is the tool for, and soft things (leaves, glass, plants);
     * never bedrock or the like, never a block with a block entity (a chest, a Shrine), never what diamond can't take, and never a hold's
     * built halls (any loaded hold's buildings and the walls round them, its digs and sites under way, and the Shrine's own surrounds, as
     * GrungniClears keeps out of them).
     */
    public static boolean digs(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.hasBlockEntity() || s.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) {
            return false;
        }
        float hard = s.getDestroySpeed(level, pos);
        if (hard < 0f) {
            return false;
        }
        boolean tooled = s.is(BlockTags.MINEABLE_WITH_PICKAXE) || s.is(BlockTags.MINEABLE_WITH_AXE) || s.is(BlockTags.MINEABLE_WITH_SHOVEL);
        if (!tooled && hard >= SOFT) {
            return false;
        }
        return !holdBuilt(level, pos);
    }

    static boolean holdBuilt(ServerLevel level, BlockPos pos) {
        for (Settlement s : SettlementSpawns.loaded(level)) {
            BlockPos o = s.getBlockPos();
            if (o.distManhattan(pos) > 512) {
                continue;
            }
            if (new BoundingBox(o.getX() - 3, o.getY() - 2, o.getZ() - 3, o.getX() + 3, o.getY() + 4, o.getZ() + 3).isInside(pos)) {
                return true;
            }
            for (Building b : s.buildings()) {
                if (b.box().inflatedBy(1).isInside(pos)) {
                    return true;
                }
            }
            for (var d : s.works().digs()) {
                if (d.box().inflatedBy(1).isInside(pos)) {
                    return true;
                }
            }
            for (var site : s.works().sites()) {
                if (site.box().inflatedBy(1).isInside(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The work clip's bite: the block is broken as its rider's own, its drops as a diamond tool's. A claim or a rule may refuse it. */
    private boolean bite(ServerLevel level, ServerPlayer rider, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!digs(level, pos, s) || !level.mayInteract(rider, pos)) {
            return false;
        }
        var asked = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, pos, s, rider);
        asked.setCanceled(rider.blockActionRestricted(level, pos, rider.gameMode()));
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(asked).isCanceled()) {
            return false;
        }
        ItemStack tool = new ItemStack(s.is(BlockTags.MINEABLE_WITH_AXE) ? Items.DIAMOND_AXE
                : s.is(BlockTags.MINEABLE_WITH_SHOVEL) ? Items.DIAMOND_SHOVEL : Items.DIAMOND_PICKAXE);
        if (level.destroyBlock(pos, false, rider)) {
            Block.dropResources(s, level, pos, null, rider, tool);
        }
        return true;
    }

    /** The block that last refused the axe: left alone until the attack key is let go. */
    @Nullable private BlockPos refused;

    /**
     * What the engine's blows all round it (the stomp, the vent) fall on: foes, anything that has turned on its god or his own, and
     * another player he may harm. A cow by the gate, a villager, a golem: spared (the review of 3fe3e69: a tap of the jump key beside
     * his farm killed his herd). The chop and the claw are aimed, and fall on whatever he aims them at.
     */
    private boolean threat(@Nullable Player rider, LivingEntity t) {
        if (t instanceof Player) {
            return true;                                         // (one he may not harm is spared already)
        }
        return t instanceof Mob m && (Muster.foe(m) || m.getTarget() != null && (m.getTarget() == rider || spares(rider, m.getTarget())));
    }

    // ------------------------------------------------------------------ its blows

    /** A blow of the engine's lands whole, whatever hit its mark a moment ago (its own cadence is its limit). */
    private static boolean strike(ServerLevel level, LivingEntity t, DamageSource src, float dmg) {
        t.invulnerableTime = 0;
        return t.hurtServer(level, src, dmg);
    }

    private void heatUp(float by) {
        sinceBlow = 0;
        setHeat(heat() + by);
    }

    private void land(ServerLevel level, ServerPlayer rider, Blow b) {
        float power = power();
        Vec3 ahead = ahead();
        DamageSource src = damageSources().playerAttack(rider);
        switch (b) {
            case CHOP -> {
                int hit = 0;
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, chopBox(), t -> !spares(rider, t))) {
                    if (strike(level, t, src, CHOP * power)) {
                        hit++;
                        t.knockback(0.8, -ahead.x, -ahead.z);
                    }
                }
                Vec3 edge = position().add(ahead.scale(CHOP_AHEAD));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, edge.x, getY() + 1.0, edge.z, 1, 0, 0, 0, 0);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 1f, 0.6f);
                if (hit > 0) {
                    heatUp(HEAT_CHOP);
                }
            }
            case CRUSH -> {
                Vec3 at = position().add(ahead.scale(CLAW_AHEAD)).add(0, CLAW_UP, 0);
                LivingEntity mark = null;
                double nearest = Double.MAX_VALUE;
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, CLAW_REACH * 2, CLAW_REACH * 2 + 1.0, CLAW_REACH * 2),
                        t -> !spares(rider, t))) {
                    double d = t.getBoundingBox().distanceToSqr(at);
                    if (d < nearest) {
                        nearest = d;
                        mark = t;
                    }
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK, getSoundSource(), 1f, 0.7f);
                if (mark != null && strike(level, mark, src, CRUSH * power)) {
                    double give = 1.0 - mark.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                    mark.knockback(2.2, -ahead.x, -ahead.z);     // thrown: back and up, as far as it can be moved at all
                    if (give > 0) {
                        Vec3 v = mark.getDeltaMovement();
                        mark.setDeltaMovement(v.x, Math.max(v.y, 0.7 * give), v.z);
                        mark.hurtMarked = true;
                    }
                    heatUp(HEAT_CRUSH);
                }
            }
            case STOMP -> {
                int hit = 0;
                double out = STOMP_REACH - getBbWidth() / 2;
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(out, 0.5, out),
                        t -> !spares(rider, t) && threat(rider, t))) {
                    if (strike(level, t, src, STOMP * power)) {
                        hit++;
                        Vec3 away = t.position().subtract(position());
                        t.knockback(1.2, -away.x, -away.z);
                        if (t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0) {
                            Vec3 v = t.getDeltaMovement();
                            t.setDeltaMovement(v.x, Math.max(v.y, 0.35), v.z);
                            t.hurtMarked = true;
                        }
                    }
                }
                BlockState under = level.getBlockState(blockPosition().below());
                if (!under.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY() + 0.1, getZ(), 40, 1.1, 0.1, 1.1, 0.1);
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, getSoundSource(), 1.6f, 0.5f);
                if (hit > 0) {
                    heatUp(HEAT_STOMP);
                }
            }
            case VENT -> {
                float spent = heat();
                float dmg = VENT_LEAST + (VENT_FULL - VENT_LEAST) * spent / MAX_HEAT;
                double out = VENT_REACH - getBbWidth() / 2;
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(out, 0.5, out),
                        t -> !spares(rider, t) && threat(rider, t))) {
                    strike(level, t, src, dmg);
                }
                setHeat(0f);
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.4, getZ(), 30 + (int) (spent * 0.6f), 1.6, 0.6, 1.6, 0.08);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 1.2f, 0.6f);
            }
        }
    }

    /** The boiler's gauge, on its rider's action bar: when it moves, and every 2 seconds while there is heat in it. */
    private void tellHeat(ServerPlayer rider) {
        int pct = Math.round(heat() * 100f / MAX_HEAT);
        if (pct == toldHeat ? pct == 0 || tickCount - toldAt < 40 : tickCount - toldAt < 4 && toldHeat >= 0) {
            return;
        }
        if (toldHeat < 0 && pct == 0) {
            toldHeat = 0;                                        // a cold engine says nothing as he climbs in
            return;
        }
        toldHeat = pct;
        toldAt = tickCount;
        int lit = Math.round(pct / 10f);
        MutableComponent bar = Component.literal("Boiler ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("|".repeat(lit)).withStyle(pct >= 100 ? ChatFormatting.RED : pct >= 70 ? ChatFormatting.GOLD : ChatFormatting.YELLOW))
                .append(Component.literal("|".repeat(10 - lit)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + pct + "%").withStyle(pct >= 100 ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (pct >= 100) {
            bar.append(Component.literal("  at the red line: vent it with ").withStyle(ChatFormatting.GRAY))
                    .append(Component.keybind("key.khazuk.vent").withStyle(ChatFormatting.WHITE));
        }
        rider.sendOverlayMessage(bar);
    }

    // ------------------------------------------------------------------ broken: the burst, its god thrown clear, the wreck's item

    /** It breaks: the burst clip plays out over vanilla's 20 ticks of death; the boiler blows at {@value #BLOWS_AT}, the seat throws its god at {@value #RIDER_OUT_AT}. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!burst && isDeadOrDying() && level() instanceof ServerLevel) {   // only a death that happened (a mod may cancel it)
            burst = true;
            blow = null;
            setWorking(false);
            setRooted(false);
            triggerAnim("attack", "burst");
        }
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level() instanceof ServerLevel sl && burst) {
            if (deathTime >= BLOWS_AT && !blown) {
                blown = true;
                blowUp(sl);
            }
            if (deathTime >= RIDER_OUT_AT && !thrown) {
                thrown = true;
                throwRider(sl);
            }
        }
    }

    /** The boiler goes off, as the Slayer mech's does: the foes round it are hurt and set burning; never its god or his hold. */
    private void blowUp(ServerLevel level) {
        Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.7);   // the boiler is at the rider's back
        double bx = getX() + back.x, by = getY() + 1.9, bz = getZ() + back.z;
        level.sendParticles(ParticleTypes.EXPLOSION, bx, by, bz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, bx + back.x, by + 0.4, bz + back.z, 12, 0.3, 0.4, 0.3, 0.04);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 40, 1.2, 0.6, 1.2, 0.06);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.4f, 0.8f);
        Player rider = getFirstPassenger() instanceof Player p ? p : null;
        DamageSource src = level.damageSources().explosion(this, rider != null ? rider : this);
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(BURST_REACH),
                m -> m.isAlive() && !(m instanceof Dawi) && Muster.foe(m) && !spares(rider, m) && m.distanceToSqr(this) <= (BURST_REACH + 1) * (BURST_REACH + 1))) {
            m.hurtServer(level, src, BURST);
            m.igniteForSeconds(4);
        }
    }

    /** The seat's ram is at its top: its god is thrown up and back out of the wreck, unhurt, and lands unhurt. */
    private void throwRider(ServerLevel level) {
        if (!(getFirstPassenger() instanceof LivingEntity rider)) {
            return;
        }
        ejecting = true;
        rider.stopRiding();
        ejecting = false;
        if (rider.getVehicle() == this) {
            return;                                              // something holds him in it: he gets down when the wreck is gone
        }
        Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-0.5);
        rider.setDeltaMovement(back.x, 0.9, back.z);
        rider.hurtMarked = true;
        rider.resetFallDistance();
        if (rider instanceof Player p) {
            THROWN.put(p.getUUID(), level.getGameTime() + NO_FALL_TICKS);
        }
    }

    /** Whether {@code e} was thrown clear of a burst engine a moment ago: that fall doesn't hurt (EngineEvents). */
    public static boolean landsUnhurt(LivingEntity e) {
        if (THROWN.isEmpty() || !(e instanceof Player)) {
            return false;
        }
        Long until = THROWN.get(e.getUUID());
        if (until == null) {
            return false;
        }
        long left = until - e.level().getGameTime();
        if (left < 0 || left > NO_FALL_TICKS) {                  // over, or another world's clock: never a grace longer than its window
            THROWN.remove(e.getUUID());
            return false;
        }
        return true;
    }

    /**
     * The wreck leaves its item where it fell, for its god to set down again (to his pack, or his feet, only if it fell out of the world).
     * Here, not in die(): this runs once, only for a death that happened, whatever the loot gamerule (the Throne's way).
     */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        super.dropAllDeathLoot(level, source);
        ItemStack item = new ItemStack(KRegistry.ANCESTOR_ENGINE_ITEM.get());
        ServerPlayer god = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (getY() > level.getMinY()) {
            ItemEntity e = spawnAtLocation(level, item);
            if (e != null) {
                e.setUnlimitedLifetime();                        // waits for its god, however long he's away
                e.setPickUpDelay(30);                            // till the burst has played out
            }
        } else if (god != null && !god.getInventory().add(item)) {
            god.drop(item, false);
        }
        if (god != null) {
            god.sendSystemMessage(Component.literal("Your Ancestor Engine's boiler has burst. Its wreck can be set down again.").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.putFloat("heat", heat());
        out.putBoolean("burst", burst);                          // saved mid-death, it still bursts after the reload (the mech's review of a20044f)
        out.putBoolean("blown", blown);
        out.putBoolean("thrown", thrown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        setHeat(in.getFloatOr("heat", 0f));
        burst = in.getBooleanOr("burst", false);
        blown = in.getBooleanOr("blown", false);
        thrown = in.getBooleanOr("thrown", false);
    }

    // ------------------------------------------------------------------ its clips

    /** Ground speed from the last tick's move, blocks a second (the same on the rider's client and everyone else's). */
    double groundSpeed() {
        double dx = getX() - xo, dz = getZ() - zo;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    /**
     * Main: unmanned it rests (kneeling down first if its rider just left); manned it heaves up, then idles, walks or runs at its clips'
     * own pace, digs, or past the red line shudders standing or striding. The blows and the burst are triggered on the attack controller,
     * after main, and overwrite only the bones they key.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AncestorEngine>("main", 4, test -> {
            AncestorEngine e = test.animatable();
            if (e.isDeadOrDying()) {
                return PlayState.CONTINUE;                       // the burst plays over whatever it was doing
            }
            test.setControllerSpeed(1f);
            if (!e.isVehicle()) {
                return test.setAndContinue(e.everManned ? SET_DOWN : REST);
            }
            if (e.rising > 0) {
                return test.setAndContinue(RISE);
            }
            double v = e.groundSpeed();
            if (v > 0.15) {
                if (e.overheated()) {
                    test.setControllerSpeed((float) Mth.clamp(v / WALK_CLIP, 0.5, 2.5));
                    return test.setAndContinue(OVERHEAT_WALK);
                }
                boolean run = v > RUN_ABOVE;
                test.setControllerSpeed((float) Mth.clamp(v / (run ? RUN_CLIP : WALK_CLIP), 0.5, 2.5));
                return test.setAndContinue(run ? RUN : WALK);
            }
            if (e.working()) {
                return test.setAndContinue(WORK);
            }
            return test.setAndContinue(e.overheated() ? OVERHEAT : IDLE);
        }));
        controllers.add(new AnimationController<AncestorEngine>("attack", 0, test -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("crush", CRUSH_CLIP).triggerableAnim("stomp", STOMP_CLIP)
                .triggerableAnim("vent", VENT_CLIP).triggerableAnim("burst", BURST_CLIP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
