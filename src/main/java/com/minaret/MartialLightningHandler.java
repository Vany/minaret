package com.minaret;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // Prevent recursive AoE hits from triggering more AoE
    private static final Set<Player> activeAoe = new HashSet<>();

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!player.hasEffect(MinaretMod.MARTIAL_LIGHTNING)) return;
        if (activeAoe.contains(player)) return;

        LivingEntity target = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        String itemPath = weapon.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();

        ToolCategory category = categorize(itemPath, weapon.isEmpty());

        // Apply damage multiplier
        event.setAmount(event.getAmount() * category.damageMultiplier);

        // AoE and secondary effects
        if (category.aoe) {
            performAoe(player, target, event.getAmount(), category);
        }

        // Apply secondary effects to primary target
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
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(
            LivingEntity.class, searchBox
        );

        activeAoe.add(player);
        try {
            for (LivingEntity entity : nearby) {
                if (entity == player || entity == primaryTarget) continue;
                if (player.isAlliedTo(entity)) continue;
                if (player.distanceToSqr(entity) > reach * reach) continue;

                // Check entity is in front of the player
                Vec3 toEntity = entity.position().subtract(eyePos).normalize();
                if (look.dot(toEntity) <= 0) continue;

                entity.hurt(player.damageSources().playerAttack(player), damage);
                applySecondaryEffect(entity, player, category);
            }
        } finally {
            activeAoe.remove(player);
        }
    }

    private static void applySecondaryEffect(
        LivingEntity target,
        Player player,
        ToolCategory category
    ) {
        switch (category) {
            case WOODEN:
                // Fatal poison: amplifier 31, 10 seconds
                target.addEffect(
                    new MobEffectInstance(MobEffects.POISON, 200, 31), player
                );
                break;
            case STONE:
                // Wither: amplifier 3, 10 seconds
                target.addEffect(
                    new MobEffectInstance(MobEffects.WITHER, 200, 3), player
                );
                break;
            default:
                break;
        }
    }

    private static ToolCategory categorize(String itemPath, boolean empty) {
        if (empty) return ToolCategory.BARE_HAND;
        if (itemPath.startsWith("wooden_")) return ToolCategory.WOODEN;
        if (itemPath.startsWith("stone_")) return ToolCategory.STONE;
        if (itemPath.startsWith("iron_")) return ToolCategory.IRON;
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
