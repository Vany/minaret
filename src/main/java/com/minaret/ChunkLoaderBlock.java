package com.minaret;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ChunkLoaderBlock extends BaseEntityBlock {

    private static final MapCodec<ChunkLoaderBlock> CODEC = simpleCodec(
        ChunkLoaderBlock::new
    );

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public ChunkLoaderBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState oldState,
        boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderData data = ChunkLoaderData.get(serverLevel);
            data.add(pos);
            forceChunk(serverLevel, pos, true);
        }
    }

    @Override
    public BlockState playerWillDestroy(
        Level level,
        BlockPos pos,
        BlockState state,
        Player player
    ) {
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderData data = ChunkLoaderData.get(serverLevel);
            data.remove(pos);
            forceChunk(serverLevel, pos, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    static void forceChunk(ServerLevel level, BlockPos pos, boolean add) {
        ChunkPos chunkPos = new ChunkPos(pos);
        level.setChunkForced(chunkPos.x, chunkPos.z, add);
    }
}
