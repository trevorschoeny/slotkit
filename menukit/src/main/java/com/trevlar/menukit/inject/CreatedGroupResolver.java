package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.slot.SlotGroupId;
import com.trevlar.menukit.api.slot.SlotGroupResolver;
import com.trevlar.menukit.api.slot.SlotGroupCategory;

import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The universal seat: reports the created slot groups present on <em>any</em>
 * menu. MenuKit-Containers installs one implementation at init; MenuKit alone
 * there are no created slots and the seat stays empty.
 *
 * <p>Distinct from {@link SlotGroupResolver}, which answers per menu class and
 * per category, because a created group is neither. It sits on whatever menu the
 * player opened, so no menu class names it, and it must report itself as its own
 * group rather than being folded into its category — several created groups can
 * share one category and they do not share a bounding box.
 */
@ApiStatus.Internal
@FunctionalInterface
public interface CreatedGroupResolver {

    /** Every created group with at least one live slot on {@code menu}. */
    List<Contribution> resolve(AbstractContainerMenu menu);

    /**
     * One created group's presence on a menu.
     *
     * @param panelId      the group's panel id, half of its {@link SlotGroupId.Created} name
     * @param groupId      the group's own id, the other half
     * @param category     what the group declared itself to be
     * @param slotIndices  its slots' indices in {@code menu.slots}
     */
    record Contribution(String panelId, String groupId,
                        SlotGroupCategory category, int[] slotIndices) {}
}
