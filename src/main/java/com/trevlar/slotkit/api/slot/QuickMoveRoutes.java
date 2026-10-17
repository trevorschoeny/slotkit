package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.group.SlotGroupId;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where a shift-click goes, on any menu, when a mod knows better than the menu.
 * SlotKit takes over shift-click at one place for every menu (vanilla's and modded):
 * it asks the registered routes, in registration order, for the clicked slot, and the
 * first answer is the order the stack tries destinations in.
 *
 * <pre>{@code
 * // Common init (both sides: the client runs the route too, to predict the move).
 * QuickMoveRoutes.register((menu, player, source) -> {
 *     if (!sessionOpen(player)) return null;                 // no opinion
 *     return List.of(Dest.group(OPENED.id()), Dest.VANILLA); // the opened item first
 * });
 * }</pre>
 *
 * <h3>How a move runs</h3>
 * For each destination in order, the stack merges into partial stacks, then fills
 * empty slots, one slot at a time through vanilla's {@code moveItemStackTo}, so each
 * slot's gate, its vetoes, {@code SHIFT_CLICK_IN}, inertness (a slot past its group's
 * active count is skipped) and the source's own lock judge it as they judge any
 * shift-click. The source's own group is never a destination. {@link Dest#VANILLA}
 * hands what is left to the menu's own {@code quickMoveStack} and may only come last;
 * a route that ends without it stops there, and what doesn't fit stays put.
 *
 * <h3>With no route</h3>
 * <ul>
 *   <li>Out of a parity slot ({@link ParityGroup}), on any menu: to the player.</li>
 *   <li>Into parity slots: groups that declared {@code shiftClickIn(true)} are tried
 *       first on the player's inventory menu, and on every other menu after the
 *       menu's own routing moved nothing (a chest takes a stack before an elytra
 *       slot does).</li>
 *   <li>Everything else: the menu's own routing.</li>
 * </ul>
 * Whenever the menu's own {@code quickMoveStack} runs, its ranges stop short of the
 * parity slots SlotKit appended, so vanilla never fills one by accident. Slots a menu
 * built itself, created ones included, are its own business.
 *
 * <p>A route must answer from state both sides share (the mod's own synced session,
 * active counts). When the two sides disagree for a tick, the server's move stands
 * and vanilla's resync puts the client right.
 */
public final class QuickMoveRoutes {

    private QuickMoveRoutes() {}

    /** Answers where a shift-click from {@code source} goes, or {@code null} for no opinion. */
    @FunctionalInterface
    public interface Route {
        @Nullable List<Dest> route(AbstractContainerMenu menu, Player player, Slot source);
    }

    /** One destination in a route. */
    public sealed interface Dest {
        /** The player's main inventory and hotbar, hotbar end first, as vanilla moves a container's stack to the player. */
        Dest PLAYER = new ToPlayer();
        /** The rest to the menu's own routing (parity slots excluded). Only last. */
        Dest VANILLA = new ToVanilla();

        /** A group on this menu: a created group, or a vanilla one by {@code SlotGroupId.category(...)}. Skipped when absent. */
        static Dest group(SlotGroupId group) { return new Group(Objects.requireNonNull(group, "group")); }

        /** Whatever group holds menu slot {@code menuSlot} on this menu: "back to where the item came from". */
        static Dest groupOf(int menuSlot) { return new GroupOf(menuSlot); }

        record Group(SlotGroupId id) implements Dest {}
        record GroupOf(int menuSlot) implements Dest {}
        record ToPlayer() implements Dest {}
        record ToVanilla() implements Dest {}
    }

    private static final List<Route> ROUTES = new CopyOnWriteArrayList<>();

    /** Adds a route. Call at mod init, on both sides. */
    public static void register(Route route) {
        com.trevlar.menukit.api.Declarations.requireOpen("QuickMoveRoutes.register");
        ROUTES.add(Objects.requireNonNull(route, "route"));
    }

    /** The first route's answer for {@code source}, or {@code null}. Library use. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static @Nullable List<Dest> routeFor(AbstractContainerMenu menu, Player player, Slot source) {
        for (Route r : ROUTES) {
            List<Dest> dests = r.route(menu, player, source);
            if (dests != null) {
                // VANILLA hands over everything left, so nothing after it could run.
                int v = dests.indexOf(Dest.VANILLA);
                return v >= 0 ? dests.subList(0, v + 1) : dests;
            }
        }
        return null;
    }
}
