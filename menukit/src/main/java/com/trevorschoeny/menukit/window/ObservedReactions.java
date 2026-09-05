package com.trevorschoeny.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The client-observed half of the reactive verbs (the part the architecture says
 * "ships working now") — a client-side detector that fires {@code ON_INSERT_OBSERVED}
 * / {@code ON_TAKE_OBSERVED} when a slot's synced contents change. Pure UI feedback
 * (flash/sound/badge), no authority, MK-alone capable.
 *
 * <h2>How it works</h2>
 *
 * Each client tick, for the open container menu, it diffs every slot against a
 * per-menu snapshot and fires the observed reaction for any change (via
 * {@link WindowReactions#fireInsert}/{@code fireTake} with {@code server=false}).
 * First sight of a menu only snapshots (no fire on open). A reopen is a new menu
 * instance, so its snapshot starts fresh ({@link WeakHashMap}, GC-friendly).
 *
 * <h2>Kind-aware addressing</h2>
 *
 * A changed slot is addressed through {@link ClientSlotAddressing} — the shared
 * client slot→address rule (MK-alone: vanilla slots; MKC installs the kind-aware
 * {@code SlotAddresses.of} so created slots resolve to their created address). The
 * reaction resolves at the SAME address a consumer set it on, and the same address
 * {@link WindowSignals} reports for hover/click.
 *
 * <p>Client-thread only. A slot with no observed reaction resolves to
 * {@link ReactiveHook#NONE} and the fire-entry short-circuits, so the common case
 * (nobody reacting) costs a resolve and nothing else.
 */
public final class ObservedReactions {

    private ObservedReactions() {}

    // menu instance -> last-seen contents (copies). Reopen = new menu = fresh.
    private static final Map<AbstractContainerMenu, List<ItemStack>> SNAPSHOTS = new WeakHashMap<>();

    /**
     * Drive one client tick: diff {@code menu}'s slots against the snapshot and fire
     * observed reactions for changes. Pass {@code null} when no container menu is
     * open (a no-op). Call from the client tick.
     */
    public static void tick(@Nullable AbstractContainerMenu menu) {
        if (menu == null) return;
        List<Slot> slots = menu.slots;
        List<ItemStack> snap = SNAPSHOTS.get(menu);
        if (snap == null) {                 // first sight: snapshot, never fire on open
            SNAPSHOTS.put(menu, snapshot(slots));
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack before = i < snap.size() ? snap.get(i) : ItemStack.EMPTY;
            ItemStack after = slot.getItem();
            if (ItemStack.matches(before, after)) continue;

            Address address = ClientSlotAddressing.addressOf(menu, slot);
            // A swap (same count, different item) is both a take of the old and an
            // insert of the new — classify by content, fire both when both happen.
            boolean tookOld = !before.isEmpty()
                    && (after.isEmpty() || !ItemStack.isSameItemSameComponents(before, after)
                        || after.getCount() < before.getCount());
            boolean gotNew = !after.isEmpty()
                    && (before.isEmpty() || !ItemStack.isSameItemSameComponents(before, after)
                        || after.getCount() > before.getCount());
            if (tookOld) WindowReactions.fireTake(address, before, after, ReactCause.SYNC, /*server=*/false);
            if (gotNew) WindowReactions.fireInsert(address, before, after, ReactCause.SYNC, /*server=*/false);
        }
        SNAPSHOTS.put(menu, snapshot(slots));
    }

    private static List<ItemStack> snapshot(List<Slot> slots) {
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (Slot s : slots) out.add(s.getItem().copy());
        return out;
    }
}
