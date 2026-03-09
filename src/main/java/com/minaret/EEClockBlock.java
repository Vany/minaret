package com.minaret;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * EE Clock: column of these blocks speeds up the machine directly above the top.
 * N stacked EE Clocks → machine gets N extra ticks per game tick (N+1 total).
 * Column pattern matches SpawnerAgitatorBlock: topmost block is the actor.
 */
public class EEClockBlock extends BaseEntityBlock {

    private static final MapCodec<EEClockBlock> CODEC = simpleCodec(EEClockBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public EEClockBlock(BlockBehaviour.Properties props) { super(props); }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EEClockBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, MinaretRegistries.EE_CLOCK_BE.get(), EEClockBlockEntity::serverTick);
    }

    /** Periodic integrity recheck in case blocks were placed/removed without a block update. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        notifyColumn(level, pos);
    }

    // ── Events ────────────────────────────────────────────────────────────

    @Override
    protected void onPlace(
        BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) notifyColumn(level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) notifyColumnExcluding(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Handles non-player removal (TNT, pistons, commands). Block at pos is already gone. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        notifyColumn(level, pos);
        notifyColumn(level, pos.above());
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /** Gets the machine's ticker by casting through BaseEntityBlock, capturing the wildcard. */
    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> BlockEntityTicker<T> getMachineTicker(
        Level level, BlockState state, BaseEntityBlock block, BlockEntity be
    ) {
        return block.getTicker(level, state, (BlockEntityType<T>) be.getType());
    }

    // ── Column helpers ────────────────────────────────────────────────────

    static void notifyColumn(Level level, BlockPos pos) {
        ColumnHelper.forEachInColumn(
            level, pos, EEClockBlock.class, EEClockBlockEntity.class,
            be -> be.recalcColumn(null)
        );
    }

    static void notifyColumnExcluding(Level level, BlockPos removed) {
        ColumnHelper.forEachInColumnExcluding(
            level, removed, EEClockBlock.class, EEClockBlockEntity.class,
            be -> be.recalcColumn(removed)
        );
    }
}
