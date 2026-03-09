package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/**
 * EE Clock block entity.
 *
 * Only the TOP block of the column ticks. It reads {@code columnHeight}
 * (= total EE Clocks in column) and calls the machine ticker that many
 * extra times per game tick.
 *
 * Machine search order:
 *   1. Block directly above the top EE Clock.
 *   2. Block directly below the bottom EE Clock (pos.below(columnHeight)).
 *
 * columnHeight is computed by ColumnHelper.countBelow — counts EE Clock blocks
 * at or below this position including itself, so the topmost block's value
 * equals the full column height.
 */
public class EEClockBlockEntity extends BlockEntity {

    boolean isTopOfColumn = true;
    int columnHeight = 1;

    public EEClockBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretRegistries.EE_CLOCK_BE.get(), pos, state);
    }

    // ── Tick ─────────────────────────────────────────────────────────────

    public static void serverTick(
        Level level, BlockPos pos, BlockState state, EEClockBlockEntity be
    ) {
        if (!be.isTopOfColumn) return;

        // 1. Try machine above the column top.
        BlockPos machinePos = pos.above();
        BlockState machineState = level.getBlockState(machinePos);
        BlockEntity machine = level.getBlockEntity(machinePos);

        // 2. No valid machine above — try machine below the column bottom.
        if (machine == null || !(machineState.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock)) {
            machinePos = pos.below(be.columnHeight);
            machineState = level.getBlockState(machinePos);
            machine = level.getBlockEntity(machinePos);
            if (machine == null) return;
        }

        if (!(machineState.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock entityBlock)) return;
        BlockEntityTicker<BlockEntity> ticker = EEClockBlock.getMachineTicker(level, machineState, entityBlock, machine);
        if (ticker == null) return;

        for (int i = 0; i < be.columnHeight; i++) {
            ticker.tick(level, machinePos, machineState, machine);
        }
    }

    // ── Column recalc ─────────────────────────────────────────────────────

    void recalcColumn(BlockPos exclude) {
        if (level == null) return;
        isTopOfColumn = ColumnHelper.isTopOfColumn(level, worldPosition, exclude, EEClockBlock.class);
        columnHeight = ColumnHelper.countBelow(level, worldPosition, exclude, EEClockBlock.class, false);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) recalcColumn(null);
    }
}
