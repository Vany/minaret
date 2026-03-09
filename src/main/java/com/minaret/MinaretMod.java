package com.minaret;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MinaretMod.MOD_ID)
public class MinaretMod {

    public static final String MOD_ID = "minaret";
    private static final Logger LOGGER = LogManager.getLogger();

    private static WebSocketServer webSocketServer;
    private static net.minecraft.server.MinecraftServer currentServer;

    public static net.minecraft.server.MinecraftServer getServer() {
        return currentServer;
    }

    public static WebSocketServer getWebSocketServer() {
        return webSocketServer;
    }

    public MinaretMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, MinaretConfig.CONFIG_SPEC);

        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                MinaretCommands.register(e.getDispatcher())
        );
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartingEvent e) -> onServerStarting(e)
        );
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> onServerStopping(e)
        );
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onItemUseFinish);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onLivingHeal);

        if (Compat.isClient()) {
            com.minaret.client.ChordKeyHandler.init(modEventBus);
            modEventBus.addListener(
                (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) ->
                    com.minaret.client.ChordKeyHandler.registerKeys(e)
            );
        }
    }

    private void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        currentServer = event.getServer();
        try {
            HostPort hp = HostPort.parse(MinaretConfig.WEBSOCKET_URL.get());
            webSocketServer = new WebSocketServer(
                hp.host,
                hp.port,
                currentServer,
                MinaretConfig.AUTH_USERNAME.get(),
                MinaretConfig.AUTH_PASSWORD.get()
            );
            webSocketServer.start();
            LOGGER.info("WebSocket server started on {}:{}", hp.host, hp.port);
        } catch (Exception e) {
            LOGGER.error("Failed to start WebSocket server", e);
        }
    }

    private void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        currentServer = null;
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                LOGGER.info("WebSocket server stopped");
            } catch (Exception e) {
                LOGGER.error("Error stopping WebSocket server", e);
            }
        }
    }

    private record HostPort(String host, int port) {
        private static final int DEFAULT_PORT = 8765;

        /**
         * Parse "host:port" into a HostPort. Supports IPv6 addresses in bracket
         * notation (e.g. "[::1]:8765") by delegating to {@link java.net.URI}.
         * Falls back to localhost:8765 on any parse failure.
         */
        static HostPort parse(String url) {
            if (url == null || url.isEmpty()) return new HostPort("localhost", DEFAULT_PORT);
            try {
                // Prepend a dummy scheme so URI can parse bare host:port strings
                java.net.URI uri = new java.net.URI("ws://" + url);
                String host = uri.getHost();
                int port = uri.getPort();
                return new HostPort(
                    host != null && !host.isEmpty() ? host : "localhost",
                    port > 0 ? port : DEFAULT_PORT
                );
            } catch (java.net.URISyntaxException e) {
                LOGGER.warn("Invalid WebSocket URL '{}', using defaults: {}", url, e.getMessage());
                return new HostPort("localhost", DEFAULT_PORT);
            }
        }
    }
}
