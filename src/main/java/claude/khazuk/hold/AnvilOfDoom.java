package claude.khazuk.hold;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Runelord;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.defence.Muster;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Anvil of Doom (D5b): the hold's oldest relic, brought up when its Runesmith is raised to Runelord (hold/Runes). Its god sets it down from
 * its item and stands on its platform (rides it: it never moves; two Anvil Guard keep it), and strikes it for its three runes of power, each on
 * its own cooldown of {@value #COOLDOWN_SECONDS} s (half that while the hold's Runelord stands within {@value #LORD_NEAR} blocks):
 * <ul>
 * <li><b>Wrath and Ruin:</b> lightning on up to {@value #WRATH_TARGETS} foes (the engine's foes) within {@value #RADIUS} blocks,
 * {@value #WRATH_DAMAGE} each, the god's own blow (a kill settles his grudges);</li>
 * <li><b>Hearth and Home:</b> every Dwarf of his hold within reach, and the god, healed {@value #HEARTH_HEAL} and warded (Resistance) for
 * {@value #BLESS_SECONDS} s;</li>
 * <li><b>Oath and Steel:</b> every Dwarf of his hold within reach, and the god, Strength and Speed for {@value #BLESS_SECONDS} s.</li>
 * </ul>
 * Used from the platform it strikes the chosen rune (or, if that one is still cooling, the next that's ready); used from the ground it is
 * mounted; sneak-used it chooses. Only its god uses it. It takes no harm; its god folds it back into its item by striking it while sneaking.
 */
public class AnvilOfDoom extends PathfinderMob implements GeoEntity {

    public enum Power {
        WRATH("Rune of Wrath and Ruin"), HEARTH("Rune of Hearth and Home"), OATH("Rune of Oath and Steel");

        final String title;

        Power(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public static final int COOLDOWN_SECONDS = 60, BLESS_SECONDS = 30, WRATH_TARGETS = 6;
    public static final double RADIUS = 24, LORD_NEAR = 8;
    public static final float WRATH_DAMAGE = 14f, HEARTH_HEAL = 10f;

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.anvil_of_doom.idle");
    static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("animation.anvil_of_doom.strike");
    /** The detail session's showpiece (handoffs/anvil_of_doom_2026-11-03_done.md): a strike of its own for each rune of power. */
    static final RawAnimation STRIKE_WRATH = RawAnimation.begin().thenPlay("animation.anvil_of_doom.strike_wrath");
    static final RawAnimation STRIKE_HEARTH = RawAnimation.begin().thenPlay("animation.anvil_of_doom.strike_hearth");
    static final RawAnimation STRIKE_OATH = RawAnimation.begin().thenPlay("animation.anvil_of_doom.strike_oath");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID owner;
    private Power chosen = Power.WRATH;
    private final int[] cooling = new int[Power.values().length];   // ticks left on each rune of power

    public AnvilOfDoom(EntityType<? extends AnvilOfDoom> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public Power chosen() {
        return chosen;
    }

    /** Ticks left before {@code p} can be struck again. */
    /** Tests: {@code p} set cooling for {@code ticks}. */
    public void coolForTests(Power p, int ticks) {
        cooling[p.ordinal()] = ticks;
    }

    public int cooling(Power p) {
        return cooling[p.ordinal()];
    }

    @Override
    public boolean canRiderInteract() {
        return true;                                             // its god strikes it from its platform
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** No lead drags it off (the review of 2f93cc2: a Mob is leashable, and a lead is handled before its god's own use). */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity e) {
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // ------------------------------------------------------------------ its god's use

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || owner != null && !owner.equals(player.getUUID())) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (owner == null) {
            owner = player.getUUID();
        }
        if (player.getVehicle() == this) {
            Power p = ready(chosen);
            if (p == null) {
                player.sendSystemMessage(Component.literal("The Anvil's runes are cooling: " + coolingText() + ".").withStyle(ChatFormatting.GRAY));
            } else {
                strike((ServerLevel) level(), (ServerPlayer) player, p);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            chosen = Power.values()[(chosen.ordinal() + 1) % Power.values().length];
            player.sendSystemMessage(Component.literal("The Anvil of Doom: the " + chosen.title + " is chosen. Stand on it and use it to strike.")
                    .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    /** {@code want} if it's ready, else the next ready rune of power, else null. */
    @Nullable Power ready(Power want) {
        for (int i = 0; i < Power.values().length; i++) {
            Power p = Power.values()[(want.ordinal() + i) % Power.values().length];
            if (cooling[p.ordinal()] <= 0) {
                return p;
            }
        }
        return null;
    }

    private String coolingText() {
        StringBuilder sb = new StringBuilder();
        for (Power p : Power.values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(p.title.replace("Rune of ", "")).append(" ").append((cooling[p.ordinal()] + 19) / 20).append(" s");
        }
        return sb.toString();
    }

    /** The hold's Runelord, standing by the Anvil. */
    public boolean lordNear() {
        return !level().getEntitiesOfClass(Runelord.class, getBoundingBox().inflate(LORD_NEAR),
                r -> r.isAlive() && r.settlement() != null && owner != null && owner.equals(r.settlement().owner())).isEmpty();
    }

    /**
     * {@code god} strikes the rune of power {@code p}: how many it reached. One that reaches no one (no foe in range, no Dwarf of his near)
     * doesn't cool: the strike isn't spent (the review of 2f93cc2).
     */
    public int strike(ServerLevel level, @Nullable ServerPlayer god, Power p) {
        AABB reach = getBoundingBox().inflate(RADIUS);
        int reached = 0;
        switch (p) {
            case WRATH -> {
                List<Mob> foes = level.getEntitiesOfClass(Mob.class, reach, m -> m.isAlive() && Muster.foe(m));
                foes.sort(Comparator.comparingDouble(this::distanceToSqr));
                DamageSource src = level.damageSources().indirectMagic(this, god);
                for (Mob m : foes.subList(0, Math.min(WRATH_TARGETS, foes.size()))) {
                    reached++;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.snapTo(m.getX(), m.getY(), m.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    m.hurtServer(level, src, WRATH_DAMAGE);
                    m.igniteForSeconds(3);
                }
            }
            case HEARTH, OATH -> {
                for (LivingEntity e : blessed(level, reach, god)) {
                    reached++;
                    if (p == Power.HEARTH) {
                        e.heal(HEARTH_HEAL);
                        e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BLESS_SECONDS * 20, 0));
                        level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 2, 0.2, 0.1, 0.2, 0);
                    } else {
                        e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BLESS_SECONDS * 20, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.SPEED, BLESS_SECONDS * 20, 0));
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
                    }
                }
            }
        }
        if (reached == 0) {
            if (god != null) {
                god.sendSystemMessage(Component.literal("The " + p.title + " finds no one to reach: it isn't spent.").withStyle(ChatFormatting.GRAY));
            }
            return 0;
        }
        cooling[p.ordinal()] = COOLDOWN_SECONDS * 20 / (lordNear() ? 2 : 1);
        triggerAnim("strike", "strike_" + p.name().toLowerCase(java.util.Locale.ROOT));   // the struck rune's own clip
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4, getZ(), 40, 0.6, 0.4, 0.6, 0.2);
        if (god != null) {
            god.sendSystemMessage(Component.literal("The " + p.title + " rings out from the Anvil of Doom.").withStyle(ChatFormatting.GOLD));
        }
        return reached;
    }

    /** Who a blessing reaches: the god, and every Dwarf of his within reach. */
    List<LivingEntity> blessed(ServerLevel level, AABB reach, @Nullable ServerPlayer god) {
        List<LivingEntity> out = new java.util.ArrayList<>(level.getEntitiesOfClass(Dawi.class, reach,
                d -> d.isAlive() && d.settlement() != null && owner != null && owner.equals(d.settlement().owner())));
        if (god != null && god.getBoundingBox().intersects(reach)) {
            out.add(god);
        }
        return out;
    }

    @Override
    public void tick() {
        super.tick();
        for (int i = 0; i < cooling.length; i++) {
            if (cooling[i] > 0) {
                cooling[i]--;
            }
        }
    }

    // ------------------------------------------------------------------ it takes no harm; its god folds it up

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        return false;                                            // nothing harms it; its god folds it with his own blow (skipAttackInteraction)
    }

    /**
     * Its god's own blow, crouched: folded into his pack, its runes' cooling and its chosen rune carried with it. Only the blow at it: a
     * sweep or a shot that happens to reach it leaves it be (the bug hunt of 2d0b04c: it folded mid-fight).
     */
    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity attacker) {
        if (attacker instanceof Player p && p.isSecondaryUseActive() && p.getUUID().equals(owner) && !isVehicle()) {
            if (level() instanceof ServerLevel level) {
                ItemStack item = folded(level);
                if (!p.getInventory().add(item)) {
                    net.minecraft.world.entity.item.ItemEntity e = spawnAtLocation(level, item);
                    if (e != null) {
                        e.setUnlimitedLifetime();
                    }
                }
                discard();
            }
            return true;
        }
        return super.skipAttackInteraction(attacker);
    }

    static final String CARRIED = "khazuk_anvil";

    /** It as an item, its state carried: the chosen rune, each rune's cooling left, and when it was folded (the cooling runs on folded). */
    public ItemStack folded(ServerLevel level) {
        ItemStack item = new ItemStack(KRegistry.ANVIL_OF_DOOM_ITEM.get());
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("chosen", chosen.name());
        t.putIntArray("cooling", cooling.clone());
        t.putLong("at", level.getGameTime());
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        root.put(CARRIED, t);
        item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
        return item;
    }

    /** Set down from {@code item}: the state it carried, its cooling less the time it spent folded. */
    public void unfold(ServerLevel level, ItemStack item) {
        var cd = item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(CARRIED)) {
            return;
        }
        net.minecraft.nbt.CompoundTag t = cd.copyTag().getCompoundOrEmpty(CARRIED);
        try {
            chosen = Power.valueOf(t.getStringOr("chosen", "WRATH"));
        } catch (IllegalArgumentException e) {
            chosen = Power.WRATH;
        }
        int[] c = t.getIntArray("cooling").orElse(new int[0]);
        long away = Math.max(0, level.getGameTime() - t.getLongOr("at", level.getGameTime()));
        for (int i = 0; i < Math.min(c.length, cooling.length); i++) {
            cooling[i] = (int) Math.max(0, c[i] - away);
        }
    }

    /** Broken past its guard (/kill, the void): back to its god, its state carried, never lost (the Throne's way; the bug hunt of 2d0b04c). */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        super.dropAllDeathLoot(level, source);
        ItemStack item = folded(level);
        ServerPlayer god = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (god != null && god.getInventory().add(item)) {
            god.sendSystemMessage(Component.literal("The Anvil of Doom is gone from where it stood; its Anvil Guard bring it back to you.").withStyle(ChatFormatting.GOLD));
            return;
        }
        if (getY() > level.getMinY()) {
            net.minecraft.world.entity.item.ItemEntity e = spawnAtLocation(level, item);
            if (e != null) {
                e.setUnlimitedLifetime();
            }
        } else if (god != null) {
            god.drop(item, false);
        }
    }

    @Override
    public boolean causeFallDamage(double fall, float mult, DamageSource src) {
        return false;
    }

    /**
     * The god's place: on the ring behind the anvil, measured with a rider in the bridge game by the detail session (handoffs/
     * anvil_of_doom_2026-11-03_done.md: -Z is behind, as the Throne's; 1.22 puts his feet on the ring).
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
        return new Vec3(0, 1.22, -0.5).yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /** He stands at the anvil: sitting, his legs would stick out over it. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    // ------------------------------------------------------------------ saves

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.putString("chosen", chosen.name());
        out.putInt("cool_wrath", cooling[0]);
        out.putInt("cool_hearth", cooling[1]);
        out.putInt("cool_oath", cooling[2]);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        try {
            chosen = Power.valueOf(in.getStringOr("chosen", "WRATH"));
        } catch (IllegalArgumentException e) {
            chosen = Power.WRATH;
        }
        cooling[0] = in.getIntOr("cool_wrath", 0);
        cooling[1] = in.getIntOr("cool_hearth", 0);
        cooling[2] = in.getIntOr("cool_oath", 0);
    }

    // ------------------------------------------------------------------ its look

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnvilOfDoom>("main", 4, test -> test.setAndContinue(IDLE)));
        controllers.add(new AnimationController<AnvilOfDoom>("strike", 2, test -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("strike", STRIKE).triggerableAnim("strike_wrath", STRIKE_WRATH)
                .triggerableAnim("strike_hearth", STRIKE_HEARTH).triggerableAnim("strike_oath", STRIKE_OATH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
