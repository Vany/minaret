package com.minaret;

import java.lang.reflect.Field;
import net.minecraft.world.level.BaseSpawner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reflection-based accessor for BaseSpawner private fields.
 * Handles cross-version field name and obfuscation changes.
 */
public final class SpawnerAccessor {

    private static final Logger LOGGER = LogManager.getLogger();

    private static volatile Field rangeField;
    private static volatile Field minDelayField;
    private static volatile Field maxDelayField;
    private static boolean probed;

    static {
        rangeField = findByName("requiredPlayerRange");
        minDelayField = findByName("minSpawnDelay");
        maxDelayField = findByName("maxSpawnDelay");
    }

    private SpawnerAccessor() {}

    // ── Public API ──────────────────────────────────────────────────────

    public static boolean isAvailable() {
        return rangeField != null;
    }

    /** Ensure fields are resolved — probes by value if name lookup failed. */
    public static synchronized void ensureResolved(BaseSpawner spawner) {
        if (probed) return;
        probed = true;
        if (rangeField == null) rangeField = probeByValue(spawner, 16, "requiredPlayerRange");
        if (minDelayField == null) minDelayField = probeByValue(spawner, 200, "minSpawnDelay");
        if (maxDelayField == null) maxDelayField = probeByValue(spawner, 800, "maxSpawnDelay");
    }

    public static int getRange(BaseSpawner spawner) {
        return getInt(rangeField, spawner, "requiredPlayerRange");
    }

    public static void setRange(BaseSpawner spawner, int value) {
        setInt(rangeField, spawner, value, "requiredPlayerRange");
    }

    public static int getMinDelay(BaseSpawner spawner) {
        return getInt(minDelayField, spawner, "minSpawnDelay");
    }

    public static void setMinDelay(BaseSpawner spawner, int value) {
        setInt(minDelayField, spawner, value, "minSpawnDelay");
    }

    public static int getMaxDelay(BaseSpawner spawner) {
        return getInt(maxDelayField, spawner, "maxSpawnDelay");
    }

    public static void setMaxDelay(BaseSpawner spawner, int value) {
        setInt(maxDelayField, spawner, value, "maxSpawnDelay");
    }

    public static boolean hasDelayFields() {
        return minDelayField != null && maxDelayField != null;
    }

    // ── Reflection helpers ──────────────────────────────────────────────

    private static int getInt(Field field, BaseSpawner spawner, String hint) {
        if (field == null) return -1;
        try {
            return field.getInt(spawner);
        } catch (Exception e) {
            LOGGER.error("Failed to read BaseSpawner.{}", hint, e);
            return -1;
        }
    }

    private static void setInt(Field field, BaseSpawner spawner, int value, String hint) {
        if (field == null) return;
        try {
            field.setInt(spawner, value);
        } catch (Exception e) {
            LOGGER.error("Failed to set BaseSpawner.{}", hint, e);
        }
    }

    private static Field findByName(String name) {
        try {
            Field f = BaseSpawner.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Field probeByValue(BaseSpawner spawner, int value, String hint) {
        try {
            for (Field f : BaseSpawner.class.getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                f.setAccessible(true);
                if (f.getInt(spawner) == value) {
                    LOGGER.info("Found BaseSpawner.{} as {}", hint, f.getName());
                    return f;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error probing for BaseSpawner.{}", hint, e);
        }
        LOGGER.warn("Could not find BaseSpawner.{}", hint);
        return null;
    }
}
