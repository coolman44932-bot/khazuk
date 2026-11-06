package claude.khazuk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * An Ironbreaker (0.14, TWW1's next units after the Longbeards, with the Hammerers): the hold's tunnel fighters, gromril head to toe under a
 * masked helm, axe and a gromril shield, who hold the deeps against what comes up out of the dark.
 * <ul>
 * <li><b>Gromril:</b> the best armour of any Dwarf: more armour and toughness than a Longbeard's, unmoved by blows, and a gromril shield that
 * turns more of a blow ({@value #SHIELD_MELEE_GROMRIL} through it) and stops more shots ({@value #SHIELD_SHOT_GROMRIL}) than a Warrior's.</li>
 * <li><b>Cinderblast bombs:</b> every {@value #BOMB_TICKS} ticks, at a foe {@value #BOMB_MIN} to {@value #BOMB_MAX} blocks off that he can
 * see, he throws a bomb ({@link Cinderblast}): it bursts where it lands, burning and blasting the hold's foes round it and never the hold's
 * own. Fire bites him less ({@value #FIRE_TAKEN} of it): he fights in his own bombs' smoke.</li>
 * <li><b>The deeps' guard:</b> a hold with a Mine raises {@value claude.khazuk.hold.Ranks#IRONBREAKER_EACH} of them (hold/Ranks), posted on
 * guard in the Mine; their god can order them elsewhere as any Dwarf.</li>
 * </ul>
 * His stats are the stronger of the army book's and TWW3's (the user's rule): Ironbreakers have the best armour of any Dwarf infantry.
 */
public class Ironbreaker extends Warrior {

    public static final double MAX_HEALTH = 54, DAMAGE = 13, ARMOR = 22, TOUGHNESS = 8, KNOCKBACK_RESISTANCE = 1.0;
    public static final float SHIELD_MELEE_GROMRIL = 0.45f, SHIELD_SHOT_GROMRIL = 0.75f, FIRE_TAKEN = 0.5f;
    public static final int BOMB_TICKS = 140;
    public static final double BOMB_MIN = 3, BOMB_MAX = 10;

    /** Bombs thrown, for the tests. */
    public int bombsForTests;
    private int bombIn = 40;

    public Ironbreaker(EntityType<? extends Ironbreaker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25)          // a step slower under the gromril, never pushed back
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    @Override
    protected float shieldMelee() {
        return SHIELD_MELEE_GROMRIL;
    }

    @Override
    protected float shieldShot() {
        return SHIELD_SHOT_GROMRIL;
    }

    @Override
    protected float taken(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= FIRE_TAKEN;
        }
        return super.taken(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && isAlive() && !isBurrowing() && --bombIn <= 0) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive() && canBomb(t)) {
                throwBomb(sl, t);
                bombIn = BOMB_TICKS;
            } else {
                bombIn = 10;                                   // look again in half a second
            }
        }
    }

    /** Whether {@code t} is where a bomb can reach: not on top of him, not too far, and in his sight. */
    public boolean canBomb(LivingEntity t) {
        double d = distanceTo(t);
        return d >= BOMB_MIN && d <= BOMB_MAX && hasLineOfSight(t);
    }

    /** A Cinderblast bomb lobbed at {@code t}. */
    public Cinderblast throwBomb(ServerLevel level, LivingEntity t) {
        Cinderblast b = new Cinderblast(level, this);
        double dx = t.getX() - getX(), dz = t.getZ() - getZ(), dy = t.getY(0.3) - b.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        b.shoot(dx, dy + flat * 0.2, dz, 1.0f, 3f);
        level.addFreshEntity(b);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, getSoundSource(), 0.8f, 0.8f + getRandom().nextFloat() * 0.2f);
        swing(net.minecraft.world.InteractionHand.OFF_HAND);
        triggerAnim("attack", "attack");
        bombsForTests++;
        return b;
    }
}
