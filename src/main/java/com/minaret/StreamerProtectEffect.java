package com.minaret;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Streamer Protect - prevents overwatchers from striking the player with lightning.
 * Implementation is handled externally; this effect serves as a visible indicator.
 */
public class StreamerProtectEffect extends MobEffect {

    public StreamerProtectEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700);
    }
}
