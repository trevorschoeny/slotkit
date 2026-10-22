package com.trevlar.slotkit.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;

/**
 * One change to an on-demand group instance on the receiving player's open menu
 * ({@code OnDemandGroup}): added at slot {@code at} with {@code count} live slots, its
 * live count changed to {@code count}, or removed. {@code containerId} names the menu,
 * so a change meant for a menu the client already left is dropped.
 */
@ApiStatus.Internal
public record GroupInstanceS2CPayload(int containerId, int action, String kind, int number, int at, int count)
        implements CustomPacketPayload {

    public static final int ADD = 0;
    public static final int COUNT = 1;
    public static final int REMOVE = 2;

    public static final Type<GroupInstanceS2CPayload> TYPE = new Type<>(Handshake.id("group_instance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GroupInstanceS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GroupInstanceS2CPayload::containerId,
                    ByteBufCodecs.VAR_INT, GroupInstanceS2CPayload::action,
                    ByteBufCodecs.STRING_UTF8, GroupInstanceS2CPayload::kind,
                    ByteBufCodecs.VAR_INT, GroupInstanceS2CPayload::number,
                    ByteBufCodecs.VAR_INT, GroupInstanceS2CPayload::at,
                    ByteBufCodecs.VAR_INT, GroupInstanceS2CPayload::count,
                    GroupInstanceS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
