package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** RepellingPost contributes +4 to column radius and enables mob repulsion. */
public class RepellingPostBlockEntity extends WardingColumnBlockEntity {

    public RepellingPostBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretRegistries.REPELLING_POST_BE.get(), pos, state);
    }
}
