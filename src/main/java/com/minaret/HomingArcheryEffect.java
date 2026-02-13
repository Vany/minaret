package com.minaret;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Homing Archery - bow fires homing shulker bullets instead of arrows.
 * Targets the mob closest to crosshair. Damage is 3x normal arrow damage.
 */
public class HomingArcheryEffect extends MobEffect {

    private static final int COLOR_PURPLE = 0x9B30FF;

    public HomingArcheryEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR_PURPLE);
    }
}
