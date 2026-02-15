package com.minaret;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerAgitatorBlock extends BaseEntityBlock {

    private static final MapCodec<SpawnerAgitatorBlock> CODEC = simpleCodec(
        SpawnerAgitatorBlock::new
    );

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public SpawnerAgitatorBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerAgitatorBlockEntity(pos, state);
    }

    // ── Events ──────────────────────────────────────────────────────────

    @Override
    protected void onPlace(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState oldState,
        boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) return;
        if (
            level.getBlockEntity(pos) instanceof SpawnerAgitatorBlockEntity be
        ) {
            be.bindSpawner();
        }
        notifyColumn(level, pos);
    }

    @Override
    public BlockState playerWillDestroy(
        Level level,
        BlockPos pos,
        BlockState state,
        Player player
    ) {
        if (!level.isClientSide()) {
            if (
                level.getBlockEntity(pos) instanceof
                    SpawnerAgitatorBlockEntity be
            ) {
                be.unbindSpawner();
            }
            notifyColumnExcluding(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ── Column helpers ──────────────────────────────────────────────────

    static void notifyColumn(Level level, BlockPos pos) {
        // Unbind whoever is currently bound (restores true originals to spawner)
        unbindCurrentTop(level, pos);
        // Recalc + rebind entire column
        ColumnHelper.forEachInColumn(
            level,
            pos,
            SpawnerAgitatorBlock.class,
            SpawnerAgitatorBlockEntity.class,
            be -> {
                be.recalcStackSize();
                be.bindSpawner();
            }
        );
    }

    private static void notifyColumnExcluding(Level level, BlockPos removed) {
        // The removed block already called unbindSpawner in playerWillDestroy
        ColumnHelper.forEachInColumnExcluding(
            level,
            removed,
            removed,
            SpawnerAgitatorBlock.class,
            SpawnerAgitatorBlockEntity.class,
            be -> {
                be.recalcStackSize(removed);
                be.bindSpawner(removed);
            }
        );
    }

    private static void unbindCurrentTop(Level level, BlockPos pos) {
        ColumnHelper.forEachInColumn(
            level,
            pos,
            SpawnerAgitatorBlock.class,
            SpawnerAgitatorBlockEntity.class,
            be -> {
                if (be.isBound()) be.unbindSpawner();
            }
        );
    }
}
