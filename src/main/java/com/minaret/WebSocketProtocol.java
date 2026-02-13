package com.minaret;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * RFC 6455 WebSocket protocol utilities — framing, handshake, HTTP helpers.
 * Pure static functions, no state.
 */
public final class WebSocketProtocol {

    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static final int OPCODE_TEXT = 0x1;
    static final int OPCODE_CLOSE = 0x8;
    static final int OPCODE_PING = 0x9;
    static final int OPCODE_PONG = 0xA;

    private static final int FIN_BIT = 0x80;
    private static final int MASK_BIT = 0x80;
    private static final int OPCODE_MASK = 0x0F;
    private static final int PAYLOAD_LEN_MASK = 0x7F;
    private static final int PAYLOAD_LEN_16BIT = 126;
    private static final int PAYLOAD_LEN_64BIT = 127;
    private static final int MASK_KEY_LENGTH = 4;
    private static final int MAX_PAYLOAD_16BIT = 65536;

    private WebSocketProtocol() {}

    // ── HTTP helpers ────────────────────────────────────────────────────

    /** Read HTTP headers from a buffered reader. Returns null if not a GET request. */
    public static Map<String, String> readHttpHeaders(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || !requestLine.startsWith("GET")) return null;

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            String[] parts = line.split(": ", 2);
            if (parts.length == 2) {
                headers.put(parts[0].toLowerCase(Locale.ROOT), parts[1]);
            }
        }
        return headers;
    }

    /** Check if headers indicate a WebSocket upgrade request. */
    public static boolean isWebSocketUpgrade(Map<String, String> headers) {
        return "websocket".equalsIgnoreCase(headers.get("upgrade"))
            && "upgrade".equalsIgnoreCase(headers.get("connection"))
            && headers.containsKey("sec-websocket-key");
    }

    /** Send a raw HTTP response string. */
    public static void sendHttpResponse(OutputStream output, String response) throws IOException {
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    /** Generate the Sec-WebSocket-Accept key for the handshake. */
    public static String generateAcceptKey(String clientKey) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest((clientKey + WEBSOCKET_MAGIC).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    // ── Frame parsing ───────────────────────────────────────────────────

    /** Parsed WebSocket frame. */
    public record Frame(int opcode, byte[] payload) {}

    /** Parse a WebSocket frame from raw bytes. Returns null if data is insufficient. */
    public static Frame parseFrame(byte[] buffer, int length) {
        if (length < 2) return null;

        int opcode = buffer[0] & OPCODE_MASK;
        boolean masked = (buffer[1] & MASK_BIT) != 0;
        int payloadLen = buffer[1] & PAYLOAD_LEN_MASK;
        int offset = 2;

        if (payloadLen == PAYLOAD_LEN_16BIT) {
            if (length < 4) return null;
            payloadLen = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
            offset = 4;
        } else if (payloadLen == PAYLOAD_LEN_64BIT) {
            return new Frame(OPCODE_CLOSE, new byte[0]); // reject 64-bit payloads
        }

        byte[] mask = null;
        if (masked) {
            if (length < offset + MASK_KEY_LENGTH) return null;
            mask = new byte[MASK_KEY_LENGTH];
            System.arraycopy(buffer, offset, mask, 0, MASK_KEY_LENGTH);
            offset += MASK_KEY_LENGTH;
        }

        if (length < offset + payloadLen) return null;

        byte[] payload = new byte[payloadLen];
        System.arraycopy(buffer, offset, payload, 0, payloadLen);

        if (masked && mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % MASK_KEY_LENGTH];
            }
        }

        return new Frame(opcode, payload);
    }

    // ── Frame sending ───────────────────────────────────────────────────

    /** Send a WebSocket frame. Output must be externally synchronized. */
    public static void sendFrame(OutputStream output, int opcode, byte[] payload) throws IOException {
        output.write(FIN_BIT | (opcode & OPCODE_MASK));

        if (payload.length < PAYLOAD_LEN_16BIT) {
            output.write(payload.length);
        } else if (payload.length < MAX_PAYLOAD_16BIT) {
            output.write(PAYLOAD_LEN_16BIT);
            output.write((payload.length >> 8) & 0xFF);
            output.write(payload.length & 0xFF);
        } else {
            throw new IOException("Message too large");
        }

        output.write(payload);
        output.flush();
    }

    /** Send a text frame. */
    public static void sendText(OutputStream output, String message) throws IOException {
        sendFrame(output, OPCODE_TEXT, message.getBytes(StandardCharsets.UTF_8));
    }

    /** Send a close frame. */
    public static void sendClose(OutputStream output) throws IOException {
        sendFrame(output, OPCODE_CLOSE, new byte[0]);
    }

    /** Send a pong frame. */
    public static void sendPong(OutputStream output, byte[] payload) throws IOException {
        sendFrame(output, OPCODE_PONG, payload);
    }
}
