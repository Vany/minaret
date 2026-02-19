package com.minaret;

import java.util.function.Function;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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
        MOB_EFFECTS.register("martial_lightning", () ->
            MinaretEffects.marker(0x00BFFF)
        );
    public static final Holder<MobEffect> STREAMER_PROTECT =
        MOB_EFFECTS.register("streamer_protect", () ->
            MinaretEffects.marker(0xFFD700)
        );
    public static final Holder<MobEffect> HOMING_ARCHERY = MOB_EFFECTS.register(
        "homing_archery",
        () -> MinaretEffects.marker(0x9B30FF)
    );

    // ── Block registration ─────────────────────────────────────────────

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

    /** Bundle of block + item + block entity suppliers created by {@link #registerBlock}. */
    record BlockBundle<B extends Block, E extends BlockEntity>(
        Supplier<B> block,
        Supplier<BlockItem> item,
        Supplier<BlockEntityType<E>> entity
    ) {}

    @SuppressWarnings("unchecked")
    private static <B extends Block, E extends BlockEntity> BlockBundle<
        B,
        E
    > registerBlock(
        String name,
        Function<BlockBehaviour.Properties, B> blockFactory,
        BlockEntityType.BlockEntitySupplier<E> entityFactory,
        BlockBehaviour.Properties props
    ) {
        Supplier<B> block = BLOCKS.register(name, () ->
            blockFactory.apply(Compat.setBlockId(props, name))
        );
        Supplier<BlockItem> item = ITEMS.register(name, () ->
            new BlockItem(
                block.get(),
                Compat.setItemId(new Item.Properties(), name)
            )
        );
        Supplier<BlockEntityType<E>> entity = BLOCK_ENTITIES.register(
            name,
            () -> Compat.createBlockEntityType(entityFactory, block.get())
        );
        return new BlockBundle<>(block, item, entity);
    }

    private static final BlockBundle<
        ChunkLoaderBlock,
        ChunkLoaderBlockEntity
    > CHUNK_LOADER = registerBlock(
        "chunk_loader",
        ChunkLoaderBlock::new,
        ChunkLoaderBlockEntity::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .sound(SoundType.METAL)
    );

    private static final BlockBundle<
        SpawnerAgitatorBlock,
        SpawnerAgitatorBlockEntity
    > SPAWNER_AGITATOR = registerBlock(
        "spawner_agitator",
        SpawnerAgitatorBlock::new,
        SpawnerAgitatorBlockEntity::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .sound(SoundType.METAL)
    );

    private static final BlockBundle<
        WardingPostBlock,
        WardingPostBlockEntity
    > WARDING_POST = registerBlock(
        "warding_post",
        WardingPostBlock::new,
        WardingPostBlockEntity::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .sound(SoundType.STONE)
            .strength(2.0f)
            .noOcclusion()
    );

    // Public accessors for block/item/entity types used by other classes
    public static final Supplier<ChunkLoaderBlock> CHUNK_LOADER_BLOCK =
        CHUNK_LOADER.block;
    public static final Supplier<BlockItem> CHUNK_LOADER_ITEM =
        CHUNK_LOADER.item;
    public static final Supplier<
        BlockEntityType<ChunkLoaderBlockEntity>
    > CHUNK_LOADER_BE = CHUNK_LOADER.entity;

    public static final Supplier<SpawnerAgitatorBlock> SPAWNER_AGITATOR_BLOCK =
        SPAWNER_AGITATOR.block;
    public static final Supplier<BlockItem> SPAWNER_AGITATOR_ITEM =
        SPAWNER_AGITATOR.item;
    public static final Supplier<
        BlockEntityType<SpawnerAgitatorBlockEntity>
    > SPAWNER_AGITATOR_BE = SPAWNER_AGITATOR.entity;

    public static final Supplier<WardingPostBlock> WARDING_POST_BLOCK =
        WARDING_POST.block;
    public static final Supplier<BlockItem> WARDING_POST_ITEM =
        WARDING_POST.item;
    public static final Supplier<
        BlockEntityType<WardingPostBlockEntity>
    > WARDING_POST_BE = WARDING_POST.entity;

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

    public static WebSocketServer getWebSocketServer() {
        return webSocketServer;
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
