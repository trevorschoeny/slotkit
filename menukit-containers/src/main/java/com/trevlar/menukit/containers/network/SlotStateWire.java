package com.trevlar.menukit.containers.network;

import com.trevlar.menukit.containers.api.state.SlotStateChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * A slot-state value on the wire: the channel's own {@code StreamCodec} bytes, as the
 * channel's type promises (§0067), never NBT. The bytes ride in a length-capped array
 * so a peer can skip a value of a channel it does not have, and decode one it does
 * with that channel's codec. Decoding never throws: a value that does not parse, or
 * leaves bytes over, is refused.
 */
@ApiStatus.Internal
public final class SlotStateWire {

    private SlotStateWire() {}

    /** The largest encoded value accepted, in bytes. A slot's state is a mark, not a document. */
    public static final int MAX_VALUE_BYTES = 32 * 1024;

    /** {@code value} as {@code channel}'s {@code StreamCodec} bytes. Throws if the codec cannot encode it. */
    public static <T> byte[] encode(SlotStateChannel<T> channel, T value, RegistryAccess registries) {
        ByteBuf raw = Unpooled.buffer();
        try {
            channel.streamCodec().encode(new RegistryFriendlyByteBuf(raw, registries), value);
            byte[] out = new byte[raw.readableBytes()];
            raw.readBytes(out);
            return out;
        } finally {
            raw.release();
        }
    }

    /** The value {@code bytes} encode on {@code channel}, or empty when they do not parse exactly. */
    public static <T> Optional<T> decode(SlotStateChannel<T> channel, byte[] bytes, RegistryAccess registries) {
        if (bytes.length > MAX_VALUE_BYTES) return Optional.empty();
        ByteBuf raw = Unpooled.wrappedBuffer(bytes);
        try {
            T value = channel.streamCodec().decode(new RegistryFriendlyByteBuf(raw, registries));
            if (value == null || raw.isReadable()) return Optional.empty(); // trailing bytes: not this value
            return Optional.of(value);
        } catch (RuntimeException e) {
            return Optional.empty();
        } finally {
            raw.release();
        }
    }
}
