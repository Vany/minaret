package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** TeleporterInhibitor contributes +4 to column radius and enables teleport inhibition. */
public class TeleporterInhibitorBlockEntity extends WardingColumnBlockEntity {

    public TeleporterInhibitorBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretRegistries.TELEPORTER_INHIBITOR_BE.get(), pos, state);
    }
}
