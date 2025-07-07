package com.minaret;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple JSON parser/generator to avoid external dependencies
 * Properly handles commas within quoted string values
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
            json = json.substring(1, json.length() - 1).trim();
        }
        
        if (json.isEmpty()) {
            return result;
        }
        
        // Parse key-value pairs respecting quoted strings
        int pos = 0;
        while (pos < json.length()) {
            // Skip whitespace
            pos = skipWhitespace(json, pos);
            if (pos >= json.length()) break;
            
            // Parse key (must be quoted)
            if (json.charAt(pos) != '"') break;
            String key = parseQuotedString(json, pos);
            if (key == null) break;
            pos = findEndQuote(json, pos + 1) + 1;
            
            // Skip whitespace and colon
            pos = skipWhitespace(json, pos);
            if (pos >= json.length() || json.charAt(pos) != ':') break;
            pos++; // Skip colon
            pos = skipWhitespace(json, pos);
            
            // Parse value (must be quoted)
            if (pos >= json.length() || json.charAt(pos) != '"') break;
            String value = parseQuotedString(json, pos);
            if (value == null) break;
            pos = findEndQuote(json, pos + 1) + 1;
            
            result.put(key, value);
            
            // Skip whitespace and optional comma
            pos = skipWhitespace(json, pos);
            if (pos < json.length() && json.charAt(pos) == ',') {
                pos++;
            }
        }
        
        return result;
    }
    
    private static int skipWhitespace(String json, int pos) {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
        return pos;
    }
    
    private static String parseQuotedString(String json, int startPos) {
        if (startPos >= json.length() || json.charAt(startPos) != '"') {
            return null;
        }
        
        int endPos = findEndQuote(json, startPos + 1);
        if (endPos == -1) {
            return null;
        }
        
        String content = json.substring(startPos + 1, endPos);
        return unescape(content);
    }
    
    private static int findEndQuote(String json, int startPos) {
        for (int i = startPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                // Count preceding backslashes
                int backslashes = 0;
                for (int j = i - 1; j >= startPos && json.charAt(j) == '\\'; j--) {
                    backslashes++;
                }
                // If even number of backslashes (including 0), quote is not escaped
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
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
    
    private static String unescape(String str) {
        return str.replace("\\\"", "\"")
                 .replace("\\\\", "\\")
                 .replace("\\n", "\n")
                 .replace("\\r", "\r")
                 .replace("\\t", "\t");
    }
    
    private static String escape(String str) {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
