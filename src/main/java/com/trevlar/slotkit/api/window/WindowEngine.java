package com.trevlar.slotkit.api.window;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.window.OwnerRef;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The window's resolution engine: one store, one cascade, one method,
 * {@link #resolve(Address, BehaviorKey)}. It never branches on {@link KindTag}; it
 * walks the declarations for an address and returns a fully resolved value, never
 * null.
 *
 * <h2>One store per tier</h2>
 *
 * Every declaration lives in the store for its key's {@link Tier}. A SERVER key
 * (what a slot accepts, the operations, reactions with authority) is held and
 * resolved in the server tier; a CLIENT key (visibility, opacity, observed
 * reactions) in the client tier. The two tiers are one data structure walked by
 * one algorithm; they differ only in which levels they have. Until 6.0.0 the
 * server tier was a second copy of this class in MenuKit: Containers.
 *
 * <h2>The cascade</h2>
 *
 * <ol>
 *   <li><b>Per address</b>: a declaration on the thing itself.</li>
 *   <li><b>Owner chain</b> (client tier only): a declaration on an ancestor, the
 *       panel a slot or element hangs under, nearest first. "Set opacity once on
 *       the panel" flows to everything inside.</li>
 *   <li><b>Groups</b>: every group the address is a member of, by
 *       {@link GroupKey#precedence()} (a slot group over its category). A tie at
 *       one precedence goes to the group whose id sorts last, never to the one
 *       registered last: registration order is mod load order, which Fabric does
 *       not define.</li>
 *   <li>The key's {@link BehaviorKey#libraryDefault()}.</li>
 * </ol>
 *
 * <h2>Mode scope</h2>
 *
 * Every declaration is made for a {@link ModeScope}. At each level of the cascade the
 * declaration for the player's current mode wins over the level's {@code BOTH} one, so
 * {@code BOTH} plus {@code CREATIVE} reads "this, except that in creative". The mode is
 * read from the player handed to {@link #resolve(Address, BehaviorKey, Collection, Player)}
 * on every call, never kept. With no player only {@code BOTH} declarations apply.
 *
 * A {@link Decl.Set} stops the walk. {@link Decl.Inherit} is not stored: writing
 * it removes the declaration, so clearing a slot leaves nothing behind and the
 * store does not grow per player or per block.
 *
 * <h2>Threading</h2>
 *
 * Declarations and resolution may race (the client thread and the integrated
 * server both use the engine). The maps are concurrent, the group lists are
 * copy-on-write, group creation is serialised, and every {@link Decl} is
 * immutable, so a resolve racing a write sees the old value or the new one.
 */
public final class WindowEngine {

    private WindowEngine() {}

    /** One tier's declarations: per address, and per group. */
    private static final class Store {
        final Map<Address, Map<Scoped, Decl<?>>> perAddress = new ConcurrentHashMap<>();
        final List<GroupBinding> groups = new CopyOnWriteArrayList<>();
    }

    /** A behavior key in one mode scope: what each level stores a declaration under. */
    private record Scoped(BehaviorKey<?> key, ModeScope scope) {}

    private record GroupBinding(GroupKey group, Map<Scoped, Decl<?>> decls) {}

    private static final Map<Tier, Store> STORES = new EnumMap<>(Tier.class);
    static {
        for (Tier t : Tier.values()) STORES.put(t, new Store());
    }

    private static Store store(BehaviorKey<?> key) {
        return STORES.get(key.tier());
    }

    /**
     * Declares {@code key} on one address, the most specific level, for {@code scope}.
     * Writing {@link Decl#inherit()} removes that scope's declaration.
     */
    public static <V> void set(ModeScope scope, Address address, BehaviorKey<V> key, Decl<V> decl) {
        Objects.requireNonNull(scope, "scope");
        requireApplies(key, address.kind());
        Map<Address, Map<Scoped, Decl<?>>> perAddress = store(key).perAddress;
        Scoped scoped = new Scoped(key, scope);
        if (decl instanceof Decl.Inherit<V>) {
            perAddress.computeIfPresent(address, (a, m) -> {
                m.remove(scoped);
                return m.isEmpty() ? null : m;
            });
            return;
        }
        perAddress.computeIfAbsent(address, a -> new ConcurrentHashMap<>()).put(scoped, decl);
    }

    /**
     * {@link #set(ModeScope, Address, BehaviorKey, Decl)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code set(ModeScope.BOTH, address, key, decl)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static <V> void set(Address address, BehaviorKey<V> key, Decl<V> decl) {
        set(ModeScope.BOTH, address, key, decl);
    }

    /** Removes every declaration on {@code address}, in both tiers. */
    public static void clear(Address address) {
        for (Store s : STORES.values()) s.perAddress.remove(address);
    }

    /**
     * Declares {@code key} as a group default for {@code scope}. A group's members
     * vary, so its kind is not checked here; the typed handles check it. Writing
     * {@link Decl#inherit()} removes the group's declaration of that key in that scope.
     */
    public static <V> void setGroup(ModeScope scope, GroupKey group, BehaviorKey<V> key, Decl<V> decl) {
        Objects.requireNonNull(scope, "scope");
        Scoped scoped = new Scoped(key, scope);
        if (decl instanceof Decl.Inherit<V>) {
            for (GroupBinding b : store(key).groups) {
                if (b.group().equals(group)) b.decls().remove(scoped);
            }
            return;
        }
        bindingFor(store(key), group).put(scoped, decl);
    }

    /**
     * {@link #setGroup(ModeScope, GroupKey, BehaviorKey, Decl)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code setGroup(ModeScope.BOTH, group, key, decl)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static <V> void setGroup(GroupKey group, BehaviorKey<V> key, Decl<V> decl) {
        setGroup(ModeScope.BOTH, group, key, decl);
    }

    /**
     * The fully resolved value of {@code key} at {@code address} with no player, so only
     * {@code BOTH} declarations apply; never null.
     */
    public static <V> V resolve(Address address, BehaviorKey<V> key) {
        return resolve(address, key, List.of(), null);
    }

    /**
     * The fully resolved value of {@code key} at {@code address} for {@code player}: its
     * current mode's declarations, then {@code BOTH}'s, at each level. Never null.
     */
    public static <V> V resolve(Address address, BehaviorKey<V> key, @Nullable Player player) {
        return resolve(address, key, List.of(), player);
    }

    /**
     * {@link #resolve(Address, BehaviorKey)} with memberships the caller knows and
     * the address alone does not say. A created slot's groups follow from its
     * address; a vanilla slot's group depends on the menu it sits in, so a seam
     * with the menu in hand ({@link SlotOperations#allows}) states it here.
     */
    public static <V> V resolve(Address address, BehaviorKey<V> key, Collection<GroupKey> alsoMemberOf) {
        return resolve(address, key, alsoMemberOf, null);
    }

    /** {@link #resolve(Address, BehaviorKey, Collection)} for {@code player}'s current mode. */
    public static <V> V resolve(Address address, BehaviorKey<V> key, Collection<GroupKey> alsoMemberOf,
                                @Nullable Player player) {
        ModeScope mode = ModeScope.of(player);   // read now: /gamemode applies on the next check
        Store s = store(key);
        Decl<V> own = declAt(s, address, key, mode);
        if (own instanceof Decl.Set<V> set) return set.value();
        if (key.tier() == Tier.CLIENT) {
            // The owner chain is the client tier's: panels are client things, and a
            // server declaration is only ever made on the slot itself or a group.
            for (Address ancestor = parentAddress(address); ancestor != null; ancestor = parentAddress(ancestor)) {
                Decl<V> a = declAt(s, ancestor, key, mode);
                if (a instanceof Decl.Set<V> set) return set.value();
            }
        }
        Decl<V> group = declForGroups(s, address, key, alsoMemberOf, mode);
        if (group instanceof Decl.Set<V> set) return set.value();
        return key.libraryDefault();
    }

    /**
     * Whether any server-tier declaration exists at all: a seam that has nothing
     * to enforce skips its work, so a slot nobody touched stays exactly vanilla.
     */
    public static boolean hasServerDeclarations() {
        Store s = STORES.get(Tier.SERVER);
        return !s.perAddress.isEmpty() || !s.groups.isEmpty();
    }

    /**
     * The address of {@code a}'s owning ancestor (the panel a created slot or panel
     * element hangs under), or {@code null} once the chain reaches a root owner.
     */
    private static Address parentAddress(Address a) {
        if (a.owner() instanceof OwnerRef.NestedOwner nested) {
            return new Address(nested.parent(), nested.parentToken(), KindTag.PANEL);
        }
        return null;
    }

    private static <V> Decl<V> declAt(Store s, Address address, BehaviorKey<V> key, @Nullable ModeScope mode) {
        Map<Scoped, Decl<?>> m = s.perAddress.get(address);
        return m == null ? null : inMode(m, key, mode);
    }

    /** One level's declaration for the current mode, else its {@code BOTH} one. */
    @SuppressWarnings("unchecked")
    private static <V> @Nullable Decl<V> inMode(Map<Scoped, Decl<?>> m, BehaviorKey<V> key, @Nullable ModeScope mode) {
        if (mode != null) {
            Decl<?> d = m.get(new Scoped(key, mode));
            if (d != null) return (Decl<V>) d;
        }
        return (Decl<V>) m.get(new Scoped(key, ModeScope.BOTH));
    }

    private static <V> Decl<V> declForGroups(Store s, Address address, BehaviorKey<V> key,
                                             Collection<GroupKey> alsoMemberOf, @Nullable ModeScope mode) {
        Decl<V> result = null;
        GroupKey winner = null;
        for (GroupBinding b : s.groups) {
            Decl<V> d = inMode(b.decls(), key, mode);
            if (d == null) continue;
            if (!b.group().contains(address) && !alsoMemberOf.contains(b.group())) continue;
            if (winner == null || outranks(b.group(), winner)) {
                winner = b.group();
                result = d;
            }
        }
        return result;
    }

    /** Higher precedence wins; at one precedence, the id that sorts last. */
    private static boolean outranks(GroupKey a, GroupKey b) {
        if (a.precedence() != b.precedence()) return a.precedence() > b.precedence();
        return a.id().compareTo(b.id()) > 0;
    }

    private static synchronized Map<Scoped, Decl<?>> bindingFor(Store s, GroupKey group) {
        for (GroupBinding b : s.groups) {
            if (b.group().equals(group)) return b.decls();
        }
        Map<Scoped, Decl<?>> m = new ConcurrentHashMap<>();
        s.groups.add(new GroupBinding(group, m));
        return m;
    }

    private static void requireApplies(BehaviorKey<?> key, KindTag kind) {
        if (!key.appliesTo(kind)) {
            throw new IllegalArgumentException(
                    "Behavior " + key.id() + " does not apply to " + kind + " (applies to " + key.appliesTo() + ")");
        }
    }
}
