package com.minaret;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles the Toughness enchantment (minaret:toughness).
 * Sums levels across all worn armor pieces; applies a single ADD_VALUE modifier
 * to ARMOR_TOUGHNESS. Four pieces of Toughness III = +12 total toughness.
 */
public final class ToughnessHandler {

    private static final String NS   = MinaretMod.MOD_ID;
    private static final String PATH = "toughness";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    static final ResourceKey<Enchantment> KEY = Compat.enchantmentKey("toughness");

    private ToughnessHandler() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        AttributeInstance attr = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (attr == null) return;

        int total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            total += getLevel(player.getItemBySlot(slot));
        }

        Compat.removeAttributeModifier(attr, NS, PATH);
        if (total > 0) {
            attr.addTransientModifier(Compat.createAttributeModifier(
                NS, PATH,
                (double) total,
                AttributeModifier.Operation.ADD_VALUE
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
