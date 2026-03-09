package com.minaret;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles the Swift Strike enchantment (minaret:swift_strike).
 * Reduces attack delay by applying ADD_MULTIPLIED_TOTAL on ATTACK_SPEED.
 *
 * Delay formula: new_delay = old_delay / (1 + mult)
 *   Level 1: mult = 1/3  → -25% delay
 *   Level 2: mult = 1.0  → -50% delay
 *   Level 3: mult = 3.0  → -75% delay
 */
public final class SwiftStrikeHandler {

    private static final String NS   = MinaretMod.MOD_ID;
    private static final String PATH = "swift_strike";

    private static final double[] MULT = { 1.0 / 3.0, 1.0, 3.0 };

    static final ResourceKey<Enchantment> KEY = Compat.enchantmentKey("swift_strike");

    private SwiftStrikeHandler() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;

        int level = getLevel(player.getMainHandItem());
        Compat.removeAttributeModifier(attr, NS, PATH);
        if (level > 0) {
            attr.addTransientModifier(Compat.createAttributeModifier(
                NS, PATH,
                MULT[Math.min(level, 3) - 1],
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
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
