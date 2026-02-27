package com.minaret;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
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

/**
 * Shared base for all warding-column post blocks (WardingPostBlock,
 * RepellingPostBlock, TeleporterInhibitorBlock). All three are identical except
 * for their block-entity type — that is supplied by the subclass via
 * {@link #beTypeSupplier()}.
 *
 * Column logic: on place/destroy, notifies all WardingColumnBlockEntity instances
 * in the mixed column so they can recalculate their state.
 * The server ticker runs on every block in the column; WardingColumnBlockEntity
 * itself skips non-bottom blocks.
 */
public abstract class WardingColumnBaseBlock extends BaseEntityBlock implements WardingColumnBlock {

    static final VoxelShape POST_SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public WardingColumnBaseBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    /** Subclasses provide the registered BlockEntityType for their BE. */
    protected abstract Supplier<BlockEntityType<? extends WardingColumnBlockEntity>> beTypeSupplier();

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx
    ) {
        return POST_SHAPE;
    }

    @Override
    protected VoxelShape getBlockSupportShape(
        BlockState state, BlockGetter level, BlockPos pos
    ) {
        return Shapes.block();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(
            type,
            (BlockEntityType<WardingColumnBlockEntity>) beTypeSupplier().get(),
            WardingColumnBlockEntity::serverTick
        );
    }

    // ── Events ───────────────────────────────────────────────────────────

    @Override
    protected void onPlace(
        BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) notifyColumn(level, pos);
    }

    @Override
    public BlockState playerWillDestroy(
        Level level, BlockPos pos, BlockState state, Player player
    ) {
        if (!level.isClientSide()) notifyColumnExcluding(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Periodic integrity recheck — recalculates column composition in case blocks were added/removed without a block update. */
    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        notifyColumn(level, pos);
    }

    // ── Column notification ───────────────────────────────────────────────

    static void notifyColumn(Level level, BlockPos pos) {
        ColumnHelper.forEachInColumn(
            level, pos, WardingColumnBlock.class,
            WardingColumnBlockEntity.class, WardingColumnBlockEntity::recalcColumn
        );
    }

    static void notifyColumnExcluding(Level level, BlockPos removed) {
        ColumnHelper.forEachInColumnExcluding(
            level, removed, WardingColumnBlock.class,
            WardingColumnBlockEntity.class, be -> be.recalcColumn(removed)
        );
    }
}
