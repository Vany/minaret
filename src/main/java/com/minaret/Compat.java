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

/**
 * Cross-version compatibility: registry IDs, NBT, permissions, environment detection.
 * KeyMapping compat is in {@link KeyMappingCompat}.
 */
public final class Compat {

    private static final Logger LOGGER = LogManager.getLogger();

    private Compat() {}

    // ── Cached reflection lookups ───────────────────────────────────────

    private static final Method HAS_PERMISSION;
    private static final Method GET_INT;
    private static final Method SET_BLOCK_ID;
    private static final Method SET_ITEM_ID;
    private static final Method IDENTIFIER_FROM;
    private static final Method RESOURCE_KEY_CREATE;

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

        Method idFrom = null;
        for (String className : new String[] {
            "net.minecraft.resources.Identifier",
            "net.minecraft.resources.ResourceLocation",
        }) {
            try {
                Class<?> idClass = Class.forName(className);
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
        IDENTIFIER_FROM = idFrom;

        // Cache ResourceKey.create(ResourceKey, identifier) lookup
        Method rkCreate = null;
        if (idFrom != null) {
            try {
                for (Method m : net.minecraft.resources
                    .ResourceKey.class.getMethods()) {
                    if (
                        m.getName().equals("create") &&
                        m.getParameterCount() == 2 &&
                        m
                            .getParameterTypes()[1].isAssignableFrom(
                                idFrom.getReturnType()
                            )
                    ) {
                        rkCreate = m;
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to resolve ResourceKey.create", e);
            }
        }
        RESOURCE_KEY_CREATE = rkCreate;
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

    // ── Environment ─────────────────────────────────────────────────────

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
            LOGGER.error("Failed to detect FML dist — assuming server", e);
            return false;
        }
    }

    // ── Registry IDs ────────────────────────────────────────────────────

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

    public static net.minecraft.world.level.block.state.BlockBehaviour.Properties setBlockId(
        net.minecraft.world.level.block.state.BlockBehaviour.Properties props,
        String name
    ) {
        if (SET_BLOCK_ID == null) return props;
        try {
            SET_BLOCK_ID.invoke(
                props,
                createResourceKey(
                    net.minecraft.core.registries.Registries.BLOCK,
                    name
                )
            );
        } catch (Exception e) {
            LOGGER.error("Failed to set block ID for {}", name, e);
        }
        return props;
    }

    public static net.minecraft.world.item.Item.Properties setItemId(
        net.minecraft.world.item.Item.Properties props,
        String name
    ) {
        if (SET_ITEM_ID == null) return props;
        try {
            SET_ITEM_ID.invoke(
                props,
                createResourceKey(
                    net.minecraft.core.registries.Registries.ITEM,
                    name
                )
            );
        } catch (Exception e) {
            LOGGER.error("Failed to set item ID for {}", name, e);
        }
        return props;
    }

    // ── NBT ─────────────────────────────────────────────────────────────

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

    // ── Permissions ─────────────────────────────────────────────────────

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

    // ── Internal helpers (package-visible for KeyMappingCompat) ─────────

    static Object createIdentifier(String namespace, String path) {
        if (IDENTIFIER_FROM == null) throw new RuntimeException(
            "No identifier factory found"
        );
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
        String name
    ) {
        Object identifier = createIdentifier(MinaretMod.MOD_ID, name);
        if (RESOURCE_KEY_CREATE == null) throw new RuntimeException(
            "ResourceKey.create not found"
        );
        try {
            return (net.minecraft.resources.ResourceKey<
                T
            >) RESOURCE_KEY_CREATE.invoke(null, registry, identifier);
        } catch (Exception e) {
            throw new RuntimeException(
                "Cannot create ResourceKey for " + name,
                e
            );
        }
    }
}
