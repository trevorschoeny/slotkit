package com.trevlar.slotkit.api.window;

/**
 * What kind of addressable thing an {@link Address} names. This is the one place
 * the window tells a slot from an element from a panel.
 *
 * <h2>Role in the engine</h2>
 *
 * The resolver never branches on {@code KindTag}. The tag takes part only in
 * {@link Address} equality (so a panel and a slot at the same coordinate never
 * collide) and in which kinds a {@code BehaviorKey} applies to, checked at the
 * call boundary, never inside resolution. Resolution stays kind-blind while keys
 * stay kind-typed.
 *
 * <h2>Open to new kinds</h2>
 *
 * The engine always has a default branch for this enum, so adding a constant later
 * is source-compatible. {@code Decl}, by contrast, is closed: it gets no new arms.
 */
public enum KindTag {
    /** A vanilla {@code net.minecraft.world.inventory.Slot} the consumer did not create. */
    VANILLA_SLOT,
    /** A slot created through SlotKit (a real synced slot in menu space). */
    CREATED_SLOT,
    /** A non-slot panel element (button, label, decoration). */
    PANEL_ELEMENT,
    /** A panel itself, for its own properties (visibility, opacity). */
    PANEL
}
