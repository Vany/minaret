package com.minaret;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MinaretConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    public static final ModConfigSpec.ConfigValue<String> WEBSOCKET_URL = BUILDER
        .comment("🌐 WebSocket server URL (host:port)")
        .define("websocket_url", "localhost:8765");
    
    public static final ModConfigSpec.ConfigValue<String> AUTH_USERNAME = BUILDER
        .comment("🔐 WebSocket authentication username (empty = no auth)")
        .define("auth_username", "");
    
    public static final ModConfigSpec.ConfigValue<String> AUTH_PASSWORD = BUILDER
        .comment("🔑 WebSocket authentication password")
        .define("auth_password", "");
    
    public static final ModConfigSpec SPEC = BUILDER.build();
}
