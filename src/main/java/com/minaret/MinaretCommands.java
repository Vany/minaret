package com.minaret;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * All /minaret subcommands: exec, addkey, delkey, listkeys, listactions.
 */
public final class MinaretCommands {

    private MinaretCommands() {}

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
            Commands.literal("minaret")
                .requires(source -> Compat.hasPermission(source, 4))
                .then(
                    Commands.literal("exec").then(
                        Commands.argument(
                            "json",
                            StringArgumentType.greedyString()
                        ).executes(ctx ->
                            exec(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "json")
                            )
                        )
                    )
                )
                .then(
                    Commands.literal("addkey").then(
                        Commands.argument(
                            "args",
                            StringArgumentType.greedyString()
                        )
                            .suggests((ctx, builder) -> {
                                String input = builder.getRemaining();
                                if (input.contains(" ")) {
                                    int sp = input.indexOf(' ');
                                    return SharedSuggestionProvider.suggest(
                                        com.minaret.client.ChordKeyHandler.getAllKeyMappingNames(),
                                        builder.createOffset(
                                            builder.getStart() + sp + 1
                                        )
                                    );
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String args = StringArgumentType.getString(
                                    ctx,
                                    "args"
                                );
                                int sp = args.indexOf(' ');
                                if (sp < 0) {
                                    ctx
                                        .getSource()
                                        .sendFailure(
                                            Component.literal(
                                                "Usage: /minaret addkey <sequence> <action>"
                                            )
                                        );
                                    return 0;
                                }
                                return addKey(
                                    ctx.getSource(),
                                    args.substring(0, sp),
                                    args.substring(sp + 1).trim()
                                );
                            })
                    )
                )
                .then(
                    Commands.literal("delkey").then(
                        Commands.argument(
                            "sequence",
                            StringArgumentType.greedyString()
                        ).executes(ctx ->
                            delKey(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "sequence")
                            )
                        )
                    )
                )
                .then(
                    Commands.literal("listkeys").executes(ctx ->
                        listKeys(ctx.getSource())
                    )
                )
                .then(
                    Commands.literal("addcommand").then(
                        Commands.argument(
                            "args",
                            StringArgumentType.greedyString()
                        ).executes(ctx -> {
                            String args = StringArgumentType.getString(
                                ctx,
                                "args"
                            );
                            int sp = args.indexOf(' ');
                            if (sp < 0) {
                                ctx
                                    .getSource()
                                    .sendFailure(
                                        Component.literal(
                                            "Usage: /minaret addcommand <sequence> <json>"
                                        )
                                    );
                                return 0;
                            }
                            return addCommand(
                                ctx.getSource(),
                                args.substring(0, sp),
                                args.substring(sp + 1).trim()
                            );
                        })
                    )
                )
                .then(
                    Commands.literal("listactions")
                        .executes(ctx -> listActions(ctx.getSource()))
                        .then(
                            Commands.argument(
                                "filter",
                                StringArgumentType.greedyString()
                            ).executes(ctx ->
                                listActions(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "filter")
                                )
                            )
                        )
                )
        );
    }

    private static int exec(CommandSourceStack source, String json) {
        MessageDispatcher.dispatch(json, source.getServer(), response ->
            source.sendSuccess(() -> Component.literal(response), false)
        );
        return 1;
    }

    private static int addKey(
        CommandSourceStack source,
        String sequence,
        String action
    ) {
        String error = com.minaret.client.ChordKeyHandler.validateSequence(
            sequence
        );
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        String target = ChordConfig.KEY_PREFIX + action;
        if (!ChordConfig.get().addChord(sequence.toLowerCase(), target)) {
            source.sendFailure(
                Component.literal(
                    "Chord '" + sequence.toLowerCase() + "' already exists"
                )
            );
            return 0;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(
            () ->
                Component.literal(
                    "Added chord '" + sequence.toLowerCase() + "' → " + action
                ),
            false
        );
        return 1;
    }

    private static int addCommand(
        CommandSourceStack source,
        String sequence,
        String json
    ) {
        String error = com.minaret.client.ChordKeyHandler.validateSequence(
            sequence
        );
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        String target = ChordConfig.CMD_PREFIX + json;
        if (!ChordConfig.get().addChord(sequence.toLowerCase(), target)) {
            source.sendFailure(
                Component.literal(
                    "Chord '" + sequence.toLowerCase() + "' already exists"
                )
            );
            return 0;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(
            () ->
                Component.literal(
                    "Added chord '" +
                        sequence.toLowerCase() +
                        "' → command: " +
                        json
                ),
            false
        );
        return 1;
    }

    private static int delKey(CommandSourceStack source, String sequence) {
        if (!ChordConfig.get().removeChord(sequence)) {
            source.sendFailure(
                Component.literal("Chord '" + sequence + "' not found")
            );
            return 0;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(
            () ->
                Component.literal(
                    "Removed chord '" + sequence.toLowerCase() + "'"
                ),
            false
        );
        return 1;
    }

    private static int listKeys(CommandSourceStack source) {
        var chords = ChordConfig.get().getChords();
        if (chords.isEmpty()) {
            source.sendSuccess(
                () ->
                    Component.literal(
                        "No chord keys configured. Use /minaret addkey <sequence> <action>"
                    ),
                false
            );
            return 1;
        }
        StringBuilder sb = new StringBuilder("Chord keys (meta: ")
            .append(ChordConfig.get().getMetaKey())
            .append("):\n");
        for (var entry : chords.entrySet()) {
            sb
                .append("  ")
                .append(entry.getKey())
                .append(" → ")
                .append(entry.getValue())
                .append('\n');
        }
        String msg = sb.toString().stripTrailing();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int listActions(CommandSourceStack source) {
        return listActions(source, null);
    }

    private static int listActions(CommandSourceStack source, String filter) {
        var names = com.minaret.client.ChordKeyHandler.getAllKeyMappingNames();
        if (filter != null && !filter.isEmpty()) {
            String f = filter.toLowerCase();
            names = names
                .stream()
                .filter(n -> n.toLowerCase().contains(f))
                .toList();
        }
        if (names.isEmpty()) {
            source.sendSuccess(
                () -> Component.literal("No matching actions found"),
                false
            );
            return 1;
        }
        StringBuilder sb = new StringBuilder("Available actions");
        if (filter != null) sb.append(" (filter: ").append(filter).append(")");
        sb.append(":\n");
        for (String name : names) {
            sb.append("  ").append(name).append('\n');
        }
        String msg = sb.toString().stripTrailing();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
