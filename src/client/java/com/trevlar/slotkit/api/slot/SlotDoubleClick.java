package com.trevlar.slotkit.api.slot;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A plain left double-click on a slot, taken for a mod's own action (open the item, say).
 * The click that would be vanilla's "gather all" goes to the mod instead, and the item
 * never seems to move. Client side; register from the client initializer.
 *
 * <pre>{@code
 * SlotDoubleClick.register("mymod:open",
 *         (slot, stack) -> MyMod.opens(stack),
 *         pair -> ClientPlayNetworking.send(new OpenC2S(pair.menuIndex())));
 * }</pre>
 *
 * <p>The gesture: a left click with no shift, then a second within one second with the
 * cursor on the same pixel. A third click starts over, and moving the cursor ends the
 * window. Only the real mouse counts, so a sort mod's clicks never pair.
 *
 * <p>The first click stays vanilla's: it lifts the stack onto the cursor. It starts a pair
 * when the cursor was empty and a registration's {@link Starts} holds; the first registration
 * that holds owns the pair. Until the pair ends, the lifted stack is drawn back in its slot.
 * The second click is taken when the stack is back on the cursor with its slot empty
 * ({@link Kind#PUT_BACK}: SlotKit puts it back on the client first), or when it never left
 * and the cursor is empty ({@link Kind#IN_PLACE}: its slot is locked). Anything else stays
 * vanilla's. The server side is the mod's: it sends its own packet, and its server puts the
 * stack back too, whose sync corrects the client if it refuses.
 *
 * <p>On creative's inventory tab, {@link Starts} and {@link Pair#slot} see the real slot on
 * {@code player.inventoryMenu}, and {@link Pair#menuIndex} is its index there, the menu the
 * server has open. Creative's item tabs never pair. A player inventory slot pairs across a
 * screen opened in place of the first one between the clicks; any other slot pairs only on
 * the same screen.
 *
 * Since 2.2.0.
 */
public final class SlotDoubleClick {

    private SlotDoubleClick() {}

    /** Whether a first click on {@code slot}'s {@code stack}, with the cursor empty, may start a pair. Asked at the first click. */
    @FunctionalInterface
    public interface Starts {
        boolean test(Slot slot, ItemStack stack);
    }

    public enum Kind {
        /** The first click lifted the stack; SlotKit has put it back in its slot on the client. */
        PUT_BACK,
        /** The stack never left its slot (the slot is locked) and the cursor is empty. */
        IN_PLACE
    }

    /** A double-click taken: the slot (the real one), its index on the menu the server has open, and the stack. */
    public record Pair(AbstractContainerScreen<?> screen, Slot slot, int menuIndex, ItemStack stack, Kind kind) {}

    private record Registration(String id, Starts starts, Consumer<Pair> onPair) {}

    private static final List<Registration> REGISTRATIONS = new ArrayList<>();

    // The window: one second, as long as the cursor stays put.
    private static final long WINDOW_MS = 1000;

    // The pending first click: who owns it (null: none), its place, when, the screen, where
    // the mouse was, the stack it lifted and where its slot sits, where that stack is drawn.
    // The place is 1000 + the inventory slot for a player inventory slot, so the second click
    // still finds it on a screen opened in place of the first; else the real menu index.
    private static @Nullable Registration owner;
    private static int place;
    private static long firstAt;
    private static @Nullable Screen firstScreen;
    private static double firstX, firstY;
    private static ItemStack lifted = ItemStack.EMPTY;
    private static int heldX, heldY;

    /** Registers a double-click action. {@code id} is unique per mod, as {@code "mymod:open"}. */
    public static void register(String id, Starts starts, Consumer<Pair> onPair) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(onPair, "onPair");
        for (Registration r : REGISTRATIONS) {
            if (r.id.equals(id)) throw new IllegalArgumentException("double-click " + id + " is already registered");
        }
        REGISTRATIONS.add(new Registration(id, starts, onPair));
    }

    /** Ends any pending pair (back in the world). */
    @ApiStatus.Internal
    public static void reset() {
        owner = null;
        firstScreen = null;
        lifted = ItemStack.EMPTY;
    }

    /**
     * Where the stack on the cursor is drawn: its slot's place while a pair is pending (the
     * window open, the cursor still), else {@code null}, at the cursor.
     */
    @ApiStatus.Internal
    public static int @Nullable [] heldAt(int mouseX, int mouseY) {
        if (owner == null) return null;
        // The screen draws at whole pixels; the click came in at fractions of one.
        if (Util.getMillis() - firstAt >= WINDOW_MS
                || mouseX != (int) Math.floor(firstX) || mouseY != (int) Math.floor(firstY)) {
            reset();
            return null;
        }
        return new int[] {heldX, heldY};
    }

    /** A real click on a container screen, before the screen sees it. True when it was taken. */
    @ApiStatus.Internal
    public static boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event, Player player) {
        if (REGISTRATIONS.isEmpty()) return false;
        Slot shown = ((AbstractContainerScreenAccessor) screen).sk$getHoveredSlot();
        boolean creative = screen instanceof CreativeModeInventoryScreen;
        if (shown == null || creative && !((CreativeModeInventoryScreen) screen).isInventoryOpen()
                || event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.hasShiftDown()) {
            reset();
            return false;
        }
        Slot real = creative ? realSlot(player, shown) : shown;
        if (real == null) {
            reset();
            return false;
        }
        int at = placeOf(real);
        long now = Util.getMillis();
        // The same pixel, not the same fraction of one: a screen opened in place of another
        // puts the cursor back where it was, to the pixel.
        boolean still = owner != null && now - firstAt < WINDOW_MS
                && Math.floor(event.x()) == Math.floor(firstX) && Math.floor(event.y()) == Math.floor(firstY);
        boolean remapped = false;
        if (still && screen != firstScreen && place >= 1000 && !creative) {
            // A screen opened in place of the first one may sit higher or lower: the second
            // click goes to the first one's inventory slot, wherever it now sits.
            Slot first = inventorySlot(screen, place - 1000);
            if (first != null) {
                shown = real = first;
                at = place;
                remapped = true;
            }
        }
        boolean second = still && at == place && (screen == firstScreen || remapped);
        if (!second) {
            first(screen, event, shown, real);
            return false;
        }

        // The second click: a third starts over.
        Registration by = Objects.requireNonNull(owner);
        ItemStack want = lifted;
        reset();
        ItemStack carried = screen.getMenu().getCarried();
        int menuIndex = real.index;
        if (!real.hasItem() && !carried.isEmpty() && ItemStack.matches(carried, want)) {
            // Back in its slot now, on this side, rather than once the server says so: the
            // lift lasts only as long as the player's own double-click.
            real.set(carried);
            screen.getMenu().setCarried(ItemStack.EMPTY);
            // Creative tells the server each slot's contents itself, as its own click does.
            if (creative) {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
            }
            by.onPair.accept(new Pair(screen, real, menuIndex, real.getItem(), Kind.PUT_BACK));
            return true;
        }
        if (real.hasItem() && carried.isEmpty()) {
            by.onPair.accept(new Pair(screen, real, menuIndex, real.getItem(), Kind.IN_PLACE));
            return true;
        }
        return false;
    }

    // A first click: pending when the cursor is empty and a registration takes the stack.
    private static void first(AbstractContainerScreen<?> screen, MouseButtonEvent event, Slot shown, Slot real) {
        reset();
        ItemStack stack = real.getItem();
        if (stack.isEmpty() || !screen.getMenu().getCarried().isEmpty()) return;
        for (Registration r : REGISTRATIONS) {
            if (!r.starts.test(real, stack)) continue;
            owner = r;
            place = placeOf(real);
            firstAt = Util.getMillis();
            firstScreen = screen;
            firstX = event.x();
            firstY = event.y();
            lifted = stack.copy();
            AbstractContainerScreenAccessor edge = (AbstractContainerScreenAccessor) screen;
            heldX = edge.sk$getLeftPos() + shown.x;
            heldY = edge.sk$getTopPos() + shown.y;
            return;
        }
    }

    private static int placeOf(Slot real) {
        return real.container instanceof Inventory ? 1000 + real.getContainerSlot() : real.index;
    }

    // The slot on player.inventoryMenu that creative shows as shown, or null.
    private static @Nullable Slot realSlot(Player player, Slot shown) {
        for (Slot real : player.inventoryMenu.slots) {
            if (real.container == shown.container && real.getContainerSlot() == shown.getContainerSlot()) return real;
        }
        return null;
    }

    // The slot on screen that shows the player's inventory slot index, or null.
    private static @Nullable Slot inventorySlot(AbstractContainerScreen<?> screen, int index) {
        for (Slot s : screen.getMenu().slots) {
            if (s.container instanceof Inventory && s.getContainerSlot() == index) return s;
        }
        return null;
    }
}
