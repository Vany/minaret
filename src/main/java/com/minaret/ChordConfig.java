package com.minaret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent config for chord key sequences.
 * Stored as JSON in config/minaret-chords.json.
 * Format: {"metaKey":"f","chords":["f>1","f>a>b"]}
 */
public class ChordConfig {

    private static final Path CONFIG_PATH = Path.of(
        "config",
        "minaret-chords.json"
    );

    private String metaKey = "f";
    private final Set<String> chords = new LinkedHashSet<>();

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

    public Set<String> getChords() {
        return Collections.unmodifiableSet(chords);
    }

    /** Add a chord sequence. Returns true if added, false if duplicate. */
    public boolean addChord(String sequence) {
        String normalized = sequence.toLowerCase();
        if (!chords.add(normalized)) return false;
        save();
        return true;
    }

    /** Remove a chord sequence. Returns true if it existed. */
    public boolean removeChord(String sequence) {
        String normalized = sequence.toLowerCase();
        if (!chords.remove(normalized)) return false;
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
            if (ch instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) chords.add(s);
                }
            }
            MinaretMod.LOGGER.info(
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
            root.put("chords", new ArrayList<>(chords));
            Files.writeString(CONFIG_PATH, SimpleJson.generate(root));
        } catch (IOException e) {
            MinaretMod.LOGGER.error("Failed to save chord config", e);
        }
    }
}
