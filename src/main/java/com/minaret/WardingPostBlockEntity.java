package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** WardingPost contributes +6 to column radius. No push, no teleport inhibition. */
public class WardingPostBlockEntity extends WardingColumnBlockEntity {

    public WardingPostBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretRegistries.WARDING_POST_BE.get(), pos, state);
    }
}
