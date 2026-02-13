package com.minaret;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
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

    private final ServerSocket serverSocket;
    private final MinecraftServer mcServer;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
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
            "WebSocket server created on port {} (auth: {})",
            port,
            authEnabled ? "enabled" : "disabled"
        );
    }

    public void start() {
        running = true;
        executor.submit(this::acceptLoop);
        LOGGER.info(
            "WebSocket server started on port {}",
            serverSocket.getLocalPort()
        );
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
            if (
                !executor.awaitTermination(
                    SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            ) {
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
                LOGGER.debug(
                    "New connection from: {}",
                    socket.getRemoteSocketAddress()
                );
                executor.submit(() -> handleNewConnection(socket));
            } catch (IOException e) {
                if (running) LOGGER.error("Error accepting connection", e);
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.UTF_8
                )
            );
            OutputStream output = socket.getOutputStream();

            Map<String, String> headers = WebSocketProtocol.readHttpHeaders(
                reader
            );
            if (headers == null) {
                socket.close();
                return;
            }

            if (!WebSocketProtocol.isWebSocketUpgrade(headers)) {
                WebSocketProtocol.sendHttpResponse(
                    output,
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: 42\r\n\r\nWebSocket endpoint - use WebSocket client"
                );
                socket.close();
                return;
            }

            if (authEnabled && !isValidAuth(headers.get("authorization"))) {
                WebSocketProtocol.sendHttpResponse(
                    output,
                    "HTTP/1.1 401 Unauthorized\r\n" +
                        "WWW-Authenticate: Basic realm=\"Minaret WebSocket\"\r\n" +
                        "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\nUnauthorized"
                );
                socket.close();
                LOGGER.warn(
                    "Authentication failed from: {}",
                    socket.getRemoteSocketAddress()
                );
                return;
            }

            // Complete handshake
            String acceptKey = WebSocketProtocol.generateAcceptKey(
                headers.get("sec-websocket-key")
            );
            WebSocketProtocol.sendHttpResponse(
                output,
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Accept: " +
                    acceptKey +
                    "\r\n\r\n"
            );

            Connection conn = new Connection(socket);
            connections.add(conn);
            executor.submit(conn::run);
            LOGGER.info(
                "WebSocket connection established: {}",
                socket.getRemoteSocketAddress()
            );
        } catch (Exception e) {
            LOGGER.error("Error handling new connection", e);
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private boolean isValidAuth(String authHeader) {
        if (
            authHeader == null || !authHeader.startsWith("Basic ")
        ) return false;
        try {
            String decoded = new String(
                Base64.getDecoder().decode(authHeader.substring(6)),
                StandardCharsets.UTF_8
            );
            String[] creds = decoded.split(":", 2);
            if (creds.length != 2) return false;
            // Constant-time comparison to prevent timing attacks
            return (
                MessageDigest.isEqual(
                    authUsername.getBytes(StandardCharsets.UTF_8),
                    creds[0].getBytes(StandardCharsets.UTF_8)
                ) &&
                MessageDigest.isEqual(
                    authPassword.getBytes(StandardCharsets.UTF_8),
                    creds[1].getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception e) {
            return false;
        }
    }

    // ── Connection ──────────────────────────────────────────────────────

    private class Connection {

        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private volatile boolean active = true;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        void run() {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (active && !socket.isClosed()) {
                    int bytesRead = input.read(buffer);
                    if (bytesRead == -1) break;
                    if (bytesRead < 2) continue;

                    WebSocketProtocol.Frame frame =
                        WebSocketProtocol.parseFrame(buffer, bytesRead);
                    if (frame == null) continue;

                    switch (frame.opcode()) {
                        case WebSocketProtocol.OPCODE_TEXT -> onMessage(
                            new String(frame.payload(), StandardCharsets.UTF_8)
                        );
                        case WebSocketProtocol.OPCODE_CLOSE -> {
                            sendClose();
                            close();
                        }
                        case WebSocketProtocol.OPCODE_PING -> sendPong(
                            frame.payload()
                        );
                        case WebSocketProtocol.OPCODE_PONG -> {
                        }
                    }
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
