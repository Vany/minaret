package com.minaret.client;

import com.minaret.ChordConfig;
import com.minaret.Compat;
import com.minaret.MessageDispatcher;
import com.minaret.MinaretMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Trie-based chord key state machine. Detects emacs-style key sequences
 * (e.g. f>1) and fires actions. Client-side only.
 *
 * Two chord target types:
 *   key:<name>  — fire a KeyMapping by name (e.g. key:key.inventory)
 *   cmd:<json>  — dispatch JSON as a WebSocket command (e.g. cmd:{"command":"time set day"})
 *
 * Key consumption: InputEvent.Key fires AFTER KeyMapping.click() has already
 * incremented clickCount. We reset clickCount via reflection to prevent
 * consumed keys from triggering game actions.
 */
public class ChordKeyHandler {

    private static final long TIMEOUT_MS = 1500;

    // ── Reflection for key consumption ──────────────────────────────────

    private static Field clickCountField;
    private static Field keyField;

    static {
        try {
            clickCountField = KeyMapping.class.getDeclaredField("clickCount");
            clickCountField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    clickCountField = f;
                    break;
                }
            }
        }
        try {
            keyField = KeyMapping.class.getDeclaredField("key");
            keyField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {}
    }

    /** Reset clickCount and isDown on all KeyMappings bound to this key. */
    private static void consumeKey(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || clickCountField == null) return;
        InputConstants.Key inputKey = InputConstants.Type.KEYSYM.getOrCreate(
            keyCode
        );
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.isActiveAndMatches(inputKey)) {
                try {
                    clickCountField.setInt(km, 0);
                } catch (IllegalAccessException ignored) {}
                km.setDown(false);
            }
        }
    }

    // ── Meta key (configurable KeyMapping in Controls) ───────────────────

    private static Object metaKeyMapping;

    /** Called from RegisterKeyMappingsEvent handler. */
    public static void registerKeys(Object event) {
        int metaCode = nameToKeyCode(ChordConfig.get().getMetaKey());
        if (metaCode < 0) metaCode = InputConstants.KEY_F;
        metaKeyMapping = Compat.createKeyMapping(
            "Chord Meta Key",
            metaCode,
            "chords"
        );
        Compat.registerCategory(event);
        Compat.registerKeyMapping(event, metaKeyMapping);
    }

    // ── Chord firing ────────────────────────────────────────────────────

    private static KeyMapping findKeyMapping(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return null;
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.getName().equals(name)) return km;
        }
        return null;
    }

    private static void fireKeyTarget(String keyMappingName) {
        KeyMapping target = findKeyMapping(keyMappingName);
        if (target == null) {
            MinaretMod.LOGGER.warn(
                "Target KeyMapping '{}' not found",
                keyMappingName
            );
            return;
        }
        try {
            InputConstants.Key boundKey = (InputConstants.Key) keyField.get(
                target
            );
            KeyMapping.click(boundKey);
        } catch (Exception e) {
            MinaretMod.LOGGER.error(
                "Failed to click target '{}'",
                keyMappingName,
                e
            );
        }
    }

    private static void fireCmdTarget(String json) {
        var server = MinaretMod.getServer();
        if (server == null) {
            MinaretMod.LOGGER.warn("Cannot execute chord command — no server");
            return;
        }
        MessageDispatcher.dispatch(json, server, response ->
            MinaretMod.LOGGER.debug("Chord command response: {}", response)
        );
    }

    /** Get all registered KeyMapping names. */
    public static List<String> getAllKeyMappingNames() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return names;
        for (KeyMapping km : mc.options.keyMappings) {
            names.add(km.getName());
        }
        return names;
    }

    // ── Trie ────────────────────────────────────────────────────────────

    static class TrieNode {

        final Map<Integer, TrieNode> children = new HashMap<>();
        String sequence;
    }

    private static TrieNode trieRoot = new TrieNode();

    public static void rebuildTrie() {
        TrieNode root = new TrieNode();
        for (String sequence : ChordConfig.get().getChordSequences()) {
            String[] parts = sequence.split(">");
            TrieNode node = root;
            boolean valid = true;
            for (String part : parts) {
                int keyCode = nameToKeyCode(part.trim());
                if (keyCode < 0) {
                    MinaretMod.LOGGER.warn(
                        "Unknown key '{}' in chord '{}'",
                        part.trim(),
                        sequence
                    );
                    valid = false;
                    break;
                }
                node = node.children.computeIfAbsent(keyCode, k ->
                    new TrieNode()
                );
            }
            if (valid) node.sequence = sequence;
        }
        trieRoot = root;
    }

    // ── State machine ───────────────────────────────────────────────────

    private static TrieNode currentNode;
    private static long stateTimestamp;
    private static final StringBuilder chordDisplay = new StringBuilder();

    private static void resetState() {
        currentNode = null;
        chordDisplay.setLength(0);
    }

    private static boolean isActive() {
        return currentNode != null;
    }

    private static boolean isTimedOut() {
        return (
            isActive() &&
            System.currentTimeMillis() - stateTimestamp > TIMEOUT_MS
        );
    }

    private static void fireChord(String sequence) {
        String target = ChordConfig.get().getTarget(sequence);
        if (target == null) {
            MinaretMod.LOGGER.warn("Chord '{}' has no target", sequence);
        } else if (target.startsWith(ChordConfig.KEY_PREFIX)) {
            fireKeyTarget(target.substring(ChordConfig.KEY_PREFIX.length()));
        } else if (target.startsWith(ChordConfig.CMD_PREFIX)) {
            fireCmdTarget(target.substring(ChordConfig.CMD_PREFIX.length()));
        } else {
            // Legacy: bare KeyMapping name without prefix
            fireKeyTarget(target);
        }
        resetState();
    }

    private static void showOverlay(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.setOverlayMessage(
            Component.literal(text),
            false
        );
    }

    private static int getMetaKeyCode() {
        if (metaKeyMapping instanceof KeyMapping km) return km
            .getKey()
            .getValue();
        return nameToKeyCode(ChordConfig.get().getMetaKey());
    }

    // ── Input handling ──────────────────────────────────────────────────

    private static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int key = event.getKey();
        if (isTimedOut()) resetState();

        if (!isActive()) {
            int metaKey = getMetaKeyCode();
            if (
                metaKey >= 0 && key == metaKey && !trieRoot.children.isEmpty()
            ) {
                TrieNode next = trieRoot.children.get(key);
                if (next != null) {
                    consumeKey(key);
                    if (next.sequence != null && next.children.isEmpty()) {
                        fireChord(next.sequence);
                        return;
                    }
                    currentNode = next;
                    stateTimestamp = System.currentTimeMillis();
                    chordDisplay.setLength(0);
                    chordDisplay.append(keyCodeToName(key)).append(" > _");
                    showOverlay(chordDisplay.toString());
                }
            }
            return;
        }

        consumeKey(key);
        TrieNode next = currentNode.children.get(key);
        if (next == null) {
            if (currentNode.sequence != null) {
                fireChord(currentNode.sequence);
            } else {
                showOverlay("Chord cancelled");
                resetState();
            }
            return;
        }
        if (next.sequence != null && next.children.isEmpty()) {
            fireChord(next.sequence);
        } else {
            currentNode = next;
            stateTimestamp = System.currentTimeMillis();
            chordDisplay.setLength(chordDisplay.length() - 1);
            chordDisplay.append(keyCodeToName(key)).append(" > _");
            showOverlay(chordDisplay.toString());
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (!isActive()) return;
        if (isTimedOut()) {
            if (currentNode.sequence != null) fireChord(currentNode.sequence);
            else resetState();
        }
    }

    // ── Key name ↔ keycode mapping ──────────────────────────────────────

    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        for (int i = 0; i < 26; i++) {
            String name = String.valueOf((char) ('a' + i));
            NAME_TO_KEY.put(name, InputConstants.KEY_A + i);
            KEY_TO_NAME.put(InputConstants.KEY_A + i, name);
        }
        for (int i = 0; i <= 9; i++) {
            NAME_TO_KEY.put(String.valueOf(i), InputConstants.KEY_0 + i);
            KEY_TO_NAME.put(InputConstants.KEY_0 + i, String.valueOf(i));
        }
        putKey("space", InputConstants.KEY_SPACE);
        putKey("tab", InputConstants.KEY_TAB);
        putKey("minus", InputConstants.KEY_MINUS);
        putKey("equals", InputConstants.KEY_EQUALS);
        putKey("lbracket", InputConstants.KEY_LBRACKET);
        putKey("rbracket", InputConstants.KEY_RBRACKET);
        putKey("semicolon", InputConstants.KEY_SEMICOLON);
        putKey("comma", InputConstants.KEY_COMMA);
        putKey("period", InputConstants.KEY_PERIOD);
        putKey("slash", InputConstants.KEY_SLASH);
        for (int i = 1; i <= 12; i++) {
            try {
                int code = InputConstants.class.getField("KEY_F" + i).getInt(
                    null
                );
                putKey("f" + i, code);
            } catch (Exception ignored) {}
        }
    }

    private static void putKey(String name, int code) {
        NAME_TO_KEY.put(name, code);
        KEY_TO_NAME.put(code, name);
    }

    public static int nameToKeyCode(String name) {
        Integer code = NAME_TO_KEY.get(name.toLowerCase());
        return code != null ? code : -1;
    }

    public static String keyCodeToName(int keyCode) {
        String name = KEY_TO_NAME.get(keyCode);
        return name != null ? name : "?";
    }

    public static String validateSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) return "Empty sequence";
        String[] parts = sequence.toLowerCase().split(">");
        if (
            parts.length < 2
        ) return "Sequence must have at least 2 keys (e.g. f>1)";
        if (!parts[0].trim().equals(ChordConfig.get().getMetaKey())) {
            return (
                "First key must be the meta key '" +
                ChordConfig.get().getMetaKey() +
                "'"
            );
        }
        for (String part : parts) {
            if (nameToKeyCode(part.trim()) < 0) return (
                "Unknown key: '" + part.trim() + "'"
            );
        }
        return null;
    }

    // ── Initialization ──────────────────────────────────────────────────

    public static void init(IEventBus modEventBus) {
        ChordConfig.get().load();
        rebuildTrie();
        NeoForge.EVENT_BUS.addListener(ChordKeyHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ChordKeyHandler::onClientTick);
        MinaretMod.LOGGER.debug(
            "Chord key handler initialized ({} chords)",
            ChordConfig.get().getChordSequences().size()
        );
    }
}
