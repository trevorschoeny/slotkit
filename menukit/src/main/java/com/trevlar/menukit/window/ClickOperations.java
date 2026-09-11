package com.trevlar.menukit.window;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

/**
 * The operations a click names on the slot it is sent for, and whether any of
 * them is refused. One classifier, two callers: vanilla's {@code doClick} (both
 * sides) and the client's send boundary, {@code MultiPlayerGameMode.handleContainerInput},
 * which refuses to send a click its own slot refuses. The second is what makes a
 * refusal hold on a server that does not run MenuKit: the click never leaves.
 *
 * <p>What a click names, from the click and the state it lands on:
 * <ul>
 *   <li>Plain click ({@code PICKUP}): a take when the slot has items and the
 *       cursor cannot merge into them; a put when the cursor holds items. A swap of
 *       different items is both.</li>
 *   <li>Shift-click ({@code QUICK_MOVE}): shift-click out of this slot. Where the
 *       stack lands is decided later, in {@code moveItemStackTo}.</li>
 *   <li>{@code THROW}: drop one (button 0) or the stack.</li>
 *   <li>{@code SWAP}: hotbar swap (buttons 0-8) or offhand swap (40), on this slot
 *       and on the inventory slot it swaps with.</li>
 *   <li>{@code QUICK_CRAFT} adding a slot to a drag: drag-fill on that slot.</li>
 * </ul>
 * Double-click collect is judged per swept slot at {@code canTakeItemForPickAll},
 * and creative clone touches nothing.
 */
@ApiStatus.Internal
public final class ClickOperations {

    private ClickOperations() {}

    /** Whether the click MenuKit is about to run or send is refused on the slot(s) it names. */
    public static boolean refuses(AbstractContainerMenu menu, int slotId, int button, ContainerInput input,
                                  Player player) {
        if (slotId < 0 || slotId >= menu.slots.size()) return false; // outside the menu: no slot to judge
        Slot slot = menu.slots.get(slotId);
        return switch (input) {
            case PICKUP -> {
                ItemStack inSlot = slot.getItem();
                ItemStack carried = menu.getCarried();
                boolean takes = !inSlot.isEmpty()
                        && (carried.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, carried));
                boolean puts = !carried.isEmpty();
                yield (takes && !allows(menu, slot, player, BehaviorKeys.CLICK_TAKE))
                        || (puts && !allows(menu, slot, player, BehaviorKeys.CLICK_PUT));
            }
            case QUICK_MOVE -> !allows(menu, slot, player, BehaviorKeys.SHIFT_CLICK_OUT);
            case THROW -> !allows(menu, slot, player, button == 0 ? BehaviorKeys.DROP : BehaviorKeys.DROP_STACK);
            case SWAP -> {
                BehaviorKey<TriBool> op = button == Inventory.SLOT_OFFHAND ? BehaviorKeys.OFFHAND_SWAP
                        : Inventory.isHotbarSlot(button) ? BehaviorKeys.HOTBAR_SWAP
                        : null; // vanilla ignores any other button, and so does this
                yield op != null && (!allows(menu, slot, player, op)
                        || !SlotOperations.allowsGesture(SlotRef.inventory(menu, player, button), op));
            }
            case QUICK_CRAFT -> AbstractContainerMenu.getQuickcraftHeader(button) == 1
                    && !allows(menu, slot, player, BehaviorKeys.DRAG_FILL);
            default -> false;
        };
    }

    private static boolean allows(AbstractContainerMenu menu, Slot slot, Player player, BehaviorKey<TriBool> gesture) {
        return SlotOperations.allowsGesture(SlotRef.of(menu, slot, player), gesture);
    }
}
