package com.trevlar.menukit.containers.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one library-owned generic "open my menu" Client → Server request. Carries
 * only the {@link Identifier} of the {@code CustomMenu} the client wants opened; the
 * server-side receiver (registered once in {@code MenuKitContainers.init()}) looks the menu up
 * by that id, hops to the main thread, and calls {@code CustomMenu.open(player)}.
 *
 * <p>This replaces the per-consumer hand-rolled open payload that every custom
 * menu used to ship: one generic payload + one generic receiver serves <em>every</em>
 * {@code CustomMenu} a consumer defines, keyed by the menu's registered id. An unknown
 * id is a fail-loud log on the receiver side, never an NPE. The server opens the menu
 * only when its definition declares {@code validWhen} and it holds for the player
 * ({@code CustomMenu.handleOpenRequest}, §0067).
 */
@ApiStatus.Internal
public record OpenMenuC2SPayload(Identifier menuId) implements CustomPacketPayload {

    public static final Type<OpenMenuC2SPayload> TYPE =
            new Type<>(Presence.id("open_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, OpenMenuC2SPayload::menuId,
                    OpenMenuC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
