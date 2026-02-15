package com.minaret;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WardingPostBlock extends BaseEntityBlock {

    private static final MapCodec<WardingPostBlock> CODEC = simpleCodec(
        WardingPostBlock::new
    );
    private static final VoxelShape POST_SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public WardingPostBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return POST_SHAPE;
    }

    @Override
    protected VoxelShape getBlockSupportShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos
    ) {
        return Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardingPostBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(
            type,
            MinaretMod.WARDING_POST_BE.get(),
            WardingPostBlockEntity::serverTick
        );
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
        if (!level.isClientSide()) {
            notifyColumn(level, pos);
        }
    }

    @Override
    public BlockState playerWillDestroy(
        Level level,
        BlockPos pos,
        BlockState state,
        Player player
    ) {
        if (!level.isClientSide()) {
            notifyColumnExcluding(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ── Column helpers ──────────────────────────────────────────────────

    static void notifyColumn(Level level, BlockPos pos) {
        // Walk down to find column base
        BlockPos base = pos;
        while (
            level.getBlockState(base.below()).getBlock() instanceof
                WardingPostBlock
        ) {
            base = base.below();
        }
        // Walk up: recalc each post
        BlockPos check = base;
        while (
            level.getBlockState(check).getBlock() instanceof WardingPostBlock
        ) {
            if (
                level.getBlockEntity(check) instanceof WardingPostBlockEntity be
            ) {
                be.recalcColumn();
            }
            check = check.above();
        }
    }

    private static void notifyColumnExcluding(Level level, BlockPos removed) {
        // Walk down from below removed to find column base
        BlockPos base = removed.below();
        while (
            level.getBlockState(base).getBlock() instanceof WardingPostBlock
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
                        WardingPostBlock)
            ) break;
            if (
                level.getBlockEntity(check) instanceof WardingPostBlockEntity be
            ) {
                be.recalcColumn(removed);
            }
            check = check.above();
        }
    }
}
