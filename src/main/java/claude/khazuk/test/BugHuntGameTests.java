package claude.khazuk.test;

import claude.khazuk.Ironbreaker;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlock;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.AnvilOfDoom;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Ranks;
import claude.khazuk.throne.ThroneOfPower;
import claude.settlementcore.food.Fare;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.io.InputStream;

/**
 * 0.14.1, the bug hunt of 2d0b04c: what it found, each held to its fix.
 * <ul>
 * <li>a lifted Shrine carries its whole hold, and set back on its dais the hold stands as it was (it was lost for good);</li>
 * <li>a Dwarf away from his hold (its Shrine out of reach) still knows his god: he takes his orders and never turns on him;</li>
 * <li>the hold keeps its ranks by a roll, not by who is in sight: a death strikes one off, and the roll is saved;</li>
 * <li>the Anvil of Doom keeps its runes' cooling folded, and the Throne its Book's grudge;</li>
 * <li>a shot carries the hold's ale and the grudge it bears, as a blow does;</li>
 * <li>a player's grudge pays its blood-price once a day;</li>
 * <li>no hold template is built of real gold, and the mountain keeps out of a built room.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
public class BugHuntGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aLiftedShrineCarriesItsHoldBackToItsDais(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        heart.addCurrency(77, "the test");
        heart.addToReserve(3, 1);
        int gold = heart.currency(), miners = heart.reserveWorkers();
        // its god lifts it: the block goes, and the Shrine, the hold in it, goes into his pack (a mock god is creative, so the lift is called as the game would)
        BlockPos at = h.absolutePos(HEART);
        var state = h.getLevel().getBlockState(at);
        h.getLevel().removeBlock(at, false);
        KRegistry.SHRINE.get().playerDestroy(h.getLevel(), god, at, state, heart, ItemStack.EMPTY);
        ItemStack stack = ItemStack.EMPTY;
        for (int i = 0; i < god.getInventory().getContainerSize(); i++) {
            if (ShrineBlock.carried(god.getInventory().getItem(i)) != null) {
                stack = god.getInventory().removeItemNoUpdate(i);
                break;
            }
        }
        ShrineBlock.Carried c = ShrineBlock.carried(stack);
        h.assertTrue(c != null && c.pos().equals(at) && c.owner().equals(god.getUUID()) && c.name().equals("Karak Test")
                && c.dim().equals(h.getLevel().dimension().identifier().toString()), "the lifted Shrine in its god's pack carries " + c);
        god.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = h.absolutePos(HEART).below();
        stack.useOn(new UseOnContext(god, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        h.runAfterDelay(2, () -> {
            if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity back)) {
                throw h.assertionException("the Shrine wasn't set back on its dais");
            }
            h.assertTrue(back.currency() == gold && back.reserveWorkers() == miners && "Karak Test".equals(back.settlementName())
                    && god.getUUID().equals(back.owner()), "the hold set back: gold " + back.currency() + " (was " + gold + "), Miners in the deeps "
                    + back.reserveWorkers() + " (was " + miners + "), name " + back.settlementName() + ", owner " + back.owner());
            h.succeed();
        });
    }

    /** Lifted mid-job, the Shrine keeps the god's kit in the hold it carries: nothing is left on the floor (the review of 719e3c3). */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aLiftedShrineKeepsTheKitAtItsForge(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        heart.runes().kit(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        BlockPos at = h.absolutePos(HEART);
        var state = h.getLevel().getBlockState(at);
        heart.lifting();                                         // what ShrineBlock#playerWillDestroy marks for its god (a mock god is creative)
        h.getLevel().removeBlock(at, false);
        KRegistry.SHRINE.get().playerDestroy(h.getLevel(), god, at, state, heart, ItemStack.EMPTY);
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(at).inflate(4),
                e -> e.getItem().is(net.minecraft.world.item.Items.IRON_CHESTPLATE)).isEmpty(), "the kit was dropped at the dais, not carried");
        ItemStack lifted = ItemStack.EMPTY;
        for (int i = 0; i < god.getInventory().getContainerSize(); i++) {
            if (ShrineBlock.carried(god.getInventory().getItem(i)) != null) {
                lifted = god.getInventory().getItem(i);
            }
        }
        var data = lifted.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data != null && data.contains("rune_kit"), "the lifted hold doesn't carry the kit: " + data);
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aDwarfAwayFromHisHoldStillKnowsHisGod(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(5, 1, 5));
        w.bindTo(heart);
        w.setNoAi(true);
        h.setBlock(HEART, Blocks.AIR.defaultBlockState());     // the Shrine out of reach, as when its chunk is unloaded far off
        h.assertTrue(w.settlement() == null, "the Warrior still finds his Shrine");
        h.assertTrue(god.getUUID().equals(w.godId()) && w.god() == god, "the Warrior away from his hold forgot his god: " + w.godId());
        w.setTarget(god);
        h.assertTrue(w.getTarget() == null, "the Warrior away from his hold turned on his god");
        SettlerMob.Mode before = w.getMode();
        var r = w.interact(god, InteractionHand.MAIN_HAND, w.position());
        h.assertTrue(r.consumesAction() && w.getMode() != before, "the god's order wasn't taken away from the hold: " + r + ", " + w.getMode());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theHoldKeepsItsRanksByARollADeathStrikesOneOff(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        heart.addBuilding(new Building("dawi_mine", new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 4, at.getY() + 3, at.getZ() + 4)), false);
        while (heart.ranks().raiseIronbreaker(h.getLevel(), heart)) {
            h.assertTrue(Ranks.count(h.getLevel(), heart, Ironbreaker.class) <= Ranks.IRONBREAKER_EACH, "over the share");
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, Ironbreaker.class) == Ranks.IRONBREAKER_EACH, "raised " + Ranks.count(h.getLevel(), heart, Ironbreaker.class));
        CompoundTag saved = new CompoundTag();
        heart.ranks().save(saved);
        Ranks loaded = new Ranks();
        loaded.load(saved);
        h.assertTrue(loaded.rolled(KRegistry.IRONBREAKER.get()) == Ranks.IRONBREAKER_EACH, "the roll after a save: " + loaded.rolled(KRegistry.IRONBREAKER.get()));
        Ironbreaker one = h.getLevel().getEntitiesOfClass(Ironbreaker.class, new AABB(h.absolutePos(HEART)).inflate(4)).get(0);
        one.kill(h.getLevel());
        h.assertTrue(Ranks.count(h.getLevel(), heart, Ironbreaker.class) == Ranks.IRONBREAKER_EACH - 1, "a death didn't strike him off: "
                + Ranks.count(h.getLevel(), heart, Ironbreaker.class));
        h.assertTrue(heart.ranks().raiseIronbreaker(h.getLevel(), heart) && !heart.ranks().raiseIronbreaker(h.getLevel(), heart),
                "the Mine didn't raise exactly the one who fell");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void theAnvilKeepsItsCoolingAndTheThroneItsGrudgeFolded(GameTestHelper h) {
        AnvilOfDoom a = KRegistry.ANVIL_OF_DOOM.get().create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        AnvilOfDoom b = KRegistry.ANVIL_OF_DOOM.get().create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        a.coolForTests(AnvilOfDoom.Power.WRATH, 1000);
        b.unfold(h.getLevel(), a.folded(h.getLevel()));
        h.assertTrue(b.cooling(AnvilOfDoom.Power.WRATH) > 990, "folded and set down, Wrath cools " + b.cooling(AnvilOfDoom.Power.WRATH) + " (was 1000)");
        ThroneOfPower t = h.spawn(KRegistry.THRONE.get(), new BlockPos(8, 1, 8));
        ThroneOfPower u = h.spawn(KRegistry.THRONE.get(), new BlockPos(8, 1, 16));
        Husk foe = h.spawn(EntityType.HUSK, new BlockPos(4, 1, 4));
        foe.setNoAi(true);
        t.wrong(foe);
        u.unfold(t.folded());
        h.assertTrue(foe.getUUID().equals(u.grudge()), "folded and set down, the Book's grudge is " + u.grudge());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "khazukhostile")
    public static void aShotCarriesTheHoldsAleAndTheGrudgeItBears(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Quarreller q = h.spawn(KRegistry.QUARRELLER.get(), new BlockPos(4, 1, 4));
        q.bindTo(heart);
        q.setNoAi(true);
        Husk mark = h.spawn(EntityType.HUSK, new BlockPos(14, 1, 4));
        mark.setNoAi(true);
        mark.setPersistenceRequired();
        float base = q.shotOn(mark);
        heart.brew().addAle(50);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(q.aleUp(), "no ale in the Quarreller yet"))
                .thenExecute(() -> {
                    float aled = q.shotOn(mark);
                    h.assertTrue(aled > base + 1, "with ale his shot is " + aled + ", without " + base);
                    heart.grudges().wronged(h.getLevel(), heart, mark, "slew a Miner");
                    for (int i = 0; i < 50 && !q.bearsGrudgeAgainst(mark); i++) {
                        heart.grudges().felled(h.getLevel(), heart, mark);   // husks felled till the grudge is settled
                    }
                    h.assertTrue(q.bearsGrudgeAgainst(mark), "the grudge against husks wasn't settled");
                    float borne = q.shotOn(mark);
                    h.assertTrue(borne > aled * 1.2f, "bearing the grudge his shot is " + borne + ", before " + aled);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aPlayersGrudgePaysItsBloodPriceOnceADay(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        var friend = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        int g0 = heart.currency();
        for (int round = 0; round < 2; round++) {
            heart.grudges().wronged(h.getLevel(), heart, friend, "broke the stone of the Great Hall");
            for (int i = 0; i < 10; i++) {
                heart.grudges().felled(h.getLevel(), heart, friend);
            }
            if (round == 0) {
                h.assertTrue(heart.currency() > g0, "the first settling paid nothing");
                g0 = heart.currency();
            } else {
                h.assertTrue(heart.currency() == g0, "the same player's grudge paid twice in a day: " + heart.currency() + " (was " + g0 + ")");
            }
        }
        h.succeed();
    }

    /** Every room the hold digs or raises: its template has no gold block nor raw gold in it, only the gilding. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void noHoldTemplateIsBuiltOfRealGold(GameTestHelper h) throws Exception {
        var jar = ModList.get().getModFileById(Khazuk.MODID).getFile().getContents();
        int gilded = 0;
        for (Rooms.Room r : HoldRooms.ALL) {
            String path = "data/khazuk/structure/room/" + r.id() + ".nbt";
            h.assertTrue(jar.containsFile(path), "no template " + path);
            CompoundTag t;
            try (InputStream in = jar.openFile(path)) {
                t = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            }
            for (var e : t.getListOrEmpty("palette")) {
                String name = e instanceof CompoundTag c ? c.getStringOr("Name", "") : "";
                h.assertTrue(!name.equals("minecraft:gold_block") && !name.equals("minecraft:raw_gold_block"), r.id() + " is built of " + name);
                gilded += name.equals("khazuk:gilded_stone") ? 1 : 0;
            }
        }
        h.assertTrue(gilded > 0, "no template has gilding: the swap didn't happen");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void theMountainKeepsOutOfABuiltRoom(GameTestHelper h) {
        BlockPos air = h.absolutePos(new BlockPos(5, 2, 5));
        h.setBlock(new BlockPos(5, 2, 5), Blocks.AIR.defaultBlockState());
        h.setBlock(new BlockPos(5, 6, 5), Blocks.OAK_PLANKS.defaultBlockState());
        h.assertTrue(HoldCrag.roofed(h.getLevel(), air), "air under planks isn't a room to the mountain");
        h.setBlock(new BlockPos(5, 6, 5), Blocks.STONE.defaultBlockState());
        h.assertTrue(!HoldCrag.roofed(h.getLevel(), air), "air under stone (a cave) is kept out of the mountain");
        // the mountain's own cobble over a cell it retries is its own, not a roof (the review of cb5d663: footings and retries were left hollow)
        h.setBlock(new BlockPos(5, 6, 5), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        h.assertTrue(HoldCrag.roofed(h.getLevel(), air) && !HoldCrag.roofedPastOwnStone(h.getLevel(), air),
                "under the mountain's own mossy cobble: a roof to a fresh cell " + HoldCrag.roofed(h.getLevel(), air) + ", to a retried one "
                        + HoldCrag.roofedPastOwnStone(h.getLevel(), air));
        h.succeed();
    }
}
