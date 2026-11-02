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
}
