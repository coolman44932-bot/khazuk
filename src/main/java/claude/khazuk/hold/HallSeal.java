package claude.khazuk.hold;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/**
 * A rune-sealed hall door (the user, 2026-11-06: "digging a hall would kind of mess up the look of the room, there should be a place in the
 * room to dig into"): a doorway the hold was built with, closed with carved stone until a hall is dug through it. An Ancestors' Plan used on
 * it digs that hall from there, its doorway in the seal's arch and running the way the seal faces (FACING: into the rock), so the hall
 * meets the room as the room's own builders meant. The Hold Gate's Shrine hall is built with two; one is crafted to mark any other spot.
 * A seal of several blocks (an arch, 3 wide and 3 high) is one doorway: its bottom middle is where the hall's door goes ({@link #anchor}).
 */
public class HallSeal extends HorizontalDirectionalBlock {

    public static final MapCodec<HallSeal> CODEC = simpleCodec(HallSeal::new);

    public HallSeal(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /** Set into a wall facing the way the player looks: the hall runs on beyond it, away from them. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection());
    }

    /**
     * The door of the hall dug through the seal at {@code hit}: the bottom middle of the seal it is part of (its blocks facing the same way,
     * stacked and side by side in the wall's plane), or null if {@code hit} is no seal.
     */
    public static @Nullable BlockPos anchor(BlockGetter level, BlockPos hit) {
        BlockState s = level.getBlockState(hit);
        if (!(s.getBlock() instanceof HallSeal)) {
            return null;
        }
        Direction f = s.getValue(FACING);
        BlockPos p = hit;
        for (int i = 0; i < 8 && same(level, p.below(), f); i++) {
            p = p.below();
        }
        Direction right = f.getClockWise();
        int r = 0, l = 0;
        while (r < 8 && same(level, p.relative(right, r + 1), f)) {
            r++;
        }
        while (l < 8 && same(level, p.relative(right, -(l + 1)), f)) {
            l++;
        }
        return p.relative(right, Math.floorDiv(r - l, 2));      // an even width: always its left middle, whichever block is aimed at
    }

    /** The way a hall dug through the seal at {@code hit} runs. */
    public static Direction runs(BlockGetter level, BlockPos hit) {
        return level.getBlockState(hit).getValue(FACING);
    }

    private static boolean same(BlockGetter level, BlockPos p, Direction f) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof HallSeal && s.getValue(FACING) == f;
    }
}
