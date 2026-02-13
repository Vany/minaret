package com.minaret;

import java.util.Iterator;
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

    private static final float ARROW_BASE_DAMAGE = 2.0F;
    private static final float VELOCITY_SCALE = 3.0F;
    private static final float DAMAGE_MULTIPLIER = 3.0F;
    private static final float MIN_POWER = 0.1F;
    private static final double RAYCAST_RANGE = 100.0;
    private static final double SEARCH_RANGE = 50.0;
    private static final double AIM_CONE_THRESHOLD = 0.5;
    private static final long STALE_ENTRY_MILLIS = 60_000L;

    private record TrackedBullet(float damage, long createdAt) {}

    private static final Map<UUID, TrackedBullet> bulletDamageMap =
        new ConcurrentHashMap<>();

    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (!player.hasEffect(MinaretMod.HOMING_ARCHERY)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < MIN_POWER) return;

        Entity target = findTarget(player);
        if (target == null) return;

        event.setCanceled(true);
        cleanupStaleEntries();

        float damage =
            ARROW_BASE_DAMAGE * VELOCITY_SCALE * power * DAMAGE_MULTIPLIER;

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
        bullet.setDeltaMovement(
            player.getLookAngle().scale(power * VELOCITY_SCALE)
        );

        bulletDamageMap.put(
            bullet.getUUID(),
            new TrackedBullet(damage, System.currentTimeMillis())
        );
        serverLevel.addFreshEntity(bullet);
    }

    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        Entity directEntity = event.getSource().getDirectEntity();
        if (!(directEntity instanceof ShulkerBullet bullet)) return;

        TrackedBullet tracked = bulletDamageMap.remove(bullet.getUUID());
        if (tracked != null) {
            event.setAmount(tracked.damage);
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
            RAYCAST_RANGE
        );
        if (hit.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hit).getEntity();
        }

        // Fallback: nearest living entity roughly in look direction
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        AABB searchBox = player.getBoundingBox().inflate(SEARCH_RANGE);

        List<LivingEntity> entities = player
            .level()
            .getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator()
            );

        Entity best = null;
        double bestAlignment = -1.0;

        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity
                .position()
                .add(0, entity.getBbHeight() / 2, 0)
                .subtract(eyePos)
                .normalize();
            double dot = look.dot(toEntity);
            if (dot > AIM_CONE_THRESHOLD && dot > bestAlignment) {
                bestAlignment = dot;
                best = entity;
            }
        }

        return best;
    }

    private static void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, TrackedBullet>> it = bulletDamageMap
            .entrySet()
            .iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().createdAt > STALE_ENTRY_MILLIS) {
                it.remove();
            }
        }
    }
}
