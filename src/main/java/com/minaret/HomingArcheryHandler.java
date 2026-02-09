package com.minaret;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;

public class HomingArcheryHandler {

    private static final Map<UUID, Float> bulletDamageMap =
        new ConcurrentHashMap<>();

    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (!player.hasEffect(MinaretMod.HOMING_ARCHERY)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < 0.1F) return;

        // Find target closest to crosshair
        Entity target = findTarget(player);
        if (target == null) return; // No target — let normal arrow fire

        event.setCanceled(true);

        // Arrow base damage = 2.0, velocity = power * 3.0
        // Final arrow damage = baseDamage * velocity = 2.0 * power * 3.0 = 6.0 * power
        // Homing damage = 3x that
        float damage = 6.0F * power * 3.0F;

        ShulkerBullet bullet = new ShulkerBullet(
            serverLevel,
            player,
            target,
            Direction.Axis.Y
        );
        Vec3 eyePos = player.getEyePosition();
        bullet.setPos(eyePos.x, eyePos.y, eyePos.z);
        bullet.setYRot(player.getYRot());
        bullet.setXRot(player.getXRot());
        bullet.setDeltaMovement(player.getLookAngle().scale(power * 3.0));

        bulletDamageMap.put(bullet.getUUID(), damage);
        serverLevel.addFreshEntity(bullet);
    }

    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        Entity directEntity = event.getSource().getDirectEntity();
        if (!(directEntity instanceof ShulkerBullet bullet)) return;

        Float damage = bulletDamageMap.remove(bullet.getUUID());
        if (damage != null) {
            event.setAmount(damage);
        }
    }

    private static Entity findTarget(Player player) {
        // First try raycast along crosshair
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(
            player,
            entity ->
                entity instanceof LivingEntity &&
                !entity.isSpectator() &&
                entity.isAlive(),
            100.0
        );
        if (hit.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hit).getEntity();
        }

        // Fallback: find nearest living entity roughly in look direction
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        double searchRange = 50.0;
        AABB searchBox = player.getBoundingBox().inflate(searchRange);

        List<LivingEntity> entities = player
            .level()
            .getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator()
            );

        Entity best = null;
        double bestScore = -1.0;

        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity
                .position()
                .add(0, entity.getBbHeight() / 2, 0)
                .subtract(eyePos)
                .normalize();
            double dot = look.dot(toEntity);
            if (dot > 0.5 && dot > bestScore) {
                // Within ~60 degree cone
                bestScore = dot;
                best = entity;
            }
        }

        return best;
    }
}
