package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The mod's data files hold together (the review of 2ff5d8f: D0's pickaxe tag and loot table named the test race's khazuk:heart, which
 * would have dropped the whole mineable/pickaxe tag and lost every broken Shrine, and no GameTest read them): the Shrine is a pickaxe's,
 * drops itself, and every item a survival player needs has its recipe.
 */
@GameTestHolder(Khazuk.MODID)
public class KhazukDataGameTests {

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theShrineIsMinedDropsItselfAndEveryItemIsCraftable(GameTestHelper h) {
        BlockState shrine = KRegistry.SHRINE.get().defaultBlockState();
        h.assertTrue(shrine.is(BlockTags.MINEABLE_WITH_PICKAXE), "the Shrine isn't in minecraft:mineable/pickaxe (or the tag failed to load)");
        h.assertTrue(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "stone isn't a pickaxe's: the mineable/pickaxe tag failed to load");
        BlockPos at = new BlockPos(12, 1, 12);
        h.setBlock(at, shrine);
        List<ItemStack> drops = Block.getDrops(shrine, h.getLevel(), h.absolutePos(at), h.getLevel().getBlockEntity(h.absolutePos(at)));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(KRegistry.SHRINE_ITEM.get()), "a broken Shrine drops " + drops + ", not itself");
        for (String id : List.of("ancestor_shrine", "plan_hold_gate", "throne_of_power")) {
            var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Khazuk.MODID, id));
            h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "no recipe " + key.identifier());
        }
        h.succeed();
    }

    /** One way to craft a thing: the recipe it must give, and what lies on the grid (row by row for a shaped one; "" an empty cell). */
    private record Way(String gives, net.minecraft.world.item.Item... grid) {
    }

    private static String crafted(GameTestHelper h, net.minecraft.world.item.Item... grid) {
        List<ItemStack> cells = new java.util.ArrayList<>();
        for (net.minecraft.world.item.Item i : grid) {
            cells.add(i == null ? ItemStack.EMPTY : new ItemStack(i));
        }
        while (cells.size() < 9) {
            cells.add(ItemStack.EMPTY);
        }
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                net.minecraft.world.item.crafting.CraftingInput.of(3, 3, cells), h.getLevel())
                .map(r -> r.id().identifier().toString()).orElse("nothing");
    }

    /**
     * A hold cut into the rock has no sugar cane, no wood and no leather (the user, 2026-11-10: "tell me, how am i gonna get paper
     * underground?"). Every plan, and the hold's other recipes that took a book, a stick or a shield, is crafted from stone and metal
     * alone; the old ways still work; and no grid gives another plan than its own (the Treasury's recipe was the Throne Room's: stone
     * bricks, iron, gold, paper, so one of the two could never be made).
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void everyPlanIsCraftedFromWhatLiesUnderground(GameTestHelper h) {
        var I = net.minecraft.world.item.Items.IRON_INGOT;
        var G = net.minecraft.world.item.Items.GOLD_INGOT;
        var T = net.minecraft.world.item.Items.COBBLED_DEEPSLATE;      // the tablet, underground
        var S = net.minecraft.world.item.Items.POLISHED_DEEPSLATE;     // dressed stone, underground
        var D = net.minecraft.world.item.Items.DEEPSLATE_BRICKS;
        var P = net.minecraft.world.item.Items.PAPER;
        var B = net.minecraft.world.item.Items.STONE_BRICKS;
        var C = net.minecraft.world.item.Items.COAL;
        var L = net.minecraft.world.item.Items.LANTERN;
        var R = net.minecraft.world.item.Items.CHISELED_DEEPSLATE;     // the rune stone, for a book
        var GB = net.minecraft.world.item.Items.GOLD_BLOCK;
        var IB = net.minecraft.world.item.Items.IRON_BLOCK;
        List<Way> ways = List.of(
                // underground: nothing of the surface
                new Way("plan_keep", S, I, G, T), new Way("plan_hold_gate", S, G, T), new Way("plan_great_hall", D, G, T),
                new Way("plan_barracks", S, I, T), new Way("plan_mine", S, net.minecraft.world.item.Items.RAW_IRON, T),
                new Way("plan_brewery", S, net.minecraft.world.item.Items.CAULDRON, T), new Way("plan_runic_forge", S, net.minecraft.world.item.Items.ANVIL, T),
                new Way("plan_slayer_shrine", S, net.minecraft.world.item.Items.REDSTONE, G, T),
                new Way("plan_engineers_guild", S, net.minecraft.world.item.Items.COPPER_INGOT, G, T),
                new Way("plan_road", D, C, T), new Way("plan_road_crossing", D, C, net.minecraft.world.item.Items.COMPASS, T),
                new Way("plan_road_descent", net.minecraft.world.item.Items.DEEPSLATE_BRICK_STAIRS, C, T),
                new Way("plan_storeroom", I, D, T), new Way("plan_treasury", S, G, G, T),
                new Way("dammaz_kron", R, G, I),
                new Way("ancestor_shrine", S, G, S, G, R, G, S, S, S),
                new Way("throne_of_power", GB, R, GB, GB, GB, GB, IB, null, IB),
                new Way("grungnis_hammer", GB, IB, GB, null, I, null, null, I, null),
                new Way("valayas_ward", G, I, G, I, IB, I, G, I, G),
                // the old ways, with paper, wood and stone bricks
                new Way("plan_keep", B, I, G, P), new Way("plan_hold_gate", B, G, P), new Way("plan_great_hall", D, G, P),
                new Way("plan_barracks", B, I, P), new Way("plan_mine", B, net.minecraft.world.item.Items.RAIL, P),
                new Way("plan_brewery", B, net.minecraft.world.item.Items.BARREL, P), new Way("plan_runic_forge", B, net.minecraft.world.item.Items.ANVIL, P),
                new Way("plan_slayer_shrine", B, net.minecraft.world.item.Items.IRON_AXE, G, P),
                new Way("plan_engineers_guild", B, net.minecraft.world.item.Items.COPPER_INGOT, G, P),
                new Way("plan_road", D, L, P), new Way("plan_road_crossing", D, L, net.minecraft.world.item.Items.COMPASS, P),
                new Way("plan_road_descent", net.minecraft.world.item.Items.DEEPSLATE_BRICK_STAIRS, L, P),
                new Way("plan_storeroom", net.minecraft.world.item.Items.BARREL, net.minecraft.world.item.Items.CHEST, P),
                new Way("plan_treasury", B, G, G, P),
                new Way("dammaz_kron", net.minecraft.world.item.Items.BOOK, G, I),
                new Way("ancestor_shrine", B, G, B, G, net.minecraft.world.item.Items.BOOK, G, B, B, B));
        for (Way w : ways) {
            String got = crafted(h, w.grid());
            h.assertTrue(got.equals(Khazuk.MODID + ":" + w.gives()), java.util.Arrays.toString(w.grid()) + " crafts " + got + ", not " + w.gives());
        }
        h.succeed();
    }
}
