package com.minaret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent config for chord key sequences.
 * Stored as TOML in config/minaret-chords.toml.
 *
 * Format:
 *   meta_key = "f"
 *
 *   [chords]
 *   "f>1" = "key:key.inventory"
 *   "f>2" = "cmd:{\"command\":\"time set day\"}"
 */
public class ChordConfig {

    private static final Path CONFIG_PATH = Path.of(
        "config",
        "minaret-chords.toml"
    );
    private static final Path LEGACY_JSON_PATH = Path.of(
        "config",
        "minaret-chords.json"
    );

    private String metaKey = "f";
    private final Map<String, ChordTarget> chords = new LinkedHashMap<>();

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

    public Map<String, ChordTarget> getChords() {
        return Collections.unmodifiableMap(chords);
    }

    public Set<String> getChordSequences() {
        return Collections.unmodifiableSet(chords.keySet());
    }

    /** Get the typed target for a chord, or null if not found. */
    public ChordTarget getTarget(String sequence) {
        return chords.get(sequence.toLowerCase());
    }

    /** Add a chord. Returns true if added, false if duplicate. */
    public boolean addChord(String sequence, ChordTarget target) {
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

    // ── TOML persistence ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void load() {
        // Migrate legacy JSON if TOML doesn't exist yet
        if (!Files.exists(CONFIG_PATH) && Files.exists(LEGACY_JSON_PATH)) {
            migrateFromJson();
            return;
        }

        if (!Files.exists(CONFIG_PATH)) return;
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH);
            chords.clear();
            boolean inChords = false;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.equals("[chords]")) {
                    inChords = true;
                    continue;
                }
                if (line.startsWith("[")) {
                    inChords = false;
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq < 0) continue;

                String key = unquoteToml(line.substring(0, eq).trim());
                String value = unquoteToml(line.substring(eq + 1).trim());

                if (!inChords) {
                    if (key.equals("meta_key")) metaKey = value;
                } else {
                    chords.put(key, ChordTarget.deserialize(value));
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
            StringBuilder sb = new StringBuilder();
            sb.append("meta_key = ").append(quoteToml(metaKey)).append('\n');
            sb.append('\n');
            sb.append("[chords]\n");
            for (var entry : chords.entrySet()) {
                sb
                    .append(quoteToml(entry.getKey()))
                    .append(" = ")
                    .append(quoteToml(entry.getValue().serialize()))
                    .append('\n');
            }
            Files.writeString(CONFIG_PATH, sb.toString());
        } catch (IOException e) {
            MinaretMod.LOGGER.error("Failed to save chord config", e);
        }
    }

    // ── TOML helpers ────────────────────────────────────────────────────

    private static String quoteToml(String value) {
        return "\"" + SimpleJson.escapeString(value) + "\"";
    }

    private static String unquoteToml(String value) {
        if (
            value.length() >= 2 &&
            value.startsWith("\"") &&
            value.endsWith("\"")
        ) {
            value = value.substring(1, value.length() - 1);
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append('\\').append(next);
                }
                i++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ── JSON migration ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void migrateFromJson() {
        try {
            String json = Files.readString(LEGACY_JSON_PATH).trim();
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
                        chords.put(key, ChordTarget.deserialize(value));
                    }
                }
            }

            save();
            MinaretMod.LOGGER.info(
                "Migrated {} chord keys from JSON to TOML",
                chords.size()
            );
        } catch (Exception e) {
            MinaretMod.LOGGER.error(
                "Failed to migrate chord config from JSON",
                e
            );
        }
    }
}
