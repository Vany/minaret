package com.minaret;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * Cancels teleportation for any entity within range of a warding column that
 * contains at least one TeleporterInhibitorBlock.
 *
 * Uses WardingColumnBlockEntity.inhibitorRegistry for O(n) lookup instead of
 * scanning all blocks in radius.
 * Skips player-commanded teleports (/tp, spreadplayers).
 */
public final class WardingPostTeleportHandler {

    private WardingPostTeleportHandler() {}

    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event instanceof EntityTeleportEvent.TeleportCommand) return;
        if (event instanceof EntityTeleportEvent.SpreadPlayersCommand) return;

        ServerLevel level = (ServerLevel) event.getEntity().level();
        ResourceKey<Level> key = level.dimension();
        Set<BlockPos> registry = WardingColumnBlockEntity.inhibitorRegistry.get(key);
        if (registry == null || registry.isEmpty()) return;

        double ex = event.getEntity().getX();
        double ez = event.getEntity().getZ();

        for (BlockPos pos : registry) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardingColumnBlockEntity wbe)) continue;
            if (!wbe.isBottomOfColumn || !wbe.hasInhibit) continue;

            double dx = ex - (pos.getX() + 0.5);
            double dz = ez - (pos.getZ() + 0.5);
            double r = wbe.totalRadius;
            if (dx * dx + dz * dz <= r * r) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
