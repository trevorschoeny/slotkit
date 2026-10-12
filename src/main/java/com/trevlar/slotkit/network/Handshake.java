package com.trevlar.slotkit.network;

import com.trevlar.slotkit.SlotKit;
import com.trevlar.slotkit.core.ParitySlotRegistry;
import com.trevlar.slotkit.state.SlotStateRegistry;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The negotiation: who may join, and what each side knows about the other.
 *
 * <p>Created slots change a menu's slot count on both sides, so a client that builds
 * different slots from its server is disconnected by a slot-count mismatch, with an
 * error that names nothing. The handshake moves that failure to Fabric's configuration
 * phase, before the world loads, and refuses only where it must: a mod declares whether
 * it needs SlotKit on both sides ({@code required}) or works better with both
 * ({@code preferred}), and the server refuses a player only for a {@code required} mod
 * ({@link ModSides}).
 *
 * <ol>
 *   <li>On {@code CONFIGURE}, a client that cannot receive {@link Hello} (vanilla,
 *       Fabric without SlotKit, MenuKit: Containers 6.x) is judged as having no SlotKit.</li>
 *   <li>Otherwise a configuration task sends the {@link Hello}: the protocol, every
 *       server mod with a {@code slotkit} block and whether it is required, every
 *       slot-state channel, and every created slot panel with its slot count. The
 *       client records it and answers with a {@link Reply}: the protocol, its own
 *       declaring mods and its own panels.</li>
 *   <li>{@link #judge} decides, as one pure function, admit (with the client's record)
 *       or refuse (with the message).</li>
 * </ol>
 *
 * <h3>Ids and versions</h3>
 * The two handshake ids carry no version and the protocol is the first field of each
 * body, so a client on another protocol hears the hello and is told the versions
 * differ. Later fields are only ever appended. Every play payload keeps the protocol
 * in its id ({@code slotkit:v1/<path>}), so a peer on another protocol has no receiver
 * for it.
 *
 * <h3>Known limit, narrowed</h3>
 * The check is by mod presence, not version. Two versions of one {@code required} mod
 * whose created slot panels or slot counts differ are refused with "doesn't match the
 * server's"; two that differ only in a custom menu's slots still meet, and desync when
 * that menu opens.
 */
@ApiStatus.Internal
public final class Handshake {

    private Handshake() {}

    /** The wire protocol. Bump it when any play payload's shape changes. */
    public static final int PROTOCOL = 1;

    /** A play payload's id for {@code path}, in SlotKit's namespace and protocol. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("slotkit", "v" + PROTOCOL + "/" + path);
    }

    static final String SLOTKIT_PAGE = "https://modrinth.com/mod/xQUBXV8I";

    // The two payloads: protocol first, then fields appended, never reordered.

    private static final StreamCodec<ByteBuf, Map<String, Boolean>> MODS =
            ByteBufCodecs.<ByteBuf, String, Boolean, Map<String, Boolean>>map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BOOL);
    private static final StreamCodec<ByteBuf, Map<String, Integer>> PANELS =
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<ByteBuf, List<Identifier>> CHANNELS =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list());

    /**
     * Server to client, in configuration: the server's protocol, its declaring mods
     * ({@code true} for required), its slot-state channels, and its created slot panels
     * with their slot counts. A body on another protocol is read as its protocol alone.
     */
    public record Hello(int protocol, Map<String, Boolean> mods, List<Identifier> channels,
                        Map<String, Integer> panels) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath("slotkit", "hello"));
        public static final StreamCodec<ByteBuf, Hello> STREAM_CODEC = StreamCodec.of(
                (buf, hello) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, hello.protocol());
                    if (hello.protocol() != PROTOCOL) return;
                    MODS.encode(buf, hello.mods());
                    CHANNELS.encode(buf, hello.channels());
                    PANELS.encode(buf, hello.panels());
                },
                buf -> {
                    int protocol = ByteBufCodecs.VAR_INT.decode(buf);
                    if (protocol != PROTOCOL) {
                        buf.skipBytes(buf.readableBytes());
                        return new Hello(protocol, Map.of(), List.of(), Map.of());
                    }
                    return new Hello(protocol, MODS.decode(buf), CHANNELS.decode(buf), PANELS.decode(buf));
                });
        @Override public Type<Hello> type() { return TYPE; }
    }

    /** Client to server, in configuration: its protocol, its declaring mods, its created slot panels. */
    public record Reply(int protocol, Map<String, Boolean> mods, Map<String, Integer> panels)
            implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("slotkit", "hello_reply"));
        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC = StreamCodec.of(
                (buf, reply) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, reply.protocol());
                    if (reply.protocol() != PROTOCOL) return;
                    MODS.encode(buf, reply.mods());
                    PANELS.encode(buf, reply.panels());
                },
                buf -> {
                    int protocol = ByteBufCodecs.VAR_INT.decode(buf);
                    if (protocol != PROTOCOL) {
                        buf.skipBytes(buf.readableBytes());
                        return new Reply(protocol, Map.of(), Map.of());
                    }
                    return new Reply(protocol, MODS.decode(buf), PANELS.decode(buf));
                });
        @Override public Type<Reply> type() { return TYPE; }
    }

    // This side's own view

    /**
     * What a side runs: its declaring mods, its slot-state channels and its created slot
     * panels (each container panel's slot groups, by slot panel id, with their counts).
     * Fixed by the declarations freeze, which runs before anyone joins.
     */
    public record Local(SortedMap<String, ModSides.Declared> mods, Set<Identifier> channels,
                        SortedMap<String, Integer> panels) {

        /** The mods as the wire carries them, {@code true} for required. */
        public Map<String, Boolean> modFlags() {
            Map<String, Boolean> flags = new TreeMap<>();
            mods.forEach((id, d) -> flags.put(id, d.need() == ModSides.Need.REQUIRED));
            return flags;
        }

        public Hello hello() {
            return new Hello(PROTOCOL, modFlags(), List.copyOf(channels), panels);
        }

        public Reply reply() {
            return new Reply(PROTOCOL, modFlags(), panels);
        }
    }

    private static volatile @Nullable Local local;

    /** This game's own view, read once (after the freeze, when it is first asked). */
    public static Local local() {
        Local l = local;
        if (l == null) {
            Set<Identifier> channels = new TreeSet<>();
            SlotStateRegistry.allChannels().forEach(c -> channels.add(c.id()));
            local = l = new Local(ModSides.declared(), Set.copyOf(channels), ParitySlotRegistry.slotCounts());
        }
        return l;
    }

    // The judgement

    /**
     * What a server knows about a joined client. {@code everything}: the singleplayer
     * owner, who runs this very game, so every answer is yes.
     */
    public record ClientRecord(boolean slotKit, boolean everything, Set<String> mods) {
        public static final ClientRecord NONE = new ClientRecord(false, false, Set.of());
        public static final ClientRecord OWNER = new ClientRecord(true, true, Set.of());

        public boolean has(String modId) {
            return everything ? FabricLoader.getInstance().isModLoaded(modId) : mods.contains(modId);
        }
    }

    /** The judgement: let the client in with what it has, or refuse it with a message. */
    public sealed interface Verdict {
        record Admit(ClientRecord record) implements Verdict {}
        record Refuse(Component message) implements Verdict {}
    }

    /**
     * The server's judgement of one joining client, as a pure function.
     *
     * @param server the server's own view
     * @param reply  the client's reply, or {@code null} when it cannot hear the hello
     */
    public static Verdict judge(Local server, @Nullable Reply reply) {
        List<ModSides.Declared> required = server.mods().values().stream()
                .filter(d -> d.need() == ModSides.Need.REQUIRED).toList();
        if (reply == null) {
            return required.isEmpty() ? new Verdict.Admit(ClientRecord.NONE)
                    : new Verdict.Refuse(missingMessage(required));
        }
        if (reply.protocol() != PROTOCOL) {
            return required.isEmpty() ? new Verdict.Admit(ClientRecord.NONE)
                    : new Verdict.Refuse(versionMessage(PROTOCOL, reply.protocol(), required));
        }
        List<ModSides.Declared> missing = required.stream().filter(d -> !reply.mods().containsKey(d.id())).toList();
        if (!missing.isEmpty()) return new Verdict.Refuse(missingModsMessage(missing));
        for (ModSides.Declared d : required) {
            if (!panelsOf(server.panels(), d.id()).equals(panelsOf(reply.panels(), d.id()))) {
                return new Verdict.Refuse(mismatchMessage(d));
            }
        }
        return new Verdict.Admit(new ClientRecord(true, false, Set.copyOf(reply.mods().keySet())));
    }

    /** One mod's created slot panels and their counts, out of a side's whole list. */
    private static SortedMap<String, Integer> panelsOf(Map<String, Integer> panels, String modId) {
        SortedMap<String, Integer> mine = new TreeMap<>();
        panels.forEach((id, count) -> { if (modId.equals(ModSides.namespaceOf(id))) mine.put(id, count); });
        return mine;
    }

    // What the player reads

    /** A client without SlotKit (or that cannot hear it): names SlotKit and every required mod. */
    public static Component missingMessage(List<ModSides.Declared> required) {
        return Component.literal("This server needs SlotKit and " + names(required) + " on your game too.\n\n"
                + "Install them, the versions the server uses:\n" + SLOTKIT_PAGE + links(required));
    }

    /** A client on another SlotKit protocol. */
    public static Component versionMessage(int serverProtocol, int clientProtocol, List<ModSides.Declared> required) {
        return Component.literal("This server runs a different version of SlotKit "
                + "(protocol " + serverProtocol + ", yours " + clientProtocol + ").\n\n"
                + "Install the SlotKit version the server uses, with " + names(required) + ":\n"
                + SLOTKIT_PAGE + links(required));
    }

    /** A client with SlotKit, missing some required mods: names those. */
    public static Component missingModsMessage(List<ModSides.Declared> missing) {
        return Component.literal("This server needs " + names(missing) + " on your game too.\n\n"
                + "Install " + (missing.size() == 1 ? "it" : "them") + ", the version the server uses:" + links(missing));
    }

    /** A client whose required mod builds different slots from the server's copy. */
    public static Component mismatchMessage(ModSides.Declared mod) {
        return Component.literal("Your " + mod.name() + " doesn't match the server's: its slots differ.\n\n"
                + "Install the version the server uses:" + links(List.of(mod)));
    }

    private static String names(List<ModSides.Declared> mods) {
        List<String> names = mods.stream().map(ModSides.Declared::name).toList();
        if (names.size() <= 1) return String.join("", names);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    private static String links(List<ModSides.Declared> mods) {
        StringBuilder out = new StringBuilder();
        for (ModSides.Declared d : mods) if (d.homepage() != null) out.append('\n').append(d.homepage());
        return out.toString();
    }

    // The server's side

    // Each joined client's record, by player UUID: set in configuration, gone at disconnect.
    private static final Map<UUID, ClientRecord> CLIENTS = new ConcurrentHashMap<>();

    /** What {@code player}'s client said when it joined; {@link ClientRecord#NONE} when it said nothing. */
    public static ClientRecord client(ServerPlayer player) {
        return CLIENTS.getOrDefault(player.getUUID(), ClientRecord.NONE);
    }

    /**
     * Sends a SlotKit play payload to a player whose client has SlotKit.
     * Everything SlotKit's server sends goes through here, so a vanilla player is never
     * sent anything it has no receiver for.
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (client(player).slotKit()) ServerPlayNetworking.send(player, payload);
    }

    /**
     * Whether a SlotKit play payload from {@code player} is heard: only
     * from a client whose handshake recorded SlotKit. A modified client that skips the
     * reply gains nothing.
     */
    public static boolean hears(ServerPlayer player) {
        return client(player).slotKit();
    }

    /** The configuration task: send the hello, then wait for the reply. */
    private record HelloTask() implements ConfigurationTask {
        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("slotkit:hello");

        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(local().hello()));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    /** Registers both payload types (both sides) and the server's judgement. From common init. */
    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Reply.TYPE, Reply.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register(Handshake::configure);
        ServerConfigurationNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> {
            ServerConfigurationPacketListenerImpl listener = context.packetListener();
            UUID id = listener.getOwner().id();
            if (isOwner(context.server(), listener)) {
                CLIENTS.put(id, ClientRecord.OWNER);
                listener.completeTask(HelloTask.TYPE);
                return;
            }
            switch (judge(local(), reply)) {
                case Verdict.Refuse refuse -> listener.disconnect(refuse.message());
                case Verdict.Admit admit -> {
                    CLIENTS.put(id, admit.record());
                    listener.completeTask(HelloTask.TYPE);
                }
            }
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> CLIENTS.remove(listener.getOwner().id()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CLIENTS.remove(handler.getPlayer().getUUID()));
    }

    /**
     * The server's {@code CONFIGURE} step for one joining client: judge a client that
     * cannot hear the hello at once, otherwise queue the hello. Public so the join probe
     * can run it against a vanilla-shaped connection.
     */
    public static void configure(ServerConfigurationPacketListenerImpl listener, MinecraftServer server) {
        UUID id = listener.getOwner().id();
        boolean canHear = ServerConfigurationNetworking.canSend(listener, Hello.TYPE);
        // The singleplayer owner is this very game: never judged, whatever the
        // in-memory channel reports. A LAN guest is judged like anyone.
        if (isOwner(server, listener)) {
            CLIENTS.put(id, ClientRecord.OWNER);
            if (canHear) listener.addTask(new HelloTask());
            return;
        }
        if (canHear) {
            listener.addTask(new HelloTask());
            return;
        }
        switch (judge(local(), null)) {
            case Verdict.Refuse refuse -> listener.disconnect(refuse.message());
            case Verdict.Admit admit -> CLIENTS.put(id, admit.record());
        }
    }

    private static boolean isOwner(MinecraftServer server, ServerConfigurationPacketListenerImpl listener) {
        return server.isSingleplayerOwner(new net.minecraft.server.players.NameAndId(listener.getOwner()));
    }

    // The client's side (set by HandshakeClient; read through Presence)

    /**
     * What this game's server said when it joined. {@code everything}: singleplayer, where
     * the server is this game, so every answer is yes.
     */
    public record ServerRecord(boolean slotKit, boolean everything, Map<String, Boolean> mods,
                               Set<Identifier> channels) {
        public static final ServerRecord NONE = new ServerRecord(false, false, Map.of(), Set.of());
        public static final ServerRecord SINGLEPLAYER = new ServerRecord(true, true, Map.of(), Set.of());

        public static ServerRecord of(Hello hello) {
            if (hello.protocol() != PROTOCOL) return NONE; // admitted without SlotKit, at most
            return new ServerRecord(true, false, Map.copyOf(hello.mods()), Set.copyOf(hello.channels()));
        }

        public boolean has(String modId) {
            return everything ? FabricLoader.getInstance().isModLoaded(modId) : mods.containsKey(modId);
        }

        public boolean hasChannel(Identifier channel) {
            return everything ? SlotStateRegistry.getChannel(channel) != null : channels.contains(channel);
        }
    }

    // Null while not connected. Only a client ever sets it.
    private static volatile @Nullable ServerRecord server;

    /** What the server said, or {@code null} when this game is not connected. */
    public static @Nullable ServerRecord server() {
        return server;
    }

    /** Set from SlotKit's client networking only. */
    public static void setServer(@Nullable ServerRecord record) {
        server = record;
        if (record != null && !record.everything()) {
            SlotKit.LOGGER.info("[SlotKit] server {}", record.slotKit()
                    ? "runs SlotKit with " + new ArrayList<>(record.mods().keySet()) : "does not run SlotKit");
        }
    }

    /**
     * Whether a menu on this side builds the created slots of panel {@code slotPanelId}.
     * A server always does. A client does when its server runs
     * SlotKit and, for a panel whose namespace is a mod with a {@code slotkit} block,
     * that mod too, so a client with a required mod its server lacks builds none of its
     * slots and both sides keep the same slot count.
     */
    public static boolean buildsCreatedSlots(boolean clientSide, String slotPanelId) {
        return !clientSide || clientBuilds(server, ModSides::of, slotPanelId);
    }

    /**
     * The client's half of {@link #buildsCreatedSlots}, pure.
     *
     * @param server what the server said ({@code null}: not connected)
     * @param sides  what a local mod declares, {@code null} for no block
     */
    public static boolean clientBuilds(@Nullable ServerRecord server,
                                       java.util.function.Function<String, ModSides.@Nullable Need> sides,
                                       String slotPanelId) {
        if (server == null || !server.slotKit()) return false;
        String owner = ModSides.namespaceOf(slotPanelId);
        return owner == null || sides.apply(owner) == null || server.has(owner);
    }
}
