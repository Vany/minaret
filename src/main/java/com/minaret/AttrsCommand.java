package com.minaret;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles /minaret attrs [player]: builds a markdown attribute report and
 * sends it to the executing player's clipboard via ClipboardPacket.
 */
public final class AttrsCommand {

    private AttrsCommand() {}

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("generic.max_health",             "Maximum health points (half-hearts)"),
        Map.entry("generic.attack_damage",           "Melee damage per hit"),
        Map.entry("generic.attack_speed",            "Attacks per second (controls cooldown)"),
        Map.entry("generic.armor",                   "Damage reduction from physical attacks"),
        Map.entry("generic.armor_toughness",         "Reduces armor bypass from high-damage hits"),
        Map.entry("generic.knockback_resistance",    "Chance (0–1) to resist knockback"),
        Map.entry("generic.movement_speed",          "Base movement speed multiplier"),
        Map.entry("generic.flying_speed",            "Flight speed multiplier"),
        Map.entry("generic.attack_knockback",        "Knockback applied to targets on hit"),
        Map.entry("generic.luck",                    "Shifts loot table luck — higher = better drops"),
        Map.entry("generic.max_absorption",          "Maximum absorption (golden) hearts"),
        Map.entry("generic.fall_damage_multiplier",  "Multiplier on fall damage taken"),
        Map.entry("generic.gravity",                 "Downward acceleration (blocks/tick²)"),
        Map.entry("generic.jump_strength",           "Initial upward velocity when jumping"),
        Map.entry("generic.safe_fall_distance",      "Blocks fallen before damage begins"),
        Map.entry("generic.scale",                   "Entity size scale factor"),
        Map.entry("generic.step_height",             "Max block step height without jumping"),
        Map.entry("generic.swim_speed",              "Speed while swimming"),
        Map.entry("player.block_break_speed",        "Multiplier on block break speed"),
        Map.entry("player.block_interaction_range",  "Max reach distance for blocks"),
        Map.entry("player.entity_interaction_range", "Max reach distance for entities and attacks"),
        Map.entry("player.mining_efficiency",        "Bonus efficiency when using the correct tool"),
        Map.entry("player.sneaking_speed",           "Movement speed while sneaking"),
        Map.entry("player.submerged_mining_speed",   "Mining speed while fully submerged"),
        Map.entry("player.sweeping_damage_ratio",    "Fraction of damage dealt to sweep targets")
    );

    public static int execute(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer executor)) {
            source.sendFailure(Component.literal("/minaret attrs must be run in-game by a player"));
            return 0;
        }

        ServerPlayer target;
        if (playerName != null) {
            target = source.getServer().getPlayerList().getPlayerByName(playerName);
            if (target == null) {
                source.sendFailure(Component.literal("Player '" + playerName + "' not found or not online"));
                return 0;
            }
        } else {
            target = executor;
        }

        String report = buildReport(target);
        PacketDistributor.sendToPlayer(executor, new ClipboardPacket(report));
        executor.sendSystemMessage(Component.literal("Copied to clipboard."));
        return 1;
    }

    private static String buildReport(ServerPlayer player) {
        List<String> sections = new ArrayList<>();

        BuiltInRegistries.ATTRIBUTE.stream().forEach(attr -> {
            AttributeInstance instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr));
            if (instance == null) return;

            Set<AttributeModifier> allMods = instance.getModifiers();
            if (allMods.isEmpty()) return;

            var loc = BuiltInRegistries.ATTRIBUTE.getKey(attr);
            String key = loc != null ? loc.getPath() : "unknown";

            double base  = instance.getBaseValue();
            double total = instance.getValue();
            double delta = total - base;

            StringBuilder sec = new StringBuilder();
            sec.append("## ").append(formatName(key)).append("\n");
            sec.append("*").append(DESCRIPTIONS.getOrDefault(key, "Attribute: " + key)).append("*\n\n");
            sec.append("**").append(fmt(base)).append("** + **").append(fmt(delta))
               .append("** = **").append(fmt(total)).append("**\n\n");
            sec.append("| Source | Amount | Operation |\n");
            sec.append("|--------|--------|-----------|\n");
            for (AttributeModifier mod : allMods) {
                String sign = mod.amount() >= 0 ? "+" : "";
                String opLabel = switch (mod.operation()) {
                    case ADD_VALUE            -> "flat";
                    case ADD_MULTIPLIED_BASE  -> "×base";
                    case ADD_MULTIPLIED_TOTAL -> "×total";
                };
                sec.append("| `").append(mod.id().getPath()).append("` | ")
                   .append(sign).append(fmt(mod.amount())).append(" | ")
                   .append(opLabel).append(" |\n");
            }

            sections.add(sec.toString());
        });

        if (sections.isEmpty()) {
            return "# Attributes: " + player.getName().getString() + "\n\n*No active attribute modifiers.*";
        }
        return "# Attributes: " + player.getName().getString() + "\n\n"
            + String.join("\n", sections);
    }

    private static String formatName(String key) {
        int dot = key.lastIndexOf('.');
        String raw = dot >= 0 ? key.substring(dot + 1) : key;
        StringBuilder result = new StringBuilder();
        for (String word : raw.split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        String s = String.format("%.4f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }
}
