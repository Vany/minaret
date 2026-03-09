package com.minaret;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the Accelerate enchantment (minaret:accelerate) for bows and crossbows.
 * Scales arrow velocity when the arrow enters the world.
 *
 * Cross-version: AbstractArrow moved from net.minecraft.world.entity.projectile
 * (1.21.1) to net.minecraft.world.entity.projectile.arrow (1.21.11).
 * Resolved at class load via reflection; all required methods live on Entity.
 *
 *   Level 1: ×1.5
 *   Level 2: ×2.0
 *   Level 3: ×3.0
 */
public final class AccelerateHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final double[] MULT = { 1.5, 2.0, 3.0 };

    static final ResourceKey<Enchantment> KEY = Compat.enchantmentKey("accelerate");

    // 1.21.11 moved AbstractArrow to a sub-package; resolve at class load.
    private static final Class<?> ABSTRACT_ARROW_CLASS = resolveAbstractArrow();

    private static Class<?> resolveAbstractArrow() {
        for (String name : new String[] {
            "net.minecraft.world.entity.projectile.arrow.AbstractArrow",  // 1.21.11+
            "net.minecraft.world.entity.projectile.AbstractArrow"         // 1.21.1
        }) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {}
        }
        LOGGER.error("AccelerateHandler: could not resolve AbstractArrow class");
        return null;
    }

    private AccelerateHandler() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (ABSTRACT_ARROW_CLASS == null) return;

        Entity entity = event.getEntity();
        if (!ABSTRACT_ARROW_CLASS.isInstance(entity)) return;
        // AbstractArrow extends Projectile in both versions; getOwner() lives on Projectile
        if (!(((Projectile) entity).getOwner() instanceof Player player)) return;

        // Check both hands — crossbow can be in offhand
        int level = Math.max(
            getLevel(player.getMainHandItem()),
            getLevel(player.getOffhandItem())
        );
        if (level <= 0) return;

        double mult = MULT[Math.min(level, 3) - 1];
        entity.setDeltaMovement(entity.getDeltaMovement().scale(mult));
    }

    private static int getLevel(ItemStack stack) {
        ItemEnchantments enc = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enc.keySet()) {
            if (holder.is(KEY)) {
                return enc.getLevel(holder);
            }
        }
        return 0;
    }
}
