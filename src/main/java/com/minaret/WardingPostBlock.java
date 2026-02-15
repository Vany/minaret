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
        ColumnHelper.forEachInColumn(
            level,
            pos,
            WardingPostBlock.class,
            WardingPostBlockEntity.class,
            WardingPostBlockEntity::recalcColumn
        );
    }

    private static void notifyColumnExcluding(Level level, BlockPos removed) {
        ColumnHelper.forEachInColumnExcluding(
            level,
            removed,
            removed,
            WardingPostBlock.class,
            WardingPostBlockEntity.class,
            be -> be.recalcColumn(removed)
        );
    }
}
