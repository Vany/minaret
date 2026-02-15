package com.minaret;

/**
 * Typed representation of a chord action target.
 * Replaces string-prefix dispatch (key:/cmd:) with sealed types.
 */
public sealed interface ChordTarget {

    /** Fire a KeyMapping by name (client-side). */
    record Key(String mappingName) implements ChordTarget {}

    /** Execute a WebSocket JSON command (server-side). */
    record Command(String json) implements ChordTarget {}

    // ── Serialization (TOML storage format) ─────────────────────────────

    String KEY_PREFIX = "key:";
    String CMD_PREFIX = "cmd:";

    /** Serialize to the TOML-stored string format. */
    default String serialize() {
        return switch (this) {
            case Key k -> KEY_PREFIX + k.mappingName;
            case Command c -> CMD_PREFIX + c.json;
        };
    }

    /** Deserialize from the TOML-stored string format. */
    static ChordTarget deserialize(String raw) {
        if (raw.startsWith(CMD_PREFIX)) {
            return new Command(raw.substring(CMD_PREFIX.length()));
        }
        if (raw.startsWith(KEY_PREFIX)) {
            return new Key(raw.substring(KEY_PREFIX.length()));
        }
        // Legacy: bare KeyMapping name
        return new Key(raw);
    }
}
