package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The waking of the Dawi (god/Waking): gold struck on the deep rock cracks it, and the first Dawi step out, hand their god the Shrine and
 * the kit and are named; the Shrine takes root only for a god who woke them; the first one set down brings them up and takes the hold's
 * name. On the arena's floor (the GameTest world is deep: its floor is below y 0).
 */
@GameTestHolder(Khazuk.MODID)
public class WakingGameTests {

    private static final BlockPos ROCK = new BlockPos(12, 1, 12);

    @GameTest(template = "arena", timeoutTicks = 200, batch = "khazuk")
    public static void goldStruckOnTheDeepRockWakesTheDawi(GameTestHelper h) {
        h.setBlock(ROCK, Blocks.DEEPSLATE);
        h.setBlock(ROCK.above(), Blocks.DEEPSLATE);
        BlockPos at = h.absolutePos(ROCK);
        h.assertTrue(at.getY() < Waking.DEEP_Y && Waking.deepRock(h.getLevel().getBlockState(at), at), "the test's rock isn't deep rock at y " + at.getY());
        var god = h.makeMockServerPlayerInLevel();
        god.snapTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);   // beside the rock (the mock comes in at 0 0 0)
        h.assertTrue(!Waking.god(god).woke(), "a new player has woken the Dawi already");
        Waking.begin(h.getLevel(), god, at);
        var far = h.makeMockServerPlayerInLevel();                // one who strikes and walks off: the stone falls quiet
        Waking.begin(h.getLevel(), far, at.east(6));
        h.startSequence()
                .thenExecuteAfter(2, () -> h.assertTrue(!Waking.waking(far.getUUID()), "a waking went on with its god far off"))
                .thenExecuteAfter(Waking.WAKE_TICKS / 2, () -> h.assertTrue(!Waking.god(god).woke(), "the Dawi woke before the rock had cracked"))
                .thenWaitUntil(() -> h.assertTrue(Waking.god(god).woke(), "the Dawi never woke"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(at).isAir() && h.getLevel().getBlockState(at.above()).isAir(), "the rock didn't open");
                    var inv = god.getInventory();
                    h.assertTrue(inv.countItem(KRegistry.SHRINE_ITEM.get()) == 1 && inv.countItem(KRegistry.PLAN_HOLD_GATE.get()) == 1
                            && inv.countItem(KRegistry.PLAN_GREAT_HALL.get()) == 1 && inv.countItem(KRegistry.PLAN_MINE.get()) == 1
                            && inv.countItem(Items.GOLD_INGOT) >= Waking.KIT_GOLD, "the kit isn't all there");
                    Waking.named(god, "  Karak Azgal  ");
                    h.assertTrue(Waking.god(god).holdName().equals("Karak Azgal"), "the hold is called " + Waking.god(god).holdName());
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theShrineTakesRootOnlyForTheDawisGod(GameTestHelper h) {
        // the rule itself (26.1's mock player is always creative, so a survival stranger can't be stood up here)
        h.assertTrue(!claude.khazuk.ShrineBlock.mayRoot(false, Waking.God.NONE), "the Shrine takes root for a survival player who never woke the Dawi");
        h.assertTrue(claude.khazuk.ShrineBlock.mayRoot(false, new Waking.God(true, "Karak Kadrin", 0)), "the Shrine won't take root for the Dawi's god");
        h.assertTrue(claude.khazuk.ShrineBlock.mayRoot(true, Waking.God.NONE), "the Shrine won't take root for a creative player");
        var stranger = h.makeMockServerPlayerInLevel();
        ItemStack shrine = new ItemStack(KRegistry.SHRINE_ITEM.get());
        BlockPos floor = h.absolutePos(ROCK.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        Waking.setGod(stranger, new Waking.God(true, "Karak Kadrin", 0));
        h.assertTrue(KRegistry.SHRINE.get().getStateForPlacement(new BlockPlaceContext(stranger, InteractionHand.MAIN_HAND, shrine, hit)) != null,
                "the Shrine didn't take root for the Dawi's god");
        // set down, the first hold takes the hold's name and the first Dawi
        h.setBlock(ROCK, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), h.absolutePos(ROCK), KRegistry.SHRINE.get().defaultBlockState(), stranger, shrine);
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ROCK));
        h.assertTrue(hold.settlementName().equals("Karak Kadrin") && hold.reserveWorkers() == Waking.FIRST_MINERS
                && hold.reserveSoldiers() == Waking.FIRST_WARRIORS, "the first hold is " + hold.settlementName() + " with " + hold.reserveWorkers()
                + " Miners and " + hold.reserveSoldiers() + " Warriors in its deeps");
        h.assertTrue(Waking.god(stranger).holds() == 1 && Waking.god(stranger).firstUp(), "the god's holds weren't counted, or the first Dawi not");
        // the first Shrine picked up and set down elsewhere: still the hold, by its name, and the first Dawi don't come twice
        h.setBlock(ROCK, Blocks.AIR);
        BlockPos moved = ROCK.east(4);
        h.setBlock(moved, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), h.absolutePos(moved), KRegistry.SHRINE.get().defaultBlockState(), stranger, shrine);
        ShrineBlockEntity again = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(moved));
        h.assertTrue(again.settlementName().equals("Karak Kadrin") && again.reserveWorkers() == 0,
                "the Shrine set down again is " + again.settlementName() + " with " + again.reserveWorkers() + " Miners");
        h.succeed();
    }

    /** The real click: a gold ingot used on the deep rock begins the waking (the event cancelled), and nothing for a god who woke them already. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aGoldIngotUsedOnTheDeepRockBeginsTheWaking(GameTestHelper h) {
        h.setBlock(ROCK, Blocks.DEEPSLATE);
        BlockPos at = h.absolutePos(ROCK);
        var god = h.makeMockServerPlayerInLevel();
        god.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        var e = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(god, InteractionHand.MAIN_HAND, at, hit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(e);
        h.assertTrue(e.isCanceled() && Waking.waking(god.getUUID()), "the gold on the deep rock didn't begin the waking");
        h.assertTrue(god.getMainHandItem().getCount() == 3, "the gold was spent before the Dawi woke");
        var woke = h.makeMockServerPlayerInLevel();
        Waking.setGod(woke, new Waking.God(true, "Karak Eight Peaks", 1, true));
        woke.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
        var e2 = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(woke, InteractionHand.MAIN_HAND, at, hit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(e2);
        h.assertTrue(!e2.isCanceled() && !Waking.waking(woke.getUUID()), "a god who woke the Dawi already began another waking");
        h.succeed();
    }
}
