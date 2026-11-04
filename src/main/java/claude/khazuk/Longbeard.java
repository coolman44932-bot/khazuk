package claude.khazuk;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A Longbeard (0.11, TWW1's next unit after the missile troops): the hold's veterans, grey beards to the knees under great-horned helms,
 * gold-trimmed plate over their mail. Two loadouts, as TWW has them: hand weapon and shield (a Warrior's shield, turned blows and stopped
 * shots), or a great axe in both hands that bites through armour ({@value #GREAT_BITE} for every point, at most {@value #GREAT_BITE_MAX}).
 * <ul>
 * <li><b>Old Grumblers:</b> every 2 s a Longbeard steadies the hold's Dawi within {@value #GRUMBLE_REACH} blocks of him for
 * {@value #STEADY_TICKS} ticks: stubborn whatever their wounds (Dawi#isStubborn). He himself is always stubborn (immune to fear).</li>
 * <li><b>Grudgebearers:</b> he strikes harder at the kind of every grudge in the Book, open or settled: the old ones remember them all.</li>
 * <li>Raised by a hold with a Great Hall (hold/Ranks), and a Warrior who has fought long enough (Warrior#VETERAN_KILLS) grows into one.</li>
 * </ul>
 * His stats are the stronger of the army book's and TWW3's (the user's rule): above a Warrior's in health, armour and blow.
 */
public class Longbeard extends Warrior {

    public static final double MAX_HEALTH = 48, DAMAGE = 13, ARMOR = 16, GREAT_DAMAGE = 17, GREAT_ARMOR = 14, TOUGHNESS = 5, KNOCKBACK_RESISTANCE = 0.9;
    public static final double GREAT_BITE = 0.4, GREAT_BITE_MAX = 6, GRUMBLE_REACH = 8;
    public static final int GRUMBLE_TICKS = 40, STEADY_TICKS = 50;
    static final Identifier BITE = Identifier.fromNamespaceAndPath(Khazuk.MODID, "great_axe_bites_armour");

    /** Dawi he has steadied, for the tests. */
    public int steadiedForTests;

    public Longbeard(EntityType<? extends Longbeard> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes(boolean great) {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, great ? GREAT_DAMAGE : DAMAGE)
                .add(Attributes.ARMOR, great ? GREAT_ARMOR : ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    /** Whether he carries the great axe (and no shield). */
    public boolean great() {
        return getType() == KRegistry.LONGBEARD_GREAT.get();
    }

    @Override
    protected boolean hasShield() {
        return !great();
    }

    /** Immune to fear: he holds whatever his wounds. */
    @Override
    public boolean isStubborn() {
        return true;
    }

    /** The old ones remember every grudge in the Book, open or settled. */
    @Override
    protected boolean bearsGrudge(ShrineBlockEntity h, Entity target) {
        return h.grudges().bears(target) || h.grudges().written(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && tickCount % GRUMBLE_TICKS == 7 && isAlive() && !isBurrowing()) {
            grumble(sl);
        }
    }

    /** Old Grumblers: the hold's Dawi round him steadied. How many. */
    public int grumble(ServerLevel level) {
        ShrineBlockEntity h = settlement();
        if (h == null) {
            return 0;
        }
        long until = level.getGameTime() + STEADY_TICKS;
        int n = 0;
        for (Dawi d : level.getEntitiesOfClass(Dawi.class, new AABB(blockPosition()).inflate(GRUMBLE_REACH),
                d -> d != this && d.isAlive() && d.settlement() == h && d.distanceToSqr(this) <= GRUMBLE_REACH * GRUMBLE_REACH)) {
            d.steady(until);
            n++;
        }
        steadiedForTests = n;
        return n;
    }

    /** The great axe's bite through armour, for a blow at {@code target}. */
    public double greatBite(Entity target) {
        return great() && target instanceof LivingEntity l ? Math.min(GREAT_BITE_MAX, l.getArmorValue() * GREAT_BITE) : 0;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        var a = getAttribute(Attributes.ATTACK_DAMAGE);
        double bite = greatBite(target);
        if (a == null || bite <= 0) {
            return super.doHurtTarget(level, target);
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(BITE, bite, AttributeModifier.Operation.ADD_VALUE));
        try {
            return super.doHurtTarget(level, target);
        } finally {
            a.removeModifier(BITE);
        }
    }
}
