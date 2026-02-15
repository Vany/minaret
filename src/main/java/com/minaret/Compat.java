package com.minaret;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Compat {

    private static final Logger LOGGER = LogManager.getLogger();

    private Compat() {}

    // ── Cached reflection lookups ───────────────────────────────────────

    private static final Method HAS_PERMISSION;
    private static final Method GET_INT;
    private static final Method SET_BLOCK_ID;
    private static final Method SET_ITEM_ID;
    private static final Class<?> IDENTIFIER_CLASS;
    private static final Method IDENTIFIER_FROM;

    static {
        HAS_PERMISSION = findMethod(
            CommandSourceStack.class,
            "hasPermission",
            int.class
        );
        GET_INT = findMethod(CompoundTag.class, "getInt", String.class);

        SET_BLOCK_ID = findMethod(
            net.minecraft.world.level.block.state.BlockBehaviour
                .Properties.class,
            "setId",
            net.minecraft.resources.ResourceKey.class
        );
        SET_ITEM_ID = findMethod(
            net.minecraft.world.item.Item.Properties.class,
            "setId",
            net.minecraft.resources.ResourceKey.class
        );

        Class<?> idClass = null;
        Method idFrom = null;
        for (String className : new String[] {
            "net.minecraft.resources.Identifier",
            "net.minecraft.resources.ResourceLocation",
        }) {
            try {
                idClass = Class.forName(className);
                idFrom = idClass.getMethod(
                    "fromNamespaceAndPath",
                    String.class,
                    String.class
                );
                break;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to find fromNamespaceAndPath on {}",
                    className,
                    e
                );
            }
        }
        IDENTIFIER_CLASS = idClass;
        IDENTIFIER_FROM = idFrom;
    }

    private static Method findMethod(
        Class<?> clazz,
        String name,
        Class<?>... params
    ) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    public static boolean isClient() {
        try {
            Class<?> fmlEnv = Class.forName(
                "net.neoforged.fml.loading.FMLEnvironment"
            );
            Object dist;
            try {
                dist = fmlEnv.getMethod("getDist").invoke(null);
            } catch (NoSuchMethodException e) {
                dist = fmlEnv.getField("dist").get(null);
            }
            return "CLIENT".equals(dist.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to detect dist", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityType<
        T
    > createBlockEntityType(
        BlockEntityType.BlockEntitySupplier<T> factory,
        Block... blocks
    ) {
        // Try 1.21.1: BlockEntityType.Builder.of(factory, blocks).build(null)
        try {
            Class<?> builderClass = Class.forName(
                "net.minecraft.world.level.block.entity.BlockEntityType$Builder"
            );
            Object builder = builderClass
                .getMethod(
                    "of",
                    BlockEntityType.BlockEntitySupplier.class,
                    Block[].class
                )
                .invoke(null, factory, blocks);
            return (BlockEntityType<T>) builderClass
                .getMethod("build", com.mojang.datafixers.types.Type.class)
                .invoke(builder, (Object) null);
        } catch (Exception ignored) {}

        // 1.21.11+: new BlockEntityType<>(factory, Set.of(blocks))
        try {
            Constructor<?> ctor = BlockEntityType.class.getConstructor(
                BlockEntityType.BlockEntitySupplier.class,
                Set.class
            );
            return (BlockEntityType<T>) ctor.newInstance(
                factory,
                Set.of(blocks)
            );
        } catch (Exception e) {
            throw new RuntimeException("Cannot create BlockEntityType", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static int getTagInt(CompoundTag tag, String key) {
        if (GET_INT == null) return 0;
        try {
            Object result = GET_INT.invoke(tag, key);
            if (result instanceof Integer i) return i;
            if (result instanceof Optional<?> opt) return (
                (Optional<Integer>) opt
            ).orElse(0);
            return ((Number) result).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public static net.minecraft.world.level.block.state.BlockBehaviour.Properties setBlockId(
        net.minecraft.world.level.block.state.BlockBehaviour.Properties props,
        String name
    ) {
        if (SET_BLOCK_ID == null) return props; // 1.21.1 — no setId needed
        try {
            Object id = createIdentifier(MinaretMod.MOD_ID, name);
            var key = createResourceKey(
                net.minecraft.core.registries.Registries.BLOCK,
                id
            );
            SET_BLOCK_ID.invoke(props, key);
        } catch (Exception e) {
            LOGGER.error("Failed to set block ID for {}", name, e);
        }
        return props;
    }

    public static net.minecraft.world.item.Item.Properties setItemId(
        net.minecraft.world.item.Item.Properties props,
        String name
    ) {
        if (SET_ITEM_ID == null) return props; // 1.21.1 — no setId needed
        try {
            Object id = createIdentifier(MinaretMod.MOD_ID, name);
            var key = createResourceKey(
                net.minecraft.core.registries.Registries.ITEM,
                id
            );
            SET_ITEM_ID.invoke(props, key);
        } catch (Exception e) {
            LOGGER.error("Failed to set item ID for {}", name, e);
        }
        return props;
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        if (HAS_PERMISSION != null) {
            try {
                return (boolean) HAS_PERMISSION.invoke(source, level);
            } catch (Exception ignored) {}
        }
        return source
            .getServer()
            .getCommands()
            .getDispatcher()
            .getRoot()
            .canUse(source);
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private static Object createIdentifier(String namespace, String path) {
        if (IDENTIFIER_FROM == null) {
            throw new RuntimeException("No identifier factory found");
        }
        try {
            return IDENTIFIER_FROM.invoke(null, namespace, path);
        } catch (Exception e) {
            throw new RuntimeException(
                "Cannot create identifier " + namespace + ":" + path,
                e
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> net.minecraft.resources.ResourceKey<T> createResourceKey(
        net.minecraft.resources.ResourceKey<
            ? extends net.minecraft.core.Registry<T>
        > registry,
        Object identifier
    ) {
        try {
            for (Method m : net.minecraft.resources
                .ResourceKey.class.getMethods()) {
                if (
                    m.getName().equals("create") && m.getParameterCount() == 2
                ) {
                    if (m.getParameterTypes()[1].isInstance(identifier)) {
                        return (net.minecraft.resources.ResourceKey<
                            T
                        >) m.invoke(null, registry, identifier);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot create ResourceKey", e);
        }
        throw new RuntimeException(
            "No matching ResourceKey.create method found"
        );
    }

    // ── KeyMapping reflection (client-side) ─────────────────────────────

    /**
     * Create a KeyMapping cross-version.
     * 1.21.1: KeyMapping(String, int, String) — String category
     * 1.21.11: KeyMapping(String, int, Category) — Category record with Identifier
     */
    public static Object createKeyMapping(
        String name,
        int keyCode,
        String categoryId
    ) {
        try {
            Class<?> km = Class.forName("net.minecraft.client.KeyMapping");

            // Try 1.21.11: KeyMapping(String, int, KeyMapping.Category)
            for (Class<?> inner : km.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Category")) {
                    // Create Category via Category.register(Identifier)
                    Object identifier = createIdentifier(
                        MinaretMod.MOD_ID,
                        categoryId
                    );
                    Object category = getCategoryInstance(inner, identifier);
                    Constructor<?> ctor = km.getConstructor(
                        String.class,
                        int.class,
                        inner
                    );
                    return ctor.newInstance(name, keyCode, category);
                }
            }

            // Fallback 1.21.1: KeyMapping(String, int, String)
            Constructor<?> ctor = km.getConstructor(
                String.class,
                int.class,
                String.class
            );
            return ctor.newInstance(name, keyCode, "Minaret Chords");
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyMapping: " + name, e);
        }
    }

    /**
     * Get or create a Category instance. On 1.21.11+ Category.register(Identifier)
     * adds to SORT_ORDER and returns the Category. We cache ours to avoid
     * double-registration.
     */
    private static Object cachedCategory;

    private static Object getCategoryInstance(
        Class<?> categoryClass,
        Object identifier
    ) {
        if (cachedCategory != null) return cachedCategory;
        try {
            // Try Category.register(Identifier) — adds to SORT_ORDER
            for (Method m : categoryClass.getMethods()) {
                if (
                    m.getName().equals("register") &&
                    m.getParameterCount() == 1 &&
                    m.getParameterTypes()[0].isInstance(identifier)
                ) {
                    cachedCategory = m.invoke(null, identifier);
                    return cachedCategory;
                }
            }
            // Fallback: construct directly
            Constructor<?> ctor = categoryClass.getConstructor(
                identifier.getClass()
            );
            cachedCategory = ctor.newInstance(identifier);
            return cachedCategory;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyMapping.Category", e);
        }
    }

    /**
     * Register a KeyMapping category via RegisterKeyMappingsEvent (1.21.11+ only).
     * On 1.21.1 this is a no-op (categories are plain strings).
     */
    public static void registerCategory(Object event) {
        if (cachedCategory == null) return;
        try {
            Method rc = event
                .getClass()
                .getMethod("registerCategory", cachedCategory.getClass());
            rc.invoke(event, cachedCategory);
        } catch (NoSuchMethodException ignored) {
            // 1.21.1 — no registerCategory method, string categories just work
        } catch (Exception e) {
            LOGGER.warn("Failed to register key mapping category", e);
        }
    }

    /** Call RegisterKeyMappingsEvent.register(KeyMapping) via reflection. */
    public static void registerKeyMapping(Object event, Object keyMapping) {
        try {
            Class<?> kmClass = Class.forName("net.minecraft.client.KeyMapping");
            Method reg = event.getClass().getMethod("register", kmClass);
            reg.invoke(event, keyMapping);
        } catch (Exception e) {
            throw new RuntimeException("Cannot register KeyMapping", e);
        }
    }
}
