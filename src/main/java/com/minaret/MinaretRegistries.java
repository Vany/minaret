package com.minaret;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All DeferredRegister declarations, block/item/entity registrations, mob effects,
 * and creative tab for the Minaret mod. Extracted from MinaretMod for clarity.
 */
public final class MinaretRegistries {

    private MinaretRegistries() {}

    // ── Mob effects ──────────────────────────────────────────────────────

    private static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MinaretMod.MOD_ID);

    public static final Holder<MobEffect> MARTIAL_LIGHTNING =
        MOB_EFFECTS.register("martial_lightning", () -> MinaretEffects.marker(0x00BFFF));
    public static final Holder<MobEffect> STREAMER_PROTECT =
        MOB_EFFECTS.register("streamer_protect", () -> MinaretEffects.marker(0xFFD700));
    public static final Holder<MobEffect> HOMING_ARCHERY =
        MOB_EFFECTS.register("homing_archery", () -> MinaretEffects.marker(0x9B30FF));
    public static final Holder<MobEffect> MEGA_CHANTER =
        MOB_EFFECTS.register("mega_chanter", () -> MinaretEffects.marker(0x00FF99));
    public static final Holder<MobEffect> INSANE_LIGHT =
        MOB_EFFECTS.register("insane_light", () -> MinaretEffects.marker(0xFFFF44));
    public static final Holder<MobEffect> DEAD_BLOW =
        MOB_EFFECTS.register("dead_blow", () -> MinaretEffects.marker(0xFF2200));  // Deep red

    // ── Potions ──────────────────────────────────────────────────────────

    private static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(BuiltInRegistries.POTION, MinaretMod.MOD_ID);

    public static final DeferredHolder<Potion, Potion> MEGA_CHANTER_POTION =
        POTIONS.register("mega_chanter", () ->
            new Potion("mega_chanter", new MobEffectInstance(MEGA_CHANTER, 3600, 0))
        );

    // ── Block registration ───────────────────────────────────────────────

    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, MinaretMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, MinaretMod.MOD_ID);

    @SuppressWarnings("unchecked")
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(
            (net.minecraft.core.Registry<BlockEntityType<?>>) (net.minecraft.core.Registry<?>) BuiltInRegistries.BLOCK_ENTITY_TYPE,
            MinaretMod.MOD_ID
        );

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MinaretMod.MOD_ID);

    /** Bundle of block + item + block entity suppliers created by {@link #registerBlock}. */
    private record BlockBundle<B extends Block, E extends BlockEntity>(
        Supplier<B> block,
        Supplier<BlockItem> item,
        Supplier<BlockEntityType<E>> entity
    ) {}

    @SuppressWarnings("unchecked")
    private static <B extends Block, E extends BlockEntity> BlockBundle<B, E> registerBlock(
        String name,
        Function<BlockBehaviour.Properties, B> blockFactory,
        BlockEntityType.BlockEntitySupplier<E> entityFactory,
        BlockBehaviour.Properties props
    ) {
        Supplier<B> block = BLOCKS.register(name, () ->
            blockFactory.apply(Compat.setBlockId(props, name))
        );
        Supplier<BlockItem> item = ITEMS.register(name, () ->
            new BlockItem(block.get(), Compat.setItemId(new Item.Properties(), name))
        );
        Supplier<BlockEntityType<E>> entity = BLOCK_ENTITIES.register(
            name, () -> Compat.createBlockEntityType(entityFactory, block.get())
        );
        return new BlockBundle<>(block, item, entity);
    }

    private record SimpleBlockBundle<B extends Block>(Supplier<B> block, Supplier<BlockItem> item) {}

    /** Register a block+item pair with no block entity. */
    private static <B extends Block> SimpleBlockBundle<B> registerSimpleBlock(
        String name,
        Function<BlockBehaviour.Properties, B> blockFactory,
        BlockBehaviour.Properties props
    ) {
        Supplier<B> block = BLOCKS.register(name, () ->
            blockFactory.apply(Compat.setBlockId(props, name))
        );
        Supplier<BlockItem> item = ITEMS.register(name, () ->
            new BlockItem(block.get(), Compat.setItemId(new Item.Properties(), name))
        );
        return new SimpleBlockBundle<>(block, item);
    }

    // ── Block instances ──────────────────────────────────────────────────

    private static final BlockBundle<ChunkLoaderBlock, ChunkLoaderBlockEntity> CHUNK_LOADER =
        registerBlock("chunk_loader", ChunkLoaderBlock::new, ChunkLoaderBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).sound(SoundType.METAL));

    private static final BlockBundle<SpawnerAgitatorBlock, SpawnerAgitatorBlockEntity> SPAWNER_AGITATOR =
        registerBlock("spawner_agitator", SpawnerAgitatorBlock::new, SpawnerAgitatorBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).sound(SoundType.METAL).randomTicks());

    private static final BlockBundle<WardingPostBlock, WardingPostBlockEntity> WARDING_POST =
        registerBlock("warding_post", WardingPostBlock::new, WardingPostBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(0.1f).noOcclusion().randomTicks());

    private static final BlockBundle<TeleporterInhibitorBlock, TeleporterInhibitorBlockEntity> TELEPORTER_INHIBITOR =
        registerBlock("teleporter_inhibitor", TeleporterInhibitorBlock::new, TeleporterInhibitorBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.AMETHYST).strength(0.1f).noOcclusion().randomTicks());

    private static final BlockBundle<RepellingPostBlock, RepellingPostBlockEntity> REPELLING_POST =
        registerBlock("repelling_post", RepellingPostBlock::new, RepellingPostBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).sound(SoundType.BONE_BLOCK).strength(0.1f).noOcclusion().randomTicks());

    private static final BlockBundle<EEClockBlock, EEClockBlockEntity> EE_CLOCK =
        registerBlock("ee_clock", EEClockBlock::new, EEClockBlockEntity::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).sound(SoundType.METAL).randomTicks());

    // Obsidian mining level (requires diamond+): strength 50 (hardness) / resistance 1200.
    // explosionResistance(3600000) makes it immune to all explosions including the Wither.
    private static final SimpleBlockBundle<AntiWitherBlock> ANTI_WITHER =
        registerSimpleBlock("antiwither", AntiWitherBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .strength(50f, 3_600_000f));

    // ── Public accessors ─────────────────────────────────────────────────

    public static final Supplier<BlockEntityType<ChunkLoaderBlockEntity>> CHUNK_LOADER_BE = CHUNK_LOADER.entity;
    public static final Supplier<BlockEntityType<SpawnerAgitatorBlockEntity>> SPAWNER_AGITATOR_BE = SPAWNER_AGITATOR.entity;
    public static final Supplier<BlockEntityType<WardingPostBlockEntity>> WARDING_POST_BE = WARDING_POST.entity;
    public static final Supplier<BlockEntityType<TeleporterInhibitorBlockEntity>> TELEPORTER_INHIBITOR_BE = TELEPORTER_INHIBITOR.entity;
    public static final Supplier<BlockEntityType<RepellingPostBlockEntity>> REPELLING_POST_BE = REPELLING_POST.entity;
    public static final Supplier<BlockEntityType<EEClockBlockEntity>> EE_CLOCK_BE = EE_CLOCK.entity;

    public static final Supplier<BlockItem> CHUNK_LOADER_ITEM = CHUNK_LOADER.item;
    public static final Supplier<BlockItem> SPAWNER_AGITATOR_ITEM = SPAWNER_AGITATOR.item;
    public static final Supplier<BlockItem> WARDING_POST_ITEM = WARDING_POST.item;
    public static final Supplier<BlockItem> TELEPORTER_INHIBITOR_ITEM = TELEPORTER_INHIBITOR.item;
    public static final Supplier<BlockItem> REPELLING_POST_ITEM = REPELLING_POST.item;
    public static final Supplier<BlockItem> ANTI_WITHER_ITEM = ANTI_WITHER.item;
    public static final Supplier<BlockItem> EE_CLOCK_ITEM = EE_CLOCK.item;

    // ── Creative tab ─────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private static final Supplier<CreativeModeTab> MINARET_TAB =
        CREATIVE_TABS.register("minaret", () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.minaret"))
                .icon(() -> CHUNK_LOADER_ITEM.get().getDefaultInstance())
                .displayItems((params, output) -> {
                    output.accept(CHUNK_LOADER_ITEM.get());
                    output.accept(SPAWNER_AGITATOR_ITEM.get());
                    output.accept(WARDING_POST_ITEM.get());
                    output.accept(TELEPORTER_INHIBITOR_ITEM.get());
                    output.accept(REPELLING_POST_ITEM.get());
                    output.accept(ANTI_WITHER_ITEM.get());
                    output.accept(EE_CLOCK_ITEM.get());
                })
                .build()
        );

    // ── Registration ─────────────────────────────────────────────────────

    /** Register all deferred registries on the mod event bus. */
    static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
