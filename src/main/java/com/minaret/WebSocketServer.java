package com.minaret;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * WebSocket server — manages connections, authentication, and lifecycle.
 * Delegates framing to WebSocketProtocol and message handling to MessageDispatcher.
 */
public class WebSocketServer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int BUFFER_SIZE = 4096;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
    /**
     * Idle read timeout: if a client sends no data for this long the connection is closed.
     * Prevents zombie threads from stuck/dead clients holding executor slots indefinitely.
     */
    private static final int READ_TIMEOUT_MS = 300_000; // 5 minutes
    /** Max bytes accumulated across reads before giving up on a connection. */
    private static final int MAX_ACCUMULATOR = 131_072; // 128 KB

    private final ServerSocket serverSocket;
    private final MinecraftServer mcServer;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
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
        this.authUsername = username;
        this.authPassword = password;
        this.authEnabled = !username.isEmpty();

        // Bind to the configured host; null = all interfaces (0.0.0.0)
        InetAddress bindAddr = (host.isEmpty() || host.equals("0.0.0.0"))
            ? null
            : InetAddress.getByName(host);
        this.serverSocket = new ServerSocket(port, 50, bindAddr);

        LOGGER.info("WebSocket server created on {}:{} (auth: {})",
            bindAddr == null ? "*" : bindAddr.getHostAddress(),
            port,
            authEnabled ? "enabled" : "disabled"
        );
    }

    public void start() {
        running = true;
        executor.submit(this::acceptLoop);
        LOGGER.info("WebSocket server started on port {}", serverSocket.getLocalPort());
    }

    /** Sends a message to all connected clients. Called from server thread. */
    public void broadcast(String message) {
        connections.forEach(conn -> {
            try {
                conn.send(message);
            } catch (IOException e) {
                LOGGER.debug("Broadcast failed for connection: {}", e.getMessage());
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (!serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing server socket", e);
        }

        connections.forEach(Connection::close);
        connections.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("WebSocket server stopped");
    }

    // ── Accept loop ─────────────────────────────────────────────────────

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                LOGGER.debug("New connection from: {}", socket.getRemoteSocketAddress());
                executor.submit(() -> handleNewConnection(socket));
            } catch (IOException e) {
                if (running) LOGGER.error("Error accepting connection", e);
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        try {
            socket.setSoTimeout(READ_TIMEOUT_MS);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            OutputStream output = socket.getOutputStream();

            Map<String, String> headers = WebSocketProtocol.readHttpHeaders(reader);
            if (headers == null) {
                socket.close();
                return;
            }

            if (!WebSocketProtocol.isWebSocketUpgrade(headers)) {
                WebSocketProtocol.sendHttpResponse(output,
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                    "Content-Length: 42\r\n\r\nWebSocket endpoint - use WebSocket client"
                );
                socket.close();
                return;
            }

            if (authEnabled && !isValidAuth(headers.get("authorization"))) {
                WebSocketProtocol.sendHttpResponse(output,
                    "HTTP/1.1 401 Unauthorized\r\n" +
                    "WWW-Authenticate: Basic realm=\"Minaret WebSocket\"\r\n" +
                    "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\nUnauthorized"
                );
                socket.close();
                LOGGER.warn("Authentication failed from: {}", socket.getRemoteSocketAddress());
                return;
            }

            String acceptKey = WebSocketProtocol.generateAcceptKey(headers.get("sec-websocket-key"));
            WebSocketProtocol.sendHttpResponse(output,
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                "Connection: Upgrade\r\nSec-WebSocket-Accept: " + acceptKey + "\r\n\r\n"
            );

            Connection conn = new Connection(socket, mcServer, connections);
            connections.add(conn);
            executor.submit(conn::run);
            LOGGER.info("WebSocket connection established: {}", socket.getRemoteSocketAddress());
        } catch (Exception e) {
            LOGGER.error("Error handling new connection", e);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private boolean isValidAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;
        try {
            String decoded = new String(
                Base64.getDecoder().decode(authHeader.substring(6)),
                StandardCharsets.UTF_8
            );
            String[] creds = decoded.split(":", 2);
            if (creds.length != 2) return false;
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    authUsername.getBytes(StandardCharsets.UTF_8),
                    creds[0].getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(
                    authPassword.getBytes(StandardCharsets.UTF_8),
                    creds[1].getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Connection ──────────────────────────────────────────────────────

    private static class Connection {

        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final MinecraftServer mcServer;
        private final Set<Connection> connections;
        private volatile boolean active = true;

        Connection(Socket socket, MinecraftServer mcServer, Set<Connection> connections)
                throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
            this.mcServer = mcServer;
            this.connections = connections;
        }

        void run() {
            byte[] readBuf = new byte[BUFFER_SIZE];
            // Single pre-allocated accumulation buffer. `limit` tracks how many
            // bytes are valid. On consume, the remainder is shifted left in-place —
            // no per-read allocation, no Arrays.copyOfRange churn.
            byte[] buf = new byte[MAX_ACCUMULATOR];
            int limit = 0;

            try {
                while (active && !socket.isClosed()) {
                    int bytesRead;
                    try {
                        bytesRead = input.read(readBuf);
                    } catch (SocketTimeoutException e) {
                        LOGGER.debug("Read timeout — closing idle connection: {}",
                            socket.getRemoteSocketAddress());
                        break;
                    }
                    if (bytesRead == -1) break;

                    if (limit + bytesRead > MAX_ACCUMULATOR) {
                        LOGGER.warn("Frame buffer overflow ({} bytes) — closing: {}",
                            limit + bytesRead, socket.getRemoteSocketAddress());
                        break;
                    }

                    System.arraycopy(readBuf, 0, buf, limit, bytesRead);
                    limit += bytesRead;

                    // Consume all complete frames from the front of buf
                    int pos = 0;
                    while (limit - pos >= 2) {
                        WebSocketProtocol.Frame frame =
                            WebSocketProtocol.parseFrame(buf, pos, limit - pos);
                        if (frame == null) break; // incomplete — wait for more bytes

                        if (frame.consumed() < 0) {
                            // Unsupported 64-bit payload — close cleanly
                            LOGGER.debug("Unsupported 64-bit frame — closing: {}",
                                socket.getRemoteSocketAddress());
                            sendClose();
                            close();
                            return;
                        }

                        pos += frame.consumed();

                        switch (frame.opcode()) {
                            case WebSocketProtocol.OPCODE_TEXT ->
                                onMessage(new String(frame.payload(), StandardCharsets.UTF_8));
                            case WebSocketProtocol.OPCODE_CLOSE -> {
                                sendClose();
                                close();
                                return;
                            }
                            case WebSocketProtocol.OPCODE_PING -> sendPong(frame.payload());
                            case WebSocketProtocol.OPCODE_PONG -> {}
                        }
                    }

                    // Shift unconsumed bytes to the front
                    int remaining = limit - pos;
                    if (remaining > 0 && pos > 0) System.arraycopy(buf, pos, buf, 0, remaining);
                    limit = remaining;
                }
            } catch (IOException e) {
                LOGGER.debug("WebSocket connection closed: {}", e.getMessage());
            } finally {
                close();
            }
        }

        private void onMessage(String message) {
            MessageDispatcher.dispatch(message, mcServer, response -> {
                try {
                    send(response);
                } catch (IOException e) {
                    LOGGER.error("Failed to send response", e);
                }
            });
        }

        private void send(String message) throws IOException {
            synchronized (output) {
                WebSocketProtocol.sendText(output, message);
            }
        }

        private void sendClose() throws IOException {
            synchronized (output) {
                WebSocketProtocol.sendClose(output);
            }
        }

        private void sendPong(byte[] payload) throws IOException {
            synchronized (output) {
                WebSocketProtocol.sendPong(output, payload);
            }
        }

        void close() {
            active = false;
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.debug("Error closing WebSocket connection", e);
            }
            connections.remove(this);
        }
    }
}
