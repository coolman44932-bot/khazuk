package claude.khazuk.hold;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Longbeard;
import claude.khazuk.Warrior;
import claude.khazuk.Marksman;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * D6: the hold's missile troops, raised by its Barracks. With a Barracks dug, every {@value #RAISE_SECONDS} s the Shrine brings up one more
 * Quarreller or Thunderer (whichever the hold has fewer of) out of {@value #COST} food, until it keeps {@value #EACH} of each. They keep near
 * the Shrine as the hold's people do and shoot what comes within their range (Marksman), over the hold's walls when it's walled. Counted
 * among the loaded ones round the Shrine; not on the engine's roll (nor its muster), as the Runesmith isn't.
 */
public final class Ranks {

    public static final int RAISE_SECONDS = 30, EACH = 4, COST = 8;
    /** 0.11: with a Great Hall, a Longbeard every {@value #LONGBEARD_SECONDS} s for {@value #LONGBEARD_COST} food, {@value #LONGBEARD_EACH} of each loadout. */
    public static final int LONGBEARD_SECONDS = 60, LONGBEARD_EACH = 2, LONGBEARD_COST = 12;

    private int clock;

    /** How many Longbeards of {@code type} the hold keeps up round its Shrine now. */
    public static int count(ServerLevel level, ShrineBlockEntity hold, EntityType<? extends Longbeard> type) {
        return level.getEntitiesOfClass(Longbeard.class, new AABB(hold.getBlockPos()).inflate(ShrineBlockEntity.RADIUS + 16),
                m -> m.isAlive() && m.settlement() == hold && m.getType() == type).size();
    }

    /** How many of {@code kind} the hold keeps up round its Shrine now. */
    public static int count(ServerLevel level, ShrineBlockEntity hold, Class<? extends Marksman> kind) {
        return level.getEntitiesOfClass(kind, new AABB(hold.getBlockPos()).inflate(ShrineBlockEntity.RADIUS + 16),
                m -> m.isAlive() && m.settlement() == hold).size();
    }

    /** The Shrine's second: one more of the fewer kind, every {@value #RAISE_SECONDS} s, while the Barracks stands and the food lasts. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        ++clock;
        if (clock % RAISE_SECONDS == 0 && hold.has(HoldRooms.BARRACKS)) {
            raise(level, hold);
        }
        if (clock % LONGBEARD_SECONDS == 0 && hold.has(HoldRooms.GREAT_HALL)) {
            raiseLongbeard(level, hold);
        }
    }

    /** One more Longbeard of the fewer loadout now (the clock's minute, and the tests), while the hold keeps fewer than its share. Whether one came up. */
    public boolean raiseLongbeard(ServerLevel level, ShrineBlockEntity hold) {
        int shield = count(level, hold, KRegistry.LONGBEARD.get()), great = count(level, hold, KRegistry.LONGBEARD_GREAT.get());
        if (shield >= LONGBEARD_EACH && great >= LONGBEARD_EACH || hold.food() < LONGBEARD_COST) {
            return false;
        }
        EntityType<? extends Longbeard> kind = shield <= great ? KRegistry.LONGBEARD.get() : KRegistry.LONGBEARD_GREAT.get();
        Longbeard d = kind.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(LONGBEARD_COST, "Longbeards raised");
        d.bindTo(hold);
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (shield + great == 0) {
            hold.tellOwner(hold.settlementName() + ": the Great Hall's old ones come up, grumbling: the first Longbeard. Where he stands, the Dawi round "
                    + "him hold firm whatever their wounds.", ChatFormatting.GOLD);
        }
        return true;
    }

    /** A Warrior's beard has grown long in the hold's wars: he is a Longbeard now, where he stands (off the roll: the brood makes another Warrior). */
    public static Longbeard promote(ServerLevel level, ShrineBlockEntity hold, Warrior w) {
        Longbeard d = KRegistry.LONGBEARD.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return null;
        }
        d.bindTo(hold);
        d.snapTo(w.getX(), w.getY(), w.getZ(), w.getYRot(), w.getXRot());
        d.setYHeadRot(w.getYHeadRot());
        if (w.hasCustomName()) {
            d.setCustomName(w.getCustomName());
        }
        d.setHealth(d.getMaxHealth() * Math.max(0.1f, w.getHealth() / w.getMaxHealth()));
        d.copyKills(w);
        hold.takeFromRoll(w.getUUID());
        w.discard();
        level.addFreshEntity(d);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, d.getX(), d.getY() + 1, d.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        hold.tellOwner(hold.settlementName() + ": a Warrior has felled " + w.kills() + " foes in the hold's wars, and his beard has grown long: "
                + "he is a Longbeard now.", ChatFormatting.GOLD);
        return d;
    }

    /** One more of the fewer kind now (the clock's own second, and the tests). Whether one came up. */
    public boolean raise(ServerLevel level, ShrineBlockEntity hold) {
        int q = count(level, hold, Quarreller.class), t = count(level, hold, Thunderer.class);
        if (q >= EACH && t >= EACH || hold.food() < COST) {
            return false;
        }
        EntityType<? extends Dawi> kind = q <= t ? KRegistry.QUARRELLER.get() : KRegistry.THUNDERER.get();
        Dawi d = kind.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(COST, (q <= t ? "Quarrellers" : "Thunderers") + " raised");
        d.bindTo(hold);
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (q + t == 0) {
            hold.tellOwner(hold.settlementName() + ": the Barracks sends up its first " + (q <= t ? "Quarreller, crossbow on his shoulder"
                    : "Thunderer, handgun primed") + ". The hold's walls have guns now.", ChatFormatting.GOLD);
        }
        return true;
    }
}
