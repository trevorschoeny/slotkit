package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.PanelAddressing;
import com.trevlar.menukit.window.VanillaAddressing;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The window's view of one open menu, which mints typed {@link WindowHandle}s. A
 * handle is a value addressing one thing; the view is how you get one for "this
 * slot on the screen in front of me".
 *
 * <h2>What it does</h2>
 *
 * Mints handles by address. {@link #slot(int)} turns a vanilla menu index into a
 * slot handle (through {@link VanillaAddressing}); the methods that take an address
 * wrap one a consumer already holds (a created slot's address, or a panel's),
 * checking the {@link KindTag} so a panel address can't pass as a slot.
 *
 * <p>Setting and reading behavior on a handle goes straight to the engine
 * ({@link WindowEngine}, by address) and needs no live resolution. So it works with
 * MenuKit alone for client-tier behavior, and with Containers present for
 * server-tier behavior too. Live resolution (reading a slot's contents, drawing a
 * decoration) and minting created slots, panels and elements by identity are not
 * here yet.
 */
public final class WindowView {

    private final AbstractContainerMenu menu;

    private WindowView(AbstractContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** A view over {@code menu}. */
    public static WindowView of(AbstractContainerMenu menu) {
        return new WindowView(menu);
    }

    /**
     * A handle on the vanilla slot at flat menu {@code index}. For the library's
     * own resolution only. Consumers name a vanilla slot with {@link #slot(Address)}
     * and an address minted by identity, since a flat menu index ties the slot to
     * one menu, which is what addressing exists to avoid.
     */
    @ApiStatus.Internal
    public SlotHandle slot(int index) {
        return new SlotHandle(VanillaAddressing.addressOf(menu, menu.getSlot(index)));
    }

    /** A handle on a slot named by {@code address} (vanilla or created). */
    public SlotHandle slot(Address address) {
        requireKind(address, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
        return new SlotHandle(address);
    }

    /** A handle on a panel element named by {@code address}. */
    public ElementHandle element(Address address) {
        requireKind(address, KindTag.PANEL_ELEMENT);
        return new ElementHandle(address);
    }

    /** A handle on a panel named by {@code address}. */
    public PanelHandle panel(Address address) {
        requireKind(address, KindTag.PANEL);
        return new PanelHandle(address);
    }

    // By identity. The panel subtree roots at a constant family, so these need no
    // live screen and give one address everywhere.

    /** A handle on the panel with id {@code panelId} (its own visibility and opacity). */
    public PanelHandle panel(String panelId) {
        return new PanelHandle(PanelAddressing.ofPanel(panelId));
    }

    /** A handle on element {@code elementDeclId} within panel {@code panelId}. */
    public ElementHandle element(String panelId, String elementDeclId) {
        return new ElementHandle(PanelAddressing.ofElement(panelId, elementDeclId));
    }

    private static void requireKind(Address address, KindTag... allowed) {
        Objects.requireNonNull(address, "address");
        for (KindTag k : allowed) {
            if (address.kind() == k) return;
        }
        throw new IllegalArgumentException(
                "Address kind " + address.kind() + " is not one of " + java.util.Arrays.toString(allowed));
    }
}
