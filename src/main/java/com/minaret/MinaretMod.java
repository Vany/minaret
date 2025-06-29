package com.minaret;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MinaretMod.MODID)
public class MinaretMod {
    public static final String MODID = "minaret";
    public static final Logger LOGGER = LogManager.getLogger();
    
    private static WebSocketServer webSocketServer;
    
    public MinaretMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.SERVER, MinaretConfig.SPEC);
        
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("🗼 Minaret mod loading...");
    }
    
    private void onServerStarting(ServerStartingEvent event) {
        try {
            String wsUrl = MinaretConfig.WEBSOCKET_URL.get();
            String username = MinaretConfig.AUTH_USERNAME.get();
            String password = MinaretConfig.AUTH_PASSWORD.get();
            
            LOGGER.info("🔧 Configured WebSocket URL: {}", wsUrl);
            
            // Parse URL to extract host and port
            String[] parts = wsUrl.split(":");
            String host = parts.length > 0 ? parts[0] : "localhost";
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8765;
            
            // Start WebSocket server
            webSocketServer = new WebSocketServer(host, port, event.getServer(), username, password);
            webSocketServer.start();
            
            LOGGER.info("🚀 WebSocket server started on {}:{}", host, port);
            if (!username.isEmpty()) {
                LOGGER.info("🔐 Authentication enabled for user: {}", username);
                LOGGER.info("📡 Connect with Authorization: Basic <base64({}:{})>", username, "*".repeat(password.length()));
            } else {
                LOGGER.info("🌐 No authentication required");
            }
            LOGGER.info("📡 Ready for WebSocket connections! Protocol:");
            LOGGER.info("   💬 Chat: {\"message\": \"Hello world!\"}");
            LOGGER.info("   ⚡ Command: {\"command\": \"say Hello from WebSocket!\"}");
            
        } catch (Exception e) {
            LOGGER.error("❌ Failed to start WebSocket server", e);
        }
    }
    
    private void onServerStopping(ServerStoppingEvent event) {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                LOGGER.info("🛑 WebSocket server stopped");
            } catch (Exception e) {
                LOGGER.error("❌ Error stopping WebSocket server", e);
            }
        }
    }
}
