package com.trevlar.slotkit.network;

import com.trevlar.slotkit.api.reserve.Reservation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client to server: reserve a slot of the menu the client has open (or clear it), named
 * by the menu's id and the slot's index on it, as a click names one. The server applies
 * it as the player's own action ({@code Reservations.reserve}).
 */
@ApiStatus.Internal
public record ReserveC2SPayload(int containerId, int menuSlot, Reservation reservation) implements CustomPacketPayload {

    public static final Type<ReserveC2SPayload> TYPE = new Type<>(Handshake.id("reserve"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReserveC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReserveC2SPayload::containerId,
            ByteBufCodecs.VAR_INT, ReserveC2SPayload::menuSlot,
            Reservation.STREAM_CODEC, ReserveC2SPayload::reservation,
            ReserveC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
