package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HallSeal;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Stores;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.work.Bins;
import claude.settlementcore.work.HoardWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The hold's stores (the user, 2026-11-09: "is there a storage room", then "yeah but also make the buildings passively add ore and
 * whatever they produce even if they dont mine it out or whatever"): the Storeroom is the engine's Hoard in five bays; what the digs
 * cut and what the halls make goes into it (hold/Stores), the pile at the Shrine until one is dug; food in its barrels feeds the hold.
 */
@GameTestHolder(Khazuk.MODID)
public class StoresGameTests {

    private static final BlockPos SHRINE = new BlockPos(44, 3, 36);
    /** A sealed door standing in the open field, the Storeroom dug east through it (as HallSealGameTests' descent). */
    private static final BlockPos DOOR = new BlockPos(30, 20, 36);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(SHRINE)) instanceof ShrineBlockEntity hold)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(SHRINE));
        }
        return hold;
    }

    /** The Storeroom ordered through a sealed door in the field, rock where it goes, begun and dug whole: the hold's building. */
    private static PlanPlacement storeroom(GameTestHelper h, ShrineBlockEntity hold) {
        ServerLevel level = h.getLevel();
        BlockPos door = h.absolutePos(DOOR);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean seal = Math.abs(dz) <= 1 && dy <= 2;
                level.setBlock(door.offset(0, dy, dz), seal ? KRegistry.HALL_SEAL.get().defaultBlockState().setValue(HallSeal.FACING, Direction.EAST)
                        : Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        Rooms.Room room = HoldRooms.room("dawi_storeroom");
        h.assertTrue(room != null && room.function() == Rooms.Function.HOARD, "no Storeroom among the hold's rooms, or it is not the engine's Hoard");
        PlanPlacement pl = AncestorPlan.aimedAt(level, room, door.above(), 0, Direction.WEST);
        for (BlockPos p : BlockPos.betweenClosed(pl.box().minX(), pl.box().minY(), pl.box().minZ(), pl.box().maxX(), pl.box().maxY(), pl.box().maxZ())) {
            if (p.getX() > door.getX() && level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            }
        }
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.dig(hold, level, pl, true, c -> told.add(c.getString())), "the Storeroom through the seal was refused: " + told);
        hold.works().digs().forEach(d -> d.job().finishAll(level));
        hold.works().second(level, 303);
        h.assertTrue(hold.works().digs().isEmpty() && hold.has(Rooms.Function.HOARD), "the Storeroom wasn't dug: digs " + hold.works().digs().size());
        return pl;
    }

    private static List<BlockPos> bay(GameTestHelper h, PlanPlacement pl, String name) {
        return Bins.named(h.getLevel(), pl.box(), name);
    }

    private static int in(GameTestHelper h, List<BlockPos> barrels, Item item) {
        return Bins.count(h.getLevel(), barrels, s -> s.is(item));
    }

    /** A hall of this id given to the hold, as a finished dig gives it. */
    private static void give(GameTestHelper h, ShrineBlockEntity hold, String room, int x) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, 1));
        hold.addBuilding(new Building(room, new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 2, at.getY() + 2, at.getZ() + 2)), false);
    }

    /**
     * The Storeroom dug through a sealed door is the hold's Hoard: its barrels stand in five named bays (18 each along the walls, 27 Spoils
     * across the end), and goods put into the stores are sorted into their own.
     */
    @GameTest(template = "field", timeoutTicks = 200, batch = "khazuk")
    public static void theStoreroomIsTheHoldsHoardInFiveBays(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ShrineBlockEntity hold = hold(h);
        PlanPlacement pl = storeroom(h, hold);
        h.assertTrue(hold.works().all(HoardWork.class).size() == 1, "the hold has " + hold.works().all(HoardWork.class).size() + " hoards at work, not 1");
        String[] bays = {"Metals & Gems", "Stone", "Food", "Timber", "Spoils"};
        int[] want = {18, 18, 18, 18, 27};
        for (int i = 0; i < bays.length; i++) {
            h.assertTrue(bay(h, pl, bays[i]).size() == want[i], "the " + bays[i] + " bay has " + bay(h, pl, bays[i]).size() + " barrels, not " + want[i]);
        }
        Item[] goods = {Items.RAW_IRON, Items.COBBLED_DEEPSLATE, Items.BREAD, Items.OAK_LOG, Items.STRING};
        for (int i = 0; i < goods.length; i++) {
            h.assertTrue(Stores.put(level, hold, new ItemStack(goods[i], 10), "the test"), "the stores refused " + goods[i]);
            h.assertTrue(in(h, bay(h, pl, bays[i]), goods[i]) == 10, goods[i] + " is not in the " + bays[i] + " bay: " + in(h, bay(h, pl, bays[i]), goods[i])
                    + " of 10 there, the pile at the Shrine " + hold.spoils());
        }
        h.assertTrue(hold.spoils().isEmpty(), "with a Storeroom dug, goods went onto the pile at the Shrine: " + hold.spoils());
        h.succeed();
    }

    /**
     * What a dig cuts: an ore's drops go to the pile at the Shrine while there is no Storeroom (gold ore is the hold's gold, as ever, and
     * the rock is rubble), the haul carries the pile to the Storeroom once one is dug, and from then on the rock too is kept, in the
     * Stone bay.
     */
    @GameTest(template = "field", timeoutTicks = 200, batch = "khazuk")
    public static void whatTheDigsCutGoesToTheStores(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ShrineBlockEntity hold = hold(h);
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
        int g0 = hold.currency();
        hold.takeSpoils(level, at, Blocks.IRON_ORE.defaultBlockState(), null);
        hold.takeSpoils(level, at, Blocks.GOLD_ORE.defaultBlockState(), null);
        hold.takeSpoils(level, at, Blocks.DEEPSLATE.defaultBlockState(), null);
        hold.stores().second(level, hold);
        int iron = 0, other = 0;
        for (ItemStack s : hold.spoils()) {
            if (s.is(Items.RAW_IRON)) {
                iron += s.getCount();
            } else {
                other += s.getCount();
            }
        }
        h.assertTrue(iron == 1 && other == 0, "an iron ore, a gold ore and a block of deepslate cut with no Storeroom left the pile at " + hold.spoils()
                + ", not one raw iron");
        h.assertTrue(hold.currency() - g0 == ShrineBlockEntity.GOLD_PER_ORE, "the gold ore paid " + (hold.currency() - g0) + " gold");
        PlanPlacement pl = storeroom(h, hold);
        hold.works().second(level, 305);                         // a haul's second: the pile to the Storeroom
        h.assertTrue(hold.spoils().isEmpty() && in(h, bay(h, pl, "Metals & Gems"), Items.RAW_IRON) == 1, "the haul didn't carry the pile to the "
                + "Storeroom: the pile " + hold.spoils() + ", raw iron in Metals & Gems " + in(h, bay(h, pl, "Metals & Gems"), Items.RAW_IRON));
        for (int i = 0; i < 5; i++) {
            hold.stores().second(level, hold);                   // the stores look at the Stone bay (every 5 s): it has room
        }
        for (int i = 0; i < 13; i++) {
            hold.takeSpoils(level, at, (i < 10 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState(), null);
        }
        hold.takeSpoils(level, at, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), null);
        hold.stores().second(level, hold);
        List<BlockPos> stone = bay(h, pl, "Stone");
        h.assertTrue(in(h, stone, Items.COBBLED_DEEPSLATE) == 10 && in(h, stone, Items.COBBLESTONE) == 3, "10 deepslate and 3 stone cut left the "
                + "Stone bay with " + in(h, stone, Items.COBBLED_DEEPSLATE) + " cobbled deepslate and " + in(h, stone, Items.COBBLESTONE) + " cobblestone");
        h.assertTrue(in(h, bay(h, pl, "Metals & Gems"), Items.DIAMOND) == 1, "the diamond ore's diamond is not in Metals & Gems");
        h.succeed();
    }

    /**
     * What the halls make with nobody at work in them: a round gives a Brewery's ale, a Treasury's gold, an Engineers' Guild's black
     * powder, a Mine's ore and cobble, and a Runic Forge's ingots out of the raw ore in the Storeroom; and a round comes every
     * {@value Stores#PRODUCE_SECONDS} s of the Shrine's seconds.
     */
    @GameTest(template = "field", timeoutTicks = 200, batch = "khazuk")
    public static void theHallsMakeTheirGoodsWithNobodyAtWork(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ShrineBlockEntity hold = hold(h);
        PlanPlacement pl = storeroom(h, hold);
        give(h, hold, "dawi_mine", 2);
        give(h, hold, "dawi_brewery", 8);
        give(h, hold, "dawi_treasury", 14);
        give(h, hold, "dawi_engineers_guild", 20);
        give(h, hold, "dawi_runic_forge", 26);
        h.assertTrue(Stores.put(level, hold, new ItemStack(Items.RAW_IRON, 20), "the test"), "the stores refused the raw iron");
        List<BlockPos> all = hold.works().all(HoardWork.class).get(0).barrels(level);
        int g0 = hold.currency(), ale0 = hold.brew().ale();
        hold.stores().produce(level, hold);
        h.assertTrue(hold.brew().ale() == ale0 + Stores.BREWERY_ALE, "the Brewery made " + (hold.brew().ale() - ale0) + " ale, not " + Stores.BREWERY_ALE);
        h.assertTrue(hold.currency() - g0 >= Stores.TREASURY_GOLD, "the Treasury made " + (hold.currency() - g0) + " gold");
        h.assertTrue(in(h, all, Items.GUNPOWDER) == Stores.GUILD_POWDER, "the Engineers' Guild made " + in(h, all, Items.GUNPOWDER) + " black powder, not "
                + Stores.GUILD_POWDER);
        h.assertTrue(in(h, all, Items.IRON_INGOT) == Stores.FORGE_SMELTS && in(h, all, Items.RAW_IRON) >= 20 - Stores.FORGE_SMELTS,
                "the Runic Forge smelted " + in(h, all, Items.IRON_INGOT) + " ingots, not " + Stores.FORGE_SMELTS + "; raw iron left " + in(h, all, Items.RAW_IRON));
        List<BlockPos> stone = bay(h, pl, "Stone");
        h.assertTrue(in(h, stone, Items.COBBLED_DEEPSLATE) + in(h, stone, Items.COBBLESTONE) == Stores.MINE_STONE, "the Mine's cobble in the Stone bay: "
                + (in(h, stone, Items.COBBLED_DEEPSLATE) + in(h, stone, Items.COBBLESTONE)) + ", not " + Stores.MINE_STONE);
        int made = 0;
        for (ItemStack s : Bins.stacks(level, all)) {
            made += s.getCount();
        }
        int known = 20 + Stores.GUILD_POWDER + Stores.MINE_STONE;   // the raw iron (some of it ingots now), the powder, the cobble
        h.assertTrue(made - known + (hold.currency() - g0 - Stores.TREASURY_GOLD) > 0, "the Mine's " + Stores.MINE_ROLLS + " rolls made nothing: "
                + (made - known) + " more in the barrels, " + (hold.currency() - g0 - Stores.TREASURY_GOLD) + " gold beyond the Treasury's");
        int ale1 = hold.brew().ale();
        for (int i = 0; i < Stores.PRODUCE_SECONDS; i++) {
            hold.stores().second(level, hold);
        }
        h.assertTrue(hold.brew().ale() >= ale1 + Stores.BREWERY_ALE, Stores.PRODUCE_SECONDS + " of the Shrine's seconds made no round: ale " + ale1
                + " then " + hold.brew().ale());
        h.succeed();
    }

    /** Food in the Storeroom's barrels is the hold's food (the engine's larder counts its Hoard): bread set in the Food bay feeds it. */
    @GameTest(template = "field", timeoutTicks = 200, batch = "khazuk")
    public static void foodInTheStoreroomFeedsTheHold(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ShrineBlockEntity hold = hold(h);
        PlanPlacement pl = storeroom(h, hold);
        hold.larderStores().changed();
        int f0 = hold.food();
        h.assertTrue(Bins.insert(level, bay(h, pl, "Food"), new ItemStack(Items.BREAD, 32)).isEmpty(), "the Food bay took no bread");
        hold.larderStores().changed();
        h.assertTrue(hold.food() > f0, "32 bread in the Food bay and the hold's food is " + hold.food() + ", as before (" + f0 + ")");
        h.succeed();
    }
}
