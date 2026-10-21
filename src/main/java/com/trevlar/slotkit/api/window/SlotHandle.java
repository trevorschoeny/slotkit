package com.trevlar.slotkit.api.window;

import com.trevlar.menukit.api.panel.ModeScope;

/**
 * A typed handle on a slot, vanilla or created: the slot verbs, plus the generic
 * {@link WindowHandle#set}. The slot author's server rules are {@link #gate} and
 * {@link #binding}; operations are set by key
 * ({@code set(BehaviorKeys.SHIFT_CLICK_IN, TriBool.FALSE)}). The client-observed
 * reactions ({@link ReactiveHook}) have shorthand methods here too.
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

    SlotHandle(ModeScope scope, Address address) {
        super(scope, address);
    }

    /** Client-observed reaction when synced contents grow. UI feedback only; works on any server. */
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
