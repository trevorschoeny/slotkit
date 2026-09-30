package com.trevlar.menukit.containers.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Server to client: every stored slot-state value on a menu, sent when the menu opens
 * and, for the player's own inventory menu, on join and respawn. Each entry names the
 * slot by its index in that menu and carries the value as the channel's
 * {@code StreamCodec} bytes (§0067). A client drops an entry for a channel it does not
 * have, or a value it cannot decode.
 */
@ApiStatus.Internal
public record SlotStateSnapshotS2CPayload(int containerId, List<Entry> entries) implements CustomPacketPayload {

    public static final Type<SlotStateSnapshotS2CPayload> TYPE = new Type<>(Presence.id("slot_state_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotStateSnapshotS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SlotStateSnapshotS2CPayload::containerId,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SlotStateSnapshotS2CPayload::entries,
                    SlotStateSnapshotS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One slot's value on one channel. */
    public record Entry(int menuSlotIndex, Identifier channelId, byte[] value) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Entry::menuSlotIndex,
                        Identifier.STREAM_CODEC, Entry::channelId,
                        ByteBufCodecs.byteArray(SlotStateWire.MAX_VALUE_BYTES), Entry::value,
                        Entry::new);
    }
}
