package com.trevorschoeny.menukit.window;

import java.util.Objects;

/**
 * The base of THE ONE WINDOW's typed handle family — an immutable value addressing
 * one thing by its {@link Address}, never by a held live reference (the menu and
 * every {@code Slot} are recreated each reopen, so a held reference would dangle).
 * Carries only the address; {@code equals}/{@code hashCode} are over it, so two
 * handles naming the same thing are interchangeable and safe as map keys.
 *
 * <h2>Two-layer surface (architecture Part 2 §2)</h2>
 *
 * <ul>
 *   <li><b>Generic substrate</b> (here, every kind): {@link #set}/{@link #behavior}
 *       — the engine is the truth. Any behavior, including ones added later, with
 *       zero new handle code.</li>
 *   <li><b>Named sugar</b> (the typed subclasses): {@code SlotHandle.onInsert(...)},
 *       {@code PanelHandle.opacity(...)} — thin, discoverable wrappers over the
 *       substrate, restricted per kind so a slot verb is unreachable on a panel
 *       handle (compile-checked).</li>
 * </ul>
 *
 * <p>The handle never returns the raw backing (no {@code getSlot()}), per the Law
 * of Demeter — reads go through {@link #behavior}, which resolves the engine
 * cascade and is always non-null. Writes route to {@link WindowEngine#set} (a
 * SERVER-tier key with MKC absent is a safe no-op via the NoServerTier port).
 *
 * <p>Sealed to the three kinds that exist; new kinds extend the family additively
 * (KindTag is FROZEN-OPEN). A handle is a value — held by the consumer, resolved
 * per call (late binding), so one consumer line addresses "this thing" across
 * reopen, across sides, and across the vanilla/created divide.
 */
public abstract sealed class WindowHandle
        permits SlotHandle, ElementHandle, PanelHandle {

    final Address address;

    WindowHandle(Address address) {
        this.address = Objects.requireNonNull(address, "address");
    }

    /** The {@link Address} this handle names — the side-table key for its behavior. */
    public final Address address() {
        return address;
    }

    // ── Generic substrate — the engine is the truth ─────────────────────────

    /** Declare a behavior on this address (per-slot specificity). Returns {@code this} to chain. */
    public final <V> WindowHandle set(BehaviorKey<V> key, Decl<V> decl) {
        WindowEngine.set(address, key, decl);
        return this;
    }

    /** Declare a behavior to an explicit value (sugar for {@code set(key, Decl.set(value))}). */
    public final <V> WindowHandle set(BehaviorKey<V> key, V value) {
        WindowEngine.set(address, key, Decl.set(value));
        return this;
    }

    /** Clear a per-address declaration back to inheriting (sugar for {@code set(key, Decl.inherit())}). */
    public final <V> WindowHandle clear(BehaviorKey<V> key) {
        WindowEngine.set(address, key, Decl.inherit());
        return this;
    }

    /** The fully-resolved value of {@code key} at this address — never null (cascade → libraryDefault). */
    public final <V> V behavior(BehaviorKey<V> key) {
        return WindowEngine.resolve(address, key);
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof WindowHandle h && address.equals(h.address);
    }

    @Override
    public final int hashCode() {
        return address.hashCode();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "(" + address + ")";
    }
}
