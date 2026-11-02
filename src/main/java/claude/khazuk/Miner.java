package claude.khazuk;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Dwarf Miner: the hold's worker. Digs its halls and its mines, crews its rooms and hauls, and swings his pick at what comes for the
 * hold. On the test race's figure until D2 models him and gives him his stats.
 */
public class Miner extends Dawi {

    public Miner(EntityType<? extends Miner> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public boolean isWorker() {
        return true;
    }
}
