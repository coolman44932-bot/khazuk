package claude.khazuk.hold;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
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
 * Quarreller or Thunderer (whichever the hold has fewer of) out of {@value #COST} food, until it keeps {@value #EACH} of each. They stand
 * their posts like the hold's guards (the engine's muster brings them to the walls when foes come), and shoot (Marksman). Counted among the
 * loaded ones round the Shrine; not on the engine's roll, as the Runesmith isn't.
 */
public final class Ranks {

    public static final int RAISE_SECONDS = 30, EACH = 4, COST = 8;

    private int clock;

    /** How many of {@code kind} the hold keeps up round its Shrine now. */
    public static int count(ServerLevel level, ShrineBlockEntity hold, Class<? extends Marksman> kind) {
        return level.getEntitiesOfClass(kind, new AABB(hold.getBlockPos()).inflate(ShrineBlockEntity.RADIUS + 16),
                m -> m.isAlive() && m.settlement() == hold).size();
    }

    /** The Shrine's second: one more of the fewer kind, every {@value #RAISE_SECONDS} s, while the Barracks stands and the food lasts. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        if (++clock % RAISE_SECONDS != 0 || !hold.has(HoldRooms.BARRACKS)) {
            return;
        }
        raise(level, hold);
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
