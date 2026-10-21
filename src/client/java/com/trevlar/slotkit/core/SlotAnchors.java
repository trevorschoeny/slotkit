package com.trevlar.slotkit.core;

import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.CreatedSlots;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.slotkit.window.VanillaAddressing;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SlotKit's MenuKit anchors: one per slot and one per slot group, each declared on first
 * use and kept, since MenuKit refuses a second {@code Anchor.of} with one id and panels on
 * one anchor share its host. Each box is read from the open menu every frame, so it follows
 * creative's tab switches and {@code /gamemode}: where the slot or group isn't on the menu
 * now, the anchor has no box and its panels don't draw.
 */
@ApiStatus.Internal
public final class SlotAnchors {

    private SlotAnchors() {}

    private static final Map<Address, Anchor> SLOTS = new ConcurrentHashMap<>();
    private static final Map<SlotGroupId, Anchor> GROUPS = new ConcurrentHashMap<>();

    /** The anchor whose box is the slot {@code address} names: its 18x18 frame. */
    public static Anchor slot(Address address) {
        return SLOTS.computeIfAbsent(address,
                a -> Anchor.of(id("slot/", a.asString()), screen -> slotBox(screen, a)));
    }

    /** The anchor whose box is a slot group's slots' frames ({@link SlotGroupBox#of}). */
    public static Anchor group(SlotGroupId group) {
        return GROUPS.computeIfAbsent(group,
                g -> Anchor.of(id("group/", g.asString()), screen -> groupBox(screen, g)));
    }

    /**
     * An id readable in a log: {@code slotkit:<kind><sanitised name>_<hash>}. The hash of the
     * raw name keeps two names that sanitise alike ("Pocket" and "pocket") apart.
     */
    private static Identifier id(String kind, String name) {
        String path = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return Identifier.fromNamespaceAndPath("slotkit", kind + path + "_" + Integer.toHexString(name.hashCode()));
    }

    /**
     * The slot's frame on this screen this frame, or {@code null} when it isn't there: not a
     * container screen, the slot not on the menu (armour on a creative item tab), not active,
     * parked because no panel presents it, or cut by the clip it's shown in (a row half
     * scrolled out of a region), so a panel anchored on it doesn't draw outside the view.
     */
    public static @Nullable Reference slotBox(Screen screen, Address address) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        Slot slot = live(acs.getMenu(), address);
        if (slot == null || !slot.isActive() || slot.x == CreatedSlots.OFFSCREEN) return null;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        // The frame, not the 16x16 item box, as for a group: an offset of 18 is one pitch.
        int x = acc.sk$getLeftPos() + slot.x - 1, y = acc.sk$getTopPos() + slot.y - 1;
        Reference clip = com.trevlar.menukit.api.host.SlotClip.clipOf(slot);
        if (clip != null && (x < clip.leftPos() || y < clip.topPos()
                || x + 18 > clip.leftPos() + clip.imageWidth() || y + 18 > clip.topPos() + clip.imageHeight())) return null;
        return new Reference(x, y, 18, 18);
    }

    /** The live slot (the creative wrapper where the menu holds one) {@code address} names on {@code menu}, or {@code null}. */
    private static @Nullable Slot live(AbstractContainerMenu menu, Address address) {
        return switch (address.kind()) {
            case CREATED_SLOT -> CreatedSlotAdapter.INSTANCE.resolve(menu, address);
            case VANILLA_SLOT -> VanillaAddressing.resolve(menu, address);
            default -> null;
        };
    }

    private static @Nullable Reference groupBox(Screen screen, SlotGroupId id) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        return SlotGroupBox.of(acs.getMenu(), acc.sk$getLeftPos(), acc.sk$getTopPos(), id);
    }
}
