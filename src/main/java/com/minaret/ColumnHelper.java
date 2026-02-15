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
        Level level, BlockPos pos,
        Class<? extends Block> blockClass, Class<T> entityClass,
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
        Level level, BlockPos pos, BlockPos excluded,
        Class<? extends Block> blockClass, Class<T> entityClass,
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
            if (!blockClass.isInstance(level.getBlockState(check).getBlock())) break;
            if (entityClass.isInstance(level.getBlockEntity(check))) {
                action.accept(entityClass.cast(level.getBlockEntity(check)));
            }
            check = check.above();
        }
    }

    /** Walk down from {@code pos} to find the lowest block of this type. */
    private static BlockPos findBase(
        Level level, BlockPos pos, Class<? extends Block> blockClass
    ) {
        BlockPos base = pos;
        while (blockClass.isInstance(level.getBlockState(base.below()).getBlock())) {
            base = base.below();
        }
        return base;
    }
}
