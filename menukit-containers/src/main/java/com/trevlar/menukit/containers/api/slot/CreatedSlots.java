package com.trevlar.menukit.containers.api.slot;

import com.trevlar.menukit.containers.api.storage.KeyedStorage;
import com.trevlar.menukit.containers.api.menu.MenuPanel;
import com.trevlar.menukit.containers.api.storage.StorageAttachment;
import com.trevlar.menukit.containers.api.storage.StorageContainerAdapter;
import com.trevlar.menukit.api.window.Address;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.Storage;

import com.trevlar.menukit.api.window.PersistentContainerKey;

import com.trevlar.menukit.api.slot.SlotGroupId;
import com.trevlar.menukit.containers.mixin.AbstractContainerMenuInvoker;
import com.trevlar.menukit.api.window.SlotNames;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;


import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Adds real, server-synced, persistent slots to a vanilla menu. This is the library's
 * half of slots a consumer composes itself, such as pockets or extra equipment slots.
 *
 * <h3>Who calls this</h3>
 *
 * The consumer calls {@code CreatedSlots} from their own mixin, injected at
 * {@code TAIL} on the vanilla menu's constructor ({@code InventoryMenu.<init>}, for
 * example). This path ships no such mixin and no slot registry, because owning a
 * vanilla injection point or a hook consumers register into would make the library a
 * platform. This class is a stateless helper the consumer's mixin calls directly; it
 * registers and applies nothing on its own.
 *
 * <p>The vanilla constructor runs again every time the menu is rebuilt (login,
 * respawn, dimension change, on both sides), so registering from there brings the
 * slots back each time, with no separate lifecycle hook.
 *
 * <h3>What you get</h3>
 *
 * The slots are {@link CreatedSlot}s over a standalone {@link MenuPanel} and
 * {@link SlotGroup}, neither of which needs a {@code CustomContainerMenu}. That gives
 * the usual inertness rules: when the panel is hidden the slots are invisible and
 * inert, {@code getItem} returns EMPTY, they refuse placement and pickup, and vanilla
 * skips them for rendering and hover. The reveal predicate below shows and hides the
 * whole group at runtime.
 *
 * <h3>Reveal on hover, and which side sees what</h3>
 *
 * {@code revealWhen(BooleanSupplier)} drives the panel's visibility from a
 * client-side predicate ("is the matching hotbar slot hovered", say). The helper
 * applies it per side. On the server the panel is always visible, so {@code getItem}
 * returns the real content and vanilla's {@code broadcastChanges} keeps syncing it. On
 * the client the predicate controls rendering and input. That is why content
 * survives while the panel is hidden: hiding is a client rendering choice, not server
 * state. The predicate must be client-safe. It only ever runs on the client, but it
 * should live in a client-only class so a dedicated server never loads client types.
 *
 * <h3>Persistence and slot state</h3>
 *
 * Item content persists when the slot is backed by a player- or block-attached
 * {@link Storage} (see {@link StorageAttachment}), and vanilla's slot protocol syncs
 * it at no extra cost. For per-slot state (lock flags and the like), back the slot
 * with a {@link KeyedStorage} that returns a stable {@link PersistentContainerKey};
 * the {@code SlotStateChannel} API then works on these slots.
 *
 * <h3>Creative and survival</h3>
 *
 * The slots round-trip in both game modes with no consumer code. Survival edits reach
 * the slot through vanilla's container-click path. A creative edit that vanilla's
 * {@code handleSetCreativeModeSlot} would silently discard (the index is past
 * vanilla's {@code [1,45]} range) is routed to the slot's backing {@code Storage} by
 * the library's {@code CreativeSetSlotMixin}. Direct click-to-place works in both
 * modes, and per-slot state comes along with it.
 *
 * <h3>One menu only (use {@link ContainerPanel} for parity)</h3>
 *
 * {@code onto} adds slots to the one menu the consumer's mixin targets (such as
 * {@code InventoryMenu}). It does not project onto other menus: open a chest and the
 * slots are not there. That is on purpose, since {@code onto} is the narrow tool for
 * one menu whose mixin the consumer owns. For container parity (the same slots on
 * every container screen, with the foreign-menu projection and creative wiring, from
 * one call and no mixin) use {@link ContainerPanel#define}. It builds on this and
 * adds the projection and the panel around the slots. Use {@code onto} only when you
 * want slots on one menu you mix into.
 *
 * <h3>Known limitation</h3>
 *
 * Shift-click into these slots does not work by default, because vanilla's
 * {@code quickMoveStack} knows nothing about them. Direct click-to-place works in both
 * modes. Full shift-click routing is up to the consumer (who may override
 * {@code quickMoveStack} in their own mixin), or left for later.
 *
 * <h3>Recipe</h3>
 *
 * <pre>{@code
 * // Mod init, declare the persistent storage once:
 * public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
 *     StorageAttachment.playerAttached("inventory-plus", "pockets", 3);
 *
 * // Consumer mixin, @Inject(method = "<init>", at = @At("TAIL")) on InventoryMenu:
 * Storage storage = POCKETS.bind(player);              // or a KeyedStorage for metadata
 * CreatedSlots.RegisteredSlots pockets = CreatedSlots.onto((AbstractContainerMenu)(Object) this, player)
 *     .panel("inventory-plus:pockets")
 *     .group("pockets")
 *     .category(SlotGroupCategory.PLAYER_INVENTORY)       // required: what the group is to other mods
 *     .storage(storage)
 *     .layout(originX, originY, 3)                      // screen-relative origin + columns
 *     .revealWhen(() -> PocketHoverState.isRevealed())  // client-side predicate
 *     .register();
 *
 * // Client init (ONCE, NOT per menu construction), wire the PRESENTATION half.
 * // register() above added the synced slot data; this draws the slots:
 * ClientSlots.renderGroup("inventory-plus:pockets", "pockets", 3, 3,
 *         OutsideRegion.RIGHT_ALIGN_TOP, ScreenPanelAdapter.DEFAULT_PADDING,
 *         ScreenMatcher.all());
 * }</pre>
 */
public final class CreatedSlots {

    /** Vanilla slot pitch in pixels (16px slot + 2px frame). */
    public static final int SLOT_PITCH = 18;

    /**
     * Off-screen position for a registered slot's vanilla {@code Slot.x/y}. A new slot
     * starts here and returns here at the end of any frame in which no panel showed it
     * ({@link SlotElementRegistry#parkUnpresented}); the panel that shows it writes its
     * real {@code x/y} each frame. Parked here, vanilla's slot pass and hover checks
     * do nothing with it. The creative wrapper is parked at the same coordinate
     * ({@code MKCCreativeSlotParkMixin}).
     */
    static final int OFFSCREEN = -10000;

    private CreatedSlots() {}

    /**
     * The {@link SlotGroupId} of a group registered through {@link #onto}, which is
     * what {@code SlotGroupPanelAdapter.onGroup(...)} anchors to. This path registers
     * under the declared panel id as is, unlike the container-parity path
     * ({@code ContainerPanel.groupId}), which derives one.
     */
    public static SlotGroupId.Created groupId(String panelId, String groupId) {
        return SlotGroupId.created(panelId, groupId);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Slot";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * What {@code register()} created: the {@link MenuPanel} and {@link SlotGroup},
     * plus the slot-index range appended to the menu, for consumers that walk the
     * slots directly. Empty (no slots, {@code flatStart == flatEnd}) on a client
     * whose server does not run Containers.
     *
     * <p>{@code register()} adds only the synced slot data. To make the slots draw and
     * take input, show them from the client: a panel of {@code SlotElement}s, or
     * {@code ClientSlots.renderGroup(...)} once at client init with the same
     * {@code panelId} and {@code groupId}.
     */
    public record RegisteredSlots(MenuPanel panel, SlotGroup group, int flatStart, int flatEnd,
                          List<CreatedSlot> slots) {}

    /**
     * Starts adding slots to {@code menu}, owned by {@code player}. Call from the
     * consumer's {@code @Inject(at = TAIL)} on the vanilla menu's constructor, where
     * both the menu ({@code this}) and the owning player are in scope.
     */
    public static Builder onto(AbstractContainerMenu menu, Player player) {
        return new Builder(menu, player);
    }

    /** Configures one group of slots. Ends with {@code register()}. */
    public static final class Builder {
        private final AbstractContainerMenu menu;
        private final Player player;

        private String panelId = "menukit:slot";
        private String groupId = "slot";
        private SlotGroupCategory category;                   // required: what the group is
        private Storage storage;                              // required
        private int columns = 9;
        private int originX = 0;
        private int originY = 0;
        private BooleanSupplier revealWhen = null;            // null => always client-visible
        private String displayLabel = null;                  // null => capitalized groupId

        Builder(AbstractContainerMenu menu, Player player) {
            this.menu = menu;
            this.player = player;
        }

        /** Panel id, unique per group. Used for inertness and render anchoring. */
        public Builder panel(String id) { this.panelId = id; return this; }

        /** Slot-group id within the panel. */
        public Builder group(String id) { this.groupId = id; return this; }

        /**
         * What the group is, in MenuKit core's registry terms. Required; see
         * {@link SlotSpec#at(String, SlotGroupCategory)} for why there is no default.
         * The group is listed under it in
         * {@code SlotGroupCategories.of(menu)} on every menu it sits on.
         */
        public Builder category(SlotGroupCategory category) { this.category = category; return this; }

        /**
         * Where items live. Required. Use storage bound from a {@link StorageAttachment}
         * to persist content, or a {@link KeyedStorage} (which declares its own
         * {@link PersistentContainerKey}) to persist per-slot state as well.
         */
        public Builder storage(Storage storage) { this.storage = storage; return this; }

        /**
         * Slot layout: an origin relative to the screen (added to its {@code leftPos}
         * and {@code topPos} at render time) and a column count. Slots fill left to
         * right, top to bottom on an 18px grid.
         */
        public Builder layout(int originX, int originY, int columns) {
            this.originX = originX;
            this.originY = originY;
            this.columns = Math.max(1, columns);
            return this;
        }

        /**
         * Client-side reveal predicate. The panel, and so the whole group, is visible and
         * usable on the client only while this returns true; the server keeps the
         * slots syncing either way (see the class doc). Must be client-safe. If unset,
         * the group is always visible on the client.
         */
        public Builder revealWhen(BooleanSupplier clientReveal) {
            this.revealWhen = clientReveal;
            return this;
        }

        /**
         * Display label for these slots' names. Each slot is named
         * {@code "<label> <ordinal>"}, or just {@code "<label>"} for a single slot,
         * registered against the slot's address so {@code WindowSignals.hoveredName()}
         * and a consumer's {@code SlotNames} lookup return it. Defaults to the
         * capitalized {@code groupId}. A consumer can still set a specific name for one
         * slot with {@code SlotNames.override(address, name)} afterwards.
         */
        public Builder label(String displayLabel) {
            this.displayLabel = displayLabel;
            return this;
        }

        /**
         * Builds the standalone {@link MenuPanel} and {@link SlotGroup}, creates the
         * {@link CreatedSlot}s over a {@link StorageContainerAdapter}, and appends them
         * to the menu through vanilla's {@code addSlot}, which keeps the sync-tracking
         * lists consistent. Returns the result.
         */
        public RegisteredSlots register() {
            if (storage == null) {
                throw new IllegalStateException(
                        "CreatedSlots: storage() is required before register()");
            }
            if (category == null) {
                throw new IllegalStateException(
                        "CreatedSlots '" + panelId + "/" + groupId + "': category(SlotGroupCategory) is "
                        + "required before register(), say what the group is (PLAYER_INVENTORY, or "
                        + "your own category) so other mods can find it and know what it is.");
            }

            // Listed with no menu open (SlotGroups), from the first menu built with
            // it on. Idempotent across menu constructions. A mod that wants the group
            // listed from the title screen declares it at init as well.
            com.trevlar.menukit.api.slot.SlotGroups.declareDerived(groupId(panelId, groupId), category);

            // 1. A standalone SlotGroup with storage and layout only. Behaviour
            //    (gating, quick-move, binding, mending) resolves from the engine by
            //    each slot's address (vanilla by default), set by the consumer with
            //    Window.slot(address).set(...).
            SlotGroup group = new SlotGroup(
                    groupId, category, storage,
                    /*shiftClickPriority*/ 100, columns, /*rowGapAfter*/ -1, /*rowGapSize*/ 0);

            // 2. The panel as the menu knows it: its id, and whether its slots are
            //    shown. The server always shows it, so getItem returns real content and
            //    broadcastChanges syncs it; the client follows the consumer's predicate.
            //    The predicate is only evaluated when isClientSide() is true, so a
            //    client-only predicate never runs or loads on the server.
            final BooleanSupplier reveal = this.revealWhen;
            MenuPanel panel = new MenuPanel(panelId, () ->
                    !player.level().isClientSide()    // server side: always shown
                    || reveal == null                 // no predicate: always shown
                    || reveal.getAsBoolean());         // client side: follow the predicate

            // A client whose server does not run Containers builds no created
            // slots, so its menus keep the server's slot count instead of desyncing.
            if (!com.trevlar.menukit.containers.network.Presence.buildsCreatedSlots(player.level().isClientSide())) {
                int at = menu.slots.size();
                return new RegisteredSlots(panel, group, at, at, List.of());
            }

            // 3. Build and append the slots.
            //
            // A slot's position is presentation that changes, not fixed structure. It
            // starts parked off-screen (vanilla draws and hit-tests nothing there),
            // and the panel that shows it (its SlotElement) writes its real position
            // into vanilla's Slot.x/y every frame, before vanilla's slot pass. The
            // originX/originY/columns layout is the elements' layout
            // (ClientSlots.renderGroup), not the slots'.
            StorageContainerAdapter adapter = new StorageContainerAdapter(storage);
            AbstractContainerMenuInvoker inv = (AbstractContainerMenuInvoker) menu;

            int flatStart = menu.slots.size();
            List<CreatedSlot> mkSlots = new ArrayList<>();
            for (int local = 0; local < storage.size(); local++) {
                CreatedSlot slot = new CreatedSlot(
                        adapter, local, OFFSCREEN, OFFSCREEN, group, panel, groupId, local);
                inv.mk$addSlot(slot);
                mkSlots.add(slot);
            }
            int flatEnd = menu.slots.size();
            group.setFlatIndexRange(flatStart, flatEnd);

            // Display names, client only: the server never reads them, and this keeps
            // MenuKit's display types off the server path. Keyed by the same address
            // the window uses to resolve a created slot, so WindowSignals.hoveredName()
            // matches. MenuKit never sees CreatedSlot; CreatedSlotAdapter maps
            // (panel, group, index) to an Address.
            if (player.level().isClientSide()) {
                String label = displayLabel != null ? displayLabel : capitalize(groupId);
                int size = storage.size();
                for (int local = 0; local < size; local++) {
                    SlotNames.override(
                            Address.createdSlot(SlotGroupId.created(panelId, groupId), local),
                            size <= 1 ? label : label + " " + (local + 1));
                }
            }

            return new RegisteredSlots(panel, group, flatStart, flatEnd, List.copyOf(mkSlots));
        }
    }
}
