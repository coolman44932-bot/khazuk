package claude.khazuk;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Dwarf Miner: the hold's worker. Digs its halls and its mines, crews its rooms and hauls, and swings his two-handed pick at what comes
 * for the hold (D2, the look sheet handoffs/look_dwarf_miner_warrior_2026-11-03.md: the candle-crowned helm, the ginger wedge of a beard to
 * the belt, the heavy pick, the lantern on his belt, mail over a dark tunic and a blue kilt).
 * <p>TWW's Miners carry great weapons, armour-piercing, and are as well armoured as Warriors; the army book gives their picks great
 * weapons' strength. So: a heavy blow, and his pick bites through armour, {@value #PICK_BITE} more damage for every point of the
 * target's armour (at most {@value #PICK_BITE_MAX}).
 * <p>The floor (D2's tests): one Miner beats three zombies.
 */
public class Miner extends Dawi {

    public static final double MAX_HEALTH = 32, DAMAGE = 10, ARMOR = 10, TOUGHNESS = 2, KNOCKBACK_RESISTANCE = 0.6;
    public static final double PICK_BITE = 0.5, PICK_BITE_MAX = 8;
    static final Identifier PICK = Identifier.fromNamespaceAndPath(Khazuk.MODID, "pick_bites_armour");

    public Miner(EntityType<? extends Miner> type, Level level) {
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

    /** What his pick adds against {@code target}'s armour. */
    public static double pickBite(Entity target) {
        return target instanceof LivingEntity l ? Math.min(PICK_BITE_MAX, l.getArmorValue() * PICK_BITE) : 0;
    }

    /** A blow: the pick's bite added for that blow alone, and gone again after, whatever the blow did. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        AttributeInstance a = getAttribute(Attributes.ATTACK_DAMAGE);
        double bite = pickBite(target);
        if (a == null || bite <= 0) {
            return super.doHurtTarget(level, target);
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(PICK, bite, AttributeModifier.Operation.ADD_VALUE));
        try {
            return super.doHurtTarget(level, target);
        } finally {
            a.removeModifier(PICK);
        }
    }

    @Override
    public boolean isWorker() {
        return true;
    }
}
