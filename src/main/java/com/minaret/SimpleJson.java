package com.minaret;

import java.util.*;

/**
 * Minimal JSON parser/generator — supports strings, numbers, booleans, null,
 * nested objects, and arrays. No external dependencies.
 *
 * Parse:  Object result = SimpleJson.parseValue(jsonString)
 *         → returns Map<String,Object>, List<Object>, String, Number, Boolean, or null
 *
 * Generate: SimpleJson.generate(map) → compact JSON string
 *
 * Flat convenience: SimpleJson.parseFlat(json) → Map<String,String> (legacy compat)
 */
public class SimpleJson {

    // ── Parser ──────────────────────────────────────────────────────────

    private final String src;
    private int pos;

    private SimpleJson(String src) {
        this.src = src;
        this.pos = 0;
    }

    /** Parse a JSON value (object, array, string, number, boolean, null). */
    public static Object parseValue(String json) {
        if (json == null || json.trim().isEmpty()) return new LinkedHashMap<>();
        SimpleJson parser = new SimpleJson(json.trim());
        return parser.readValue();
    }

    /** Legacy flat parse — returns Map<String,String> for top-level string values only. */
    @SuppressWarnings("unchecked")
    public static Map<String, String> parseFlat(String json) {
        Object parsed = parseValue(json);
        if (!(parsed instanceof Map)) return new HashMap<>();
        Map<String, String> flat = new HashMap<>();
        for (var entry : ((Map<String, Object>) parsed).entrySet()) {
            Object v = entry.getValue();
            if (v instanceof String s) flat.put(entry.getKey(), s);
            else if (v != null) flat.put(entry.getKey(), v.toString());
        }
        return flat;
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= src.length()) return null;
        char ch = src.charAt(pos);
        return switch (ch) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip '{'
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < src.length()) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            Object value = readValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                break;
            }
            break;
        }
        return map;
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // skip '['
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < src.length()) {
            list.add(readValue());
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                break;
            }
            break;
        }
        return list;
    }

    private String readString() {
        if (pos >= src.length() || src.charAt(pos) != '"') return "";
        pos++; // skip opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (ch == '\\' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 5 < src.length()) {
                            try {
                                sb.append(
                                    (char) Integer.parseInt(
                                        src.substring(pos + 2, pos + 6),
                                        16
                                    )
                                );
                                pos += 6;
                                continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        sb.append('\\').append(next);
                    }
                    default -> sb.append('\\').append(next);
                }
                pos += 2;
            } else if (ch == '"') {
                pos++;
                return sb.toString();
            } else {
                sb.append(ch);
                pos++;
            }
        }
        return sb.toString();
    }

    private Number readNumber() {
        int start = pos;
        if (pos < src.length() && src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (
                pos < src.length() && Character.isDigit(src.charAt(pos))
            ) pos++;
        }
        if (
            pos < src.length() &&
            (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')
        ) {
            isFloat = true;
            pos++;
            if (
                pos < src.length() &&
                (src.charAt(pos) == '+' || src.charAt(pos) == '-')
            ) pos++;
            while (
                pos < src.length() && Character.isDigit(src.charAt(pos))
            ) pos++;
        }
        String num = src.substring(start, pos);
        if (isFloat) return Double.parseDouble(num);
        long val = Long.parseLong(num);
        if (
            val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE
        ) return (int) val;
        return val;
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException(
            "Expected boolean at position " + pos
        );
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Expected null at position " + pos);
    }

    private void skipWhitespace() {
        while (
            pos < src.length() && Character.isWhitespace(src.charAt(pos))
        ) pos++;
    }

    private void expect(char ch) {
        if (pos < src.length() && src.charAt(pos) == ch) pos++;
    }

    // ── Generator ───────────────────────────────────────────────────────

    /** Generate compact JSON from a Map/List/String/Number/Boolean/null tree. */
    @SuppressWarnings("unchecked")
    public static String generate(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + escapeString(s) + "\"";
        if (value instanceof Number n) {
            if (
                n instanceof Double d &&
                d == Math.floor(d) &&
                !Double.isInfinite(d)
            ) {
                return String.valueOf(d.longValue());
            }
            return n.toString();
        }
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                if (!first) sb.append(",");
                sb
                    .append("\"")
                    .append(escapeString(entry.getKey()))
                    .append("\":");
                sb.append(generate(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(generate(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        return "\"" + escapeString(value.toString()) + "\"";
    }

    public static String escapeString(String str) {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
