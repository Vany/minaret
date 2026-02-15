package com.minaret;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * All /minaret subcommands: exec, addkey, addcommand, delkey, listkeys, listactions.
 */
public final class MinaretCommands {

    private static final int OP_LEVEL = 4;

    private MinaretCommands() {}

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
            Commands.literal("minaret")
                .requires(source -> Compat.hasPermission(source, OP_LEVEL))
                .then(execCommand())
                .then(addKeyCommand())
                .then(addCommandCommand())
                .then(delKeyCommand())
                .then(listKeysCommand())
                .then(listActionsCommand())
        );
    }

    // ── Command definitions ─────────────────────────────────────────────

    private static LiteralArgumentBuilder<CommandSourceStack> execCommand() {
        return Commands.literal("exec").then(
            Commands.argument(
                "json",
                StringArgumentType.greedyString()
            ).executes(ctx ->
                exec(ctx.getSource(), StringArgumentType.getString(ctx, "json"))
            )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addKeyCommand() {
        return Commands.literal("addkey").then(
            Commands.argument("args", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    String input = builder.getRemaining();
                    if (input.contains(" ")) {
                        int sp = input.indexOf(' ');
                        return SharedSuggestionProvider.suggest(
                            com.minaret.client.ChordKeyHandler.getAllKeyMappingNames(),
                            builder.createOffset(builder.getStart() + sp + 1)
                        );
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String[] parts = splitArgs(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "args"),
                        "Usage: /minaret addkey <sequence> <action>"
                    );
                    return parts != null
                        ? addChord(
                              ctx.getSource(),
                              parts[0],
                              new ChordTarget.Key(parts[1])
                          )
                        : 0;
                })
        );
    }

    private static LiteralArgumentBuilder<
        CommandSourceStack
    > addCommandCommand() {
        return Commands.literal("addcommand").then(
            Commands.argument(
                "args",
                StringArgumentType.greedyString()
            ).executes(ctx -> {
                String[] parts = splitArgs(
                    ctx.getSource(),
                    StringArgumentType.getString(ctx, "args"),
                    "Usage: /minaret addcommand <sequence> <json>"
                );
                return parts != null
                    ? addChord(
                          ctx.getSource(),
                          parts[0],
                          new ChordTarget.Command(parts[1])
                      )
                    : 0;
            })
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> delKeyCommand() {
        return Commands.literal("delkey").then(
            Commands.argument(
                "sequence",
                StringArgumentType.greedyString()
            ).executes(ctx ->
                delKey(
                    ctx.getSource(),
                    StringArgumentType.getString(ctx, "sequence")
                )
            )
        );
    }

    private static LiteralArgumentBuilder<
        CommandSourceStack
    > listKeysCommand() {
        return Commands.literal("listkeys").executes(ctx ->
            listKeys(ctx.getSource())
        );
    }

    private static LiteralArgumentBuilder<
        CommandSourceStack
    > listActionsCommand() {
        return Commands.literal("listactions")
            .executes(ctx -> listActions(ctx.getSource(), null))
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
            );
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Split "sequence rest" on first space, or send failure and return null. */
    private static String[] splitArgs(
        CommandSourceStack source,
        String args,
        String usage
    ) {
        int sp = args.indexOf(' ');
        if (sp < 0) {
            source.sendFailure(Component.literal(usage));
            return null;
        }
        return new String[] {
            args.substring(0, sp),
            args.substring(sp + 1).trim(),
        };
    }

    // ── Handlers ────────────────────────────────────────────────────────

    private static int exec(CommandSourceStack source, String json) {
        MessageDispatcher.dispatch(json, source.getServer(), response ->
            source.sendSuccess(() -> Component.literal(response), false)
        );
        return 1;
    }

    private static int addChord(
        CommandSourceStack source,
        String sequence,
        ChordTarget target
    ) {
        String error = com.minaret.client.ChordKeyHandler.validateSequence(
            sequence
        );
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        String normalized = sequence.toLowerCase();
        if (!ChordConfig.get().addChord(normalized, target)) {
            source.sendFailure(
                Component.literal("Chord '" + normalized + "' already exists")
            );
            return 0;
        }
        com.minaret.client.ChordKeyHandler.rebuildTrie();
        source.sendSuccess(
            () ->
                Component.literal(
                    "Added chord '" + normalized + "' → " + target.serialize()
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
                .append(entry.getValue().serialize())
                .append('\n');
        }
        source.sendSuccess(
            () -> Component.literal(sb.toString().stripTrailing()),
            false
        );
        return 1;
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
        source.sendSuccess(
            () -> Component.literal(sb.toString().stripTrailing()),
            false
        );
        return 1;
    }
}
