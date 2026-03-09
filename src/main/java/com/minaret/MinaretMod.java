package com.minaret;

import net.neoforged.bus.api.EventPriority;
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
        modContainer.registerConfig(
            ModConfig.Type.SERVER,
            MinaretConfig.CONFIG_SPEC
        );
        MinaretRegistries.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                MinaretCommands.register(e.getDispatcher())
        );
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartingEvent e) ->
                onServerStarting(e)
        );
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent e) ->
                onServerStopping(e)
        );
        NeoForge.EVENT_BUS.addListener(
            MartialLightningHandler::onLivingIncomingDamage
        );
        NeoForge.EVENT_BUS.addListener(DeadBlowHandler::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onArrowLoose);
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(MegaChanterHandler::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(InsaneLightHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(InsaneLightHandler::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(SwiftStrikeHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(AccelerateHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ToughnessHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(
            EventPriority.LOWEST, WardingPostTeleportHandler::onEntityTeleport
        );
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onItemUseFinish);
        NeoForge.EVENT_BUS.addListener(EventBroadcaster::onLivingHeal);

        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent e) -> {
                e.getBuilder().addMix(
                    net.minecraft.world.item.alchemy.Potions.AWKWARD,
                    net.minecraft.world.item.Items.BOOK,
                    MinaretRegistries.MEGA_CHANTER_POTION
                );
            }
        );

        if (Compat.isClient()) {
            com.minaret.client.ChordKeyHandler.init(modEventBus);
            modEventBus.addListener(
                (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) ->
                    com.minaret.client.ChordKeyHandler.registerKeys(e)
            );
        }
    }

    private void onServerStarting(
        net.neoforged.neoforge.event.server.ServerStartingEvent event
    ) {
        currentServer = event.getServer();
        var server = currentServer;
        for (var level : server.getAllLevels()) {
            ChunkLoaderData.get(level).forceAll(level);
        }

        try {
            HostPort hp = HostPort.parse(MinaretConfig.WEBSOCKET_URL.get());

            webSocketServer = new WebSocketServer(
                hp.host,
                hp.port,
                server,
                MinaretConfig.AUTH_USERNAME.get(),
                MinaretConfig.AUTH_PASSWORD.get()
            );
            webSocketServer.start();
            LOGGER.info("WebSocket server started on {}:{}", hp.host, hp.port);
        } catch (Exception e) {
            LOGGER.error("Failed to start WebSocket server", e);
        }
    }

    private void onServerStopping(
        net.neoforged.neoforge.event.server.ServerStoppingEvent event
    ) {
        SpawnerAgitatorBlockEntity.unbindAll();
        WardingColumnBlockEntity.clearRegistry();
        ChunkLoaderData.reset();
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

        static HostPort parse(String url) {
            int colon = url.lastIndexOf(':');
            String host =
                colon > 0
                    ? url.substring(0, colon)
                    : (url.isEmpty() ? "localhost" : url);
            int port = DEFAULT_PORT;
            if (colon >= 0 && colon + 1 < url.length()) {
                try {
                    port = Integer.parseInt(url.substring(colon + 1));
                } catch (NumberFormatException e) {
                    LOGGER.warn(
                        "Invalid port in '{}', using {}",
                        url,
                        DEFAULT_PORT
                    );
                }
            }
            return new HostPort(host, port);
        }
    }
}
