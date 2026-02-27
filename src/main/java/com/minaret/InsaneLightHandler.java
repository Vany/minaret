package com.minaret;

import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles the Insane Light mob effect:
 *   - Player glows (MobEffects.GLOWING) — visible to others in the dark
 *   - Player has night vision (MobEffects.NIGHT_VISION) — full brightness for themselves
 *   - All mobs within 48 blocks detect the player at 2x normal FOLLOW_RANGE
 *   - When a mob changes target to the player: 25% chance Blindness, 25% chance Weakness
 */
public final class InsaneLightHandler {

    private InsaneLightHandler() {}

    /** Ticks between FOLLOW_RANGE refresh scans (every 2 seconds). */
    private static final int RANGE_REFRESH_INTERVAL = 40;

    /** Radius around player to boost mob detection range. */
    private static final double BOOST_RADIUS = 48.0;

    /** Duration to keep reapplying vanilla effects (5 seconds — always fresh). */
    private static final int EFFECT_DURATION = 100;

    private static final String RANGE_BOOST_NS = "minaret";
    private static final String RANGE_BOOST_PATH = "insane_light_range";

    // ── Tick handler ────────────────────────────────────────────────────────

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        boolean hasEffect = player.hasEffect(MinaretRegistries.INSANE_LIGHT);

        if (hasEffect) {
            applyVanillaEffects(player);
            if (player.tickCount % RANGE_REFRESH_INTERVAL == 0) {
                boostNearbyMobs(player, true);
            }
        } else {
            // Clean up any lingering FOLLOW_RANGE boosts when effect expires
            if (player.tickCount % RANGE_REFRESH_INTERVAL == 0) {
                boostNearbyMobs(player, false);
            }
        }
    }

    /**
     * Keeps GLOWING and NIGHT_VISION fresh on the player.
     * Only reapplied when about to expire (< 10 ticks remaining) to avoid spam.
     */
    private static void applyVanillaEffects(Player player) {
        MobEffectInstance glowing = player.getEffect(MobEffects.GLOWING);
        if (glowing == null || glowing.getDuration() < 10) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, EFFECT_DURATION, 0, false, false));
        }
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 10) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, false, false));
        }
    }

    /**
     * Adds (or removes) a 2x FOLLOW_RANGE multiplier on all nearby mobs.
     * ADD_MULTIPLIED_BASE: effective_range = base + base * 1.0 = 2 * base.
     */
    private static void boostNearbyMobs(Player player, boolean boost) {
        AABB box = player.getBoundingBox().inflate(BOOST_RADIUS);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, box);

        for (Mob mob : mobs) {
            AttributeInstance attr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (attr == null) continue;

            Compat.removeAttributeModifier(attr, RANGE_BOOST_NS, RANGE_BOOST_PATH);

            if (boost) {
                attr.addTransientModifier(Compat.createAttributeModifier(
                    RANGE_BOOST_NS,
                    RANGE_BOOST_PATH,
                    1.0,  // +100% of base = 2x total
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            }
        }
    }

    // ── Target change handler ────────────────────────────────────────────────

    /**
     * When a mob acquires the Insane Light player as its target:
     *   - 25% chance: Blindness (5 seconds)
     *   - 25% chance: Weakness (5 seconds)
     * Each roll is independent.
     */
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (!(newTarget instanceof Player player)) return;
        if (!player.hasEffect(MinaretRegistries.INSANE_LIGHT)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        if (mob.getRandom().nextFloat() < 0.25f) {
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        }
        if (mob.getRandom().nextFloat() < 0.25f) {
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        }
    }
}
