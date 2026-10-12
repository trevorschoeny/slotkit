package com.trevlar.slotkit.window;


import com.trevlar.slotkit.api.window.PersistentContainerKey;
import org.jetbrains.annotations.ApiStatus;

/**
 * The persistent owner a slot's state attaches to, plus the slot index local to
 * that owner.
 *
 * <p>For a single-owner container the local index equals the global one. For a
 * double chest ({@code CompoundContainer}) the global index is split into the
 * owning half's {@link PersistentContainerKey.BlockEntityKey} and the index within
 * that half (global minus the first half's size). Storage and the menu-free read
 * use the local index. Sync stays on global, menu-relative indices, because the
 * client never sees the split: it backs the menu with a flat container.
 */
@ApiStatus.Internal
public record ResolvedSlot(PersistentContainerKey key, int localSlotIndex) {}
