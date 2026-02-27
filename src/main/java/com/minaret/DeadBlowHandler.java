package com.minaret;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Handles the Dead Blow mob effect:
 *   - The next hit the affected player lands is multiplied by 8×
 *   - The effect is consumed (removed) immediately after that hit
 */
public final class DeadBlowHandler {

    private static final float DAMAGE_MULTIPLIER = 8.0f;

    private DeadBlowHandler() {}

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!player.hasEffect(MinaretRegistries.DEAD_BLOW)) return;

        event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
        player.removeEffect(MinaretRegistries.DEAD_BLOW);
    }
}
