package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotRef;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;

/**
 * The operations a player action names with no screen open, and whether any of
 * them is refused. Q, Ctrl-Q and F while playing do not go through a menu click:
 * they are {@code ServerboundPlayerActionPacket} actions on the selected hotbar
 * slot. They are the same operations as Q, Ctrl-Q and F over a slot in a screen,
 * so they resolve the same keys:
 *
 * <ul>
 *   <li>{@code DROP_ITEM}: {@link BehaviorKeys#DROP} on the selected slot.</li>
 *   <li>{@code DROP_ALL_ITEMS}: {@link BehaviorKeys#DROP_STACK} on the selected slot.</li>
 *   <li>{@code SWAP_ITEM_WITH_OFFHAND}: {@link BehaviorKeys#OFFHAND_SWAP} on the
 *       selected slot and on the offhand slot, as a swap has two participants.</li>
 * </ul>
 *
 * The slot is read on {@code player.inventoryMenu}, so a hotbar slot resolves with
 * its {@code PLAYER_HOTBAR} category rung. Two callers: the client, before it
 * drops or sends, and the server when the action arrives.
 */
@ApiStatus.Internal
public final class PlayerActions {

    private PlayerActions() {}

    /** Whether this action is one of the three that are slot operations. */
    public static boolean isOperation(ServerboundPlayerActionPacket.Action action) {
        return action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
    }

    /** Whether {@code player} doing {@code action} is refused. False for actions that are not operations. */
    public static boolean refuses(Player player, ServerboundPlayerActionPacket.Action action) {
        return switch (action) {
            case DROP_ITEM -> !SlotOperations.allowsGesture(selected(player), BehaviorKeys.DROP);
            case DROP_ALL_ITEMS -> !SlotOperations.allowsGesture(selected(player), BehaviorKeys.DROP_STACK);
            case SWAP_ITEM_WITH_OFFHAND -> !SlotOperations.allowsGesture(selected(player), BehaviorKeys.OFFHAND_SWAP)
                    || !SlotOperations.allowsGesture(
                            SlotRef.inventory(player.inventoryMenu, player, Inventory.SLOT_OFFHAND),
                            BehaviorKeys.OFFHAND_SWAP);
            default -> false;
        };
    }

    private static SlotRef selected(Player player) {
        return SlotRef.inventory(player.inventoryMenu, player, player.getInventory().getSelectedSlot());
    }
}
