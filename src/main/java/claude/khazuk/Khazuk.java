package claude.khazuk;

import claude.settlementcore.settlement.Currencies;
import claude.settlementcore.settlement.Settlements;
import claude.settlementcore.test.GameTests;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Khazuk! (2026-11-01): the Dwarfs on Settlement Core, the third race on the engine and the second on 26.1. The player is the Ancestor
 * Gods (Grungni, Valaya and Grimnir); the plan is docs/plan.md. D0 is the engine's test race copied as the mod:
 * <ul>
 * <li>an {@link ShrineBlock}, the Ancestor Shrine, whose block entity ({@link ShrineBlockEntity}) is the engine's settlement, the hold;
 * placed, it is its placer's;</li>
 * <li>two units on one base ({@link Dawi}): the {@link Miner}, who crews and digs, and the {@link Warrior}, the Dwarf Warrior, who
 * fights; the hold makes them from food and brings them up from the deeps beside the Shrine;</li>
 * <li>gold its money, mined from gold ore;</li>
 * <li>a crowd drawn by the engine's renderer (client/), and its GameTests (test/).</li>
 * </ul>
 * Their own models (D2), the dug hold (D1), ale, the Book of Grudges and runes come after.
 */
@Mod(Khazuk.MODID)
public final class Khazuk {
    public static final String MODID = "khazuk";
    public static final Logger LOG = LogUtils.getLogger();

    public Khazuk(IEventBus modBus, ModContainer container) {
        KRegistry.register(modBus);
        Currencies.register(() -> Items.GOLD_INGOT, () -> Items.GOLD_BLOCK, null, () -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE);
        Settlements.ownedBy(ShrineBlockEntity::ownedBy);
        claude.khazuk.hold.HoldRooms.register();
        GameTests.register(container, modBus);
    }
}
