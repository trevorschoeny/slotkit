package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * One slot group as it exists on an open menu: what it is called, what kind of
 * thing it is, and which slots it currently holds.
 *
 * <p>This is the <b>anchor</b> view of the registry, the one with geometry in it.
 * {@link SlotGroupCategories#of} is the <b>search</b> view, which unions a
 * category's groups because "find me every inventory slot" wants the union.
 *
 * @param id       the group's stable name, which a panel anchors to
 * @param category what kind of group it is, which a consumer searches by
 * @param slots    its slots on this menu, in menu order
 */
public record ResolvedSlotGroup(SlotGroupId id, SlotGroupCategory category, List<Slot> slots) {

    public ResolvedSlotGroup {
        slots = List.copyOf(slots);
    }
}
