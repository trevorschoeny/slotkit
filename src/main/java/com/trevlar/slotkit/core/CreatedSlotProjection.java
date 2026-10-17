package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.CreatedSlots;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Projects {@code InventoryMenu}-bound slots onto foreign menus (chests, furnaces,
 * donkeys, any {@link AbstractContainerMenu}), so a slot a consumer registered for the
 * player inventory also appears, takes clicks and syncs on a container screen. This
 * extends inventory-screen parity from "every screen whose menu already carries the
 * slot" to "every screen that shows the player inventory."
 *
 * <h3>Why the library owns a registry here</h3>
 *
 * {@link CreatedSlots} has no registry on purpose: for the player's own
 * {@code InventoryMenu} the consumer owns the {@code <init>} mixin, so the library
 * injects nothing. Foreign menus have no such per-menu hook a consumer could own.
 * {@code AbstractContainerMenu}'s constructor doesn't carry the player, and mixing
 * into every menu class (modded ones included) isn't generic. To cover them, the
 * library has to own a player-lifecycle hook ({@code ServerPlayer.initMenu} on the
 * server, {@code ScreenEvents.AFTER_INIT} on the client) and so a registry of what to
 * project. It is kept as narrow as possible: a registry of slot factories the library
 * only invokes (data plus a deterministic build function), never a behaviour callback
 * that decides how interaction works. The slots it builds are ordinary
 * {@link CreatedSlot}s, so rendering, clicks into the storage, creative and sync all
 * take the same vanilla-slot paths the {@code InventoryMenu} slot uses. The library
 * adds slots to a foreign menu's list and invents no behaviour.
 *
 * <h3>Sync safety</h3>
 *
 * The slots must appear on the server menu and the client menu in the same set and
 * order, before the menu's first content sync, or {@code remoteSlots} and
 * {@code broadcastChanges} desync (items flicker or duplicate). So both hooks call
 * this one {@link #appendProjectedSlots} against the same registry:
 * <ul>
 *   <li>server: {@code ServerPlayer.initMenu} HEAD, which runs after
 *       {@code createMenu} but before {@code initMenu} sends the initial
 *       {@code ContainerSetContent}, so that packet already includes the slots;</li>
 *   <li>client: {@code ScreenEvents.AFTER_INIT}, which fires synchronously inside
 *       {@code handleOpenScreen} before the content packet is processed, so the
 *       client menu has the slots when it arrives.</li>
 * </ul>
 * Both sides walk the same sources in the same order, and each source's factory
 * builds the same slot (same player-attached {@code Storage} size, same layout), so
 * the appended blocks are byte-identical. Keep the registry order, the reveal
 * predicate and the storage size the same on both sides.
 *
 * <h3>What projection does not do</h3>
 *
 * Projection only puts the slots on the foreign menu. The mod's own panel draws them
 * (each slot is a {@code SlotElement} in it, on whatever screens its adapter
 * accepts). Shift-click into them is the handler's own {@code quickMoveStack}, and creative placement is the creative
 * set-slot bridge, which now works on any menu. A consumer that wants pockets on a
 * chest registers a projection source here and also widens its panel adapter's
 * targeting to that screen.
 */
public final class CreatedSlotProjection {

    private CreatedSlotProjection() {}

    /**
     * Appends a slot onto a matching foreign menu. Called on both sides for each
     * opened menu the {@link #register registered} predicate accepts, so it must be
     * deterministic: the same slot set, order and storage size on either side.
     * Usually just {@code CreatedSlots.onto(menu, player)....register()} with the
     * parameters the consumer uses for the player inventory.
     */
    @FunctionalInterface
    public interface ProjectionFactory {
        void project(AbstractContainerMenu menu, Player player);
    }

    private record Source(Predicate<AbstractContainerMenu> appliesTo, ProjectionFactory factory) {}

    // Strong references: consumers register once at init and hold nothing.
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    // Per-menu-instance guard so a menu is never projected twice, should some path
    // re-enter a hook for the same instance. Weak so a closed menu is collected.
    // Server and client menus are separate instances, each guarded on its own side.
    private static final Set<AbstractContainerMenu> PROJECTED =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Registers a projection source: project the slot built by {@code factory}
     * onto every opened menu {@code appliesTo} accepts. Call once at mod init.
     *
     * <p>Both {@code appliesTo} and {@code factory} must give the same result on the
     * logical server and the client: a predicate over the menu's class, type or
     * synced content, and a factory over a player-attached {@code Storage}. See the
     * sync-safety note in the class doc. This is opt-in per slot, so a slot that
     * should stay inventory-only is just not registered here.
     */
    public static void register(Predicate<AbstractContainerMenu> appliesTo,
                                ProjectionFactory factory) {
        SOURCES.add(new Source(appliesTo, factory));
    }

    /**
     * Appends every matching projected slot onto {@code menu}. Called from the
     * server hook ({@code initMenu} HEAD) and the client hook ({@code AFTER_INIT}),
     * at most once per menu instance. Does nothing when no source matches, so the
     * player's own {@code InventoryMenu}, never registered here, is untouched.
     */
    public static void appendProjectedSlots(AbstractContainerMenu menu, Player player) {
        if (menu == null || player == null || SOURCES.isEmpty()) return;
        synchronized (PROJECTED) {
            if (!PROJECTED.add(menu)) return; // this instance already projected
        }
        for (Source source : SOURCES) {
            if (source.appliesTo.test(menu)) {
                source.factory.project(menu, player);
            }
        }
    }
}
