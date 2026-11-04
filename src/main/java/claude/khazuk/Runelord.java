package claude.khazuk;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The Runelord (D5b): the hold's Runesmith raised to a lord once he has struck {@value claude.khazuk.hold.Runes#TO_RUNELORD} runes (hold/Runes).
 * He works the rune anvil as the Runesmith did, and his rising brings the hold the Anvil of Doom (AnvilOfDoom), whose runes of power come
 * twice as fast while he stands by it. A lord of the army book: the stronger of its and TWW3's stats per stat (the user's rule), the
 * hold's mightiest Dwarf. On a fast model (bbsrc/khazuk_build.js, 'runelord') until the detail session gives him a showpiece's.
 * <p>The floor (D5b's tests): one Runelord beats an iron golem.
 */
public class Runelord extends Runesmith {

    public static final double MAX_HEALTH = 90, DAMAGE = 16, ARMOR = 18, TOUGHNESS = 6, KNOCKBACK_RESISTANCE = 1.0;

    public Runelord(EntityType<? extends Runelord> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }
}
