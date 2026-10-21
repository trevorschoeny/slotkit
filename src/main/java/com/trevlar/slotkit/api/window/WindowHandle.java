package com.trevlar.slotkit.api.window;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import com.trevlar.menukit.api.panel.ModeScope;
import java.util.Objects;

/**
 * The base of the window's typed handles: an immutable value that addresses one
 * thing by its {@link Address}, never by a live reference. The menu and every
 * {@code Slot} are recreated on each reopen, so a held reference would go stale.
 * {@code equals} and {@code hashCode} are over the address and the mode scope, so two
 * handles naming the same thing in the same scope are interchangeable and safe as map keys.
 *
 * <h2>Two layers</h2>
 *
 * <ul>
 *   <li><b>Generic</b> (here, for every kind): {@link #set} and {@link #behavior}
 *       reach any behavior, including ones added later, with no new handle code.</li>
 *   <li><b>Named methods</b> (the typed subclass): {@code SlotHandle.gate(...)},
 *       thin wrappers over the generic layer.</li>
 * </ul>
 *
 * <p>The handle never returns the raw backing (no {@code getSlot()}). Reads go
 * through {@link #behavior}, which resolves the cascade and is never null. Writes
 * go to {@link WindowEngine#set}.
 *
 * <p>Sealed to the one kind it serves, slots; a new kind would add a subclass. A
 * handle is resolved on each call, so one consumer line addresses the same thing
 * across reopens, across sides, and for vanilla and created slots alike.
 */
public abstract sealed class WindowHandle
        permits SlotHandle {

    final Address address;
    /** The mode scope this handle's declarations are made for. */
    final ModeScope scope;

    WindowHandle(ModeScope scope, Address address) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.address = Objects.requireNonNull(address, "address");
    }

    /** The mode scope this handle's {@link #set} and {@link #clear} declare for. */
    public final ModeScope scope() {
        return scope;
    }

    /** The {@link Address} this handle names, the key its behavior is stored under. */
    public final Address address() {
        return address;
    }

    /** Declares a behavior on this address (per-slot specificity), in this handle's scope. Returns {@code this}. */
    public final <V> WindowHandle set(BehaviorKey<V> key, Decl<V> decl) {
        WindowEngine.set(scope, address, key, decl);
        return this;
    }

    /** Declares a behavior's value. Shorthand for {@code set(key, Decl.set(value))}. */
    public final <V> WindowHandle set(BehaviorKey<V> key, V value) {
        WindowEngine.set(scope, address, key, Decl.set(value));
        return this;
    }

    /** Clears this scope's declaration back to inheriting. Shorthand for {@code set(key, Decl.inherit())}. */
    public final <V> WindowHandle clear(BehaviorKey<V> key) {
        WindowEngine.set(scope, address, key, Decl.inherit());
        return this;
    }

    /**
     * The resolved value of {@code key} at this address with no player, so only
     * {@code BOTH} declarations count. Never null: the cascade ends at the library default.
     */
    public final <V> V behavior(BehaviorKey<V> key) {
        return WindowEngine.resolve(address, key);
    }

    /** The resolved value of {@code key} at this address for {@code player}'s current mode. */
    public final <V> V behavior(BehaviorKey<V> key, @Nullable Player player) {
        return WindowEngine.resolve(address, key, player);
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof WindowHandle h && address.equals(h.address) && scope == h.scope;
    }

    @Override
    public final int hashCode() {
        return 31 * address.hashCode() + scope.hashCode();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "(" + scope + ", " + address + ")";
    }
}
