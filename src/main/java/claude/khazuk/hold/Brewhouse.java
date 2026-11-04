package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * D3, gold and ale (docs/plan.md section 3): what a hold brews and hoards, kept on its Shrine.
 * <ul>
 * <li><b>Malt:</b> wheat its god brings to the Shrine ({@value #MALT_PER_WHEAT} a sheaf, {@value #MALT_PER_HAY} a hay bale).</li>
 * <li><b>Ale:</b> with a Brewery dug, every {@value #BREW_SECONDS} s a malt is brewed into {@value #ALE_PER_MALT} ale, up to
 * {@value #ALE_CAP} in the barrels. The hold drinks one ale every {@value #DRINK_SECONDS} s for every {@value #DRINKERS} Dwarfs up.
 * While there is ale, the hold's Dwarfs hit harder, walk faster and work faster (Dawi#aleUp). Buffs only: a dry hold is just a hold.</li>
 * <li><b>The hoard:</b> with a Treasury dug, every gold ore its digs cut pays one more, and every {@value #INTEREST_SECONDS} s the hoard
 * grows by one for every {@value #INTEREST_PER} it holds (at most {@value #INTEREST_MAX}): gold draws gold.</li>
 * </ul>
 */
public final class Brewhouse {

    public static final int MALT_PER_WHEAT = 1, MALT_PER_HAY = 9;
    public static final int BREW_SECONDS = 5, ALE_PER_MALT = 2, ALE_CAP = 256;
    public static final int DRINK_SECONDS = 60, DRINKERS = 4;
    public static final int INTEREST_SECONDS = 300, INTEREST_PER = 50, INTEREST_MAX = 10;
    public static final int MALT_CAP = 1024;

    private int malt, ale, clock;
    private boolean dirty;

    public int malt() {
        return malt;
    }

    public int ale() {
        return ale;
    }

    /** Whether the hold has ale in its barrels: its Dwarfs drink, and fight and work the better for it. */
    public boolean aleUp() {
        return ale > 0;
    }

    /** Wheat (or hay) brought to the Shrine: {@code amount} malt, as much as the store holds. How much it took. */
    public int addMalt(int amount) {
        int took = Math.max(0, Math.min(amount, MALT_CAP - malt));
        malt += took;
        dirty |= took > 0;
        return took;
    }

    /** Tests and a settled grudge (D4): ale straight into the barrels. */
    public void addAle(int amount) {
        ale = Math.max(0, Math.min(ALE_CAP, ale + amount));
        dirty = true;
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** The Shrine's second: brew, drink, and the hoard's interest. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        clock++;
        boolean brewery = hold.has(HoldRooms.BREWERY);
        if (brewery && clock % BREW_SECONDS == 0 && malt > 0 && ale < ALE_CAP) {
            boolean wasDry = ale == 0;
            malt--;
            ale = Math.min(ALE_CAP, ale + ALE_PER_MALT);
            dirty = true;
            if (wasDry) {
                hold.tellOwner(hold.settlementName() + ": the first barrel is tapped. The Dawi drink, and work and fight the better for it.",
                        ChatFormatting.GOLD);
            }
        }
        if (clock % DRINK_SECONDS == 0 && ale > 0) {
            int drunk = (hold.peopleCount() + DRINKERS - 1) / DRINKERS;
            if (drunk > 0) {
                ale = Math.max(0, ale - drunk);
                dirty = true;
                if (ale == 0) {
                    hold.tellOwner(hold.settlementName() + ": the barrels are dry. " + (brewery ? "Bring wheat to the Shrine for the brewery."
                            : "Dig a Brewery and bring wheat to the Shrine."), ChatFormatting.YELLOW);
                }
            }
        }
        if (clock % INTEREST_SECONDS == 0 && hold.has(HoldRooms.TREASURY)) {
            int grows = Math.min(INTEREST_MAX, hold.currency() / INTEREST_PER);
            if (grows > 0) {
                hold.addCurrency(grows, "The Treasury's hoard grows");
            }
        }
    }

    /** Tests: {@code seconds} of the Shrine's seconds at once. */
    public void secondsForTests(ServerLevel level, ShrineBlockEntity hold, int seconds) {
        for (int i = 0; i < seconds; i++) {
            second(level, hold);
        }
    }

    public void save(CompoundTag tag) {
        tag.putInt("malt", malt);
        tag.putInt("ale", ale);
        tag.putInt("brew_clock", clock);
    }

    public void load(CompoundTag tag) {
        malt = Math.max(0, Math.min(MALT_CAP, tag.getIntOr("malt", 0)));
        ale = Math.max(0, Math.min(ALE_CAP, tag.getIntOr("ale", 0)));
        clock = tag.getIntOr("brew_clock", 0);
    }
}
