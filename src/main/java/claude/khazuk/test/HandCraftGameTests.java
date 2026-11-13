package claude.khazuk.test;

import claude.khazuk.Khazuk;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every Khazuk! recipe crafted BY HAND, underground (the user's rule, 2026-11-10: "tell me, how am i gonna get paper underground?"; every
 * recipe craftable underground with no wood or paper, and new recipes must be too). KhazukDataGameTests asks the recipe manager about grids
 * it was told; this reads every crafting recipe of the khazuk namespace off the recipe manager itself, so a new one is in it the day it is
 * added, and a survival god crafts each at a crafting table standing in the digsite's rock: the menu's own clicks, the result taken into
 * his pack, the grid emptied.
 * <ul>
 * <li>For each ingredient its most underground member is laid down (a tag's members are its items): the first not only ever made of wood
 * (coal before a lantern), else the first that is no wood, paper, book or sapling itself; an ingredient with no such member is a failure:
 * it could not be made underground.</li>
 * <li>He is given exactly what the grid takes, and must end with the recipe's result and nothing else.</li>
 * <li>One test runs all of them and fails with the whole list of what went wrong, not the first.</li>
 * <li>Not covered here: the namespace has no smelting, smithing or stonecutting recipe today (a test lists any that appears, as uncovered,
 * and fails so it gets its own test); the mod's own workstations (the Runic Forge's runes, the Anvil of Doom, the Brewery's ale) aren't
 * recipes, and their own tests drive them.</li>
 * <li>One step further (made-of-wood): an ingredient member that is itself made only of wood (an iron axe needs sticks, a lantern a torch,
 * a rail sticks): every crafting recipe of every item read, worked out to a fixed point. Its own test, not required: Grimnir's Fury takes an
 * iron axe, and blaze powder, which the user's rule may or may not mean to forbid; the log names each.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazukcraft", time = 6000, daylightCycle = BatchWorld.Rule.OFF, difficulty = Difficulty.NORMAL)
public class HandCraftGameTests {

    private static final BlockPos TABLE = new BlockPos(5, 2, 4), STAND = new BlockPos(3, 2, 4);

    /** Wood, paper and what is only ever made of them: never down in the rock. */
    static boolean woodOrPaper(Item i) {
        ItemStack s = new ItemStack(i);
        return s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS) || s.is(ItemTags.SAPLINGS) || s.is(ItemTags.WOODEN_SLABS) || s.is(ItemTags.WOODEN_STAIRS)
                || s.is(ItemTags.WOODEN_FENCES) || s.is(ItemTags.WOODEN_DOORS) || s.is(ItemTags.WOODEN_TRAPDOORS) || s.is(ItemTags.WOODEN_BUTTONS)
                || s.is(ItemTags.WOODEN_PRESSURE_PLATES) || s.is(ItemTags.BOATS) || s.is(ItemTags.SIGNS) || s.is(ItemTags.LEAVES)
                || i == Items.STICK || i == Items.PAPER || i == Items.BOOK || i == Items.WRITABLE_BOOK || i == Items.SUGAR_CANE || i == Items.BOWL
                || i == Items.BARREL || i == Items.CHEST || i == Items.CRAFTING_TABLE || i == Items.BAMBOO || i == Items.LEATHER;
    }

    /** One craft: the grid (row by row, 9 cells or fewer), its result, and why it couldn't be laid out (or null). */
    record Plan(String id, @Nullable Item[] grid, ItemStack result, @Nullable String why) {
    }

    /**
     * The khazuk namespace's recipes; crafting ones planned as a grid (each cell its ingredient's most underground member), the rest named as
     * uncovered.
     */
    static List<Plan> plans(GameTestHelper h, List<String> uncovered, Set<Item> wood) {
        List<Plan> out = new ArrayList<>();
        for (RecipeHolder<?> r : h.getLevel().getServer().getRecipeManager().getRecipes()) {
            String id = r.id().identifier().toString();
            if (!r.id().identifier().getNamespace().equals(Khazuk.MODID)) {
                continue;
            }
            if (!(r.value() instanceof CraftingRecipe craft)) {
                uncovered.add(id + " (" + BuiltInRegistries.RECIPE_TYPE.getKey(r.value().getType()) + ")");
                continue;
            }
            Item[] grid = new Item[9];
            String why = null;
            List<Optional<Ingredient>> cells = new ArrayList<>();
            int width;
            if (craft instanceof ShapedRecipe shaped) {
                width = shaped.getWidth();
                cells.addAll(shaped.getIngredients());
            } else {
                width = 3;
                craft.placementInfo().ingredients().forEach(i -> cells.add(Optional.of(i)));
            }
            if (cells.size() > 9 || width > 3) {
                out.add(new Plan(id, grid, ItemStack.EMPTY, "it doesn't fit a 3 x 3 grid"));
                continue;
            }
            for (int k = 0; k < cells.size(); k++) {
                if (cells.get(k).isEmpty()) {
                    continue;
                }
                Ingredient ing = cells.get(k).get();
                // the most underground member: none made only of wood if there is one (coal, not a lantern), else none that IS wood or paper
                Item pick = ing.items().map(Holder::value).filter(i -> !wood.contains(i)).findFirst()
                        .orElse(ing.items().map(Holder::value).filter(i -> !woodOrPaper(i)).findFirst().orElse(null));
                if (pick == null) {
                    why = "its ingredient " + ing.items().map(Holder::value).toList() + " is only wood or paper";
                    break;
                }
                grid[k / width * 3 + k % width] = pick;
            }
            ItemStack result = ItemStack.EMPTY;
            if (why == null) {
                List<ItemStack> in = new ArrayList<>();
                for (Item i : grid) {
                    in.add(i == null ? ItemStack.EMPTY : new ItemStack(i));
                }
                result = craft.assemble(CraftingInput.of(3, 3, in));
            }
            out.add(new Plan(id, grid, result, why));
        }
        return out;
    }

    /** A room in the digsite's stone, a crafting table on its floor: underground, as the hold is. */
    private static SurvivalGod atTheTable(GameTestHelper h) {
        SurvivalPlay.fill(h, new BlockPos(2, 2, 2), new BlockPos(8, 4, 6), Blocks.AIR.defaultBlockState());
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        return SurvivalGod.join(h, STAND);
    }

    @GameTest(template = "digsite", timeoutTicks = 200, batch = "khazukcraft")
    public static void everyRecipeIsCraftedByHandUndergroundWithNoWoodOrPaper(GameTestHelper h) {
        SurvivalGod god = atTheTable(h);
        List<String> uncovered = new ArrayList<>();
        List<Plan> plans = plans(h, uncovered, madeOfWood(h));
        List<String> bad = new ArrayList<>();
        int crafted = 0;
        for (Plan p : plans) {
            if (p.why() != null) {
                bad.add(p.id() + ": " + p.why());
                continue;
            }
            god.inventory().clearContent();
            for (Item i : p.grid()) {
                if (i != null) {
                    god.give(i, 1);
                }
            }
            ItemStack made = god.tryCraft(TABLE, p.grid());
            if (made.isEmpty()) {
                bad.add(p.id() + ": nothing made: " + god.lastCraftFailure());
                continue;
            }
            if (!ItemStack.isSameItem(made, p.result()) || made.getCount() != p.result().getCount()) {
                bad.add(p.id() + ": made " + made + ", the recipe gives " + p.result());
                continue;
            }
            String left = god.contents();
            int has = god.count(made.getItem());
            int stacks = 0;
            for (int s = 0; s < god.inventory().getContainerSize(); s++) {
                stacks += god.inventory().getItem(s).isEmpty() ? 0 : 1;
            }
            if (has != p.result().getCount() || stacks != 1) {
                bad.add(p.id() + ": after the craft he holds " + left + " (the grid not used up, or the result not in his pack)");
                continue;
            }
            crafted++;
            Khazuk.LOG.info("KHAZUK craft: {} by hand from {}", p.id(), java.util.Arrays.stream(p.grid()).map(i -> i == null ? "-" : BuiltInRegistries.ITEM.getKey(i).getPath()).toList());
        }
        h.assertTrue(god.player().containerMenu == god.player().inventoryMenu, "the crafting table was left open");
        h.assertTrue(uncovered.isEmpty(), "khazuk recipes this test can't craft at a table (give each its own test): " + uncovered);
        h.assertTrue(bad.isEmpty(), bad.size() + " of " + plans.size() + " recipes can't be crafted by hand underground: " + bad);
        h.assertTrue(crafted >= 20, "only " + crafted + " khazuk crafting recipes were found (the recipe manager should have 24 or more)");
        h.succeed();
    }

    /**
     * One step further: no ingredient laid down is only ever made of wood or paper (an iron axe needs sticks; a lantern a torch, and a torch
     * a stick). Every crafting recipe of every namespace is read; an item is made-of-wood when it is wood or paper, or every crafting recipe
     * that makes it has a cell whose every member is made-of-wood (an item no recipe makes is mined, smelted or found: not wood). Worked out
     * to a fixed point. Required since Grimnir's Fury took an iron ingot as well as an iron axe (2026-11-13: the axe needs sticks).
     */
    @GameTest(template = "digsite", timeoutTicks = 60, batch = "khazukcraft")
    public static void noIngredientIsOnlyEverMadeOfWood(GameTestHelper h) {
        Set<Item> wood = madeOfWood(h);
        List<String> bad = new ArrayList<>();
        for (Plan p : plans(h, new ArrayList<>(), wood)) {
            if (p.why() != null) {
                continue;
            }
            for (Item i : p.grid()) {
                if (i != null && wood.contains(i)) {
                    bad.add(p.id() + " takes " + BuiltInRegistries.ITEM.getKey(i) + " (only ever made of wood or paper)");
                }
            }
        }
        bad = new ArrayList<>(new java.util.LinkedHashSet<>(bad));
        for (String b : bad) {
            Khazuk.LOG.warn("KHAZUK craft: {}", b);
        }
        h.assertTrue(bad.isEmpty(), "ingredients a hold underground can't make: " + bad);
        h.succeed();
    }

    /**
     * Every item only ever made of wood or paper: wood and paper themselves, and (to a fixed point) any item every crafting recipe of which
     * has a cell whose every member is so. An item no crafting recipe makes is mined, smelted or found: not wood.
     */
    static Set<Item> madeOfWood(GameTestHelper h) {
        Map<Item, List<List<Set<Item>>>> makes = new HashMap<>();
        for (RecipeHolder<?> r : h.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (!(r.value() instanceof CraftingRecipe craft)) {
                continue;
            }
            List<Set<Item>> cells = new ArrayList<>();
            List<Optional<Ingredient>> ings = craft instanceof ShapedRecipe s ? s.getIngredients()
                    : craft.placementInfo().ingredients().stream().map(Optional::of).toList();
            for (Optional<Ingredient> o : ings) {
                o.ifPresent(i -> cells.add(new HashSet<>(i.items().map(Holder::value).toList())));
            }
            ItemStack out;
            try {
                out = recipeResult(craft, ings);
            } catch (RuntimeException ex) {
                continue;                                        // a special recipe (dyeing, a map): no fixed result
            }
            if (!out.isEmpty()) {
                makes.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(cells);
            }
        }
        Set<Item> wood = new HashSet<>();
        for (Item i : BuiltInRegistries.ITEM) {
            if (woodOrPaper(i)) {
                wood.add(i);
            }
        }
        for (boolean grew = true; grew; ) {
            grew = false;
            for (var e : makes.entrySet()) {
                if (wood.contains(e.getKey())) {
                    continue;
                }
                boolean allWood = e.getValue().stream().allMatch(cells -> cells.stream().anyMatch(c -> !c.isEmpty() && wood.containsAll(c)));
                if (allWood) {
                    wood.add(e.getKey());
                    grew = true;
                }
            }
        }
        return wood;
    }

    /** A crafting recipe's result, with its own ingredients' first members on the grid. */
    private static ItemStack recipeResult(CraftingRecipe craft, List<Optional<Ingredient>> ings) {
        int width = craft instanceof ShapedRecipe s ? s.getWidth() : 3;
        int height = craft instanceof ShapedRecipe s ? s.getHeight() : 3;
        if (ings.size() > 9 || width > 3 || height > 3) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> in = new ArrayList<>();
        for (int k = 0; k < 9; k++) {
            in.add(ItemStack.EMPTY);
        }
        for (int k = 0; k < ings.size(); k++) {
            Optional<Ingredient> o = ings.get(k);
            if (o.isPresent()) {
                Item first = o.get().items().map(Holder::value).findFirst().orElse(Items.AIR);
                in.set(k / width * 3 + k % width, new ItemStack(first));
            }
        }
        return craft.assemble(CraftingInput.of(3, 3, in));
    }
}
