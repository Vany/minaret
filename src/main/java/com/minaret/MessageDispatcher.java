package com.minaret;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dispatches JSON messages to handlers: chat, command, getEffects.
 * Used by both WebSocketServer and /minaret exec command.
 */
public final class MessageDispatcher {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String COLOR_GRAY = "\u00a77";
    private static final String COLOR_WHITE = "\u00a7f";

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
            } else if (json.containsKey("use")) {
                handleUse(json, server, respond);
            } else {
                respondError(
                    respond,
                    null,
                    "Unknown message type. Use 'message', 'command', 'getEffects', or 'use' fields."
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
                sb.append(COLOR_GRAY).append("[").append(chat).append("]");
            }
            if (user != null && !user.isEmpty()) {
                sb.append(COLOR_WHITE).append("<").append(user).append("> ");
            } else {
                sb
                    .append(COLOR_GRAY)
                    .append("[WebSocket] ")
                    .append(COLOR_WHITE);
            }
            sb.append(COLOR_WHITE).append(chatMessage);

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

    /**
     * Switches the player to the specified hotbar slot (0–8) and simulates a
     * right-click use of the item in that slot. Runs on the server thread.
     *
     * JSON: {"use": "playerName", "slot": 3}
     */
    private static void handleUse(
        Map<String, String> json,
        MinecraftServer server,
        Consumer<String> respond
    ) {
        String playerName = json.get("use");
        String slotStr = json.get("slot");

        if (slotStr == null) {
            respondError(respond, "use", "Missing 'slot' field");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            respondError(respond, "use", "Invalid slot: " + slotStr);
            return;
        }
        if (slot < 0 || slot > 8) {
            respondError(respond, "use", "Slot must be 0–8, got: " + slot);
            return;
        }

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
            if (player == null) {
                respondError(respond, "use", "Player not found: " + playerName);
                return;
            }

            Inventory inv = player.getInventory();
            int prevSlot = Compat.getInventorySlot(inv);
            Compat.setInventorySlot(inv, slot);

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            String itemId = stack.isEmpty() ? "air" : stack.getItem().getDescriptionId();

            try {
                player.gameMode.useItem(player, player.level(), stack, InteractionHand.MAIN_HAND);
                respondSuccess(
                    respond, "use",
                    "player", playerName,
                    "slot", String.valueOf(slot),
                    "item", itemId
                );
                LOGGER.info("use: player={} slot={} item={}", playerName, slot, itemId);
            } catch (Exception e) {
                respondError(
                    respond, "use",
                    e.getMessage() != null ? e.getMessage() : "useItem failed",
                    "player", playerName,
                    "slot", String.valueOf(slot)
                );
                LOGGER.error("use failed: player={} slot={}", playerName, slot, e);
            } finally {
                Compat.setInventorySlot(inv, prevSlot);
            }
        });
    }

    // ── Response helpers ────────────────────────────────────────────────

    private static void respondSuccess(
        Consumer<String> respond,
        String type,
        Object... extra
    ) {
        respond(respond, "success", type, null, extra);
    }

    private static void respondError(
        Consumer<String> respond,
        String type,
        String error,
        Object... extra
    ) {
        respond(respond, "error", type, error, extra);
    }

    private static void respond(
        Consumer<String> respond,
        String status,
        String type,
        String error,
        Object... extra
    ) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", status);
        if (error != null) r.put("error", error);
        if (type != null) r.put("type", type);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            r.put((String) extra[i], extra[i + 1]);
        }
        respond.accept(SimpleJson.generate(r));
    }
}
