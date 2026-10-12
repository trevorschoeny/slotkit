package com.trevlar.slotkit.api.window;

import java.util.Objects;

/**
 * The base of the window's typed handles: an immutable value that addresses one
 * thing by its {@link Address}, never by a live reference. The menu and every
 * {@code Slot} are recreated on each reopen, so a held reference would go stale.
 * {@code equals} and {@code hashCode} are over the address, so two handles naming
 * the same thing are interchangeable and safe as map keys.
 *
 * <h2>Two layers</h2>
 *
 * <ul>
 *   <li><b>Generic</b> (here, for every kind): {@link #set} and {@link #behavior}
 *       reach any behavior, including ones added later, with no new handle code.</li>
 *   <li><b>Named methods</b> (the typed subclasses): {@code SlotHandle.gate(...)},
 *       {@code PanelHandle.opacity(...)}, thin wrappers over the generic layer,
 *       limited per kind so a slot verb on a panel handle does not compile.</li>
 * </ul>
 *
 * <p>The handle never returns the raw backing (no {@code getSlot()}). Reads go
 * through {@link #behavior}, which resolves the cascade and is never null. Writes
 * go to {@link WindowEngine#set}.
 *
 * <p>Sealed to the three kinds that exist; a new kind would add a subclass. A
 * handle is resolved on each call, so one consumer line addresses the same thing
 * across reopens, across sides, and for vanilla and created slots alike.
 */
public abstract sealed class WindowHandle
        permits SlotHandle, ElementHandle, PanelHandle {

    final Address address;

    WindowHandle(Address address) {
        this.address = Objects.requireNonNull(address, "address");
    }

    /** The {@link Address} this handle names, the key its behavior is stored under. */
    public final Address address() {
        return address;
    }

    /** Declares a behavior on this address (per-slot specificity). Returns {@code this}. */
    public final <V> WindowHandle set(BehaviorKey<V> key, Decl<V> decl) {
        WindowEngine.set(address, key, decl);
        return this;
    }

    /** Declares a behavior's value. Shorthand for {@code set(key, Decl.set(value))}. */
    public final <V> WindowHandle set(BehaviorKey<V> key, V value) {
        WindowEngine.set(address, key, Decl.set(value));
        return this;
    }

    /** Clears this address's declaration back to inheriting. Shorthand for {@code set(key, Decl.inherit())}. */
    public final <V> WindowHandle clear(BehaviorKey<V> key) {
        WindowEngine.set(address, key, Decl.inherit());
        return this;
    }

    /** The resolved value of {@code key} at this address. Never null: the cascade ends at the library default. */
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
