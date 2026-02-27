package com.minaret;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
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
    private static final Method SET_BLOCK_ID;
    private static final Method SET_ITEM_ID;
    private static final Method IDENTIFIER_FROM;
    private static final Method RESOURCE_KEY_CREATE;
    /** 1.21.11: setSelectedSlot(int) — null on 1.21.1 (uses public field). */
    private static final Method INVENTORY_SET_SLOT;
    /** 1.21.11: getSelectedSlot() — null on 1.21.1 (uses public field). */
    private static final Method INVENTORY_GET_SLOT;
    /** 1.21.1: public field Inventory.selected — null on 1.21.11. */
    private static final Field INVENTORY_SELECTED_FIELD;

    static {
        HAS_PERMISSION = findMethod(
            CommandSourceStack.class,
            "hasPermission",
            int.class
        );
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

        // Inventory selected slot — cross-version
        Method setSlot = findMethod(Inventory.class, "setSelectedSlot", int.class);
        Method getSlot = findMethod(Inventory.class, "getSelectedSlot");
        Field selectedField = null;
        if (setSlot == null) {
            // 1.21.1: public field
            try {
                selectedField = Inventory.class.getField("selected");
            } catch (NoSuchFieldException e) {
                LOGGER.error("Cannot find Inventory.selected field", e);
            }
        }
        INVENTORY_SET_SLOT = setSlot;
        INVENTORY_GET_SLOT = getSlot;
        INVENTORY_SELECTED_FIELD = selectedField;
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

    // ── Anvil event ─────────────────────────────────────────────────────

    /**
     * Sets the XP cost on an AnvilUpdateEvent cross-version.
     * 1.21.1 NeoForge: setCost(long); 1.21.11 NeoForge: setXpCost(int).
     */
    public static void setAnvilEventCost(
        net.neoforged.neoforge.event.AnvilUpdateEvent event,
        int cost
    ) {
        // Try 1.21.1 API first
        try {
            event.getClass().getMethod("setCost", long.class).invoke(event, (long) cost);
            return;
        } catch (NoSuchMethodException ignored) {}
        catch (Exception e) {
            LOGGER.error("setCost(long) failed", e);
            return;
        }
        // 1.21.11 API
        try {
            event.getClass().getMethod("setXpCost", int.class).invoke(event, cost);
        } catch (Exception e) {
            LOGGER.error("setXpCost(int) failed", e);
        }
    }

    // ── AttributeModifier (cross-version: ResourceLocation vs Identifier) ───

    /**
     * Creates an AttributeModifier with the given namespace:path ID.
     * 1.21.1 uses ResourceLocation; 1.21.11 uses Identifier (same class, renamed).
     * The id object is obtained via {@link #createIdentifier}.
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.world.entity.ai.attributes.AttributeModifier createAttributeModifier(
        String namespace,
        String path,
        double amount,
        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation
    ) {
        Object id = createIdentifier(namespace, path);
        try {
            return net.minecraft.world.entity.ai.attributes.AttributeModifier.class
                .getDeclaredConstructor(id.getClass(), double.class,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class)
                .newInstance(id, amount, operation);
        } catch (Exception e) {
            // Fallback: try superclass (in case Identifier extends ResourceLocation)
            try {
                Class<?> idClass = id.getClass().getSuperclass();
                return net.minecraft.world.entity.ai.attributes.AttributeModifier.class
                    .getDeclaredConstructor(idClass, double.class,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class)
                    .newInstance(id, amount, operation);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot create AttributeModifier for " + namespace + ":" + path, e2);
            }
        }
    }

    /**
     * Removes an attribute modifier identified by namespace:path.
     * Works cross-version (ResourceLocation vs Identifier).
     */
    public static void removeAttributeModifier(
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance,
        String namespace,
        String path
    ) {
        Object id = createIdentifier(namespace, path);
        try {
            // Both versions have removeModifier(id-type) — find the method by param type
            for (java.lang.reflect.Method m : instance.getClass().getMethods()) {
                if (m.getName().equals("removeModifier") && m.getParameterCount() == 1
                        && !m.getParameterTypes()[0].equals(
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.class)) {
                    m.invoke(instance, id);
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.error("removeAttributeModifier failed for {}:{}", namespace, path, e);
        }
    }

    // ── NBT compat ─────────────────────────────────────────────────────

    /**
     * Cross-version CompoundTag.getInt — returns int on 1.21.1,
     * but Optional&lt;Integer&gt; on 1.21.11. Returns 0 if key missing.
     */
    public static int getTagInt(CompoundTag tag, String key) {
        Object result;
        try {
            result = tag.getClass().getMethod("getInt", String.class).invoke(tag, key);
        } catch (Exception e) {
            LOGGER.error("getInt reflection failed for key {}", key, e);
            return 0;
        }
        if (result instanceof Integer i) return i;
        // 1.21.11: Optional<Integer>
        if (result instanceof java.util.Optional<?> opt) {
            return opt.map(v -> (Integer) v).orElse(0);
        }
        return 0;
    }

    // ── Inventory slot compat ─────────────────────────────────────────

    public static int getInventorySlot(Inventory inventory) {
        if (INVENTORY_GET_SLOT != null) {
            try {
                return (int) INVENTORY_GET_SLOT.invoke(inventory);
            } catch (Exception e) {
                LOGGER.error("getSelectedSlot failed", e);
            }
        }
        if (INVENTORY_SELECTED_FIELD != null) {
            try {
                return INVENTORY_SELECTED_FIELD.getInt(inventory);
            } catch (Exception e) {
                LOGGER.error("Inventory.selected get failed", e);
            }
        }
        return 0;
    }

    public static void setInventorySlot(Inventory inventory, int slot) {
        if (INVENTORY_SET_SLOT != null) {
            try {
                INVENTORY_SET_SLOT.invoke(inventory, slot);
                return;
            } catch (Exception e) {
                LOGGER.error("setSelectedSlot failed", e);
            }
        }
        if (INVENTORY_SELECTED_FIELD != null) {
            try {
                INVENTORY_SELECTED_FIELD.setInt(inventory, slot);
            } catch (Exception e) {
                LOGGER.error("Inventory.selected set failed", e);
            }
        }
    }

    // ── ResourceKey compat ────────────────────────────────────────────

    /**
     * Cross-version ResourceKey → namespace + path extraction.
     * 1.21.1: key.location().getNamespace() / .getPath()
     * 1.21.11: key.location() may be renamed to key.registryKey() or similar.
     */
    public static String resourceKeyId(net.minecraft.resources.ResourceKey<?> key) {
        try {
            // Try location() first (1.21.1)
            Object loc = key.getClass().getMethod("location").invoke(key);
            String ns = (String) loc.getClass().getMethod("getNamespace").invoke(loc);
            String path = (String) loc.getClass().getMethod("getPath").invoke(loc);
            return ns + "_" + path.replace('/', '_');
        } catch (NoSuchMethodException ignored) {}
        catch (Exception e) {
            LOGGER.error("resourceKeyId reflection failed", e);
        }
        // Fallback: toString() parsing — format is "ResourceKey[registry / namespace:path]"
        String s = key.toString();
        int slash = s.indexOf('/');
        if (slash >= 0) {
            String idPart = s.substring(slash + 1).replace(']', ' ').trim();
            return idPart.replace(':', '_').replace('/', '_');
        }
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
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
