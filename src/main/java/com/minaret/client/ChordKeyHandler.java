package com.minaret.client;

import com.minaret.ChordConfig;
import com.minaret.ChordTarget;
import com.minaret.Compat;
import com.minaret.MessageDispatcher;
import com.minaret.MinaretMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
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
 * Delegates to:
 *   {@link KeyNames}    — key name ↔ keycode translation
 *   {@link ChordTrie}   — trie construction from chord sequences
 *   {@link KeyConsumer}  — reflection-based key consumption
 */
public class ChordKeyHandler {

    private static final long TIMEOUT_MS = 1500;

    // ── Meta key ────────────────────────────────────────────────────────

    private static Object metaKeyMapping;

    public static void registerKeys(Object event) {
        int metaCode = KeyNames.toKeyCode(ChordConfig.get().getMetaKey());
        if (metaCode < 0) metaCode = InputConstants.KEY_F;
        metaKeyMapping = Compat.createKeyMapping(
            "Chord Meta Key",
            metaCode,
            "chords"
        );
        Compat.registerCategory(event);
        Compat.registerKeyMapping(event, metaKeyMapping);
    }

    private static int getMetaKeyCode() {
        if (metaKeyMapping instanceof KeyMapping km) return km
            .getKey()
            .getValue();
        return KeyNames.toKeyCode(ChordConfig.get().getMetaKey());
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
        KeyConsumer.clickKeyMapping(target);
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

    private static void fireChord(String sequence) {
        ChordTarget target = ChordConfig.get().getTarget(sequence);
        if (target == null) {
            MinaretMod.LOGGER.warn("Chord '{}' has no target", sequence);
        } else {
            switch (target) {
                case ChordTarget.Key k -> fireKeyTarget(k.mappingName());
                case ChordTarget.Command c -> fireCmdTarget(c.json());
            }
        }
        resetState();
    }

    // ── Public API ──────────────────────────────────────────────────────

    public static List<String> getAllKeyMappingNames() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return names;
        for (KeyMapping km : mc.options.keyMappings) {
            names.add(km.getName());
        }
        return names;
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
            if (KeyNames.toKeyCode(part.trim()) < 0) {
                return "Unknown key: '" + part.trim() + "'";
            }
        }
        return null;
    }

    // ── Trie ────────────────────────────────────────────────────────────

    private static ChordTrie.Node trieRoot = new ChordTrie.Node();

    public static void rebuildTrie() {
        trieRoot = ChordTrie.build(ChordConfig.get().getChordSequences());
    }

    // ── State machine ───────────────────────────────────────────────────

    private static ChordTrie.Node currentNode;
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

    private static void showOverlay(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.setOverlayMessage(
            Component.literal(text),
            false
        );
    }

    // ── Input handling ──────────────────────────────────────────────────

    private static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int key = event.getKey();
        if (isTimedOut()) resetState();

        if (!isActive()) {
            handleIdleKey(key);
        } else {
            handleActiveKey(key);
        }
    }

    private static void handleIdleKey(int key) {
        int metaKey = getMetaKeyCode();
        if (
            metaKey < 0 || key != metaKey || trieRoot.children.isEmpty()
        ) return;

        ChordTrie.Node next = trieRoot.children.get(key);
        if (next == null) return;

        KeyConsumer.consumeKey(key);
        if (next.sequence != null && next.children.isEmpty()) {
            fireChord(next.sequence);
            return;
        }
        currentNode = next;
        stateTimestamp = System.currentTimeMillis();
        chordDisplay.setLength(0);
        chordDisplay.append(KeyNames.toName(key)).append(" > _");
        showOverlay(chordDisplay.toString());
    }

    private static void handleActiveKey(int key) {
        KeyConsumer.consumeKey(key);
        ChordTrie.Node next = currentNode.children.get(key);

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
            chordDisplay.append(KeyNames.toName(key)).append(" > _");
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
