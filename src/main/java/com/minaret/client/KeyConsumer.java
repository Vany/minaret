package com.minaret.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Reflection-based key consumption. Resets clickCount and isDown on
 * KeyMappings to prevent consumed chord keys from triggering game actions.
 *
 * InputEvent.Key fires AFTER KeyMapping.click() has already incremented
 * clickCount, so we undo that increment via reflection.
 */
public final class KeyConsumer {

    private static final Field CLICK_COUNT;
    private static final Field KEY_FIELD;

    static {
        CLICK_COUNT = resolveClickCount();
        KEY_FIELD = resolveField("key");
    }

    private KeyConsumer() {}

    /** Reset clickCount to 0 and setDown(false) on all KeyMappings bound to this key. */
    public static void consumeKey(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || CLICK_COUNT == null) return;
        InputConstants.Key inputKey = InputConstants.Type.KEYSYM.getOrCreate(
            keyCode
        );
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.isActiveAndMatches(inputKey)) {
                try {
                    CLICK_COUNT.setInt(km, 0);
                } catch (IllegalAccessException ignored) {}
                km.setDown(false);
            }
        }
    }

    /** Trigger the action bound to a KeyMapping by simulating a click on its bound key. */
    public static void clickKeyMapping(KeyMapping target) {
        if (KEY_FIELD == null) return;
        try {
            InputConstants.Key boundKey = (InputConstants.Key) KEY_FIELD.get(
                target
            );
            KeyMapping.click(boundKey);
        } catch (Exception ignored) {}
    }

    // ── Reflection resolution ───────────────────────────────────────────

    private static Field resolveClickCount() {
        try {
            Field f = KeyMapping.class.getDeclaredField("clickCount");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            // Fallback: scan for first int field (obfuscated environments)
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return f;
                }
            }
            return null;
        }
    }

    private static Field resolveField(String name) {
        try {
            Field f = KeyMapping.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
