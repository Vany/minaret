package com.minaret;

import java.lang.reflect.Constructor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → Client packet: switch to hotbar slot, fire a KeyMapping action, restore original slot.
 * Sent by the "cast" WebSocket command.
 *
 * TYPE is created reflectively because the ID class changed between MC versions:
 *   1.21.1:  net.minecraft.resources.ResourceLocation
 *   1.21.11: net.minecraft.resources.Identifier
 */
public record CastPacket(int slot, String action) implements CustomPacketPayload {

    public static final Type<CastPacket> TYPE = createType();

    public static final StreamCodec<io.netty.buffer.ByteBuf, CastPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,     CastPacket::slot,
            ByteBufCodecs.STRING_UTF8, CastPacket::action,
            CastPacket::new
        );

    @Override
    public Type<CastPacket> type() { return TYPE; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Type<CastPacket> createType() {
        Object id = Compat.createIdentifier("minaret", "cast");
        try {
            Constructor<?>[] ctors = Type.class.getDeclaredConstructors();
            return (Type<CastPacket>) ctors[0].newInstance(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CastPacket.TYPE", e);
        }
    }
}
