package com.minaret;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Generic column traversal for stacking blocks (spawner agitators, warding posts).
 * Walks down to column base, then up applying an action to each block entity.
 *
 * The column type is identified by any {@code Class<?>} — works for both
 * concrete block classes (SpawnerAgitatorBlock) and marker interfaces
 * (WardingColumnBlock) since both use {@code isInstance()} for membership.
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
        Class<?> columnType,
        Class<T> entityClass,
        Consumer<T> action
    ) {
        BlockPos base = findBase(level, pos, columnType);
        BlockPos check = base;
        while (columnType.isInstance(level.getBlockState(check).getBlock())) {
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
        BlockPos excluded,
        Class<?> columnType,
        Class<T> entityClass,
        Consumer<T> action
    ) {
        BlockPos base = findBase(level, excluded.below(), columnType);
        BlockPos check = base.above();
        while (true) {
            if (check.equals(excluded)) { check = check.above(); continue; }
            if (!columnType.isInstance(level.getBlockState(check).getBlock())) break;
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
        Class<?> blockClass,
        boolean skipExcluded
    ) {
        int count = 1;
        BlockPos check = pos.below();
        while (blockClass.isInstance(level.getBlockState(check).getBlock())) {
            if (excluded != null && check.equals(excluded)) {
                if (skipExcluded) { check = check.below(); continue; }
                else break;
            }
            count++;
            check = check.below();
        }
        return count;
    }

    /**
     * Count column members matching {@code filter} (or all if filter is null),
     * within a column identified by {@code columnType}, excluding {@code excluded}.
     */
    public static int countInColumn(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<?> columnType,
        Class<? extends Block> filter
    ) {
        int count = 0;
        BlockPos base = findBase(level, pos, columnType);
        BlockPos check = base;
        while (columnType.isInstance(level.getBlockState(check).getBlock())) {
            if (!check.equals(excluded)) {
                if (filter == null || filter.isInstance(level.getBlockState(check).getBlock())) {
                    count++;
                }
            }
            check = check.above();
        }
        return count;
    }

    /** Check if the block directly above {@code pos} is NOT a column member. */
    public static boolean isTopOfColumn(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<?> columnType
    ) {
        BlockPos above = pos.above();
        if (excluded != null && above.equals(excluded)) above = above.above();
        return !columnType.isInstance(level.getBlockState(above).getBlock());
    }

    /** Check if the block directly below {@code pos} is NOT a column member (i.e. pos is the bottom). */
    public static boolean isBottomOfColumn(
        Level level,
        BlockPos pos,
        BlockPos excluded,
        Class<?> columnType
    ) {
        BlockPos below = pos.below();
        if (excluded != null && below.equals(excluded)) below = below.below();
        return !columnType.isInstance(level.getBlockState(below).getBlock());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Walk down from {@code pos} to find the lowest block of this column type. */
    private static BlockPos findBase(Level level, BlockPos pos, Class<?> columnType) {
        BlockPos base = pos;
        while (columnType.isInstance(level.getBlockState(base.below()).getBlock())) {
            base = base.below();
        }
        return base;
    }
}
