package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.core.Slots;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.window.ClientSlotAddressing;
import com.trevlar.slotkit.window.VanillaAddressing;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The kind-aware slot addressing rule, which {@link ClientSlotAddressing} applies.
 * A created slot ({@link CreatedSlot}, or the creative wrapper around one)
 * resolves to its menu-independent created address. A vanilla slot resolves to its
 * container identity, or else its menu address. The client's observers, the
 * operation seams, the vetoes and the slot-level gate all use this one rule, so a
 * behaviour set on a slot's address is found whichever path reaches it.
 *
 * <p>Internal plumbing: consumers mint created-slot addresses by identity
 * ({@code Address.createdSlot(group, index)}), never from a live slot.
 */
@ApiStatus.Internal
public final class SlotAddresses {

    private SlotAddresses() {}

    /** The rule, for {@code ClientSlotAddressing.install}. */
    public static final ClientSlotAddressing.SlotAddressFn RULE = new ClientSlotAddressing.SlotAddressFn() {
        @Override
        public Address addressOf(AbstractContainerMenu menu, Slot slot) {
            return of(menu, slot);
        }

        @Override
        public @Nullable Address addressOf(Slot slot) {
            CreatedSlot created = CreatedSlotAccess.asMKCSlot(slot);
            if (created != null) return CreatedSlotAdapter.addressOf(created);
            Slot target = Slots.target(slot);
            return VanillaAddressing.addressOf(target.container, target.getContainerSlot()).orElse(null);
        }
    };

    /** The address of {@code slot} as it sits in {@code menu}, by slot kind. */
    public static Address of(AbstractContainerMenu menu, Slot slot) {
        CreatedSlot created = CreatedSlotAccess.asMKCSlot(slot);
        if (created != null) return CreatedSlotAdapter.addressOf(created);
        return VanillaAddressing.addressOf(menu, slot);
    }
}
