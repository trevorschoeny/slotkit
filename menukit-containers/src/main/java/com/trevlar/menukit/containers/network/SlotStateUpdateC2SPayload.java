package com.trevlar.menukit.containers.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client to server: a slot-state write the server judges (§0067). Names the menu the
 * client wrote in ({@code containerId}, so a write that races a menu switch is refused),
 * the slot by its index in that menu, the channel, and the value as the channel's own
 * {@code StreamCodec} bytes. The server decodes those bytes with that codec, and stores
 * the value's canonical re-encoding, never the client's bytes.
 */
@ApiStatus.Internal
public record SlotStateUpdateC2SPayload(int containerId, int menuSlotIndex, Identifier channelId, byte[] value)
        implements CustomPacketPayload {

    public static final Type<SlotStateUpdateC2SPayload> TYPE = new Type<>(Presence.id("slot_state_write"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotStateUpdateC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SlotStateUpdateC2SPayload::containerId,
                    ByteBufCodecs.VAR_INT, SlotStateUpdateC2SPayload::menuSlotIndex,
                    Identifier.STREAM_CODEC, SlotStateUpdateC2SPayload::channelId,
                    ByteBufCodecs.byteArray(SlotStateWire.MAX_VALUE_BYTES), SlotStateUpdateC2SPayload::value,
                    SlotStateUpdateC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
