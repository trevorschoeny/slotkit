package com.trevlar.slotkit.window;

import com.trevlar.slotkit.core.Slots;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Who is clicking, for the duration of one menu click transaction.
 *
 * <p>Vanilla's deep seams have no player in scope: {@code moveItemStackTo} (the
 * destination side of a shift-click) takes a stack and a slot range and nothing
 * else. A decision made there that depends on the player (a per-player lock, a
 * capability check) needs the player captured at the click boundary. The library
 * captures it in {@code AbstractContainerMenu.clicked} and clears it in a
 * {@code finally}, so it never outlives the click.
 *
 * <p>A mod may also move items on the server with no click at all, calling a
 * menu's {@code quickMoveStack(player, index)} directly (Hotbar Pockets restocking
 * from a pocket). {@code quickMoveStack} is abstract and every menu overrides it,
 * so it cannot be wrapped once; every one of those moves goes through
 * {@code moveItemStackTo}, which is concrete and is the seam that lacks a player.
 * The library wraps it: when no click set the player, the menu's own player is
 * acting ({@link #ownerOf}). Automation (a hopper) never touches a menu, and stays
 * {@code null}.
 *
 * <p>In 5.0.0 this was MenuKit: Containers' {@code GatingContext} thread-local.
 */
@ApiStatus.Internal
public final class ActingPlayer {

    private ActingPlayer() {}

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    /**
     * Makes {@code player} the acting player and returns whoever was acting before,
     * for {@link #exit} to put back. Nesting-safe: a click or a move inside another
     * restores the outer one's player when it ends, instead of clearing it.
     */
    public static @Nullable Player enter(@Nullable Player player) {
        Player outer = CURRENT.get();
        set(player);
        return outer;
    }

    /** Restores what {@link #enter} returned. Always called in a {@code finally}. */
    public static void exit(@Nullable Player outer) {
        set(outer);
    }

    /** Sets the acting player for a click transaction. Prefer {@link #enter}, which nests. */
    public static void set(@Nullable Player player) {
        if (player != null) CURRENT.set(player);
        else CURRENT.remove();
    }

    /** Always called in a {@code finally}, never left to a return path. */
    public static void clear() {
        CURRENT.remove();
    }

    /** The player acting on the current thread, or {@code null} outside a click or a menu move. */
    public static @Nullable Player current() {
        return CURRENT.get();
    }

    /**
     * The player a menu belongs to: the owner of the player inventory it shows.
     * A menu is opened by and for one player, and every player-facing menu shows
     * that player's inventory, so a move on it with no click behind it is that
     * player's. {@code null} for a menu that shows no player inventory.
     *
     * <p>Scans the slot list on every move. Cache it per menu if a profile ever
     * shows the cost.
     */
    public static @Nullable Player ownerOf(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (Slots.target(slot).container instanceof Inventory inventory) return inventory.player;
        }
        return null;
    }
}
