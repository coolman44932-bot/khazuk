package claude.khazuk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Dwarf Warrior: the hold's soldier, axe in the right hand and a round shield on the left, short, broad and immovable; he rises with
 * the hold's muster when foes come near (D2, the look sheet handoffs/look_dwarf_miner_warrior_2026-11-03.md: TWW's concept art and GW's
 * 2024 kit, a horned helm, a braided ginger beard, a mail shirt over blue, gold knotwork).
 * <p>His stats are the stronger of the army book's and TWW3's (the user's rule): a Dwarf Warrior's armour and melee defence are among the
 * best of any line infantry, his blows an Ork Boy's. So: more armour and toughness than a Boy, near a Boy's health, a heavier blow.
 * <p>His shield: what comes at him from the front ({@value #SHIELD_ARC} degrees either side of where he faces) is turned. A blow does
 * {@value #SHIELD_MELEE} of its damage, and a shot is stopped dead {@value #SHIELD_SHOT} of the time (TWW's shield blocks a third of the
 * missiles from the front; his more). What bypasses a shield (the tag) gets past it.
 * <p>The floors (D2's tests): one Warrior beats three zombies, and a vindicator with an iron axe.
 */
public class Warrior extends Dawi {

    public static final double MAX_HEALTH = 40, DAMAGE = 11, ARMOR = 14, TOUGHNESS = 4, KNOCKBACK_RESISTANCE = 0.8;
    public static final double SHIELD_ARC = 60;
    public static final float SHIELD_MELEE = 0.6f, SHIELD_SHOT = 0.5f;

    /** Shots the shield stopped, and blows it turned, for the tests. */
    public int shotsStoppedForTests, blowsTurnedForTests;

    public Warrior(EntityType<? extends Warrior> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    /**
     * Whether {@code from} is in front of him, within the shield's arc of where his body faces or where he looks (the body lags the head by
     * up to 75 degrees: a foe he has turned his head to is one he brings the shield round to; the review of 08527b2). Level ground: height
     * doesn't count.
     */
    public boolean shieldFaces(Vec3 from) {
        Vec3 to = new Vec3(from.x - getX(), 0, from.z - getZ());
        if (to.lengthSqr() < 1e-6) {
            return false;                                        // from inside him: no side to turn it from
        }
        Vec3 dir = to.normalize();
        double arc = Math.cos(Math.toRadians(SHIELD_ARC));
        return Vec3.directionFromRotation(0, yBodyRot).dot(dir) >= arc || Vec3.directionFromRotation(0, getYHeadRot()).dot(dir) >= arc;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !isBurrowing() && !source.is(DamageTypeTags.BYPASSES_SHIELD) && shieldFaces(from)) {
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                if (getRandom().nextFloat() < SHIELD_SHOT) {
                    shotsStoppedForTests++;
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, getSoundSource(), 1f, 0.9f + getRandom().nextFloat() * 0.2f);
                    return false;                                // the shot glances off
                }
            } else {
                blowsTurnedForTests++;
                amount *= SHIELD_MELEE;
            }
        }
        return super.hurtServer(level, source, amount);
    }
}
