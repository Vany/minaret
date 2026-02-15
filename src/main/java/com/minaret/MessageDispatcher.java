package com.minaret;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dispatches JSON messages to handlers: chat, command, getEffects.
 * Used by both WebSocketServer and /minaret exec command.
 */
public final class MessageDispatcher {

    private static final Logger LOGGER = LogManager.getLogger();

    private MessageDispatcher() {}

    /** Dispatch a JSON message string to the appropriate handler. */
    public static void dispatch(
        String message,
        MinecraftServer server,
        Consumer<String> respond
    ) {
        try {
            LOGGER.debug("Processing message: {}", message);
            Map<String, String> json = SimpleJson.parseFlat(message);

            if (json.containsKey("message")) {
                handleChat(json, server, respond);
            } else if (json.containsKey("command")) {
                handleCommand(json.get("command"), server, respond);
            } else if (json.containsKey("getEffects")) {
                handleGetEffects(json.get("getEffects"), server, respond);
            } else {
                respondError(
                    respond,
                    null,
                    "Unknown message type. Use 'message', 'command', or 'getEffects' fields."
                );
            }
        } catch (Exception e) {
            LOGGER.error("Error processing message: {}", message, e);
            respondError(respond, null, "Invalid JSON or processing error");
        }
    }

    private static void handleChat(
        Map<String, String> json,
        MinecraftServer server,
        Consumer<String> respond
    ) {
        String chatMessage = json.get("message");
        String user = json.getOrDefault("user", null);
        String chat = json.getOrDefault("chat", null);

        server.execute(() -> {
            StringBuilder sb = new StringBuilder();
            if (chat != null && !chat.isEmpty()) {
                sb.append("\u00a77[").append(chat).append("]");
            }
            if (user != null && !user.isEmpty()) {
                sb.append("\u00a7f<").append(user).append("> ");
            } else {
                sb.append("\u00a77[WebSocket] \u00a7f");
            }
            sb.append("\u00a7f").append(chatMessage);

            Component component = Component.literal(sb.toString());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(component);
            }
            LOGGER.info("Chat: {}", chatMessage);
        });

        respondSuccess(respond, "message");
    }

    private static void handleCommand(
        String command,
        MinecraftServer server,
        Consumer<String> respond
    ) {
        server.execute(() -> {
            try {
                CommandSourceStack source = server
                    .createCommandSourceStack()
                    .withSuppressedOutput();
                int result = server
                    .getCommands()
                    .getDispatcher()
                    .execute(command, source);

                if (result > 0) {
                    respondSuccess(
                        respond,
                        "command",
                        "command",
                        command,
                        "result",
                        String.valueOf(result)
                    );
                    LOGGER.info(
                        "Command executed: {} (result: {})",
                        command,
                        result
                    );
                } else {
                    respondError(
                        respond,
                        "command",
                        "Command returned 0 - may lack permissions, be invalid, or had no effect",
                        "command",
                        command,
                        "result",
                        String.valueOf(result)
                    );
                    LOGGER.warn(
                        "Command failed: {} (result: {})",
                        command,
                        result
                    );
                }
            } catch (Exception e) {
                respondError(
                    respond,
                    "command",
                    e.getMessage() != null
                        ? e.getMessage()
                        : "Command execution failed",
                    "command",
                    command
                );
                LOGGER.error("Command error: {}", command, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void handleGetEffects(
        String playerName,
        MinecraftServer server,
        Consumer<String> respond
    ) {
        server.execute(() -> {
            try {
                ServerPlayer player = server
                    .getPlayerList()
                    .getPlayerByName(playerName);
                if (player == null) {
                    respondError(
                        respond,
                        "getEffects",
                        "Player not found: " + playerName
                    );
                    return;
                }

                Collection<MobEffectInstance> effects =
                    player.getActiveEffects();
                List<Object> effectList = new ArrayList<>();
                for (MobEffectInstance effect : effects) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("effect", effect.getEffect().getRegisteredName());
                    e.put(
                        "duration",
                        effect.isInfiniteDuration() ? -1 : effect.getDuration()
                    );
                    e.put("amplifier", effect.getAmplifier());
                    effectList.add(e);
                }

                respondSuccess(
                    respond,
                    "getEffects",
                    "player",
                    playerName,
                    "effects",
                    effectList
                );
                LOGGER.info("getEffects for player: {}", playerName);
            } catch (Exception e) {
                respondError(
                    respond,
                    "getEffects",
                    e.getMessage() != null
                        ? e.getMessage()
                        : "Failed to get effects"
                );
            }
        });
    }

    // ── Response helpers ────────────────────────────────────────────────

    private static void respondSuccess(
        Consumer<String> respond,
        String type,
        Object... extra
    ) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "success");
        r.put("type", type);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            r.put((String) extra[i], extra[i + 1]);
        }
        respond.accept(SimpleJson.generate(r));
    }

    private static void respondError(
        Consumer<String> respond,
        String type,
        String error,
        String... extra
    ) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "error");
        r.put("error", error);
        if (type != null) r.put("type", type);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            r.put(extra[i], extra[i + 1]);
        }
        respond.accept(SimpleJson.generate(r));
    }
}
