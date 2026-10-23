package com.trevlar.slotkit.api.slot;

import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.KindTag;
import com.trevlar.slotkit.core.SlotAnchors;

import java.util.Objects;

/**
 * MenuKit {@link Placement}s against slots, for a panel's {@code in(ModeScope, Placement)}
 * (since 2.1.0). Client only.
 *
 * <pre>{@code
 * Panel elytra = Panel.builder("mymod:elytra")
 *         .add(SlotFlowElement.of(ELYTRA_SLOT))
 *         .in(ModeScope.SURVIVAL, SlotPlacement.slot(Address.playerInventory(40), 0, -18))  // above the offhand
 *         .in(ModeScope.CREATIVE, SlotPlacement.slot(Address.playerInventory(38), 18, 0))   // right of the chestplate
 *         .build();
 * new AnchorPanelAdapter(elytra);
 * }</pre>
 *
 * <h3>Presence</h3>
 * A slot or group placement shows where that slot or group is on the open menu this frame,
 * and nowhere else. Armor isn't on a creative item tab, so a panel placed on an armor slot
 * doesn't draw there, with no tab check in the mod.
 *
 * <h3>Declare at init</h3>
 * The first placement on a slot or group declares its MenuKit anchor, which MenuKit allows
 * only before its declarations freeze at client start. Build panels at init. A custom
 * menu's own panels ({@code ClientMenu.panel}) are built on each open and placed by the
 * menu's screen, so they take screen placements only, not these.
 */
public final class SlotPlacement {

    private SlotPlacement() {}

    /**
     * At the slot {@code address} names, {@code (dx, dy)} from the top-left of its 18x18
     * frame, at the panel's natural size. Nothing moves it apart from another panel there
     * or keeps it on the screen. A panel holding one slot at {@code (0, -18)} with no
     * padding sits one slot above, on vanilla's grid.
     *
     * @throws IllegalArgumentException when {@code address} doesn't name a slot
     */
    public static Placement slot(Address address, int dx, int dy) {
        Objects.requireNonNull(address, "address");
        if (address.kind() != KindTag.VANILLA_SLOT && address.kind() != KindTag.CREATED_SLOT) {
            throw new IllegalArgumentException("SlotPlacement.slot needs a slot's address, got " + address);
        }
        return Placement.at(SlotAnchors.slot(address), dx, dy);
    }

    /**
     * Around a slot group's box: {@code region(OutsideRegion)} stacks with the group's other
     * panels by priority, as a {@code SlotGroupPanelAdapter} did; {@code center()} and
     * {@code pixel(...)} work as anywhere. A panel holding slots keeps vanilla's pitch with
     * {@code gap(0)}.
     */
    public static Placement group(SlotGroupId group, PanelPosition position) {
        return Placement.around(SlotAnchors.group(Objects.requireNonNull(group, "group")), position);
    }

    /**
     * {@link #group(SlotGroupId, PanelPosition)} on a category's vanilla group. For a panel
     * on every group of the category, created ones too, see
     * {@link com.trevlar.slotkit.api.group.CategoryPanels}.
     */
    public static Placement group(SlotGroupCategory category, PanelPosition position) {
        return group(SlotGroupId.category(Objects.requireNonNull(category, "category")), position);
    }
}
