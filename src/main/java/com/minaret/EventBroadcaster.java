package com.minaret;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Broadcasts server-side game events to all connected WebSocket clients.
 * Each handler builds a flat JSON string and calls WebSocketServer.broadcast().
 *
 * Events:
 *   player_join    — player logged in
 *   player_leave   — player logged out
 *   player_death   — player died (includes cause)
 *   player_kill    — player killed a mob (includes mob type)
 *   player_eat     — player finished eating food (includes item, nutrition, saturation)
 *   player_heal    — aggregated HP healed; fires when ≥10 HP accumulated or ≥1 min since last broadcast
 */
public class EventBroadcaster {

    private static final Logger LOGGER = LogManager.getLogger();

    // ── Heal aggregation ─────────────────────────────────────────────────

    private static final float HEAL_THRESHOLD = 10f;
    private static final long HEAL_TIMEOUT_MS = 60_000L;

    /** Per-player heal accumulator. All access is on the server thread. */
    private static final ConcurrentHashMap<UUID, HealAccum> healAccum = new ConcurrentHashMap<>();

    private record HealAccum(float total, long lastBroadcastMs) {
        HealAccum add(float amount) {
            return new HealAccum(total + amount, lastBroadcastMs);
        }
        HealAccum reset() {
            return new HealAccum(0f, System.currentTimeMillis());
        }
    }

    // ── Player join ──────────────────────────────────────────────────────

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws == null) return;
        String player = event.getEntity().getName().getString();
        ws.broadcast(
            "{\"event\":\"player_join\",\"player\":\"" + escape(player) + "\"}"
        );
    }

    // ── Player leave ─────────────────────────────────────────────────────

    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws == null) return;
        String player = event.getEntity().getName().getString();
        ws.broadcast(
            "{\"event\":\"player_leave\",\"player\":\"" + escape(player) + "\"}"
        );
    }

    // ── Death ────────────────────────────────────────────────────────────

    public static void onLivingDeath(LivingDeathEvent event) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws == null) return;

        // player died
        if (event.getEntity() instanceof ServerPlayer player) {
            String name = player.getName().getString();
            String cause = event.getSource().getMsgId();
            ws.broadcast(
                "{\"event\":\"player_death\"" +
                ",\"player\":\"" + escape(name) + "\"" +
                ",\"cause\":\"" + escape(cause) + "\"}"
            );
            return;
        }

        // player killed a mob
        if (event.getSource().getEntity() instanceof Player killer) {
            // only server-side kills
            if (!(killer instanceof ServerPlayer)) return;
            String killerName = killer.getName().getString();
            String mobType = event.getEntity().getType().toShortString();
            ws.broadcast(
                "{\"event\":\"player_kill\"" +
                ",\"player\":\"" + escape(killerName) + "\"" +
                ",\"mob\":\"" + escape(mobType) + "\"}"
            );
        }
    }

    // ── Player ate food ──────────────────────────────────────────────────

    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) return; // not food

        String playerName = player.getName().getString();
        String item = event.getItem().getItem().getDescriptionId();
        // strip "item.minecraft." / "block.minecraft." prefix for readability
        int dot = item.lastIndexOf('.');
        String itemShort = dot >= 0 ? item.substring(dot + 1) : item;
        int nutrition = food.nutrition();
        float saturation = food.saturation();

        ws.broadcast(
            "{\"event\":\"player_eat\"" +
            ",\"player\":\"" + escape(playerName) + "\"" +
            ",\"item\":\"" + escape(itemShort) + "\"" +
            ",\"nutrition\":" + nutrition +
            ",\"saturation\":" + saturation + "}"
        );
    }

    // ── Player healed (aggregated) ───────────────────────────────────────

    public static void onLivingHeal(LivingHealEvent event) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        float amount = event.getAmount();

        HealAccum accum = healAccum
            .getOrDefault(uuid, new HealAccum(0f, System.currentTimeMillis()))
            .add(amount);

        long now = System.currentTimeMillis();
        boolean thresholdReached = accum.total() >= HEAL_THRESHOLD;
        boolean timedOut = (now - accum.lastBroadcastMs()) >= HEAL_TIMEOUT_MS;

        if (thresholdReached || timedOut) {
            ws.broadcast(
                "{\"event\":\"player_heal\"" +
                ",\"player\":\"" + escape(name) + "\"" +
                ",\"amount\":" + String.format("%.1f", accum.total()) + "}"
            );
            healAccum.put(uuid, accum.reset());
        } else {
            healAccum.put(uuid, accum);
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /** Minimal JSON string escaping — backslash and double-quote only. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
