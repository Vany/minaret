package com.minaret;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Anti-Wither block: obsidian mining level, immune to explosions (resistance 3600000).
 * Fully solid cube with same texture on all sides.
 */
public class AntiWitherBlock extends Block {

    private static final MapCodec<AntiWitherBlock> CODEC = simpleCodec(AntiWitherBlock::new);

    @Override
    public MapCodec<AntiWitherBlock> codec() { return CODEC; }

    public AntiWitherBlock(BlockBehaviour.Properties props) {
        super(props);
    }
}
