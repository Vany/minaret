package com.minaret;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Streamer Protect - prevents overwatchers from striking the player with lightning.
 * Implementation is handled externally; this effect serves as a visible indicator.
 */
public class StreamerProtectEffect extends MobEffect {

    private static final int COLOR_GOLD = 0xFFD700;

    public StreamerProtectEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR_GOLD);
    }
}
