package com.trevlar.slotkit.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * The library's one generic "open my menu" request from client to server. Carries only
 * the {@link Identifier} of the {@code CustomMenu} the client wants opened. The server
 * receiver, registered once in {@code SlotKit.init()}, looks the menu up by
 * that id, moves to the main thread and calls {@code CustomMenu.open(player)}.
 *
 * <p>Custom menus used to ship their own hand-written open payload each. Now one
 * payload and one receiver serve every {@code CustomMenu} a consumer defines, keyed by
 * the menu's registered id. An unknown id is logged loudly on the receiver side, never
 * an NPE. The server opens the menu only when its definition declares
 * {@code validWhen} and it holds for the player ({@code CustomMenu.handleOpenRequest}).
 */
@ApiStatus.Internal
public record OpenMenuC2SPayload(Identifier menuId) implements CustomPacketPayload {

    public static final Type<OpenMenuC2SPayload> TYPE =
            new Type<>(Handshake.id("open_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, OpenMenuC2SPayload::menuId,
                    OpenMenuC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
