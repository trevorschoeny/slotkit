package com.trevlar.slotkit.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;

/**
 * Opens the player's own inventory screen ({@code MenuSwitch.openInventory}), which
 * vanilla has no packet for. The client opens it without closing the menu it had up,
 * since the server already left that menu. The inventory's contents follow at once.
 */
@ApiStatus.Internal
public record OpenInventoryS2CPayload() implements CustomPacketPayload {

    public static final OpenInventoryS2CPayload INSTANCE = new OpenInventoryS2CPayload();

    public static final Type<OpenInventoryS2CPayload> TYPE = new Type<>(Handshake.id("open_inventory"));

    public static final StreamCodec<ByteBuf, OpenInventoryS2CPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
