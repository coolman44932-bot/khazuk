package claude.khazuk.test;

import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.HoldLedger;
import claude.settlementcore.ledger.LedgerMenu;
import claude.settlementcore.ledger.SettlementLedger;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.work.Bins;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The hold's ledger (the user, 2026-11-10: "i need something to access everything i have in my chests"; hold/HoldLedger): the
 * Storeroom's lectern is the ledger for the hold's god and nobody else, the ledger counts what stands in the Storeroom's barrels and
 * in a chest its god set down in a hall, and it opens for him.
 */
@GameTestHolder(Khazuk.MODID)
public class HoldLedgerGameTests {

    @GameTest(template = "field", timeoutTicks = 200, batch = "khazuk")
    public static void theStoreroomsLecternIsTheHoldsLedgerForItsGodAlone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ShrineBlockEntity hold = StoresGameTests.hold(h);
        ServerPlayer god = h.makeMockServerPlayerInLevel(), stranger = h.makeMockServerPlayerInLevel();
        hold.claim(god, "Karak Tally");
        PlanPlacement pl = StoresGameTests.storeroom(h, hold);
        // a few ticks on: a Shrine just set down is among the level's loaded holds from its first tick (SettlementSpawns), and the lectern
        // and the book both look there
        h.startSequence().thenIdle(3).thenExecute(() -> theLedger(h, level, hold, god, stranger, pl)).thenSucceed();
    }

    private static void theLedger(GameTestHelper h, ServerLevel level, ShrineBlockEntity hold, ServerPlayer god, ServerPlayer stranger, PlanPlacement pl) {
        BlockPos lectern = pl.world(new BlockPos(12, 1, 7));
        h.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the Storeroom has no lectern at its ledger's place: " + level.getBlockState(lectern));
        h.assertTrue(HoldLedger.storeroomOf(level, god, lectern) == hold, "the Storeroom's lectern is not the ledger of its hold's god");
        h.assertTrue(HoldLedger.storeroomOf(level, stranger, lectern) == null, "the Storeroom's lectern is a stranger's ledger too");
        h.assertTrue(HoldLedger.storeroomOf(level, god, lectern.above()) == null, "the air over the lectern is a ledger");
        BlockPos outside = h.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(outside, Blocks.LECTERN.defaultBlockState(), 2);
        h.assertTrue(HoldLedger.storeroomOf(level, god, outside) == null, "a lectern out in the field is the hold's ledger");
        // what the ledger counts: the Storeroom's barrels, and a chest its god set down in the hall himself
        h.assertTrue(Bins.insert(level, Bins.named(level, pl.box(), "Metals & Gems"), new ItemStack(Items.RAW_IRON, 40)).isEmpty(), "no room for the iron");
        BlockPos chest = pl.world(new BlockPos(6, 1, 7));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        Bins.at(level, chest).setItem(0, new ItemStack(Items.DIAMOND, 5));
        long iron = 0, diamonds = 0;
        for (SettlementLedger.Line line : SettlementLedger.tally(level, hold).lines()) {
            iron += line.item().is(Items.RAW_IRON) ? line.count() : 0;
            diamonds += line.item().is(Items.DIAMOND) ? line.count() : 0;
        }
        h.assertTrue(iron == 40 && diamonds == 5, "the ledger counts " + iron + " raw iron (40 in a barrel) and " + diamonds + " diamonds (5 in the god's own chest)");
        h.assertTrue(!LedgerMenu.open(stranger, hold), "the ledger opened for a stranger");
        h.assertTrue(LedgerMenu.open(god, hold) && god.containerMenu instanceof LedgerMenu, "the ledger didn't open for the hold's god: he has "
                + god.containerMenu.getClass().getSimpleName());
        god.closeContainer();
    }
}
