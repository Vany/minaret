package com.minaret;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MinaretConfig {

    private static final String DEFAULT_URL = "localhost:8765";
    private static final String DEFAULT_USERNAME = "";
    private static final String DEFAULT_PASSWORD = "";

    private static final ModConfigSpec.Builder BUILDER =
        new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> WEBSOCKET_URL =
        BUILDER.comment("WebSocket server URL (host:port)").define(
            "websocket_url",
            DEFAULT_URL
        );

    public static final ModConfigSpec.ConfigValue<String> AUTH_USERNAME =
        BUILDER.comment(
            "WebSocket authentication username (empty = no auth)"
        ).define("auth_username", DEFAULT_USERNAME);

    public static final ModConfigSpec.ConfigValue<String> AUTH_PASSWORD =
        BUILDER.comment("WebSocket authentication password").define(
            "auth_password",
            DEFAULT_PASSWORD
        );

    public static final ModConfigSpec CONFIG_SPEC = BUILDER.build();
}
