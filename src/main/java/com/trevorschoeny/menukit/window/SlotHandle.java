package com.trevorschoeny.menukit.window;

/**
 * A typed handle on a slot (vanilla or created) — exposes the slot verbs whose
 * value type is an MK type, plus the generic substrate inherited from
 * {@link WindowHandle}. The SERVER slot behaviors whose value type is an MKC type
 * (gating, quick-move) are set through the generic {@link #set} with the MKC keys,
 * or via MKC-side sugar; MK can't name {@code SlotGate}/{@code QuickMoveParticipation}
 * (§0042), so it offers no typed sugar for them — the substrate is the truth.
 *
 * <p>Reactive verbs ({@link ReactiveHook}) and visibility ({@link TriBool}) are
 * MK types, so their sugar lives here. A SERVER reaction with MKC absent is a safe
 * no-op (the dispatch port is the null-object); an observed reaction is MK-alone.
 */
public final class SlotHandle extends WindowHandle {

    SlotHandle(Address address) {
        super(address);
    }

    /** Show/hide this slot on the client (CLIENT-tier; a created slot's contents keep syncing). */
    public SlotHandle visibility(TriBool visible) {
        set(BehaviorKeys.VISIBILITY, visible);
        return this;
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
