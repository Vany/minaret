package com.minaret;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Bypasses the vanilla "Too Expensive" (cost >= 40) cap on anvils for players
 * with the mega_chanter effect. Replicates vanilla AnvilMenu.createResultInternal()
 * logic but omits the `cost >= 40 → empty output` guard.
 *
 * In NeoForge 1.21.11, AnvilUpdateEvent fires after vanilla already ran. If we
 * provide a non-empty output in the event, it overrides whatever vanilla put in
 * the result slot. So we always recompute and provide our own output when the
 * effect is active.
 */
public final class MegaChanterHandler {

    private MegaChanterHandler() {}

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!event.getPlayer().hasEffect(MinaretRegistries.MEGA_CHANTER)) return;

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        String name = event.getName();

        if (left.isEmpty() || !EnchantmentHelper.canStoreEnchantments(left)) return;

        ItemStack result = left.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
            EnchantmentHelper.getEnchantmentsForCrafting(result)
        );

        // Vanilla repair cost base (accumulated prior costs on both items)
        long baseCost = left.getOrDefault(DataComponents.REPAIR_COST, 0)
            + right.getOrDefault(DataComponents.REPAIR_COST, 0);

        int workCost = 0;
        int materialCost = 0;
        boolean isBook = false;

        if (!right.isEmpty()) {
            isBook = right.has(DataComponents.STORED_ENCHANTMENTS);

            // Branch 1: material repair (e.g. diamonds on diamond sword)
            if (result.isDamageableItem() && left.isValidRepairItem(right)) {
                int repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (repairAmount <= 0) return; // nothing to repair

                int j;
                for (j = 0; repairAmount > 0 && j < right.getCount(); j++) {
                    result.setDamageValue(result.getDamageValue() - repairAmount);
                    workCost++;
                    repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
                materialCost = j;
            } else {
                // Branch 2: item+item combine, or book enchant transfer
                if (!isBook && (!result.is(right.getItem()) || !result.isDamageableItem())) {
                    return; // incompatible
                }

                // Durability combine
                if (result.isDamageableItem() && !isBook) {
                    int leftDur = left.getMaxDamage() - left.getDamageValue();
                    int rightDur = right.getMaxDamage() - right.getDamageValue();
                    int combined = rightDur + result.getMaxDamage() * 12 / 100;
                    int newDamage = result.getMaxDamage() - (leftDur + combined);
                    if (newDamage < 0) newDamage = 0;
                    if (newDamage < result.getDamageValue()) {
                        result.setDamageValue(newDamage);
                        workCost += 2;
                    }
                }

                // Enchantment transfer
                ItemEnchantments rightEnchants = EnchantmentHelper.getEnchantmentsForCrafting(right);
                boolean anyCompatible = false;
                boolean anyIncompatible = false;

                for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchants.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int curLevel = enchantments.getLevel(holder);
                    int newLevel = entry.getIntValue();
                    newLevel = (curLevel == newLevel) ? newLevel + 1 : Math.max(newLevel, curLevel);

                    boolean compatible = left.supportsEnchantment(holder);
                    if (event.getPlayer().getAbilities().instabuild) compatible = true;

                    for (Holder<Enchantment> existing : enchantments.keySet()) {
                        if (!existing.equals(holder) && !Enchantment.areCompatible(holder, existing)) {
                            compatible = false;
                            workCost++;
                        }
                    }

                    if (!compatible) {
                        anyIncompatible = true;
                    } else {
                        anyCompatible = true;
                        if (newLevel > holder.value().getMaxLevel()) newLevel = holder.value().getMaxLevel();
                        enchantments.set(holder, newLevel);
                        int anvilCost = holder.value().getAnvilCost();
                        if (isBook) anvilCost = Math.max(1, anvilCost / 2);
                        workCost += anvilCost * newLevel;
                        if (left.getCount() > 1) workCost = 40;
                    }
                }

                if (anyIncompatible && !anyCompatible) return;
            }
        }

        // Rename
        int renameCost = 0;
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                renameCost = 1;
                workCost += renameCost;
                result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            renameCost = 1;
            workCost += renameCost;
            result.remove(DataComponents.CUSTOM_NAME);
        }

        if (workCost <= 0) return;

        long totalCost = Mth.clamp(baseCost + workCost, 0L, Integer.MAX_VALUE);

        // ── KEY DIFFERENCE FROM VANILLA ─────────────────────────────────
        // Vanilla blocks the operation when cost >= 40. We allow it, but cap
        // the displayed cost at 39 so AnvilScreen doesn't show "Too Expensive".
        // The player still pays up to 39 XP levels.
        if (totalCost >= 40) totalCost = 39;
        // ────────────────────────────────────────────────────────────────

        // Update accumulated repair cost on the output item (same as vanilla)
        int repairCost = result.getOrDefault(DataComponents.REPAIR_COST, 0);
        if (repairCost < right.getOrDefault(DataComponents.REPAIR_COST, 0)) {
            repairCost = right.getOrDefault(DataComponents.REPAIR_COST, 0);
        }
        if (renameCost != workCost || renameCost == 0) {
            repairCost = (int) Math.min((long) repairCost * 2L + 1L, Integer.MAX_VALUE);
        }
        result.set(DataComponents.REPAIR_COST, repairCost);
        EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());

        event.setOutput(result);
        // NeoForge 21.1.x uses setCost(long); 21.11.x renamed it to setXpCost(int).
        // We use reflection via Compat to stay cross-version compatible.
        Compat.setAnvilEventCost(event, (int) Math.min(totalCost, Integer.MAX_VALUE));
        event.setMaterialCost(materialCost);
    }
}
