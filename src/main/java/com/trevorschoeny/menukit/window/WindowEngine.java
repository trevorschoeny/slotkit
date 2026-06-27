package com.trevorschoeny.menukit.window;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The resolution engine of THE ONE WINDOW — MK ring-0, pure policy, no Minecraft
 * I/O. Every behavior of every addressable thing flows through one method:
 * {@link #resolve(Address, BehaviorKey)}. The engine never branches on
 * {@link KindTag}; it sorts {@link Decl}s by the frozen cascade and returns a
 * fully-resolved value, always non-null.
 *
 * <h2>The frozen two-axis cascade</h2>
 *
 * <ol>
 *   <li><b>AXIS 1 — authority</b> (higher): server-tier authoritative declaration
 *       &gt; client-tier declaration &gt; library default. Compared only where a
 *       key's {@link Tier} makes it meaningful. <em>Plugs in at Phase 3b</em> via
 *       the server-tier port; this client engine holds only client-tier decls.</li>
 *   <li><b>AXIS 2 — specificity</b> (within equal authority): per-slot override
 *       &gt; per-group default &gt; library default.</li>
 *   <li><b>Tie-break</b>: last-declared wins (here: the last registered matching
 *       group).</li>
 * </ol>
 *
 * A {@link Decl.Set} stops the walk with its value; {@link Decl.Inherit} or the
 * absence of a declaration falls through; the walk always terminates at the key's
 * {@link BehaviorKey#libraryDefault()} (the Null-Object bottom). So a slot nobody
 * touched resolves straight to the vanilla-equivalent default.
 *
 * <p>This client store is MK-resident and MK-alone usable. The server tier (the
 * MKC {@code BehaviorBindingTable} feeding AXIS-1) arrives in Phase 3b/3c.
 *
 * <h2>Threading</h2>
 *
 * Declaration ({@code set}/{@code setGroup}) and resolution ({@code resolve}) are
 * expected on the client thread (declare at screen/menu setup, resolve during
 * render/input). The store is nonetheless safe under incidental cross-thread
 * access: the maps are concurrent, {@code GROUPS} is copy-on-write, group
 * creation is serialized in {@link #bindingFor}, and every {@link Decl} is
 * immutable — so a {@code resolve} racing a declaration sees a consistent
 * old-or-new value, never torn state. (The Phase-3b server port supplies AXIS-1
 * declarations through the same immutable-{@code Decl} contract.)
 */
public final class WindowEngine {

    private WindowEngine() {}

    // Per-address (the per-slot / per-element / per-panel specificity level).
    private static final Map<Address, Map<BehaviorKey<?>, Decl<?>>> PER_ADDRESS = new ConcurrentHashMap<>();

    // Per-group, in registration order (the last-declared tie-break reads this order).
    private static final List<GroupBinding> GROUPS = new CopyOnWriteArrayList<>();

    private record GroupBinding(GroupKey group, Map<BehaviorKey<?>, Decl<?>> decls) {}

    // ── declare (client tier) ──────────────────────────────────────────

    /** Declare a client-tier behavior on one address (the most specific level). */
    public static <V> void set(Address address, BehaviorKey<V> key, Decl<V> decl) {
        requireApplies(key, address.kind());
        PER_ADDRESS.computeIfAbsent(address, a -> new ConcurrentHashMap<>()).put(key, decl);
    }

    /** Declare a client-tier behavior as a group default (the bulk level). */
    public static <V> void setGroup(GroupKey group, BehaviorKey<V> key, Decl<V> decl) {
        // A group's members vary, so its kind can't be pre-checked here;
        // applicability is enforced at the typed handle boundary (Phase 6).
        bindingFor(group).put(key, decl);
    }

    // ── resolve (the cascade) ──────────────────────────────────────────

    /** The fully-resolved value of {@code key} at {@code address} — never null. */
    public static <V> V resolve(Address address, BehaviorKey<V> key) {
        // AXIS 1 (server authority) sorts ABOVE this and plugs in at Phase 3b via
        // the ServerTierBridge / AuthoritativeDeclarations port. None present yet,
        // so resolution is the client tier's AXIS 2 only.
        Decl<V> slot = declAt(address, key);
        if (slot instanceof Decl.Set<V> s) return s.value();   // Inherit / absent => fall through
        Decl<V> group = declForGroups(address, key);
        if (group instanceof Decl.Set<V> s) return s.value();
        return key.libraryDefault();
    }

    /** Whether the server tier (MKC) is present. False until Phase 3b wires the port. */
    public static boolean serverTierPresent() {
        return false;
    }

    // ── internals ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <V> Decl<V> declAt(Address address, BehaviorKey<V> key) {
        Map<BehaviorKey<?>, Decl<?>> m = PER_ADDRESS.get(address);
        return m == null ? null : (Decl<V>) m.get(key);
    }

    @SuppressWarnings("unchecked")
    private static <V> Decl<V> declForGroups(Address address, BehaviorKey<V> key) {
        Decl<V> result = null;
        for (GroupBinding b : GROUPS) {                // registration order
            if (!b.group().contains(address)) continue;
            Decl<?> d = b.decls().get(key);
            if (d != null) result = (Decl<V>) d;       // last matching group wins
        }
        return result;
    }

    private static synchronized Map<BehaviorKey<?>, Decl<?>> bindingFor(GroupKey group) {
        for (GroupBinding b : GROUPS) {
            if (b.group().equals(group)) return b.decls();
        }
        Map<BehaviorKey<?>, Decl<?>> m = new ConcurrentHashMap<>();
        GROUPS.add(new GroupBinding(group, m));
        return m;
    }

    private static void requireApplies(BehaviorKey<?> key, KindTag kind) {
        if (!key.appliesTo(kind)) {
            throw new IllegalArgumentException(
                    "Behavior " + key.id() + " does not apply to " + kind + " (applies to " + key.appliesTo() + ")");
        }
    }
}
