package com.minaret;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Inventory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cross-version compatibility: permissions, environment detection, inventory slot access.
 * KeyMapping compat is in {@link KeyMappingCompat}.
 */
public final class Compat {

    private static final Logger LOGGER = LogManager.getLogger();

    private Compat() {}

    // ── Cached reflection lookups ───────────────────────────────────────

    private static final Method HAS_PERMISSION;
    /** 1.21.11: setSelectedSlot(int) — null on 1.21.1 (uses public field). */
    private static final Method INVENTORY_SET_SLOT;
    /** 1.21.11: getSelectedSlot() — null on 1.21.1 (uses public field). */
    private static final Method INVENTORY_GET_SLOT;
    /** 1.21.1: public field Inventory.selected — null on 1.21.11. */
    private static final Field INVENTORY_SELECTED_FIELD;

    static {
        HAS_PERMISSION = findMethod(CommandSourceStack.class, "hasPermission", int.class);

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

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ── Identifier (ResourceLocation on 1.21.1, Identifier on 1.21.11) ──

    /**
     * Factory method: Identifier.of(ns, path) on 1.21.11, ResourceLocation.fromNamespaceAndPath on 1.21.1.
     * Returns the ID object as Object to avoid a compile-time dependency on either class.
     */
    private static final Method CREATE_IDENTIFIER = resolveCreateIdentifier();

    private static Method resolveCreateIdentifier() {
        // Both Identifier (1.21.11) and ResourceLocation (1.21.1) use fromNamespaceAndPath.
        for (String cls : new String[]{
                "net.minecraft.resources.Identifier",
                "net.minecraft.resources.ResourceLocation"}) {
            try {
                return Class.forName(cls).getMethod("fromNamespaceAndPath", String.class, String.class);
            } catch (Exception ignored) {}
        }
        LOGGER.error("Cannot resolve identifier factory — neither Identifier nor ResourceLocation found");
        return null;
    }

    /** Create a resource identifier (Identifier on 1.21.11, ResourceLocation on 1.21.1). */
    public static Object createIdentifier(String namespace, String path) {
        if (CREATE_IDENTIFIER == null) throw new RuntimeException("No identifier factory available");
        try {
            return CREATE_IDENTIFIER.invoke(null, namespace, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create identifier " + namespace + ":" + path, e);
        }
    }

    // ── Environment ─────────────────────────────────────────────────────

    private static final boolean IS_CLIENT = resolveIsClient();

    public static boolean isClient() { return IS_CLIENT; }

    private static boolean resolveIsClient() {
        try {
            Class<?> fmlEnv = Class.forName("net.neoforged.fml.loading.FMLEnvironment");
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

    // ── Permissions ─────────────────────────────────────────────────────

    public static boolean hasPermission(CommandSourceStack source, int level) {
        if (HAS_PERMISSION != null) {
            try {
                return (boolean) HAS_PERMISSION.invoke(source, level);
            } catch (Exception ignored) {}
        }
        return source.getServer().getCommands().getDispatcher().getRoot().canUse(source);
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
}
