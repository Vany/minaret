package com.minaret;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Repelling Post: contributes +4 to column radius and enables mob repulsion. */
public class RepellingPostBlock extends WardingColumnBaseBlock {

    private static final MapCodec<RepellingPostBlock> CODEC = simpleCodec(RepellingPostBlock::new);

    @Override
    protected MapCodec<RepellingPostBlock> codec() { return CODEC; }

    public RepellingPostBlock(BlockBehaviour.Properties props) { super(props); }

    @Override
    protected Supplier<BlockEntityType<? extends WardingColumnBlockEntity>> beTypeSupplier() {
        return MinaretRegistries.REPELLING_POST_BE::get;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RepellingPostBlockEntity(pos, state);
    }
}
