package com.minaret;

import java.lang.reflect.Constructor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → Client packet: copy a text string to the client's clipboard.
 * Sent by /minaret attrs.
 */
public record ClipboardPacket(String text) implements CustomPacketPayload {

    public static final Type<ClipboardPacket> TYPE = createType();

    public static final StreamCodec<io.netty.buffer.ByteBuf, ClipboardPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClipboardPacket::text,
            ClipboardPacket::new
        );

    @Override
    public Type<ClipboardPacket> type() { return TYPE; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Type<ClipboardPacket> createType() {
        Object id = Compat.createIdentifier("minaret", "clipboard");
        try {
            Constructor<?>[] ctors = Type.class.getDeclaredConstructors();
            return (Type<ClipboardPacket>) ctors[0].newInstance(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ClipboardPacket.TYPE", e);
        }
    }
}
