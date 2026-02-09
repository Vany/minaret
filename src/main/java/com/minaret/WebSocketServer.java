package com.minaret;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manual WebSocket server implementation using ServerSocket
 */
public class WebSocketServer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String WEBSOCKET_MAGIC =
        "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private final MinecraftServer mcServer;
    private final Set<WebSocketConnection> connections =
        ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private final String authUsername;
    private final String authPassword;
    private final boolean authEnabled;

    public WebSocketServer(
        String host,
        int port,
        MinecraftServer mcServer,
        String username,
        String password
    ) throws IOException {
        this.mcServer = mcServer;
        this.serverSocket = new ServerSocket(port);
        this.authUsername = username;
        this.authPassword = password;
        this.authEnabled = !username.isEmpty();
        LOGGER.info(
            "🔧 WebSocket server created on port {} (auth: {})",
            port,
            authEnabled ? "enabled" : "disabled"
        );
    }

    public void start() {
        running = true;
        executor.submit(this::acceptConnections);
        LOGGER.info(
            "🚀 WebSocket server started on port {}",
            serverSocket.getLocalPort()
        );
    }

    public void stop() {
        running = false;
        try {
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Error closing server socket", e);
        }

        connections.forEach(WebSocketConnection::close);
        connections.clear();
        executor.shutdown();
        LOGGER.info("🛑 WebSocket server stopped");
    }

    /**
     * Process a JSON message as if it came from a WebSocket client.
     * Responses are sent to the provided callback.
     */
    public static void processMessage(
        String message,
        MinecraftServer mcServer,
        Consumer<String> responseSender
    ) {
        try {
            LOGGER.debug("📨 Processing message: {}", message);
            Map<String, String> json = SimpleJson.parse(message);

            if (json.containsKey("message")) {
                String chatMessage = json.get("message");
                String user = json.getOrDefault("user", null);
                String chat = json.getOrDefault("chat", null);
                mcServer.execute(() -> {
                    StringBuilder sb = new StringBuilder();
                    if (chat != null && !chat.isEmpty()) {
                        sb.append("§7[").append(chat).append("]");
                    }
                    if (user != null && !user.isEmpty()) {
                        sb.append("§f<").append(user).append("> ");
                    } else {
                        sb.append("§7[WebSocket] §f");
                    }
                    sb.append("§f").append(chatMessage);
                    Component component = Component.literal(sb.toString());
                    for (ServerPlayer player : mcServer
                        .getPlayerList()
                        .getPlayers()) {
                        player.sendSystemMessage(component);
                    }
                    LOGGER.info("💬 Chat: {}", chatMessage);
                });

                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("type", "message");
                responseSender.accept(SimpleJson.generate(response));
            } else if (json.containsKey("command")) {
                String command = json.get("command");
                mcServer.execute(() -> {
                    try {
                        CommandSourceStack source = mcServer
                            .createCommandSourceStack()
                            .withSuppressedOutput();

                        int result = mcServer
                            .getCommands()
                            .getDispatcher()
                            .execute(command, source);

                        if (result > 0) {
                            Map<String, String> response = new HashMap<>();
                            response.put("status", "success");
                            response.put("type", "command");
                            response.put("command", command);
                            response.put("result", String.valueOf(result));
                            responseSender.accept(
                                SimpleJson.generate(response)
                            );
                            LOGGER.info(
                                "⚡ Command executed: {} (result: {})",
                                command,
                                result
                            );
                        } else {
                            Map<String, String> errorResponse = new HashMap<>();
                            errorResponse.put("status", "error");
                            errorResponse.put("type", "command");
                            errorResponse.put(
                                "error",
                                "Command returned 0 - may lack permissions, be invalid, or had no effect"
                            );
                            errorResponse.put("command", command);
                            errorResponse.put("result", String.valueOf(result));
                            responseSender.accept(
                                SimpleJson.generate(errorResponse)
                            );
                            LOGGER.warn(
                                "⚠️ Command failed: {} (result: {})",
                                command,
                                result
                            );
                        }
                    } catch (CommandSyntaxException e) {
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("status", "error");
                        errorResponse.put("type", "command");
                        errorResponse.put(
                            "error",
                            "Command syntax error: " + e.getMessage()
                        );
                        errorResponse.put("command", command);
                        responseSender.accept(
                            SimpleJson.generate(errorResponse)
                        );
                        LOGGER.error("❌ Command syntax error: {}", command, e);
                    } catch (Exception e) {
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("status", "error");
                        errorResponse.put("type", "command");
                        errorResponse.put(
                            "error",
                            e.getMessage() != null
                                ? e.getMessage()
                                : "Command execution failed"
                        );
                        errorResponse.put("command", command);
                        responseSender.accept(
                            SimpleJson.generate(errorResponse)
                        );
                        LOGGER.error("❌ Command error: {}", command, e);
                    }
                });
            } else if (json.containsKey("getEffects")) {
                String playerName = json.get("getEffects");
                mcServer.execute(() -> {
                    try {
                        ServerPlayer player = mcServer
                            .getPlayerList()
                            .getPlayerByName(playerName);
                        if (player == null) {
                            Map<String, String> errorResponse = new HashMap<>();
                            errorResponse.put("status", "error");
                            errorResponse.put("type", "getEffects");
                            errorResponse.put(
                                "error",
                                "Player not found: " + playerName
                            );
                            responseSender.accept(
                                SimpleJson.generate(errorResponse)
                            );
                            return;
                        }

                        Collection<MobEffectInstance> effects =
                            player.getActiveEffects();
                        StringBuilder sb = new StringBuilder();
                        sb.append(
                            "{\"status\":\"success\",\"type\":\"getEffects\",\"player\":\""
                        );
                        sb.append(SimpleJson.escapeString(playerName));
                        sb.append("\",\"effects\":[");

                        boolean first = true;
                        for (MobEffectInstance effect : effects) {
                            if (!first) sb.append(",");
                            sb.append("{\"effect\":\"");
                            sb.append(
                                SimpleJson.escapeString(
                                    effect.getEffect().getRegisteredName()
                                )
                            );
                            sb.append("\",\"duration\":");
                            sb.append(
                                effect.isInfiniteDuration()
                                    ? -1
                                    : effect.getDuration()
                            );
                            sb.append(",\"amplifier\":");
                            sb.append(effect.getAmplifier());
                            sb.append("}");
                            first = false;
                        }

                        sb.append("]}");
                        responseSender.accept(sb.toString());
                        LOGGER.info("🧪 getEffects for player: {}", playerName);
                    } catch (Exception e) {
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("status", "error");
                        errorResponse.put("type", "getEffects");
                        errorResponse.put(
                            "error",
                            e.getMessage() != null
                                ? e.getMessage()
                                : "Failed to get effects"
                        );
                        responseSender.accept(
                            SimpleJson.generate(errorResponse)
                        );
                    }
                });
            } else {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put(
                    "error",
                    "Unknown message type. Use 'message', 'command', or 'getEffects' fields."
                );
                responseSender.accept(SimpleJson.generate(errorResponse));
            }
        } catch (Exception e) {
            LOGGER.error("Error processing message: {}", message, e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("error", "Invalid JSON or processing error");
            responseSender.accept(SimpleJson.generate(errorResponse));
        }
    }

    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.debug(
                    "📞 New connection from: {}",
                    clientSocket.getRemoteSocketAddress()
                );
                executor.submit(() -> handleNewConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    LOGGER.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            OutputStream output = socket.getOutputStream();

            // Read HTTP headers
            Map<String, String> headers = new HashMap<>();
            String line;
            String requestLine = reader.readLine();

            if (requestLine == null || !requestLine.startsWith("GET")) {
                socket.close();
                return;
            }

            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String[] parts = line.split(": ", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].toLowerCase(), parts[1]);
                }
            }

            // Check for WebSocket upgrade
            if (
                "websocket".equalsIgnoreCase(headers.get("upgrade")) &&
                "upgrade".equalsIgnoreCase(headers.get("connection")) &&
                headers.containsKey("sec-websocket-key")
            ) {
                // Check authentication if enabled
                if (authEnabled) {
                    String authHeader = headers.get("authorization");
                    if (!isValidAuth(authHeader)) {
                        String response =
                            "HTTP/1.1 401 Unauthorized\r\n" +
                            "WWW-Authenticate: Basic realm=\"Minaret WebSocket\"\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 12\r\n" +
                            "\r\n" +
                            "Unauthorized";
                        output.write(response.getBytes());
                        output.flush();
                        socket.close();
                        LOGGER.warn(
                            "🚫 Authentication failed from: {}",
                            socket.getRemoteSocketAddress()
                        );
                        return;
                    }
                }

                performWebSocketHandshake(
                    socket,
                    output,
                    headers.get("sec-websocket-key")
                );
            } else {
                // Send HTTP response for non-WebSocket requests
                String response =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 42\r\n" +
                    "\r\n" +
                    "WebSocket endpoint - use WebSocket client";
                output.write(response.getBytes());
                output.flush();
                socket.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error handling new connection", e);
            try {
                socket.close();
            } catch (IOException ioE) {
                // Ignore
            }
        }
    }

    private void performWebSocketHandshake(
        Socket socket,
        OutputStream output,
        String key
    ) throws Exception {
        String acceptKey = generateAcceptKey(key);

        String response =
            "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: " +
            acceptKey +
            "\r\n" +
            "\r\n";

        output.write(response.getBytes());
        output.flush();

        WebSocketConnection wsConnection = new WebSocketConnection(
            socket,
            mcServer
        );
        connections.add(wsConnection);

        // Start handling WebSocket frames
        executor.submit(wsConnection::handleConnection);

        LOGGER.info(
            "✅ WebSocket connection established: {}",
            socket.getRemoteSocketAddress()
        );
    }

    private String generateAcceptKey(String key) throws Exception {
        String combined = key + WEBSOCKET_MAGIC;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(combined.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    private boolean isValidAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String encoded = authHeader.substring(6); // Remove "Basic "
            String decoded = new String(Base64.getDecoder().decode(encoded));
            String[] credentials = decoded.split(":", 2);

            if (credentials.length != 2) {
                return false;
            }

            return (
                authUsername.equals(credentials[0]) &&
                authPassword.equals(credentials[1])
            );
        } catch (Exception e) {
            return false;
        }
    }

    private class WebSocketConnection {

        private final Socket socket;
        private final MinecraftServer mcServer;
        private final InputStream input;
        private final OutputStream output;
        private volatile boolean running = true;

        public WebSocketConnection(Socket socket, MinecraftServer mcServer)
            throws IOException {
            this.socket = socket;
            this.mcServer = mcServer;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        public void handleConnection() {
            try {
                byte[] buffer = new byte[4096];
                while (running && !socket.isClosed()) {
                    int bytesRead = input.read(buffer);
                    if (bytesRead == -1) break;

                    // Parse WebSocket frame
                    if (bytesRead >= 2) {
                        parseFrame(buffer, bytesRead);
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("WebSocket connection closed: {}", e.getMessage());
            } finally {
                close();
            }
        }

        private void parseFrame(byte[] buffer, int length) throws IOException {
            if (length < 2) return;

            int opcode = buffer[0] & 0x0F;
            boolean masked = (buffer[1] & 0x80) != 0;
            int payloadLen = buffer[1] & 0x7F;

            int offset = 2;

            // Handle extended payload length
            if (payloadLen == 126) {
                if (length < 4) return;
                payloadLen = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                offset = 4;
            } else if (payloadLen == 127) {
                // For simplicity, reject very large frames
                sendClose();
                return;
            }

            // Handle mask
            byte[] mask = null;
            if (masked) {
                if (length < offset + 4) return;
                mask = new byte[4];
                System.arraycopy(buffer, offset, mask, 0, 4);
                offset += 4;
            }

            // Extract payload
            if (length < offset + payloadLen) return;

            byte[] payload = new byte[payloadLen];
            System.arraycopy(buffer, offset, payload, 0, payloadLen);

            // Unmask payload
            if (masked && mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            // Handle different frame types
            switch (opcode) {
                case 0x1: // Text frame
                    String message = new String(payload, "UTF-8");
                    handleMessage(message);
                    break;
                case 0x8: // Close frame
                    sendClose();
                    close();
                    break;
                case 0x9: // Ping frame
                    sendPong(payload);
                    break;
                case 0xA: // Pong frame
                    // Ignore pongs for now
                    break;
            }
        }

        private void handleMessage(String message) {
            processMessage(message, mcServer, response -> {
                try {
                    send(response);
                } catch (IOException e) {
                    LOGGER.error("Failed to send response", e);
                }
            });
        }

        public void send(String message) throws IOException {
            byte[] payload = message.getBytes("UTF-8");
            sendFrame(0x1, payload); // Text frame
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            synchronized (output) {
                // First byte: FIN=1, RSV=000, Opcode
                output.write(0x80 | (opcode & 0x0F));

                // Payload length
                if (payload.length < 126) {
                    output.write(payload.length);
                } else if (payload.length < 65536) {
                    output.write(126);
                    output.write((payload.length >> 8) & 0xFF);
                    output.write(payload.length & 0xFF);
                } else {
                    // For simplicity, limit to 64KB messages
                    throw new IOException("Message too large");
                }

                // Payload (server-to-client frames are not masked)
                output.write(payload);
                output.flush();
            }
        }

        private void sendClose() throws IOException {
            sendFrame(0x8, new byte[0]);
        }

        private void sendPong(byte[] payload) throws IOException {
            sendFrame(0xA, payload);
        }

        public void close() {
            running = false;
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                LOGGER.debug("Error closing WebSocket connection", e);
            }
            connections.remove(this);
        }
    }
}
