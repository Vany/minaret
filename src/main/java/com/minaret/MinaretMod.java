package com.minaret;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MinaretMod.MODID)
public class MinaretMod {

    public static final String MODID = "minaret";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MODID);

    public static final Holder<MobEffect> MARTIAL_LIGHTNING =
        MOB_EFFECTS.register("martial_lightning", MartialLightningEffect::new);

    public static final Holder<MobEffect> STREAMER_PROTECT =
        MOB_EFFECTS.register("streamer_protect", StreamerProtectEffect::new);

    public static final Holder<MobEffect> HOMING_ARCHERY = MOB_EFFECTS.register(
        "homing_archery",
        HomingArcheryEffect::new
    );

    private static WebSocketServer webSocketServer;

    public MinaretMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.SERVER, MinaretConfig.SPEC);
        MOB_EFFECTS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(
            MartialLightningHandler::onLivingIncomingDamage
        );
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onArrowLoose);
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onLivingDamage);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("🗼 Minaret mod loading...");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event
            .getDispatcher()
            .register(
                Commands.literal("minaret")
                    .requires(source -> {
                        try {
                            // 1.21.1: hasPermission(int) exists
                            return (boolean) source
                                .getClass()
                                .getMethod("hasPermission", int.class)
                                .invoke(source, 4);
                        } catch (Exception e) {
                            // 1.21.11+: fall back to server-level check
                            return source
                                .getServer()
                                .getCommands()
                                .getDispatcher()
                                .getRoot()
                                .canUse(source);
                        }
                    })
                    .then(
                        Commands.literal("exec").then(
                            Commands.argument(
                                "json",
                                StringArgumentType.greedyString()
                            ).executes(ctx -> {
                                String json = StringArgumentType.getString(
                                    ctx,
                                    "json"
                                );
                                WebSocketServer.processMessage(
                                    json,
                                    ctx.getSource().getServer(),
                                    response ->
                                        ctx
                                            .getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(response),
                                                false
                                            )
                                );
                                return 1;
                            })
                        )
                    )
            );
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
            webSocketServer = new WebSocketServer(
                host,
                port,
                event.getServer(),
                username,
                password
            );
            webSocketServer.start();

            LOGGER.info("🚀 WebSocket server started on {}:{}", host, port);
            if (!username.isEmpty()) {
                LOGGER.info("🔐 Authentication enabled for user: {}", username);
                LOGGER.info(
                    "📡 Connect with Authorization: Basic <base64({}:{})>",
                    username,
                    "*".repeat(password.length())
                );
            } else {
                LOGGER.info("🌐 No authentication required");
            }
            LOGGER.info("📡 Ready for WebSocket connections! Protocol:");
            LOGGER.info("   💬 Chat: {\"message\": \"Hello world!\"}");
            LOGGER.info(
                "   ⚡ Command: {\"command\": \"say Hello from WebSocket!\"}"
            );
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
