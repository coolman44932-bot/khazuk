package claude.khazuk;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Quarreller (D6): a Dwarf crossbowman, the hold's quick shot (the look sheet handoffs/look_dwarf_quarreller_thunderer_2026-11-03.md: a
 * steel helm banded in gold, a chestnut beard, a short steel crossbow at the shoulder, a mail skirt). The army book's crossbow and TWW's
 * long range, the stronger of each (the user's rule): a bolt of {@value #SHOT} every {@value #RELOAD} ticks out to {@value #RANGE} blocks,
 * a hand weapon close in. Floor (D6's tests): one Quarreller beats three zombies.
 */
public class Quarreller extends Marksman {

    public static final double MAX_HEALTH = 32, DAMAGE = 7, ARMOR = 10, TOUGHNESS = 2, RANGE = 24;
    public static final float SHOT = 8f;
    public static final int RELOAD = 30;

    public Quarreller(EntityType<? extends Quarreller> type, Level level) {
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

    @Override
    protected ParticleOptions trail() {
        return ParticleTypes.CRIT;
    }

    @Override
    protected SoundEvent report() {
        return SoundEvents.CROSSBOW_SHOOT;
    }
}
