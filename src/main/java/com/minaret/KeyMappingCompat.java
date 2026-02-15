package com.minaret;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side KeyMapping compatibility across Minecraft versions.
 * 1.21.1: KeyMapping(String, int, String) with string categories.
 * 1.21.11: KeyMapping(String, int, Category) with Category record + Identifier.
 */
public final class KeyMappingCompat {

    private static final Logger LOGGER = LogManager.getLogger();
    private static Object cachedCategory;

    private KeyMappingCompat() {}

    /** Create a KeyMapping cross-version. */
    public static Object createKeyMapping(String name, int keyCode, String categoryId) {
        try {
            Class<?> km = Class.forName("net.minecraft.client.KeyMapping");

            // Try 1.21.11: KeyMapping(String, int, KeyMapping.Category)
            for (Class<?> inner : km.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Category")) {
                    Object identifier = Compat.createIdentifier(MinaretMod.MOD_ID, categoryId);
                    Object category = getCategoryInstance(inner, identifier);
                    Constructor<?> ctor = km.getConstructor(String.class, int.class, inner);
                    return ctor.newInstance(name, keyCode, category);
                }
            }

            // Fallback 1.21.1: KeyMapping(String, int, String)
            Constructor<?> ctor = km.getConstructor(String.class, int.class, String.class);
            return ctor.newInstance(name, keyCode, "Minaret Chords");
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyMapping: " + name, e);
        }
    }

    /** Register a KeyMapping category via RegisterKeyMappingsEvent (1.21.11+ only). */
    public static void registerCategory(Object event) {
        if (cachedCategory == null) return;
        try {
            Method rc = event.getClass().getMethod("registerCategory", cachedCategory.getClass());
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

    private static Object getCategoryInstance(Class<?> categoryClass, Object identifier) {
        if (cachedCategory != null) return cachedCategory;
        try {
            for (Method m : categoryClass.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(identifier)) {
                    cachedCategory = m.invoke(null, identifier);
                    return cachedCategory;
                }
            }
            Constructor<?> ctor = categoryClass.getConstructor(identifier.getClass());
            cachedCategory = ctor.newInstance(identifier);
            return cachedCategory;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyMapping.Category", e);
        }
    }
}
