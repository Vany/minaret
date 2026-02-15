package com.minaret;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class MartialLightningHandler {

    private static final int POISON_AMPLIFIER = 31;
    private static final int POISON_DURATION_TICKS = 200;
    private static final int WITHER_AMPLIFIER = 3;
    private static final int WITHER_DURATION_TICKS = 200;
    private static final double FRONT_DOT_THRESHOLD = 0.0;

    /** Prevents recursive AoE hits from triggering more AoE. Thread-safe. */
    private static final Set<Player> playersInAoeSwing =
        ConcurrentHashMap.newKeySet();

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!player.hasEffect(MinaretMod.MARTIAL_LIGHTNING)) return;
        if (playersInAoeSwing.contains(player)) return;

        LivingEntity target = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        ToolCategory category = categorize(weapon);

        event.setAmount(event.getAmount() * category.damageMultiplier);

        if (category.aoe) {
            performAoe(player, target, event.getAmount(), category);
        }

        applySecondaryEffect(target, player, category);
    }

    private static void performAoe(
        Player player,
        LivingEntity primaryTarget,
        float damage,
        ToolCategory category
    ) {
        double reach = player.entityInteractionRange();
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();

        AABB searchBox = player.getBoundingBox().inflate(reach);
        List<LivingEntity> nearby = player
            .level()
            .getEntitiesOfClass(LivingEntity.class, searchBox);

        playersInAoeSwing.add(player);
        try {
            for (LivingEntity entity : nearby) {
                if (entity == player || entity == primaryTarget) continue;
                if (player.isAlliedTo(entity)) continue;
                if (player.distanceToSqr(entity) > reach * reach) continue;

                Vec3 toEntity = entity.position().subtract(eyePos).normalize();
                if (look.dot(toEntity) <= FRONT_DOT_THRESHOLD) continue;

                entity.hurt(
                    player.damageSources().playerAttack(player),
                    damage
                );
                applySecondaryEffect(entity, player, category);
            }
        } finally {
            playersInAoeSwing.remove(player);
        }
    }

    private static void applySecondaryEffect(
        LivingEntity target,
        Player player,
        ToolCategory category
    ) {
        switch (category) {
            case WOODEN:
                target.addEffect(
                    new MobEffectInstance(
                        MobEffects.POISON,
                        POISON_DURATION_TICKS,
                        POISON_AMPLIFIER
                    ),
                    player
                );
                break;
            case STONE:
                target.addEffect(
                    new MobEffectInstance(
                        MobEffects.WITHER,
                        WITHER_DURATION_TICKS,
                        WITHER_AMPLIFIER
                    ),
                    player
                );
                break;
            default:
                break;
        }
    }

    private static ToolCategory categorize(ItemStack weapon) {
        if (weapon.isEmpty()) return ToolCategory.BARE_HAND;
        String path = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
        if (path.startsWith("wooden_")) return ToolCategory.WOODEN;
        if (path.startsWith("stone_")) return ToolCategory.STONE;
        if (path.startsWith("iron_")) return ToolCategory.IRON;
        return ToolCategory.OTHER;
    }

    private enum ToolCategory {
        BARE_HAND(10.0f, true),
        WOODEN(5.0f, true),
        STONE(3.0f, true),
        IRON(1.5f, false),
        OTHER(1.0f, false);

        final float damageMultiplier;
        final boolean aoe;

        ToolCategory(float damageMultiplier, boolean aoe) {
            this.damageMultiplier = damageMultiplier;
            this.aoe = aoe;
        }
    }
}
