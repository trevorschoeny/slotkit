package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.core.CreatedSlotAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * What the client can ask about a slot group while a screen is open. A mod's own
 * panel reads it to follow the server: SlotKit keeps each group's shown state (a
 * custom menu's panel flag, a hidden reveal), and the panel shows only what is live.
 *
 * <pre>{@code
 * panel.visibleWhen(() -> ClientSlots.isShown(MY_GROUP.id()));
 * }</pre>
 */
public final class ClientSlots {

    private ClientSlots() {}

    /**
     * Whether the open screen's menu carries {@code group} and at least one of its
     * slots is live on this client (not inert). False off a container screen, on a
     * menu without the group (a server without SlotKit, a menu an {@code onto} group
     * isn't on), and while every slot of it is hidden.
     */
    public static boolean isShown(SlotGroupId.Created group) {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs)) return false;
        // Scans menu.slots on every call; cache per menu if a screen ever
        // asks about many groups every frame.
        for (Slot slot : acs.getMenu().slots) {
            CreatedSlot created = CreatedSlotAccess.asMKCSlot(slot);
            if (created != null && !created.isInert()
                    && created.panelId().equals(group.panelId())
                    && created.groupId().equals(group.groupId())) {
                return true;
            }
        }
        return false;
    }
}
