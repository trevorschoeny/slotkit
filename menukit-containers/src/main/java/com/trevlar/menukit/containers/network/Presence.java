package com.trevlar.menukit.containers.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Containers checks that a joining client has it, and requires it (§0069).
 *
 * <p>Created slots change a menu's slot count on both sides, so a client without
 * Containers on a server with it would be disconnected by a slot-count mismatch the
 * moment its inventory syncs, with an error that names nothing. This makes the
 * failure happen early, in Fabric's configuration phase before the world loads, and
 * say what to install:
 *
 * <ol>
 *   <li>The server's {@code CONFIGURE}: a client that cannot receive {@link Hello}
 *       (no Containers, or no Fabric at all) is disconnected with {@link #missingMessage()}.</li>
 *   <li>Otherwise a configuration task sends {@code hello(PROTOCOL)} and waits; the
 *       client answers with its own protocol. A different one is disconnected with
 *       {@link #versionMessage(int, int)}; the same one completes the task.</li>
 *   <li>The client records that its server runs Containers. A client that joins a
 *       server without it never hears the hello, so {@link #serverHasContainers()}
 *       stays false and it builds no created slots and sends nothing Containers-only
 *       (§0069: it turns its features off instead of desyncing).</li>
 * </ol>
 *
 * <p>Every Containers packet carries the protocol in its id
 * ({@code menukit-containers:v1/...}, §0067), so a peer on another protocol has no
 * receiver for it and never misreads one.
 */
@ApiStatus.Internal
public final class Presence {

    private Presence() {}

    /** The Containers wire protocol. Bump it when any payload's shape changes. */
    public static final int PROTOCOL = 1;

    /** The payload id for {@code path}, in Containers' namespace and protocol. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit-containers", "v" + PROTOCOL + "/" + path);
    }

    static final String MENUKIT_PAGE = "https://modrinth.com/mod/menukit";
    static final String CONTAINERS_PAGE = "https://modrinth.com/mod/menukit-containers";

    // ── The two payloads ────────────────────────────────────────────────

    /** Server to client, in configuration: "this server runs Containers, on this protocol". */
    public record Hello(int protocol) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(id("hello"));
        public static final StreamCodec<ByteBuf, Hello> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Hello::new, Hello::protocol);
        @Override public Type<Hello> type() { return TYPE; }
    }

    /** Client to server, in configuration: the client's answer, its own protocol. */
    public record Reply(int protocol) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(id("hello_reply"));
        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Reply::new, Reply::protocol);
        @Override public Type<Reply> type() { return TYPE; }
    }

    // ── What the player reads ───────────────────────────────────────────

    /** Why a client without Containers is refused: names both mods and where to get them. */
    public static Component missingMessage() {
        return Component.literal("This server needs MenuKit and MenuKit: Containers on your game too.\n\n"
                + "Install both, the same version the server uses:\n"
                + MENUKIT_PAGE + "\n" + CONTAINERS_PAGE);
    }

    /** Why a client on another Containers protocol is refused. */
    public static Component versionMessage(int serverProtocol, int clientProtocol) {
        return Component.literal("This server runs a different version of MenuKit: Containers "
                + "(protocol " + serverProtocol + ", yours " + clientProtocol + ").\n\n"
                + "Install the MenuKit and MenuKit: Containers versions the server uses:\n"
                + MENUKIT_PAGE + "\n" + CONTAINERS_PAGE);
    }

    /**
     * The server's judgement of a joining client: {@code null} to let it in, else the
     * reason to disconnect it. {@code clientProtocol} is {@code null} before it has
     * answered (only whether it can hear the hello is known then).
     */
    public static @Nullable Component refusal(boolean clientCanHear, @Nullable Integer clientProtocol) {
        if (!clientCanHear) return missingMessage();
        if (clientProtocol != null && clientProtocol != PROTOCOL) return versionMessage(PROTOCOL, clientProtocol);
        return null;
    }

    // ── The client's side of it ─────────────────────────────────────────

    // True on a server (it runs Containers by definition) and in singleplayer; the
    // client sets it false when a configuration starts and true when the hello arrives.
    private static volatile boolean serverHasContainers = true;

    /** Whether the server this game is connected to runs Containers. Always true on a server. */
    public static boolean serverHasContainers() {
        return serverHasContainers;
    }

    /** The client records what its server said. Set from Containers' client networking only. */
    public static void setServerHasContainers(boolean has) {
        serverHasContainers = has;
    }

    /**
     * Whether a menu on this side builds created slots: always on the server; on the
     * client only when its server runs Containers (§0069).
     */
    public static boolean buildsCreatedSlots(boolean clientSide) {
        return !clientSide || serverHasContainers;
    }

    // ── The server's side of it ─────────────────────────────────────────

    /** The configuration task: send the hello, then wait for the client's reply. */
    private record HelloTask() implements ConfigurationTask {
        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("menukit-containers:hello");

        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new Hello(PROTOCOL)));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    /** Registers both payload types (both sides) and the server's check. From common init. */
    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Reply.TYPE, Reply.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register(Presence::configure);
        ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> {
            Component refusal = refusal(true, reply.protocol());
            if (refusal != null) {
                context.packetListener().disconnect(refusal);
            } else {
                context.packetListener().completeTask(HelloTask.TYPE);
            }
        });
    }

    /**
     * The server's {@code CONFIGURE} step for one joining client: refuse it if it cannot
     * hear the hello, otherwise queue the hello task. Public so the join probe can run it
     * against a vanilla-shaped connection.
     */
    public static void configure(ServerConfigurationPacketListenerImpl listener, MinecraftServer server) {
        boolean canHear = ServerConfigurationNetworking.canSend(listener, Hello.TYPE);
        // The singleplayer owner is this very game, which runs Containers: never refused,
        // whatever the in-memory channel reports. A LAN guest is checked like anyone.
        boolean owner = server.isSingleplayerOwner(
                new net.minecraft.server.players.NameAndId(listener.getOwner()));
        Component refusal = refusal(canHear || owner, null);
        if (refusal != null) {
            listener.disconnect(refusal);
            return;
        }
        if (canHear) listener.addTask(new HelloTask());
    }
}
