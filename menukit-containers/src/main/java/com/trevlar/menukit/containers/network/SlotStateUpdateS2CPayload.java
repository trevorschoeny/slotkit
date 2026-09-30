package com.trevlar.menukit.containers.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Server to client: one slot's value changed. The server has already matched the slot
 * by its resolved container key in this viewer's own open menu (§0067), so the payload
 * names that menu ({@code containerId}) and the slot's index in it; the client applies
 * it only to that menu, never to another container's slot with the same index. The
 * value is the channel's {@code StreamCodec} bytes; a value equal to the channel's
 * default clears the slot.
 */
@ApiStatus.Internal
public record SlotStateUpdateS2CPayload(int containerId, int menuSlotIndex, Identifier channelId, byte[] value)
        implements CustomPacketPayload {

    public static final Type<SlotStateUpdateS2CPayload> TYPE = new Type<>(Presence.id("slot_state_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotStateUpdateS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SlotStateUpdateS2CPayload::containerId,
                    ByteBufCodecs.VAR_INT, SlotStateUpdateS2CPayload::menuSlotIndex,
                    Identifier.STREAM_CODEC, SlotStateUpdateS2CPayload::channelId,
                    ByteBufCodecs.byteArray(SlotStateWire.MAX_VALUE_BYTES), SlotStateUpdateS2CPayload::value,
                    SlotStateUpdateS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
