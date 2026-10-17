package com.trevlar.slotkit.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * The server's active counts for the receiving player's groups, keyed by each group's
 * slot panel id. A snapshot (on join and after respawn) replaces the client's copy; an
 * update changes the groups it names.
 */
@ApiStatus.Internal
public record ActiveCountS2CPayload(boolean snapshot, Map<String, Integer> counts) implements CustomPacketPayload {

    public static final Type<ActiveCountS2CPayload> TYPE = new Type<>(Handshake.id("active_count"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveCountS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ActiveCountS2CPayload::snapshot,
                    ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT, 4096),
                    ActiveCountS2CPayload::counts,
                    ActiveCountS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
