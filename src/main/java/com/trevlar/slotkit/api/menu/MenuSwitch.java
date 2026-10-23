package com.trevlar.slotkit.api.menu;

import com.trevlar.slotkit.api.slot.OnDemandGroup;
import com.trevlar.slotkit.network.Handshake;
import com.trevlar.slotkit.network.OpenInventoryS2CPayload;
import com.trevlar.slotkit.state.SlotStateHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Switching a player between menus while one menu, the one they opened in the world,
 * waits open behind the others. For tabs on a container screen: a player at a chest
 * flips to their inventory, a shulker box, their ender chest, and back, and the chest
 * stays open in the world the whole time (its lid up, no close sound), while every
 * other menu really closes when they leave it (the inventory's crafting grid hands its
 * items back).
 *
 * <pre>{@code
 * // Server, from a tab click:
 * MenuSwitch.open(player, boxProvider);     // the chest waits behind, the box opens
 * MenuSwitch.openInventory(player);         // the box closes, the inventory opens
 * MenuSwitch.back(player);                  // the inventory closes, the chest comes back
 *
 * // Common init: hear when the waiting menu closes on its own.
 * MenuSwitch.onSetAsideClosed((player, menu, why) -> ...);
 * }</pre>
 *
 * <h3>The menu set aside</h3>
 * The first switch away from a menu the player opened some other way (a chest they
 * clicked, any {@code player.openMenu} that isn't this class's) sets it aside instead
 * of closing it: no {@code removed()}, no close packet, so the block's opener count
 * holds. Menus opened through {@link #open} are the tabs: switching away from one
 * closes it normally. The inventory is never set aside, so a switch that starts at
 * the inventory screen sets nothing aside, and {@link #back} has nothing to return to.
 * Only a menu opened through {@code openMenu} can be set aside (its title is what
 * {@code back} shows again); any other closes normally.
 *
 * <p>While it waits, the set-aside menu's {@code stillValid} is checked every tick,
 * and it closes properly ({@code removed()}, so the lid shuts) when that fails, the
 * player dies or changes dimension, the menu in front closes (Escape on any screen),
 * a menu opens some other way, or the player leaves. {@link #onSetAsideClosed} hears
 * each of these. It gets no sync while it waits; {@code back} resyncs it whole.
 *
 * <h3>The cursor</h3>
 * The stack on the cursor moves across every switch, to the next menu, instead of
 * going back to the inventory.
 *
 * <h3>The client</h3>
 * {@code open} and {@code back} send only vanilla packets, so they work for a player
 * without SlotKit. {@link #openInventory} needs SlotKit on the client (vanilla has no
 * packet that opens the inventory) and returns {@code false} without it. With SlotKit
 * on the client, the screen the player is on stays up until the next menu's contents
 * arrive, so a switch never shows an empty screen for a frame.
 *
 * <p>Server thread only. Since 2.1.0.
 */
public final class MenuSwitch {

    private MenuSwitch() {}

    /** Why the set-aside menu closed. */
    public enum Reason {
        /** Its {@code stillValid} failed (walked away, the block broke), or the player died or changed dimension. */
        INVALID,
        /** The menu in front closed without a switch (Escape, its own validity), or a menu opened some other way. */
        CLOSED,
        /** The player left the server. */
        LEFT
    }

    /** Hears the set-aside menu close on its own. */
    @FunctionalInterface
    public interface SetAsideClosed {
        /** Called after {@code menu.removed(player)}. */
        void closed(ServerPlayer player, AbstractContainerMenu menu, Reason why);
    }

    // The menu each player has set aside, with what back() needs to show it again.
    private record Aside(AbstractContainerMenu menu, Component title, Level level) {}

    private static final Map<ServerPlayer, Aside> ASIDE = new WeakHashMap<>();
    // The title every openMenu showed, by menu: what back() reopens a menu under.
    private static final Map<AbstractContainerMenu, Component> TITLES = new WeakHashMap<>();
    // The menus open() opened: the tabs, which close when left.
    private static final Set<AbstractContainerMenu> TABS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<SetAsideClosed> LISTENERS = new CopyOnWriteArrayList<>();

    // Set while this class closes a menu itself, so the close hook knows it's a switch.
    private static boolean switching;
    // The cursor stack for the menu open() is opening, put on it in initMenu, before its first sync.
    private static @Nullable ItemStack pendingCarried;

    /**
     * Opens {@code next} for {@code player}. The menu in front is set aside if it can be
     * (see the class doc) and nothing is set aside yet, and otherwise closes normally.
     * The cursor stack moves to {@code next}.
     *
     * @return the new menu's container id, or empty when {@code next} made no menu. Then
     *         the player goes back to the set-aside menu, or with none, the screen closes
     *         and the cursor stack returns to the inventory.
     */
    public static OptionalInt open(ServerPlayer player, MenuProvider next) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(next, "next");
        checkFront(player);
        int leftId = player.containerMenu.containerId;
        ItemStack carried = leaveFront(player);
        pendingCarried = carried;
        OptionalInt id;
        try {
            id = player.openMenu(next);
        } finally {
            pendingCarried = null;
        }
        if (id.isPresent()) return id;
        // Refused: never leave the player on a screen whose menu is gone.
        if (!back(player, carried)) {
            player.connection.send(new ClientboundContainerClosePacket(leftId));
            if (!carried.isEmpty()) player.getInventory().placeItemBackInInventory(carried, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return id;
    }

    /**
     * Opens the player's own inventory screen, as {@link #open} does a menu: the menu in
     * front is set aside or closes, and the cursor stack moves over.
     *
     * @return {@code false}, and nothing changes, when the player's client has no SlotKit
     */
    public static boolean openInventory(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!Handshake.hears(player)) return false;
        checkFront(player);
        ItemStack carried = player.containerMenu == player.inventoryMenu
                ? takeCarried(player.inventoryMenu)   // already the inventory: nothing closes
                : leaveFront(player);
        Handshake.send(player, OpenInventoryS2CPayload.INSTANCE);
        player.inventoryMenu.setCarried(carried);
        player.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    /**
     * Closes the menu in front normally and reopens the set-aside one as itself: its
     * container id, type and title, then all its contents. The cursor stack moves over.
     *
     * @return {@code false} when nothing is set aside
     */
    public static boolean back(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        checkFront(player);
        if (!ASIDE.containsKey(player)) return false;
        return back(player, leaveFront(player));
    }

    /** The menu {@code player} has set aside, if any. */
    public static Optional<AbstractContainerMenu> setAside(ServerPlayer player) {
        Aside a = ASIDE.get(player);
        return a == null ? Optional.empty() : Optional.of(a.menu);
    }

    /** Adds a listener for the set-aside menu closing on its own. Common init. */
    public static void onSetAsideClosed(SetAsideClosed listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    // The switch itself

    /** Takes the cursor stack off the menu in front, then sets that menu aside or closes it. */
    private static ItemStack leaveFront(ServerPlayer player) {
        AbstractContainerMenu front = player.containerMenu;
        ItemStack carried = takeCarried(front);
        Component title = TITLES.get(front);
        boolean keep = front != player.inventoryMenu && !TABS.contains(front)
                && title != null && !ASIDE.containsKey(player);
        if (keep) {
            ASIDE.put(player, new Aside(front, title, player.level()));
            player.containerMenu = player.inventoryMenu;
        } else {
            switching = true;
            try {
                player.doCloseContainer();   // removed(), but no close packet: the next screen replaces this one
            } finally {
                switching = false;
            }
        }
        return carried;
    }

    private static boolean back(ServerPlayer player, ItemStack carried) {
        Aside a = ASIDE.remove(player);
        if (a == null) return false;
        AbstractContainerMenu menu = a.menu;
        player.containerMenu = menu;
        menu.setCarried(carried);
        player.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), a.title));
        // The client's menu is new: it hears about the on-demand groups before the contents.
        OnDemandGroup.replay(player, menu);
        menu.sendAllDataToRemote();
        SlotStateHooks.sendSnapshotForOpenedMenu(player);
        return true;
    }

    private static ItemStack takeCarried(AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        return carried;
    }

    /** A menu in front that neither this class nor the player's inventory put there means the world moved on. */
    private static void checkFront(ServerPlayer player) {
        AbstractContainerMenu front = player.containerMenu;
        if (ASIDE.containsKey(player) && front != player.inventoryMenu && !TABS.contains(front)) {
            closeAside(player, Reason.CLOSED);
        }
    }

    private static void closeAside(ServerPlayer player, Reason why) {
        Aside a = ASIDE.remove(player);
        if (a == null) return;
        a.menu.removed(player);
        for (SetAsideClosed l : LISTENERS) l.closed(player, a.menu, why);
    }

    // Library use, from SKMenuSwitchMixin and SlotKit's init

    /** {@code openMenu} made {@code menu}, just before its first sync: an {@link #open} gets its cursor stack. */
    @ApiStatus.Internal
    public static void menuOpening(AbstractContainerMenu menu) {
        if (pendingCarried == null) return;
        menu.setCarried(pendingCarried);
        TABS.add(menu);
        pendingCarried = null;
    }

    /** {@code openMenu} opened the player's new menu under {@code title}. */
    @ApiStatus.Internal
    public static void menuOpened(ServerPlayer player, Component title) {
        TITLES.put(player.containerMenu, title);
    }

    /** The menu in front closed: unless that was a switch, the set-aside one closes too. */
    @ApiStatus.Internal
    public static void frontClosed(ServerPlayer player) {
        if (!switching) closeAside(player, Reason.CLOSED);
    }

    /** Each player tick: the set-aside menu still has to be valid. */
    @ApiStatus.Internal
    public static void tick(ServerPlayer player) {
        Aside a = ASIDE.get(player);
        if (a == null) return;
        if (!player.isAlive() || player.level() != a.level || !a.menu.stillValid(player)) {
            closeAside(player, Reason.INVALID);
        } else {
            checkFront(player);
        }
    }

    /** The player is leaving the server. */
    @ApiStatus.Internal
    public static void left(ServerPlayer player) {
        closeAside(player, Reason.LEFT);
    }

    /** The container id the player's next menu must not take while one is set aside, or -1. */
    @ApiStatus.Internal
    public static int heldId(ServerPlayer player) {
        Aside a = ASIDE.get(player);
        return a == null ? -1 : a.menu.containerId;
    }
}
