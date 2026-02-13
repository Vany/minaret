package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChunkLoaderBlockEntity extends BlockEntity {

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretMod.CHUNK_LOADER_BE.get(), pos, state);
    }
}
