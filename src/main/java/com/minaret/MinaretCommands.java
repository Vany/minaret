package com.minaret;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * All /minaret subcommands: exec, addkey, delkey, listkeys.
 */
public final class MinaretCommands {

    private MinaretCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("minaret")
                .requires(source -> Compat.hasPermission(source, 4))
                .then(Commands.literal("exec").then(
                    Commands.argument("json", StringArgumentType.greedyString())
                        .executes(ctx -> exec(ctx.getSource(), StringArgumentType.getString(ctx, "json")))))
                .then(Commands.literal("addkey").then(
                    Commands.argument("sequence", StringArgumentType.greedyString())
                        .executes(ctx -> addKey(ctx.getSource(), StringArgumentType.getString(ctx, "sequence")))))
                .then(Commands.literal("delkey").then(
                    Commands.argument("sequence", StringArgumentType.greedyString())
                        .executes(ctx -> delKey(ctx.getSource(), StringArgumentType.getString(ctx, "sequence")))))
                .then(Commands.literal("listkeys")
                    .executes(ctx -> listKeys(ctx.getSource())))
        );
    }

    private static int exec(CommandSourceStack source, String json) {
        MessageDispatcher.dispatch(json, source.getServer(),
            response -> source.sendSuccess(() -> Component.literal(response), false));
        return 1;
    }

    private static int addKey(CommandSourceStack source, String sequence) {
        String error = com.minaret.client.ChordKeyHandler.validateSequence(sequence);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        if (!ChordConfig.get().addChord(sequence.toLowerCase())) {
            source.sendSuccess(() -> Component.literal("Chord '" + sequence.toLowerCase() + "' already exists"), false);
            return 1;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(() -> Component.literal("Added chord '" + sequence.toLowerCase() + "'"), false);
        return 1;
    }

    private static int delKey(CommandSourceStack source, String sequence) {
        if (!ChordConfig.get().removeChord(sequence)) {
            source.sendFailure(Component.literal("Chord '" + sequence + "' not found"));
            return 0;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(() -> Component.literal("Removed chord '" + sequence.toLowerCase() + "'"), false);
        return 1;
    }

    private static int listKeys(CommandSourceStack source) {
        var chords = ChordConfig.get().getChords();
        if (chords.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No chord keys configured. Use /minaret addkey <sequence>"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("Chord keys (meta: ")
            .append(ChordConfig.get().getMetaKey()).append("):\n");
        for (String chord : chords) {
            sb.append("  ").append(chord).append('\n');
        }
        String msg = sb.toString().stripTrailing();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
