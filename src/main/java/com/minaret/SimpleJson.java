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
        
        // Remove outer braces and whitespace
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        // Simple key-value parsing
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = unquote(keyValue[0].trim());
                String value = unquote(keyValue[1].trim());
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    public static String generate(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            sb.append("\"").append(escape(entry.getValue())).append("\"");
            first = false;
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    private static String unquote(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
    
    private static String escape(String str) {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
