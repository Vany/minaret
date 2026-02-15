package com.minaret;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Generic column traversal for stacking blocks (spawner agitators, warding posts).
 * Walks down to column base, then up applying an action to each block entity.
 */
public final class ColumnHelper {

    private ColumnHelper() {}

    /**
     * Walk down from {@code pos} to find the column base, then walk up
     * applying {@code action} to each block entity of type {@code entityClass}.
     */
    public static <T extends BlockEntity> void forEachInColumn(
        Level level,
        BlockPos pos,
        Class<? extends Block> blockClass,
        Class<T> entityClass,
        Consumer<T> action
    ) {
        BlockPos base = findBase(level, pos, blockClass);
        BlockPos check = base;
        while (blockClass.isInstance(level.getBlockState(check).getBlock())) {
            if (entityClass.isInstance(level.getBlockEntity(check))) {
                action.accept(entityClass.cast(level.getBlockEntity(check)));
            }
            check = check.above();
        }
    }

    /**
     * Same as {@link #forEachInColumn}, but skips the block at {@code excluded}.
     * Used when a block is being removed (still in world during playerWillDestroy).
     */
    public static <T extends BlockEntity> void forEachInColumnExcluding(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<? extends Block> blockClass,
        Class<T> entityClass,
        Consumer<T> action
    ) {
        // Start from below excluded to avoid counting it as base
        BlockPos base = findBase(level, excluded.below(), blockClass);
        BlockPos check = base.above();
        while (true) {
            if (check.equals(excluded)) {
                check = check.above();
                continue;
            }
            if (
                !blockClass.isInstance(level.getBlockState(check).getBlock())
            ) break;
            if (entityClass.isInstance(level.getBlockEntity(check))) {
                action.accept(entityClass.cast(level.getBlockEntity(check)));
            }
            check = check.above();
        }
    }

    /**
     * Count blocks of {@code blockClass} below {@code pos} (including pos itself).
     * @param skipExcluded if true, skip the excluded position and keep counting;
     *                     if false, stop at the excluded position
     */
    public static int countBelow(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<? extends Block> blockClass,
        boolean skipExcluded
    ) {
        int count = 1;
        BlockPos check = pos.below();
        while (blockClass.isInstance(level.getBlockState(check).getBlock())) {
            if (excluded != null && check.equals(excluded)) {
                if (skipExcluded) {
                    check = check.below();
                    continue;
                } else {
                    break;
                }
            }
            count++;
            check = check.below();
        }
        return count;
    }

    /** Check if the block directly above {@code pos} is NOT the same type (i.e. pos is the top). */
    public static boolean isTopOfColumn(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<? extends Block> blockClass
    ) {
        BlockPos above = pos.above();
        if (excluded != null && above.equals(excluded)) above = above.above();
        return !blockClass.isInstance(level.getBlockState(above).getBlock());
    }

    /** Walk down from {@code pos} to find the lowest block of this type. */
    private static BlockPos findBase(
        Level level,
        BlockPos pos,
        Class<? extends Block> blockClass
    ) {
        BlockPos base = pos;
        while (
            blockClass.isInstance(level.getBlockState(base.below()).getBlock())
        ) {
            base = base.below();
        }
        return base;
    }
}
