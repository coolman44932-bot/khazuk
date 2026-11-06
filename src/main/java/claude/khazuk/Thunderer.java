package claude.khazuk;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Thunderer (D6): a Dwarf handgunner, the hold's heavy shot (the look sheet: a copper helm, a near-black beard, a long flared handgun, a
 * ring of powder pouches). The handgun's armour-piercing shot, the army book's and TWW's stronger (the user's rule): {@value #SHOT} a shot,
 * and {@value #PIERCE} more for every point of the mark's armour (at most {@value #PIERCE_MAX}), every {@value #RELOAD} ticks out to
 * {@value #RANGE} blocks; a flash and smoke at the muzzle. Floor (D6's tests): one Thunderer beats three zombies.
 */
public class Thunderer extends Marksman {

    public static final double MAX_HEALTH = 32, DAMAGE = 7, ARMOR = 10, TOUGHNESS = 2, RANGE = 22;
    public static final float SHOT = 13f, PIERCE = 0.5f, PIERCE_MAX = 8f;
    public static final int RELOAD = 50;

    public Thunderer(EntityType<? extends Thunderer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.FOLLOW_RANGE, RANGE + 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public double range() {
        return RANGE;
    }

    @Override
    public int reload() {
        return RELOAD;
    }

    @Override
    public float shotDamage() {
        return SHOT;
    }

    /**
     * The ball's damage on {@code mark}: the more armour it wears, the harder it bites. Named apart from Marksman#shotOn, which it shadowed
     * (the hold's ale, runes and grudges never reached a Thunderer's shot: found adding the Engineer Slayers).
     */
    public float ballOn(LivingEntity mark) {
        return SHOT + Math.min(PIERCE_MAX, mark.getArmorValue() * PIERCE);
    }

    @Override
    public void shoot(ServerLevel level, LivingEntity mark) {
        var at = getEyePosition().add(getLookAngle().scale(0.9));
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.02);   // the muzzle flash
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.15, 0.1, 0.15, 0.02);
        super.shoot(level, mark);
    }

    @Override
    public float shotDamageOn(LivingEntity mark) {
        return ballOn(mark);
    }

    @Override
    protected ParticleOptions trail() {
        return ParticleTypes.SMOKE;
    }

    @Override
    protected SoundEvent report() {
        return SoundEvents.GENERIC_EXPLODE.value();
    }
}
