package com.minaret;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Teleporter Inhibitor: contributes +4 to column radius and enables teleport inhibition. */
public class TeleporterInhibitorBlock extends WardingColumnBaseBlock {

    private static final MapCodec<TeleporterInhibitorBlock> CODEC = simpleCodec(TeleporterInhibitorBlock::new);

    @Override
    protected MapCodec<TeleporterInhibitorBlock> codec() { return CODEC; }

    public TeleporterInhibitorBlock(BlockBehaviour.Properties props) { super(props); }

    @Override
    protected Supplier<BlockEntityType<? extends WardingColumnBlockEntity>> beTypeSupplier() {
        return MinaretRegistries.TELEPORTER_INHIBITOR_BE::get;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeleporterInhibitorBlockEntity(pos, state);
    }
}
