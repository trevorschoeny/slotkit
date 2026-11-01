package com.trevlar.slotkit.api.menu;

import com.trevlar.slotkit.api.slot.OnDemandGroup;
import com.trevlar.slotkit.mixin.MerchantMenuAccessor;
import com.trevlar.slotkit.network.Handshake;
import com.trevlar.slotkit.network.OpenInventoryS2CPayload;
import com.trevlar.slotkit.state.SlotStateHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;

import java.util.Collections;
import java.util.EnumSet;
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
 * Only a menu opened through {@code openMenu}, or a mount's inventory (a horse's, a
 * llama's, a nautilus's), can be set aside: {@code back} sends the packet that opened it
 * again. Any other closes normally. A trading screen comes back with its trades (the
 * selected one may reset); its trader stays locked to the player while it waits.
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
 * <h3>Clicks in flight</h3>
 * A click the client sent before it heard of a switch is addressed to the menu it
 * left, and vanilla drops it. Until the client's first click on the new menu, such a
 * click on one of the player's inventory slots (any slot whose container the new menu
 * also shows) lands on that same slot in the new menu: a pickup, a put-down, a number
 * key, a middle click or a drop key, or a click outside the screen. A click on the old
 * menu's own slots, a shift-click, a double-click collect and a drag are still dropped,
 * since what they would do depends on the menu they were aimed at.
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

    // The menu each player has set aside, with the packet that opened it, which back() sends again.
    private record Aside(AbstractContainerMenu menu, Packet<? super ClientGamePacketListener> opener, Level level) {}

    private static final Map<ServerPlayer, Aside> ASIDE = new WeakHashMap<>();
    // The packet that opened each menu that can be set aside: openMenu's open-screen, or a mount screen's.
    // The menu keeps its container id while it waits, so the same packet opens it again.
    private static final Map<AbstractContainerMenu, Packet<? super ClientGamePacketListener>> OPENERS = new WeakHashMap<>();
    // The trader level last sent with each trading menu's offers. The trader doesn't know
    // its own level as a Merchant (a villager's comes from its data, a wandering trader's is 1).
    private static final Map<AbstractContainerMenu, Integer> TRADER_LEVELS = new WeakHashMap<>();
    // The menus open() opened: the tabs, which close when left.
    private static final Set<AbstractContainerMenu> TABS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<SetAsideClosed> LISTENERS = new CopyOnWriteArrayList<>();
    // The menu each player was last switched away from, and the one they were switched to,
    // until the client's first click that isn't addressed to the old one.
    private record Left(AbstractContainerMenu from, AbstractContainerMenu to) {}
    private static final Map<ServerPlayer, Left> LEFT = new WeakHashMap<>();
    // The clicks whose effect is the slot's alone, whatever menu shows it.
    private static final Set<ContainerInput> SLOT_ONLY =
            EnumSet.of(ContainerInput.PICKUP, ContainerInput.SWAP, ContainerInput.CLONE, ContainerInput.THROW);

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
        AbstractContainerMenu from = player.containerMenu;
        int leftId = from.containerId;
        ItemStack carried = leaveFront(player);
        pendingCarried = carried;
        OptionalInt id;
        try {
            id = player.openMenu(next);
        } finally {
            pendingCarried = null;
        }
        if (id.isPresent()) {
            switchedFrom(player, from);
            return id;
        }
        // Refused: never leave the player on a screen whose menu is gone.
        if (back(player, carried)) {
            switchedFrom(player, from);
        } else {
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
        AbstractContainerMenu from = player.containerMenu;
        ItemStack carried = from == player.inventoryMenu
                ? takeCarried(player.inventoryMenu)   // already the inventory: nothing closes
                : leaveFront(player);
        Handshake.send(player, OpenInventoryS2CPayload.INSTANCE);
        player.inventoryMenu.setCarried(carried);
        player.inventoryMenu.sendAllDataToRemote();
        switchedFrom(player, from);
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
        AbstractContainerMenu from = player.containerMenu;
        back(player, leaveFront(player));
        switchedFrom(player, from);
        return true;
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
        Packet<? super ClientGamePacketListener> opener = OPENERS.get(front);
        boolean keep = front != player.inventoryMenu && !TABS.contains(front)
                && opener != null && !ASIDE.containsKey(player);
        if (keep) {
            ASIDE.put(player, new Aside(front, opener, player.level()));
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
        player.connection.send(a.opener);
        // A trading menu's trades come separately, as vanilla sends them, before the contents.
        Integer traderLevel = TRADER_LEVELS.get(menu);
        if (menu instanceof MerchantMenu && traderLevel != null) {
            Merchant trader = ((MerchantMenuAccessor) menu).sk$getTrader();
            player.connection.send(new ClientboundMerchantOffersPacket(menu.containerId, trader.getOffers(),
                    traderLevel, trader.getVillagerXp(), trader.showProgressBar(), trader.canRestock()));
        }
        // The client's menu is new: it hears about the on-demand groups before the contents.
        OnDemandGroup.replay(player, menu);
        menu.sendAllDataToRemote();
        SlotStateHooks.sendSnapshotForOpenedMenu(player);
        return true;
    }

    /** The client's clicks on {@code from} still in flight now belong to the menu in front. */
    private static void switchedFrom(ServerPlayer player, AbstractContainerMenu from) {
        if (from != player.containerMenu) LEFT.put(player, new Left(from, player.containerMenu));
    }

    /**
     * The slot in {@code to} showing what {@code from}'s slot {@code slotNum} shows, for a
     * click whose effect is that slot's alone, or empty when the click means nothing in {@code to}.
     */
    private static OptionalInt sameSlot(AbstractContainerMenu from, AbstractContainerMenu to, int slotNum, ContainerInput input) {
        if (!SLOT_ONLY.contains(input)) return OptionalInt.empty();
        if (slotNum == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) return OptionalInt.of(slotNum);
        if (slotNum < 0 || !from.isValidSlotIndex(slotNum)) return OptionalInt.empty();
        Slot was = from.getSlot(slotNum);
        for (Slot s : to.slots) {
            if (s.container == was.container && s.getContainerSlot() == was.getContainerSlot()) return OptionalInt.of(s.index);
        }
        return OptionalInt.empty();
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
        AbstractContainerMenu menu = player.containerMenu;
        OPENERS.put(menu, new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), title));
    }

    /** A mount's inventory opened for the player ({@code openHorseInventory}, {@code openNautilusInventory}), which skips {@code openMenu}. */
    @ApiStatus.Internal
    public static void mountOpened(ServerPlayer player, int inventoryColumns, int mountId) {
        AbstractContainerMenu menu = player.containerMenu;
        OPENERS.put(menu, new ClientboundMountScreenOpenPacket(menu.containerId, inventoryColumns, mountId));
    }

    /** The player's trading menu {@code containerId} was sent its offers at {@code traderLevel}. */
    @ApiStatus.Internal
    public static void offersSent(ServerPlayer player, int containerId, int traderLevel) {
        if (player.containerMenu.containerId == containerId) TRADER_LEVELS.put(player.containerMenu, traderLevel);
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
        LEFT.remove(player);
    }

    /**
     * The player's entity is leaving its level: a dimension change, a respawn (which
     * replaces the entity), the End's exit, or leaving the server. A respawned or
     * departed entity never ticks again, so its set-aside menu closes now or never
     * (a crafting grid's items would be lost), and this class lets go of it. Each map
     * entry's menu holds its player, so a weak key alone never frees it.
     */
    @ApiStatus.Internal
    public static void removed(ServerPlayer player) {
        closeAside(player, Reason.INVALID);
        LEFT.remove(player);
    }

    /**
     * A click from the player's client, on the server thread before vanilla handles it:
     * the same click on the menu in front when it was aimed at the menu just switched
     * away from (see "Clicks in flight" in the class doc), otherwise {@code click} itself.
     */
    @ApiStatus.Internal
    public static ServerboundContainerClickPacket clickArrived(ServerPlayer player, ServerboundContainerClickPacket click) {
        Left left = LEFT.get(player);
        if (left == null) return click;
        AbstractContainerMenu front = player.containerMenu;
        if (left.to != front || click.containerId() != left.from.containerId) {
            // The client has caught up with the switch, or the menu has moved on since.
            LEFT.remove(player);
            return click;
        }
        OptionalInt slot = sameSlot(left.from, front, click.slotNum(), click.containerInput());
        if (slot.isEmpty()) return click;   // vanilla drops it: its container id is stale
        // The client predicted this click on the old menu: a state id that never matches
        // makes vanilla send the new menu whole afterwards, and the old menu's slot
        // predictions mean nothing here.
        return new ServerboundContainerClickPacket(front.containerId, -1, (short) slot.getAsInt(),
                click.buttonNum(), click.containerInput(), Int2ObjectMaps.emptyMap(), click.carriedItem());
    }

    /** The container id the player's next menu must not take while one is set aside, or -1. */
    @ApiStatus.Internal
    public static int heldId(ServerPlayer player) {
        Aside a = ASIDE.get(player);
        return a == null ? -1 : a.menu.containerId;
    }
}
