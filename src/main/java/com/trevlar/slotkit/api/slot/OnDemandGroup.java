package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.core.ParitySlotRegistry;
import com.trevlar.slotkit.network.GroupInstanceS2CPayload;
import com.trevlar.slotkit.network.Handshake;
import com.trevlar.slotkit.network.ModSides;
import com.trevlar.slotkit.window.OwnerRef;
import com.trevlar.slotkit.window.PanelAddressing;
import com.trevlar.slotkit.window.Token;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A slot group a menu gets on demand: declared once at init, like a {@link ParityGroup},
 * but on no menu until the server adds an instance of it to the menu the player has
 * open, and gone again when the server removes it or the menu closes. For things a
 * player opens and closes while a screen is up (a shulker box opened from a chest's
 * screen, a bundle), as many at once as they like, with nothing on a menu that has
 * none open.
 *
 * <pre>{@code
 * // Common init (both sides): the kind, and the behavior every instance shares.
 * public static final OnDemandGroup BOX = OnDemandGroup.register("mymod:box",
 *         SlotSpec.at("slots", MY_CATEGORY).count(27).shiftClickIn(true));
 * for (int i = 0; i < 27; i++) {
 *     Window.slot(ModeScope.BOTH, Address.createdSlot(BOX.id(), i)).gate(NO_BOXES_IN_BOXES);
 * }
 *
 * // Server, when the player opens a box: its slots join the open menu, the client's too.
 * OnDemandGroup.Instance open = BOX.add(player, boxStorage);
 * // ... and when it closes:
 * open.remove();
 * }</pre>
 *
 * <h3>Instances</h3>
 * Each instance is a slot group of its own on the menu, with its own id,
 * {@link Instance#id()} ({@code mymod:box:slots#0}, {@code #1}, ...): what
 * {@link CreatedSlot#panelId()} reads on its slots, what {@code SlotElement.slot(id, i)}
 * and {@code SlotGroupElement} show, and what {@code QuickMoveRoutes.Dest.group} names.
 * An instance takes the lowest number no live instance of its kind holds on that menu,
 * so numbers are reused and a screen can keep a fixed set of places to show them in.
 *
 * <h3>Behavior is the kind's</h3>
 * Declarations on the kind reach every instance: one on
 * {@code Address.createdSlot(kind.id(), i)} applies to slot {@code i} of each instance
 * (unless that instance's own address declares otherwise), and the spec's group-level
 * behavior ({@code shiftClickIn}, {@code collect}, the gate) and its category hold for
 * all of them. A gate that needs to know which instance it judges reads
 * {@link #instanceOf(Slot)} of {@code GatingContext.slot()}; a quick-move route reads it
 * of its source slot. The kind is listed in {@code SlotGroups}; its instances are not.
 *
 * <h3>Sync</h3>
 * The server adds and removes; the client follows, told before any of the slots'
 * contents arrive, so both menus hold the same slots at the same indices. The server
 * adds only for a client that runs SlotKit and the kind's mod ({@link #add} returns
 * {@code null} otherwise). A removed instance's slots stay on the menu, inert (empty to
 * everything, refusing every click), until a new instance of the same kind takes their
 * place, so no other slot's index ever moves while the menu is open. The player's own
 * inventory menu takes no instances: it outlives every screen, and creative's tab copies
 * its slots when the tab opens.
 *
 * <p>Since 2.1.0.
 */
public final class OnDemandGroup {

    // Every kind, by its slot panel id ("owner:groupId").
    private static final Map<String, OnDemandGroup> KINDS = new ConcurrentHashMap<>();

    // An instance panel's registration key to its kind's, for the engine's per-address
    // fallback. Bounded: numbers are reused, so it holds a few per kind.
    private static final Map<Identifier, Identifier> KIND_REG_KEYS = new ConcurrentHashMap<>();

    // Each menu's instances in the order they took their slots, removed ones included
    // (their slots stay on the menu until a new instance reuses them). Server and client
    // menus are separate objects, so each side keeps its own. Weak keys, and an Instance
    // holds its menu weakly, so a closed menu is collected.
    private static final Map<AbstractContainerMenu, List<Instance>> BY_MENU =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final SlotGroupId.Created id;
    private final SlotSpec spec;

    private OnDemandGroup(SlotGroupId.Created id, SlotSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    /**
     * Registers a kind of on-demand group. Call once at mod init, from the common
     * initializer. The spec gives the slots and their behavior; it declares no storage,
     * since each instance brings its own to {@link #add}.
     *
     * @throws IllegalStateException after MenuKit's declarations froze, when the spec
     *         declares storage, when the owner's mod doesn't declare SlotKit on both sides,
     *         or when the same group is registered twice (as either kind of group)
     */
    public static OnDemandGroup register(String owner, SlotSpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        ModSides.requireBuildsSlots("OnDemandGroup", owner);
        com.trevlar.menukit.api.Declarations.requireOpen("OnDemandGroup " + owner + " register()");
        if (spec.storageFactory() != null) {
            throw new IllegalStateException("OnDemandGroup '" + owner + "': each instance brings its own storage "
                    + "to add(player, storage); leave .storage(...) off the spec.");
        }
        SlotGroupId.Created id = ParityGroup.idOf(owner, spec.groupId());
        synchronized (KINDS) {
            if (KINDS.containsKey(id.panelId()) || ParitySlotRegistry.isParity(id.panelId())) {
                throw new IllegalStateException("A slot group " + id.panelId() + " is already registered");
            }
            ParityGroup.armInlineBehavior(id, spec);
            OnDemandGroup kind = new OnDemandGroup(id, spec);
            KINDS.put(id.panelId(), kind);
            return kind;
        }
    }

    /** The kind's id: what declarations every instance shares are made on. */
    public SlotGroupId.Created id() { return id; }

    /** The spec it was registered with. */
    public SlotSpec spec() { return spec; }

    /** How many slots each instance has. */
    public int count() { return spec.count(); }

    /** The id instance number {@code number} of this kind has on any menu. */
    public SlotGroupId.Created instanceId(int number) {
        SlotGroupId.Created inst = SlotGroupId.created(id.panelId() + "#" + number, id.groupId());
        KIND_REG_KEYS.putIfAbsent(PanelAddressing.regKey(inst.panelId()), PanelAddressing.regKey(id.panelId()));
        return inst;
    }

    /**
     * Adds an instance to the menu {@code player} has open, its slots over
     * {@code storage}. Server only, on the server thread, and not from inside a click.
     * The client hears at once; the slots' contents follow with the menu's next sync.
     *
     * @return the instance, or {@code null} when the player's client can't follow
     *         (no SlotKit, or not this kind's mod)
     * @throws IllegalStateException    when the player has only their own inventory open
     * @throws IllegalArgumentException when {@code storage} isn't {@link #count()} slots
     */
    public @Nullable Instance add(ServerPlayer player, Storage storage) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(storage, "storage");
        if (storage.size() != count()) {
            throw new IllegalArgumentException("OnDemandGroup " + id.panelId() + ": storage has " + storage.size()
                    + " slots, the kind " + count());
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) {
            throw new IllegalStateException("OnDemandGroup " + id.panelId()
                    + ": the player's own inventory menu takes no instances; open a screen first");
        }
        Handshake.ClientRecord client = Handshake.client(player);
        String mod = ModSides.namespaceOf(id.panelId());
        if (!client.slotKit() || (mod != null && !client.has(mod))) return null;

        // The lowest free number, and the removed instance of it whose slots to reuse.
        List<Instance> all = listOn(menu);
        int number = 0;
        while (find(all, number, false) != null) number++;
        Instance old = find(all, number, true);
        int at = old != null ? old.flatStart : menu.slots.size();
        int start = spec.activeCountStart() != null ? spec.activeCountStart() : count();

        // Told first, so the client has the slots before the next sync sends their contents.
        Handshake.send(player, new GroupInstanceS2CPayload(menu.containerId, GroupInstanceS2CPayload.ADD,
                id.panelId(), number, at, start));
        return build(menu, player, player, number, storage, old, at, start);
    }

    /** The live instances of this kind on {@code menu}, in the order they were added, on either side. */
    public List<Instance> instances(AbstractContainerMenu menu) {
        List<Instance> out = new ArrayList<>();
        synchronized (BY_MENU) {
            for (Instance i : BY_MENU.getOrDefault(menu, List.of())) if (i.kind == this && !i.removed) out.add(i);
        }
        return out;
    }

    /** The on-demand instance {@code slot} belongs to (seeing through creative's wrapper), or {@code null}. */
    public static @Nullable Instance instanceOf(@Nullable Slot slot) {
        CreatedSlot created = slot == null ? null : CreatedSlot.of(slot);
        return created == null ? null : created.getGroup().onDemandInstance();
    }

    /** One instance of a kind, on one menu. */
    public static final class Instance {
        private final OnDemandGroup kind;
        private final int number;
        private final SlotGroupId.Created id;
        private final WeakReference<AbstractContainerMenu> menu;
        private final int containerId;
        private final @Nullable ServerPlayer player;   // null on the client
        private final int flatStart;
        private final List<CreatedSlot> slots;
        private volatile int activeCount;
        private volatile boolean removed;

        private Instance(OnDemandGroup kind, int number, AbstractContainerMenu menu, @Nullable ServerPlayer player,
                         int flatStart, List<CreatedSlot> slots, int activeCount) {
            this.kind = kind;
            this.number = number;
            this.id = kind.instanceId(number);
            this.menu = new WeakReference<>(menu);
            this.containerId = menu.containerId;
            this.player = player;
            this.flatStart = flatStart;
            this.slots = slots;
            this.activeCount = activeCount;
        }

        /** Its kind. */
        public OnDemandGroup kind() { return kind; }

        /** Its number among its kind's instances on the menu, from 0. */
        public int number() { return number; }

        /** Its own group id: on its slots ({@code CreatedSlot.panelId()}), in addresses and routes. */
        public SlotGroupId.Created id() { return id; }

        /** Its slots, in order. */
        public List<CreatedSlot> slots() { return slots; }

        /** Its slot {@code index}'s own address, for a declaration on this instance alone. */
        public Address address(int index) { return Address.createdSlot(id, index); }

        /** Whether it was removed, or its menu closed. */
        public boolean isRemoved() {
            AbstractContainerMenu m = menu.get();
            return removed || m == null || (player != null && player.containerMenu != m);
        }

        /** How many of its slots are live: slots {@code 0..count-1}; the rest are inert. */
        public int activeCount() { return removed ? 0 : activeCount; }

        /**
         * Sets how many of its slots are live, as {@code ParityGroup.setActiveCount} does
         * for a whole group. Server only. The client hears before the next sync, which
         * sends the slots past the count empty.
         *
         * @throws IllegalArgumentException when {@code count} is outside {@code 0..count()}
         */
        public void setActiveCount(int count) {
            if (count < 0 || count > kind.count()) {
                throw new IllegalArgumentException("OnDemandGroup " + id.panelId() + ": active count " + count
                        + " is outside 0.." + kind.count());
            }
            if (removed || count == activeCount) return;
            activeCount = count;
            tell(GroupInstanceS2CPayload.COUNT, count);
        }

        /**
         * Removes it: its slots go inert on both sides at once, and their contents leave
         * the client with the next sync. Server only. Removing twice, or after the menu
         * closed, does nothing more.
         */
        public void remove() {
            if (removed) return;
            removed = true;
            tell(GroupInstanceS2CPayload.REMOVE, 0);
        }

        private void tell(int action, int value) {
            if (player == null) throw new IllegalStateException("OnDemandGroup instances change on the server");
            AbstractContainerMenu m = menu.get();
            if (m == null || player.containerMenu != m) return;   // the menu closed: nothing left to tell
            Handshake.send(player, new GroupInstanceS2CPayload(containerId, action, kind.id.panelId(), number, flatStart, value));
        }
    }

    // Building, both sides

    private Instance build(AbstractContainerMenu menu, Player player, @Nullable ServerPlayer server, int number,
                           Storage storage, @Nullable Instance old, int at, int activeCount) {
        SlotGroupId.Created inst = instanceId(number);
        Instance[] self = new Instance[1];
        CreatedSlots.Builder b = CreatedSlots.onto(menu, player)
                .panel(inst.panelId())
                .group(inst.groupId())
                .category(spec.category())
                .storage(storage)
                .activeCount(() -> self[0] == null ? 0 : self[0].activeCount());
        if (old != null) b.replacing(at);
        if (spec.label() != null) b.label(spec.label());
        if (spec.revealWhen() != null) b.revealWhen(spec.revealWhen());
        CreatedSlots.RegisteredSlots built = b.register();
        Instance instance = new Instance(this, number, menu, server, at, built.slots(), activeCount);
        self[0] = instance;
        built.group().onDemandInstance(instance);
        synchronized (BY_MENU) {
            List<Instance> all = BY_MENU.computeIfAbsent(menu, m -> new ArrayList<>());
            if (old != null) all.set(all.indexOf(old), instance); else all.add(instance);
        }
        return instance;
    }

    private static List<Instance> listOn(AbstractContainerMenu menu) {
        synchronized (BY_MENU) {
            return new ArrayList<>(BY_MENU.getOrDefault(menu, List.of()));
        }
    }

    private @Nullable Instance find(List<Instance> all, int number, boolean removed) {
        for (Instance i : all) if (i.kind == this && i.number == number && i.removed == removed) return i;
        return null;
    }

    // Library use

    /** Client: applies what the server sent, to the player's open menu. */
    @ApiStatus.Internal
    public static void applyOnClient(GroupInstanceS2CPayload p, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        OnDemandGroup kind = KINDS.get(p.kind());
        if (menu == null || menu.containerId != p.containerId() || kind == null) return;
        List<Instance> all = listOn(menu);
        switch (p.action()) {
            case GroupInstanceS2CPayload.ADD -> {
                Instance old = kind.find(all, p.number(), true);
                int at = old != null ? old.flatStart : menu.slots.size();
                if (at != p.at() || kind.find(all, p.number(), false) != null) {
                    // The two menus already differ; building here would only shift them further.
                    org.slf4j.LoggerFactory.getLogger("slotkit").warn("OnDemandGroup {}#{}: the server put it at slot {}, "
                            + "this menu at {}; not built", p.kind(), p.number(), p.at(), at);
                    return;
                }
                kind.build(menu, player, null, p.number(), EphemeralStorage.of(kind.count()), old, at, p.count());
            }
            case GroupInstanceS2CPayload.COUNT -> {
                Instance i = kind.find(all, p.number(), false);
                if (i != null) i.activeCount = p.count();
            }
            case GroupInstanceS2CPayload.REMOVE -> {
                Instance i = kind.find(all, p.number(), false);
                if (i != null) i.removed = true;
            }
            default -> { }
        }
    }

    /**
     * Server: tells the client about every instance on {@code menu} again, removed ones
     * too, in slot order, so a new client menu for it (one {@code MenuSwitch.back}
     * reopened) builds the same slots at the same indices before its contents arrive.
     */
    @ApiStatus.Internal
    public static void replay(ServerPlayer player, AbstractContainerMenu menu) {
        List<Instance> all = listOn(menu);
        all.sort(java.util.Comparator.comparingInt(i -> i.flatStart));
        for (Instance i : all) {
            String kind = i.kind.id.panelId();
            Handshake.send(player, new GroupInstanceS2CPayload(menu.containerId, GroupInstanceS2CPayload.ADD,
                    kind, i.number, i.flatStart, i.activeCount));
            if (i.removed) {
                Handshake.send(player, new GroupInstanceS2CPayload(menu.containerId, GroupInstanceS2CPayload.REMOVE,
                        kind, i.number, i.flatStart, 0));
            }
        }
    }

    /** The kind a created group is an instance of, or {@code null} when it's no instance. */
    @ApiStatus.Internal
    public static SlotGroupId.@Nullable Created kindOf(SlotGroupId.@Nullable Created group) {
        if (group == null || KINDS.isEmpty()) return null;
        int hash = group.panelId().lastIndexOf('#');
        if (hash < 0) return null;
        OnDemandGroup kind = KINDS.get(group.panelId().substring(0, hash));
        return kind != null && kind.id.groupId().equals(group.groupId()) ? kind.id : null;
    }

    /** {@code group}'s kind when it's an instance, else {@code group}. */
    @ApiStatus.Internal
    public static SlotGroupId kindOrSelf(SlotGroupId group) {
        SlotGroupId.Created kind = group instanceof SlotGroupId.Created c ? kindOf(c) : null;
        return kind != null ? kind : group;
    }

    /** Whether {@code slotPanelId} is an instance's. */
    @ApiStatus.Internal
    public static boolean isInstance(String slotPanelId) {
        int hash = slotPanelId.lastIndexOf('#');
        return hash >= 0 && KINDS.containsKey(slotPanelId.substring(0, hash));
    }

    /**
     * The same slot of the kind, for a created slot's address in an instance, or
     * {@code null}: where the engine looks when the instance's own address declares nothing.
     */
    @ApiStatus.Internal
    public static @Nullable Address kindAddress(Address address) {
        if (KIND_REG_KEYS.isEmpty() || !(address.owner() instanceof OwnerRef.NestedOwner owner)
                || !(owner.parentToken() instanceof Token.RegToken reg)) return null;
        Identifier kind = KIND_REG_KEYS.get(reg.regKey());
        return kind == null ? null : new Address(OwnerRef.nested(owner.parent(), Token.reg(kind)), address.token(), address.kind());
    }

    /** Every kind's slot count by its slot panel id, for the join handshake. */
    @ApiStatus.Internal
    public static Map<String, Integer> slotCounts() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        for (OnDemandGroup k : KINDS.values()) out.put(k.id.panelId(), k.count());
        return out;
    }
}
