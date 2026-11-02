package claude.khazuk;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Dwarf Warrior: the hold's soldier, hand weapon and shield, short, broad and immovable; he rises with the hold's muster when foes come
 * near. On the test race's figure until D2 models him and measures his floor.
 */
public class Warrior extends Dawi {

    public Warrior(EntityType<? extends Warrior> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }
}
