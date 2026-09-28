package com.trevlar.menukit.window;

/**
 * A typed handle on a slot, vanilla or created: the slot verbs, plus the generic
 * {@link WindowHandle#set} substrate. The slot author's server rules are
 * {@link #gate} and {@link #binding}; the operations are set by key
 * ({@code set(BehaviorKeys.SHIFT_CLICK_IN, TriBool.FALSE)}). Reactive verbs
 * ({@link ReactiveHook}) and visibility ({@link TriBool}) have sugar here too.
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

    /** Drive this slot's client visibility by a {@link VisibilityRule} (a created slot's contents keep syncing regardless). */
    public SlotHandle visibility(VisibilityRule rule) {
        set(BehaviorKeys.VISIBILITY, rule);
        return this;
    }

    /** Show/hide this slot on the client (constant rule). */
    public SlotHandle visibility(boolean visible) {
        return visibility(VisibilityRule.of(visible));
    }

    /** Server-authoritative reaction when this slot gains content (MKC-only firing; the seam is owed). */
    public SlotHandle onInsert(ReactiveHook hook) {
        set(BehaviorKeys.ON_INSERT, hook);
        return this;
    }

    /** Server-authoritative reaction when this slot loses content. */
    public SlotHandle onTake(ReactiveHook hook) {
        set(BehaviorKeys.ON_TAKE, hook);
        return this;
    }

    /** Client-observed reaction when synced contents grow — pure UI feedback, MK-alone. */
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
