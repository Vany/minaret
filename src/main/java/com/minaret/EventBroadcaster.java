package com.minaret;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Broadcasts server-side game events to all connected WebSocket clients.
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

    // ── Broadcast helper ─────────────────────────────────────────────────

    /** Send a JSON string to all connected clients. No-op if server not running. */
    private static void broadcast(String json) {
        WebSocketServer ws = MinaretMod.getWebSocketServer();
        if (ws != null) ws.broadcast(json);
    }

    // ── JSON helper ──────────────────────────────────────────────────────

    /** Build an event JSON string with "event" first, then additional key-value pairs. */
    private static String event(String type, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event", type);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return SimpleJson.generate(m);
    }

    // ── Player join ──────────────────────────────────────────────────────

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        broadcast(event("player_join", "player", event.getEntity().getName().getString()));
    }

    // ── Player leave ─────────────────────────────────────────────────────

    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        broadcast(event("player_leave", "player", event.getEntity().getName().getString()));
    }

    // ── Death ────────────────────────────────────────────────────────────

    public static void onLivingDeath(LivingDeathEvent event) {
        // player died
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcast(event("player_death",
                "player", player.getName().getString(),
                "cause",  event.getSource().getMsgId()
            ));
            return;
        }

        // player killed a mob
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            broadcast(event("player_kill",
                "player", killer.getName().getString(),
                "mob",    event.getEntity().getType().toShortString()
            ));
        }
    }

    // ── Player ate food ──────────────────────────────────────────────────

    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) return;

        String item = event.getItem().getItem().getDescriptionId();
        int dot = item.lastIndexOf('.');
        String itemShort = dot >= 0 ? item.substring(dot + 1) : item;

        broadcast(event("player_eat",
            "player",     player.getName().getString(),
            "item",       itemShort,
            "nutrition",  food.nutrition(),
            "saturation", food.saturation()
        ));
    }

    // ── Player healed (aggregated) ───────────────────────────────────────

    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        HealAccum accum = healAccum
            .getOrDefault(uuid, new HealAccum(0f, System.currentTimeMillis()))
            .add(event.getAmount());

        long now = System.currentTimeMillis();
        boolean thresholdReached = accum.total() >= HEAL_THRESHOLD;
        boolean timedOut = (now - accum.lastBroadcastMs()) >= HEAL_TIMEOUT_MS;

        if (thresholdReached || timedOut) {
            broadcast(event("player_heal",
                "player", player.getName().getString(),
                "amount", accum.total()
            ));
            healAccum.put(uuid, accum.reset());
        } else {
            healAccum.put(uuid, accum);
        }
    }
}
