package claude.khazuk.god;

import claude.khazuk.KRegistry;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Grungni's Hammer (D7, AncestorPowers): a forge-hammer of gold light comes down out of the sky where the god points. A ring of gold sparks
 * gathers where it'll land; on tick {@value #IMPACT_TICK} it strikes (its clip's head meets the ground at 1.00 s), and everything within
 * {@value #RADIUS} blocks that isn't the god's (AncestorPowers#godsOwn) takes {@value #DAMAGE} at the middle, down to {@value #EDGE_DAMAGE}
 * at the edge, is set alight for {@value #BURN_SECONDS} s (forge-hot) and flung away and up. A rune-priest's blessing ({@link #blessing})
 * multiplies the blow and widens the reach. On tick {@value #TAP_TICK} the smith's second tap (the detail session's clip, 1.42 s) lands
 * lighter: {@value #TAP_SHARE} of the blow over {@value #TAP_REACH} of the reach, a lighter fling, and the anvil rings again. No block is
 * broken. It lifts away and is gone at tick {@value #LIFE}. Never saved.
 */
public class GrungniHammer extends Entity implements GeoEntity {

    public static final int IMPACT_TICK = 20, TAP_TICK = 28, LIFE = 50, BURN_SECONDS = 4;
    public static final float TAP_SHARE = 0.5f;
    public static final double TAP_REACH = 0.6;
    public static final double RADIUS = 5.0;
    public static final float DAMAGE = 40f, EDGE_DAMAGE = 20f;
    public static final double FLING = 1.0, FLING_UP = 0.8;

    static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("animation.grungnis_hammer.strike");

    private static final EntityDataAccessor<Long> BORN = SynchedEntityData.defineId(GrungniHammer.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID caster;
    private float blessing = 1f;
    /** What it hit when it struck, and with its second tap, for the tests. */
    public int hitForTests = -1, tapHitForTests = -1;

    public GrungniHammer(EntityType<? extends GrungniHammer> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Grungni's Hammer over {@code at} (on the ground), its haft the way {@code yaw} faces, {@code blessing} on its blow and reach. */
    public static GrungniHammer of(ServerLevel level, ServerPlayer god, Vec3 at, float yaw, float blessing) {
        GrungniHammer h = new GrungniHammer(KRegistry.GRUNGNIS_HAMMER_STRIKE.get(), level);
        h.snapTo(at.x, at.y, at.z, yaw, 0f);
        h.caster = god.getUUID();
        h.blessing = blessing;
        h.entityData.set(BORN, level.getGameTime());
        return h;
    }

    public float blessing() {
        return blessing;
    }

    /** Its reach: wider with a blessing (a Runesmith's 6 blocks, a Runelord's 7). */
    public double radius() {
        return RADIUS + 4 * (blessing - 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(BORN, 0L);
    }

    /** How far into its strike it is, in seconds (by the level's clock, from when it was called down). */
    public double strikeTime(float partialTick) {
        return Math.max(0, (level().getGameTime() - entityData.get(BORN) + partialTick) / 20.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        if (tickCount < IMPACT_TICK && (tickCount & 1) == 0) {
            double r = radius() * (1.0 - tickCount / (double) IMPACT_TICK) + 1.0;    // the ring of light closing in on where it falls
            for (int i = 0; i < 16; i++) {                       // full-bright, so it reads at dusk and at night (the detail session's note)
                double a = i * Math.PI / 8 - tickCount * 0.2;
                sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (tickCount == IMPACT_TICK) {
            strike(sl);
        }
        if (tickCount == TAP_TICK) {
            tap(sl);
        }
        if (tickCount >= LIFE) {
            discard();
        }
    }

    /** It strikes: everything not the god's in reach hurt, set alight and flung; sparks and a great ring of the anvil. Returns how many it hit. */
    public int strike(ServerLevel sl) {
        int hit = blow(sl, radius(), 1f, true);
        hitForTests = hit;
        return hit;
    }

    /** The smith's second tap: lighter and closer in, the anvil ringing again. Returns how many it hit. */
    public int tap(ServerLevel sl) {
        int hit = blow(sl, radius() * TAP_REACH, TAP_SHARE, false);
        tapHitForTests = hit;
        return hit;
    }

    private int blow(ServerLevel sl, double reach, float share, boolean first) {
        ServerPlayer god = caster == null ? null : sl.getServer().getPlayerList().getPlayer(caster);
        DamageSource src = sl.damageSources().source(KRegistry.HAMMER_DAMAGE, this, god);
        int hit = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(reach, 4, reach), LivingEntity::isAlive)) {
            if (caster != null && AncestorPowers.godsOwn(e, caster, god)) {
                continue;
            }
            double dx = e.getX() - getX(), dz = e.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > reach + e.getBbWidth() / 2) {
                continue;
            }
            float dmg = (float) (DAMAGE - (DAMAGE - EDGE_DAMAGE) * Math.min(1.0, d / reach)) * blessing * share;
            e.invulnerableTime = 0;                              // the second tap comes 8 ticks after the first: past its blow's i-frames
            if (!e.hurtServer(sl, src, dmg)) {
                continue;
            }
            hit++;
            e.igniteForSeconds(BURN_SECONDS);
            double k = Math.max(0, 1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 out = d < 0.1 ? Vec3.ZERO : new Vec3(dx / d, 0, dz / d);
            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(FLING * k * share)).add(0, FLING_UP * k * share, 0));
            e.hurtMarked = true;
        }
        BlockPos ground = BlockPos.containing(getX(), getY() - 0.5, getZ());
        BlockState under = sl.getBlockState(ground);
        if (!under.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY() + 0.3, getZ(), first ? 180 : 60, reach * 0.5, 0.3, reach * 0.5, 0.4);
        }
        if (!first) {                                            // the tap: sparks and the anvil's ring, no blast
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 0, Math.cos(a), 0.2, Math.sin(a), 0.4);
            }
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.5f, 0.75f);
            return hit;
        }
        for (int i = 0; i < 32; i++) {                           // the forge's sparks thrown out in a ring
            double a = i * Math.PI / 16;
            sl.sendParticles(ParticleTypes.LAVA, getX() + Math.cos(a) * reach * 0.5, getY() + 0.4, getZ() + Math.sin(a) * reach * 0.5, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.6, getZ(), 0, Math.cos(a), 0.15, Math.sin(a), 0.6);
        }
        sl.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2C744), getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 5, reach * 0.3, 0.2, reach * 0.3, 0);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4f, 0.5f);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3f, 0.7f);
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        caster = in.read("caster", UUIDUtil.CODEC).orElse(null);
        blessing = in.getFloatOr("blessing", 1f);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.storeNullable("caster", UUIDUtil.CODEC, caster);
        out.putFloat("blessing", blessing);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GrungniHammer>("strike", 0, test -> {
            test.setAndContinue(STRIKE);
            double want = Math.min(test.animatable().strikeTime(test.renderState().getPartialTick()), LIFE / 20.0);
            if (Math.abs(test.controller().getCurrentAnimationTime() - want) > 0.1) {
                test.controller().setAnimationTime(want);      // first seen late (or a hitch): back on the level's clock
            }
            return com.geckolib.animation.object.PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
