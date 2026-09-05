package com.trevorschoeny.menukit.window;

/**
 * What KIND of addressable thing an {@link Address} names — the one place THE
 * ONE WINDOW distinguishes a slot from an element from a panel.
 *
 * <h2>Role in the engine (kind-agnostic resolution)</h2>
 *
 * The resolver NEVER branches on {@code KindTag} to choose a code path. The tag
 * participates only in {@link Address} equality/hashCode (so a panel and a slot
 * at the same coordinate can never collide) and in a key's <em>applicability</em>
 * (a {@code BehaviorKey} declares which kinds it applies to, checked at the call
 * boundary, never inside resolution). This keeps resolution kind-blind while
 * keys stay kind-typed.
 *
 * <h2>FROZEN-OPEN</h2>
 *
 * New kinds are additive: the engine treats this enum non-exhaustively (always a
 * default branch), so adding a constant later is source-compatible. Contrast the
 * {@code Decl} declaration type, which is FROZEN-CLOSED (no new arms, ever).
 */
public enum KindTag {
    /** A vanilla {@code net.minecraft.world.inventory.Slot} the consumer did not create. */
    VANILLA_SLOT,
    /** A slot brought into being through MKC creation (a real synced slot in menu space). */
    CREATED_SLOT,
    /** A non-slot panel element (button, label, decoration). */
    PANEL_ELEMENT,
    /** A panel itself, for its own properties (parity, opacity, inertness). */
    PANEL
}
