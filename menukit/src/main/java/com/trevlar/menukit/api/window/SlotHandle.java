package com.trevlar.menukit.api.window;

/**
 * A typed handle on a slot, vanilla or created: the slot verbs, plus the generic
 * {@link WindowHandle#set}. The slot author's server rules are {@link #gate} and
 * {@link #binding}; operations are set by key
 * ({@code set(BehaviorKeys.SHIFT_CLICK_IN, TriBool.FALSE)}). The client-observed
 * reactions ({@link ReactiveHook}) and visibility have shorthand methods here too.
 */
public final class SlotHandle extends WindowHandle {

    /** What this slot accepts and releases, and how many: the author's rule ({@link BehaviorKeys#GATING}). */
    public SlotHandle gate(SlotGate gate) {
        set(BehaviorKeys.GATING, gate);
        return this;
    }

    /** Whether Curse of Binding holds a bound item in this slot ({@link BehaviorKeys#BINDING}). */
    public SlotHandle binding(boolean enforced) {
        set(BehaviorKeys.BINDING, TriBool.of(enforced));
        return this;
    }

    SlotHandle(Address address) {
        super(address);
    }

    /** Drives this slot's client visibility by a {@link VisibilityRule}. A created slot's contents keep syncing either way. */
    public SlotHandle visibility(VisibilityRule rule) {
        set(BehaviorKeys.VISIBILITY, rule);
        return this;
    }

    /** Shows or hides this slot on the client (a constant rule). */
    public SlotHandle visibility(boolean visible) {
        return visibility(VisibilityRule.of(visible));
    }

    /** Client-observed reaction when synced contents grow. UI feedback only; works with MenuKit alone. */
    public SlotHandle onInsertObserved(ReactiveHook hook) {
        set(BehaviorKeys.ON_INSERT_OBSERVED, hook);
        return this;
    }

    /** Client-observed reaction when synced contents shrink. */
    public SlotHandle onTakeObserved(ReactiveHook hook) {
        set(BehaviorKeys.ON_TAKE_OBSERVED, hook);
        return this;
    }
}
