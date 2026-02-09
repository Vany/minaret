package com.minaret;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple JSON parser/generator to avoid external dependencies
 */
public class SimpleJson {

    public static Map<String, String> parse(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }

        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }

        int i = 0;
        int len = json.length();
        while (i < len) {
            // Skip whitespace and commas between pairs
            while (
                i < len &&
                (json.charAt(i) == ',' ||
                    Character.isWhitespace(json.charAt(i)))
            ) i++;
            if (i >= len) break;

            // Parse key
            String key = parseString(json, i);
            if (key == null) break;
            i = skipString(json, i);

            // Skip whitespace and colon
            while (i < len && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= len || json.charAt(i) != ':') break;
            i++; // skip colon
            while (i < len && Character.isWhitespace(json.charAt(i))) i++;

            // Parse value
            String value = parseString(json, i);
            if (value == null) break;
            i = skipString(json, i);

            result.put(key, value);
        }

        return result;
    }

    private static String parseString(String json, int start) {
        if (start >= json.length() || json.charAt(start) != '"') return null;
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    default:
                        sb.append('\\').append(next);
                        break;
                }
                i += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static int skipString(String json, int start) {
        if (start >= json.length() || json.charAt(start) != '"') return start;
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '"') {
                return i + 1;
            } else {
                i++;
            }
        }
        return i;
    }

    public static String generate(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeString(entry.getKey())).append("\":");
            sb.append("\"").append(escapeString(entry.getValue())).append("\"");
            first = false;
        }

        sb.append("}");
        return sb.toString();
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
