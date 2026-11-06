package claude.khazuk;

import claude.settlementcore.defence.Muster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Slayers (0.14.2, TWW1's next units after the Ironbreakers and Hammerers): Dwarfs who have sworn to die in battle to wipe out a
 * shame, bare-chested and tattooed under tall crests dyed orange, an axe in each hand or a great axe, and no armour.
 * <ul>
 * <li><b>Slayer:</b> a blow on a large foe (a monster's bulk, or {@value #LARGE_HEALTH} health or more: an iron golem, a ravager, a
 * Warden, a spider, an enderman) lands harder by the tier's share: {@value #TROLL_LARGE} for a Troll Slayer, {@value #GIANT_LARGE} for a
 * Giant Slayer, {@value #DRAGON_LARGE} for the Dragon Slayer. They seek the largest foe near first.</li>
 * <li><b>The death-wish:</b> the nearer to death, the harder: up to {@value #DEATHWISH} more on every blow at the last of his health.
 * Unbreakable: always stubborn.</li>
 * <li><b>Fleet:</b> the quickest Dwarfs. Grimnir's Fury burns twice as long on them (god/AncestorPowers#fury).</li>
 * <li><b>The oath's road:</b> the Shrine of Grimnir raises Troll Slayers (hold/Ranks); a Troll Slayer who slays a large foe is a Giant
 * Slayer; a Giant Slayer who slays a true monster ({@link #monster}: a Warden, a Wither, a dragon, a ravager, an elder guardian, or
 * {@value #MONSTER_HEALTH} health or more) becomes the hold's Dragon Slayer, its one Slayer hero.</li>
 * </ul>
 * Stats: the stronger of the army book's and TWW3's (the user's rule): the hardest blows of the hold's foot, unarmoured.
 */
public class Slayer extends Warrior {

    public enum Tier { TROLL, GIANT, DRAGON }

    public static final double TROLL_HEALTH = 44, TROLL_DAMAGE = 17, GIANT_HEALTH = 56, GIANT_DAMAGE = 21, DRAGON_HEALTH = 90, DRAGON_DAMAGE = 28;
    public static final double TROLL_LARGE = 0.6, GIANT_LARGE = 1.0, DRAGON_LARGE = 1.5, DEATHWISH = 0.5;
    public static final float LARGE_HEALTH = 40, MONSTER_HEALTH = 100;
    static final Identifier SLAYING = Identifier.fromNamespaceAndPath(Khazuk.MODID, "slayer");

    /** What his slaying earned him (set when the foe falls, acted on at his next step: never inside the kill). */
    private @Nullable Tier earned;
    /** Blows that landed with the Slayer's bonus on a large foe, for the tests. */
    public int largeBlowsForTests;

    public Slayer(EntityType<? extends Slayer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes(Tier t) {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, t == Tier.TROLL ? TROLL_HEALTH : t == Tier.GIANT ? GIANT_HEALTH : DRAGON_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, t == Tier.DRAGON ? 0.31 : 0.30)
                .add(Attributes.ATTACK_DAMAGE, t == Tier.TROLL ? TROLL_DAMAGE : t == Tier.GIANT ? GIANT_DAMAGE : DRAGON_DAMAGE)
                .add(Attributes.ARMOR, t == Tier.TROLL ? 2 : t == Tier.GIANT ? 4 : 8)            // ink and bangles, no mail
                .add(Attributes.ARMOR_TOUGHNESS, t == Tier.DRAGON ? 4 : 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, t == Tier.DRAGON ? 1.0 : 0.7)
                .add(Attributes.FOLLOW_RANGE, 28.0);                                             // he looks far for something worth dying to
    }

    public Tier tier() {
        EntityType<?> t = getType();
        return t == KRegistry.DRAGON_SLAYER.get() ? Tier.DRAGON : t == KRegistry.GIANT_SLAYER.get() ? Tier.GIANT : Tier.TROLL;
    }

    public double largeShare() {
        return switch (tier()) {
            case TROLL -> TROLL_LARGE;
            case GIANT -> GIANT_LARGE;
            case DRAGON -> DRAGON_LARGE;
        };
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // the largest foe near first: a Slayer seeks his doom (above the hold's plain foe-finding, beside retaliation)
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (m, level) -> m instanceof Mob mob && Muster.foe(mob) && large(mob)));
    }

    @Override
    protected boolean hasShield() {
        return false;
    }

    @Override
    public boolean isStubborn() {
        return true;
    }

    /** Whether {@code e} is a large foe to a Slayer: a monster's bulk, or {@value #LARGE_HEALTH} health or more. */
    public static boolean large(LivingEntity e) {
        return e.getBbWidth() >= 1.2f || e.getBbHeight() >= 2.6f || e.getMaxHealth() >= LARGE_HEALTH;
    }

    /** Whether {@code e} is a true monster, the slaying that makes a Dragon Slayer. */
    public static boolean monster(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.monster.warden.Warden || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || e instanceof net.minecraft.world.entity.monster.Ravager
                || e instanceof net.minecraft.world.entity.monster.ElderGuardian || e.getMaxHealth() >= MONSTER_HEALTH;
    }

    /** What his blow at {@code target} gets on top: the Slayer's share on a large foe, and the death-wish. A multiplier. */
    public double slaying(Entity target) {
        double m = 1 + DEATHWISH * (1 - getHealth() / getMaxHealth());
        if (target instanceof LivingEntity l && large(l)) {
            m *= 1 + largeShare();
        }
        return m;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        var a = getAttribute(Attributes.ATTACK_DAMAGE);
        double m = slaying(target);
        if (a == null || m <= 1.0001) {
            return super.doHurtTarget(level, target);
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(SLAYING, m - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        try {
            boolean hit = super.doHurtTarget(level, target);
            if (hit && target instanceof LivingEntity l && large(l)) {
                largeBlowsForTests++;
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            }
            return hit;
        } finally {
            a.removeModifier(SLAYING);
        }
    }

    /** A large foe slain: a Troll Slayer becomes a Giant Slayer; a monster slain makes a Giant Slayer the hold's Dragon Slayer. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (tier() == Tier.TROLL && large(victim)) {
            earned = Tier.GIANT;
        } else if (tier() == Tier.GIANT && monster(victim)) {
            earned = Tier.DRAGON;
        }
        return super.killedEntity(level, victim, source);
    }

    /** What his slaying has earned, waiting for his next step (the tests). */
    public @Nullable Tier earned() {
        return earned;
    }

    /** The tests: he has slain what earns {@code t}. */
    public void earnForTests(Tier t) {
        earned = t;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (earned != null && level() instanceof ServerLevel sl && isAlive() && !isBurrowing()) {
            ShrineBlockEntity h = settlement();
            if (h != null) {                                     // far from the hold he keeps what he earned till its Shrine is in reach (the review of 8a7fa52)
                Tier t = earned;
                earned = null;
                rise(sl, h, t);
            }
        }
    }

    /** Raised to {@code t} where he stands, his orders, kills and name kept; a Dragon Slayer only if the hold has none. The new one, or null. */
    public @Nullable Slayer rise(ServerLevel level, ShrineBlockEntity hold, Tier t) {
        if (t == Tier.DRAGON && hold.ranks().rolled(KRegistry.DRAGON_SLAYER.get()) > 0) {
            return null;                                         // the hold has its Dragon Slayer: he stays a Giant Slayer, no less
        }
        EntityType<Slayer> to = t == Tier.DRAGON ? KRegistry.DRAGON_SLAYER.get() : KRegistry.GIANT_SLAYER.get();
        Slayer s = to.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (s == null) {
            return null;
        }
        s.bindTo(hold);
        s.snapTo(getX(), getY(), getZ(), getYRot(), getXRot());
        s.setYHeadRot(getYHeadRot());
        if (hasCustomName()) {
            s.setCustomName(getCustomName());
        }
        s.setHealth(s.getMaxHealth() * Math.max(0.25f, getHealth() / getMaxHealth()));
        s.copyKills(this);
        s.takeOrders(this);
        if (claude.khazuk.god.AncestorPowers.furious(this)) {   // Grimnir's Fury goes on burning in him (the review of 8a7fa52)
            claude.khazuk.god.AncestorPowers.rage(s, claude.khazuk.god.AncestorPowers.furyUntil(this));
        }
        hold.ranks().forget(getUUID());
        hold.ranks().enlist(s);                                  // on the roll as what he is now: the Shrine of Grimnir raises another Troll Slayer
        discard();
        level.addFreshEntity(s);
        level.sendParticles(ParticleTypes.FLAME, s.getX(), s.getY() + 1, s.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LAVA, s.getX(), s.getY() + 1, s.getZ(), 8, 0.3, 0.5, 0.3, 0);
        hold.tellOwner(hold.settlementName() + (t == Tier.DRAGON
                ? ": a Giant Slayer has slain a monster and lived. He is the hold's Dragon Slayer now, and he looks for a worse one."
                : ": a Troll Slayer has slain a giant of a foe and lived. He is a Giant Slayer now, and his doom must be greater still."),
                ChatFormatting.GOLD);
        return s;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (earned != null) {
            out.putString("earned", earned.name());
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        String e = in.getStringOr("earned", "");
        try {
            earned = e.isEmpty() ? null : Tier.valueOf(e);
        } catch (IllegalArgumentException x) {
            earned = null;
        }
    }
}
