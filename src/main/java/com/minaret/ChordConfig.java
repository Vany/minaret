package com.minaret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent config for chord key sequences.
 * Stored as JSON in config/minaret-chords.json.
 *
 * Chord targets use a prefix convention:
 *   "key:key.inventory"         → fire a KeyMapping action
 *   "cmd:{\"command\":\"...\"}" → execute as WebSocket JSON command
 *
 * Format: {"metaKey":"f","chords":{"f>1":"key:key.inventory","f>2":"cmd:{\"command\":\"time set day\"}"}}
 */
public class ChordConfig {

    public static final String KEY_PREFIX = "key:";
    public static final String CMD_PREFIX = "cmd:";

    private static final Path CONFIG_PATH = Path.of(
        "config",
        "minaret-chords.json"
    );

    private String metaKey = "f";
    private final Map<String, String> chords = new LinkedHashMap<>();

    private static final ChordConfig INSTANCE = new ChordConfig();

    public static ChordConfig get() {
        return INSTANCE;
    }

    private ChordConfig() {}

    public String getMetaKey() {
        return metaKey;
    }

    public void setMetaKey(String key) {
        this.metaKey = key.toLowerCase();
        save();
    }

    public Map<String, String> getChords() {
        return Collections.unmodifiableMap(chords);
    }

    public Set<String> getChordSequences() {
        return Collections.unmodifiableSet(chords.keySet());
    }

    /** Get the raw target string for a chord, or null if not found. */
    public String getTarget(String sequence) {
        return chords.get(sequence.toLowerCase());
    }

    /** Add a chord. Returns true if added, false if duplicate. */
    public boolean addChord(String sequence, String target) {
        String normalized = sequence.toLowerCase();
        if (chords.containsKey(normalized)) return false;
        chords.put(normalized, target);
        save();
        return true;
    }

    /** Remove a chord sequence. Returns true if it existed. */
    public boolean removeChord(String sequence) {
        String normalized = sequence.toLowerCase();
        if (chords.remove(normalized) == null) return false;
        save();
        return true;
    }

    // ── JSON persistence ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String json = Files.readString(CONFIG_PATH).trim();
            Object parsed = SimpleJson.parseValue(json);
            if (!(parsed instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) parsed;

            Object mk = root.get("metaKey");
            if (mk instanceof String s) metaKey = s;

            chords.clear();
            Object ch = root.get("chords");

            if (ch instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    if (
                        entry.getKey() instanceof String key &&
                        entry.getValue() instanceof String value
                    ) {
                        chords.put(key, value);
                    }
                }
            }

            MinaretMod.LOGGER.debug(
                "Loaded {} chord keys (meta: {})",
                chords.size(),
                metaKey
            );
        } catch (Exception e) {
            MinaretMod.LOGGER.error("Failed to load chord config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("metaKey", metaKey);
            Map<String, Object> chordsMap = new LinkedHashMap<>(chords);
            root.put("chords", chordsMap);
            Files.writeString(CONFIG_PATH, SimpleJson.generate(root));
        } catch (IOException e) {
            MinaretMod.LOGGER.error("Failed to save chord config", e);
        }
    }
}
