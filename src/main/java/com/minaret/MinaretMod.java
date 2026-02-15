package com.minaret;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MinaretMod.MOD_ID)
public class MinaretMod {

    public static final String MOD_ID = "minaret";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MOD_ID);

    public static final Holder<MobEffect> MARTIAL_LIGHTNING =
        MOB_EFFECTS.register("martial_lightning", MartialLightningEffect::new);
    public static final Holder<MobEffect> STREAMER_PROTECT =
        MOB_EFFECTS.register("streamer_protect", StreamerProtectEffect::new);
    public static final Holder<MobEffect> HOMING_ARCHERY = MOB_EFFECTS.register(
        "homing_archery",
        HomingArcheryEffect::new
    );

    // ── Blocks ──────────────────────────────────────────────────────────

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
        BuiltInRegistries.ITEM,
        MOD_ID
    );

    @SuppressWarnings("unchecked")
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(
            (net.minecraft.core.Registry<
                BlockEntityType<?>
            >) (net.minecraft.core.Registry<
                ?
            >) BuiltInRegistries.BLOCK_ENTITY_TYPE,
            MOD_ID
        );

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Supplier<ChunkLoaderBlock> CHUNK_LOADER_BLOCK =
        BLOCKS.register("chunk_loader", () ->
            new ChunkLoaderBlock(
                Compat.setBlockId(
                    BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_GREEN)
                        .sound(SoundType.METAL),
                    "chunk_loader"
                )
            )
        );
    public static final Supplier<SpawnerAgitatorBlock> SPAWNER_AGITATOR_BLOCK =
        BLOCKS.register("spawner_agitator", () ->
            new SpawnerAgitatorBlock(
                Compat.setBlockId(
                    BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_YELLOW)
                        .sound(SoundType.METAL),
                    "spawner_agitator"
                )
            )
        );
    public static final Supplier<WardingPostBlock> WARDING_POST_BLOCK =
        BLOCKS.register("warding_post", () ->
            new WardingPostBlock(
                Compat.setBlockId(
                    BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .sound(SoundType.STONE)
                        .strength(2.0f)
                        .noOcclusion(),
                    "warding_post"
                )
            )
        );

    public static final Supplier<BlockItem> CHUNK_LOADER_ITEM = ITEMS.register(
        "chunk_loader",
        () ->
            new BlockItem(
                CHUNK_LOADER_BLOCK.get(),
                Compat.setItemId(new Item.Properties(), "chunk_loader")
            )
    );
    public static final Supplier<BlockItem> SPAWNER_AGITATOR_ITEM =
        ITEMS.register("spawner_agitator", () ->
            new BlockItem(
                SPAWNER_AGITATOR_BLOCK.get(),
                Compat.setItemId(new Item.Properties(), "spawner_agitator")
            )
        );
    public static final Supplier<BlockItem> WARDING_POST_ITEM = ITEMS.register(
        "warding_post",
        () ->
            new BlockItem(
                WARDING_POST_BLOCK.get(),
                Compat.setItemId(new Item.Properties(), "warding_post")
            )
    );

    @SuppressWarnings("unchecked")
    public static final Supplier<
        BlockEntityType<ChunkLoaderBlockEntity>
    > CHUNK_LOADER_BE = BLOCK_ENTITIES.register("chunk_loader", () ->
        Compat.createBlockEntityType(
            ChunkLoaderBlockEntity::new,
            CHUNK_LOADER_BLOCK.get()
        )
    );

    @SuppressWarnings("unchecked")
    public static final Supplier<
        BlockEntityType<SpawnerAgitatorBlockEntity>
    > SPAWNER_AGITATOR_BE = BLOCK_ENTITIES.register("spawner_agitator", () ->
        Compat.createBlockEntityType(
            SpawnerAgitatorBlockEntity::new,
            SPAWNER_AGITATOR_BLOCK.get()
        )
    );

    @SuppressWarnings("unchecked")
    public static final Supplier<
        BlockEntityType<WardingPostBlockEntity>
    > WARDING_POST_BE = BLOCK_ENTITIES.register("warding_post", () ->
        Compat.createBlockEntityType(
            WardingPostBlockEntity::new,
            WARDING_POST_BLOCK.get()
        )
    );

    public static final Supplier<CreativeModeTab> MINARET_TAB =
        CREATIVE_TABS.register("minaret", () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.minaret"))
                .icon(() -> CHUNK_LOADER_ITEM.get().getDefaultInstance())
                .displayItems((params, output) -> {
                    output.accept(CHUNK_LOADER_ITEM.get());
                    output.accept(SPAWNER_AGITATOR_ITEM.get());
                    output.accept(WARDING_POST_ITEM.get());
                })
                .build()
        );

    private static WebSocketServer webSocketServer;
    private static net.minecraft.server.MinecraftServer currentServer;

    public static net.minecraft.server.MinecraftServer getServer() {
        return currentServer;
    }

    public MinaretMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(
            ModConfig.Type.SERVER,
            MinaretConfig.CONFIG_SPEC
        );
        MOB_EFFECTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

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
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onArrowLoose);
        NeoForge.EVENT_BUS.addListener(HomingArcheryHandler::onLivingDamage);

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
            String wsUrl = MinaretConfig.WEBSOCKET_URL.get();
            String host = parseHost(wsUrl);
            int port = parsePort(wsUrl);

            webSocketServer = new WebSocketServer(
                host,
                port,
                server,
                MinaretConfig.AUTH_USERNAME.get(),
                MinaretConfig.AUTH_PASSWORD.get()
            );
            webSocketServer.start();
            LOGGER.info("WebSocket server started on {}:{}", host, port);
        } catch (Exception e) {
            LOGGER.error("Failed to start WebSocket server", e);
        }
    }

    private void onServerStopping(
        net.neoforged.neoforge.event.server.ServerStoppingEvent event
    ) {
        // Restore all agitated spawners before save loop to prevent shutdown hang
        SpawnerAgitatorBlockEntity.unbindAll();
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

    private static String parseHost(String url) {
        int colon = url.lastIndexOf(':');
        return colon > 0
            ? url.substring(0, colon)
            : (url.isEmpty() ? "localhost" : url);
    }

    private static int parsePort(String url) {
        int colon = url.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < url.length()) {
            try {
                return Integer.parseInt(url.substring(colon + 1));
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid port in '{}', using 8765", url);
            }
        }
        return 8765;
    }
}
