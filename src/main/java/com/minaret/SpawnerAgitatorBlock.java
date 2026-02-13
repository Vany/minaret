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
        // First: unbind whoever is currently bound (restores true originals to spawner)
        unbindCurrentTop(level, pos, null);
        // Walk down to find column base
        BlockPos base = pos;
        while (
            level.getBlockState(base.below()).getBlock() instanceof
                SpawnerAgitatorBlock
        ) {
            base = base.below();
        }
        // Walk up: recalc + rebind (only new top will bind with fresh original values)
        BlockPos check = base;
        while (
            level.getBlockState(check).getBlock() instanceof
                SpawnerAgitatorBlock
        ) {
            if (
                level.getBlockEntity(check) instanceof
                    SpawnerAgitatorBlockEntity be
            ) {
                be.recalcStackSize();
                be.bindSpawner();
            }
            check = check.above();
        }
    }

    private static void notifyColumnExcluding(Level level, BlockPos removed) {
        // First: unbind whoever is currently bound (restores true originals to spawner)
        // (the removed block already called unbindSpawner in playerWillDestroy)

        // Walk down from below removed to find column base
        BlockPos base = removed.below();
        while (
            level.getBlockState(base).getBlock() instanceof SpawnerAgitatorBlock
        ) {
            base = base.below();
        }
        // base is now below the column; walk up, skip removed
        BlockPos check = base.above();
        while (true) {
            if (check.equals(removed)) {
                check = check.above();
                continue;
            }
            if (
                !(level.getBlockState(check).getBlock() instanceof
                        SpawnerAgitatorBlock)
            ) break;
            if (
                level.getBlockEntity(check) instanceof
                    SpawnerAgitatorBlockEntity be
            ) {
                be.recalcStackSize(removed);
                be.bindSpawner(removed);
            }
            check = check.above();
        }
    }

    private static void unbindCurrentTop(
        Level level,
        BlockPos pos,
        BlockPos exclude
    ) {
        // Walk up from pos to find whoever is currently bound, unbind them
        BlockPos check = pos;
        while (true) {
            if (exclude != null && check.equals(exclude)) {
                check = check.above();
                continue;
            }
            if (
                !(level.getBlockState(check).getBlock() instanceof
                        SpawnerAgitatorBlock)
            ) break;
            if (
                level.getBlockEntity(check) instanceof
                    SpawnerAgitatorBlockEntity be
            ) {
                if (be.isBound()) {
                    be.unbindSpawner();
                    return;
                }
            }
            check = check.above();
        }
    }
}
