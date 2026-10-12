package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.ReactCause;
import com.trevlar.slotkit.api.window.ReactiveHook;
import com.trevlar.slotkit.api.window.WindowSignals;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fires {@code ON_INSERT_OBSERVED} and {@code ON_TAKE_OBSERVED} on the client when
 * a slot's synced contents change. UI feedback only (a flash, a sound, a badge),
 * with no authority, and it works with MenuKit alone.
 *
 * <h2>How it works</h2>
 *
 * Each client tick, for the open container menu, it diffs every slot against a
 * per-menu snapshot and fires the observed reaction for any change
 * ({@link WindowReactions#fireInsert}, {@code fireTake}). The first sight of a
 * menu only takes the snapshot, so nothing fires on open. A reopen is a new menu
 * instance, so its snapshot starts fresh (a {@link WeakHashMap}, so old menus can
 * be collected).
 *
 * <h2>Kind-aware addressing</h2>
 *
 * A changed slot is addressed through {@link ClientSlotAddressing}, the shared rule
 * (vanilla slots with MenuKit alone; with Containers, created slots resolve to their
 * created address). So the reaction resolves at the address a consumer set it on,
 * the same one {@link WindowSignals} reports for hover and click.
 *
 * <p>Client thread only. A slot with no observed reaction resolves to
 * {@link ReactiveHook#NONE} and returns early, so the common case (nobody reacting)
 * costs one resolve.
 */
public final class ObservedReactions {

    private ObservedReactions() {}

    // Last-seen contents (copies) per menu instance. A reopen is a new menu, so it starts fresh.
    private static final Map<AbstractContainerMenu, List<ItemStack>> SNAPSHOTS = new WeakHashMap<>();

    /**
     * Runs one client tick: diffs {@code menu}'s slots against the snapshot and fires
     * observed reactions for changes. Pass {@code null} when no container menu is
     * open (a no-op).
     */
    public static void tick(@Nullable AbstractContainerMenu menu) {
        if (menu == null) return;
        List<Slot> slots = menu.slots;
        List<ItemStack> snap = SNAPSHOTS.get(menu);
        if (snap == null) {                 // first sight: snapshot only, never fire on open
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
            // insert of the new, so classify by content and fire both when both happen.
            boolean tookOld = !before.isEmpty()
                    && (after.isEmpty() || !ItemStack.isSameItemSameComponents(before, after)
                        || after.getCount() < before.getCount());
            boolean gotNew = !after.isEmpty()
                    && (before.isEmpty() || !ItemStack.isSameItemSameComponents(before, after)
                        || after.getCount() > before.getCount());
            if (tookOld) WindowReactions.fireTake(address, before, after, ReactCause.SYNC);
            if (gotNew) WindowReactions.fireInsert(address, before, after, ReactCause.SYNC);
        }
        SNAPSHOTS.put(menu, snapshot(slots));
    }

    private static List<ItemStack> snapshot(List<Slot> slots) {
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (Slot s : slots) out.add(s.getItem().copy());
        return out;
    }
}
