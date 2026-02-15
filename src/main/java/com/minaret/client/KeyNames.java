package com.minaret.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;

/**
 * Bidirectional mapping between human-readable key names and GLFW keycodes.
 * Letters a-z, digits 0-9, function keys f1-f12, and common punctuation.
 */
public final class KeyNames {

    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        for (int i = 0; i < 26; i++) {
            String name = String.valueOf((char) ('a' + i));
            put(name, InputConstants.KEY_A + i);
        }
        for (int i = 0; i <= 9; i++) {
            put(String.valueOf(i), InputConstants.KEY_0 + i);
        }
        put("space", InputConstants.KEY_SPACE);
        put("tab", InputConstants.KEY_TAB);
        put("minus", InputConstants.KEY_MINUS);
        put("equals", InputConstants.KEY_EQUALS);
        put("lbracket", InputConstants.KEY_LBRACKET);
        put("rbracket", InputConstants.KEY_RBRACKET);
        put("semicolon", InputConstants.KEY_SEMICOLON);
        put("comma", InputConstants.KEY_COMMA);
        put("period", InputConstants.KEY_PERIOD);
        put("slash", InputConstants.KEY_SLASH);
        for (int i = 1; i <= 12; i++) {
            try {
                int code = InputConstants.class.getField("KEY_F" + i).getInt(null);
                put("f" + i, code);
            } catch (Exception ignored) {}
        }
    }

    private KeyNames() {}

    private static void put(String name, int code) {
        NAME_TO_KEY.put(name, code);
        KEY_TO_NAME.put(code, name);
    }

    /** Returns keycode for name, or -1 if unknown. */
    public static int toKeyCode(String name) {
        Integer code = NAME_TO_KEY.get(name.toLowerCase());
        return code != null ? code : -1;
    }

    /** Returns name for keycode, or "?" if unknown. */
    public static String toName(int keyCode) {
        String name = KEY_TO_NAME.get(keyCode);
        return name != null ? name : "?";
    }
}
