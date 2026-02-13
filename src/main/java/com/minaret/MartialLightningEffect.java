package com.minaret;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Martial Lightning - a beneficial status effect that enhances melee attacks.
 *
 * Bare hand: 10x damage, AoE in front
 * Wooden tools: 5x damage, AoE + fatal poison
 * Stone tools: 3x damage, AoE + wither
 * Iron tools: always critical (1.5x)
 */
public class MartialLightningEffect extends MobEffect {

    private static final int COLOR_DEEP_SKY_BLUE = 0x00BFFF;

    public MartialLightningEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR_DEEP_SKY_BLUE);
    }
}
