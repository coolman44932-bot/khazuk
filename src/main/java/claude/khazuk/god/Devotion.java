package claude.khazuk.god;

import claude.khazuk.Runelord;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * D7: the hold's devotion, what the Ancestor Gods' powers are paid from (AncestorPowers). The Dawi pray at their Shrine: every
 * {@value #PRAY_SECONDS} s each Dwarf up adds 1 (half again with ale in the barrels, toasts to the Ancestors), and each Runesmith or
 * Runelord of the hold {@value #SMITH_PRAYER} more. A settled grudge adds {@value #GRUDGE_SETTLED} (the Ancestors are pleased). It holds up
 * to {@value #MAX}. The first time the hold's devotion reaches a power's price, the Ancestors hand their god that power's token (into his
 * pack if he's on, else at the Shrine, never despawning); a second hold hands him another set. Saved with the Shrine.
 */
public final class Devotion {

    public static final int MAX = 200, PRAY_SECONDS = 60, SMITH_PRAYER = 3, GRUDGE_SETTLED = 30;
    public static final float ALE_PRAYER = 1.5f;

    private float devotion;
    private int clock;
    /** The powers whose tokens this hold has handed its god, a bit each (AncestorPowers.Power#ordinal). */
    private int gifted;
    private boolean dirty;

    public int devotion() {
        return (int) devotion;
    }

    /** {@code n} more devotion, up to {@value #MAX}. */
    public void add(float n) {
        float was = devotion;
        devotion = Math.max(0, Math.min(MAX, devotion + n));
        dirty |= devotion != was;
    }

    /** Pays {@code n} out of the devotion if it's there. Whether it was. */
    public boolean spend(int n) {
        if (devotion < n) {
            return false;
        }
        devotion -= n;
        dirty = true;
        return true;
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** The Shrine's second: the prayers once a minute, and the Ancestors' gifts when they're earned. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        if (++clock % PRAY_SECONDS == 0) {
            pray(level, hold);
        }
        gifts(level, hold);
    }

    /** A minute's prayers (the clock's, and the tests'): how much they added. Any at all, and the statue plays its prayer. */
    public float pray(ServerLevel level, ShrineBlockEntity hold) {
        float n = hold.peopleCount();
        if (hold.brew().aleUp()) {
            n *= ALE_PRAYER;
        }
        n += SMITH_PRAYER * level.getEntitiesOfClass(Runesmith.class, new AABB(hold.getBlockPos()).inflate(ShrineBlockEntity.RADIUS + 16),
                s -> s.isAlive() && s.settlement() == hold).size();
        add(n);
        if (n > 0) {
            hold.prayed();                                       // the statue's band swells (ShrineBlockEntity's rite)
        }
        return n;
    }

    /** The first time the devotion reaches a power's price, its token for the god. */
    void gifts(ServerLevel level, ShrineBlockEntity hold) {
        if (hold.owner() == null) {
            return;
        }
        for (AncestorPowers.Power p : AncestorPowers.Power.values()) {
            int bit = 1 << p.ordinal();
            if ((gifted & bit) != 0 || devotion < p.cost) {
                continue;
            }
            gifted |= bit;
            dirty = true;
            ItemStack token = p.token();
            ServerPlayer god = level.getServer().getPlayerList().getPlayer(hold.owner());
            boolean handed = god != null && god.level() == level && god.getInventory().add(token);
            if (!handed && !token.isEmpty()) {
                ItemEntity drop = new ItemEntity(level, hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1.2, hold.getBlockPos().getZ() + 0.5, token);
                drop.setUnlimitedLifetime();
                drop.setPickUpDelay(10);
                level.addFreshEntity(drop);
            }
            hold.tellOwner(hold.settlementName() + ": the hold's prayers reach " + p.god + ". " + p.title + " is yours"
                    + (handed ? " (in your pack): " : " (at the Shrine): ") + p.how + " It costs " + p.cost + " devotion.", ChatFormatting.GOLD);
        }
    }

    /** Whether the token of {@code p} has been handed over yet, for the tests. */
    public boolean gifted(AncestorPowers.Power p) {
        return (gifted & 1 << p.ordinal()) != 0;
    }

    public void save(CompoundTag tag) {
        tag.putFloat("devotion", devotion);
        tag.putInt("pray_clock", clock);
        tag.putInt("devotion_gifts", gifted);
    }

    public void load(CompoundTag tag) {
        devotion = tag.getFloatOr("devotion", 0f);
        clock = tag.getIntOr("pray_clock", 0);
        gifted = tag.getIntOr("devotion_gifts", 0);
    }

    /** Whether {@code s} is a Runelord (the stronger blessing), for AncestorPowers. */
    static boolean lord(Runesmith s) {
        return s instanceof Runelord;
    }
}
