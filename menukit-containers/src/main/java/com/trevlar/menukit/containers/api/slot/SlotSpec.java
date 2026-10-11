package com.trevlar.menukit.containers.api.slot;

import org.jetbrains.annotations.ApiStatus;

import com.trevlar.menukit.containers.core.CreatedSlotProjection;
import com.trevlar.menukit.containers.api.storage.StorageAttachment;
import com.trevlar.menukit.api.window.GatingContext;
import com.trevlar.menukit.api.window.SlotGate;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.Storage;

import com.trevlar.menukit.api.window.TriBool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A side-neutral description of one group of registered slots that a
 * {@link ContainerPanel} adds to every container the player opens. It is the recipe
 * half of container parity.
 *
 * <h3>Why a recipe and not a built slot</h3>
 *
 * A real {@link CreatedSlot} belongs to one menu. The player's own
 * {@code InventoryMenu}, every chest or furnace they open, and the creative item
 * picker each carry their own slot instance for the same logical slot. To put a slot
 * on all of them from one registration, the panel can't hold a slot. It holds the
 * instructions to build one, applied to each menu and bound to that menu's player.
 * That is this class: plain data plus a {@code Player -> Storage} factory, evaluated
 * the same way on the logical server and the client so the appended slots are
 * byte-identical (see {@link CreatedSlotProjection} on sync safety).
 *
 * <p>It holds the same settings as the per-call parameters on
 * {@link CreatedSlots.Builder} (storage, layout, the client reveal predicate), but as
 * reusable data instead of calls against one menu. {@link ContainerPanel} turns each
 * {@code SlotSpec} into the real slots (every menu, both sides) and the
 * {@link SlotElement}s that show them (client).
 *
 * <h3>Behaviour methods are shorthand for setting it by address</h3>
 *
 * Gating, shift-click, collect, drag-fill, binding and mending are set in the window
 * engine by the slot's {@link com.trevlar.menukit.api.window.Address}. That is where
 * slot behaviour lives, the same for a vanilla slot and a created slot. The methods
 * here ({@link #gate}, {@link #accepts}, {@link #binding}, {@link #mending},
 * {@link #shiftClickOut}, {@link #shiftClickIn}, {@link #collect}, {@link #dragFill})
 * don't add a second place to store behaviour. They record what the consumer asked
 * for, and {@link ContainerPanel.Builder#register()} sets it in the engine by address
 * for every index in the group, exactly
 * {@code Window.slot(ContainerPanel.address(panelId, groupId, i)).set(KEY, value)},
 * the call a consumer could make by hand. The gain is convenience: behaviour is
 * declared next to the slot group, with no separate step to forget, and the consumer
 * never computes an address or writes a per-slot loop. Setting behaviour by address
 * still works as before.
 *
 * <h3>Storage is a factory, not bound storage</h3>
 *
 * {@link CreatedSlots.Builder#storage} takes a {@code Storage} already bound to a
 * player, because it runs inside a mixin where the player is in scope. A recipe is
 * registered once at init with no player in scope and applied to many players'
 * menus, so it takes a {@code Function<Player, Storage>}, almost always
 * {@code player -> SOME_ATTACHMENT.bind(player)}. The factory must be deterministic
 * and give the same storage size for a given player on both sides, or the appended
 * slots desync.
 *
 * <p>A mutable builder, used once by {@link ContainerPanel.Builder#addSlot}.
 */
public final class SlotSpec {

    private final String groupId;
    private final SlotGroupCategory category;

    private int count = 1;                                // logical slots in this group
    private @Nullable Function<Player, Storage> storageFactory;   // required
    private @Nullable BooleanSupplier revealWhen = null;  // client-side reveal; null => always
    private @Nullable String label = null;               // display name; null => capitalized groupId
    private @Nullable Supplier<Component> tooltip = null; // hover tooltip on each slot in the group; null => none

    // The behaviour the consumer declared, which register() sets in the engine by
    // each slot's Address. null leaves the engine default (gating OPEN, shift-click
    // both ways, binding and mending FALSE), so an undeclared slot is plain vanilla.
    private @Nullable SlotGate gate = null;
    private @Nullable TriBool binding = null;
    private @Nullable TriBool mending = null;
    private @Nullable TriBool shiftClickOut = null;
    private @Nullable TriBool shiftClickIn = null;
    private @Nullable TriBool collect = null;
    private @Nullable TriBool dragFill = null;

    private SlotSpec(String groupId, SlotGroupCategory category) {
        this.groupId = groupId;
        this.category = Objects.requireNonNull(category,
                "SlotSpec '" + groupId + "': a SlotGroupCategory is required");
    }

    /**
     * Starts a slot-group spec. Position is not declared: the group's slots flow and
     * wrap to the owning panel's width through {@link SlotFlowElement}, so a spec only
     * says what a group is (storage, count, reveal, behaviour), never where it sits.
     *
     * <p>The category is required, and it is the group's identity. It is how other
     * mods find these slots ({@code SlotGroupCategories.of(menu)} lists created
     * groups next to vanilla ones) and tell what they are. A pocket group declared
     * {@link SlotGroupCategory#PLAYER_INVENTORY} counts as player inventory to a
     * search; an elytra slot under the mod's own category does not. There is no
     * default on purpose: a group that forgot to say what it is would silently look
     * like plain storage to every consumer, which is the dangerous outcome. Once
     * another mod depends on a category name it is a public contract, and renaming it
     * is a breaking change.
     *
     * @param groupId  slot-group id, unique within the owning {@link ContainerPanel}
     * @param category what the group is: a vanilla category, or one the mod
     *                 declares ({@code new SlotGroupCategory("mymod", "pockets")})
     */
    public static SlotSpec at(String groupId, SlotGroupCategory category) {
        return new SlotSpec(groupId, category);
    }

    /**
     * Where items live, as a per-player factory. Required. To persist content, bind
     * from a {@link StorageAttachment}:
     * {@code .storage(player -> POCKETS.bind(player))}.
     */
    public SlotSpec storage(Function<Player, Storage> storageFactory) {
        this.storageFactory = storageFactory;
        return this;
    }

    /**
     * Number of slots in this group (default {@code 1}). The {@code storage} factory
     * must give storage of exactly this size for every player, or the appended slots
     * desync between the sides.
     */
    public SlotSpec count(int count) {
        this.count = Math.max(1, count);
        return this;
    }

    /**
     * Client-side reveal predicate. The group is visible and usable on the client only
     * while this returns true; the server keeps it syncing either way (see
     * {@link CreatedSlots.Builder#revealWhen}). Must be client-safe. If unset, the
     * group is always visible.
     */
    public SlotSpec revealWhen(BooleanSupplier clientReveal) {
        this.revealWhen = clientReveal;
        return this;
    }

    /**
     * Display name for the group's slots, applied like
     * {@link CreatedSlots.Builder#label}: each slot is named {@code "{label} {n}"}
     * (from 1) in a multi-slot group, or just {@code "{label}"} for a single slot. If
     * unset, the slots use the capitalized group id. Names are client-side only; the
     * build step applies them only on the client.
     */
    public SlotSpec label(String label) {
        this.label = label;
        return this;
    }

    /**
     * Adds a hover tooltip to every slot in this group. The library applies it to each
     * {@link SlotElement} it builds, so the consumer never touches those. Shows only
     * over an empty slot; a slot holding an item shows vanilla's item tooltip instead.
     * Chainable.
     */
    public SlotSpec tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** {@link #tooltip(Component)} with the text from a supplier. */
    public SlotSpec tooltip(@Nullable Supplier<Component> supplier) {
        this.tooltip = supplier;
        return this;
    }

    // Behaviour methods. Each records a value that ContainerPanel.Builder.register()
    // sets in the engine by the slot's Address for every index in this group; see the
    // class doc.

    /**
     * Sets a {@link SlotGate}: what every slot in this group accepts, releases and caps
     * at. Shorthand for {@code Window.slot(addr).set(BehaviorKeys.GATING, gate)} on
     * each index. Unset, it is {@link SlotGate#OPEN}, plain vanilla.
     */
    public SlotSpec gate(SlotGate gate) {
        this.gate = gate;
        return this;
    }

    /**
     * A placement-only filter, shorthand for {@link #gate}. Builds a {@link SlotGate}
     * that accepts a stack only if {@code accept} passes; pickup stays open and the
     * stack cap stays vanilla. For a pickup rule or stack cap, pass a full
     * {@link SlotGate} to {@link #gate}.
     */
    public SlotSpec accepts(Predicate<ItemStack> accept) {
        this.gate = new SlotGate() {
            @Override public boolean mayPlace(ItemStack stack, GatingContext context) {
                return accept.test(stack);
            }
            @Override public boolean mayPickup(Player player, GatingContext context) {
                return true;
            }
        };
        return this;
    }

    /**
     * Applies Curse of Binding to every slot in this group: a bound item can't be taken
     * out while alive, in survival only (creative ignores it). Shorthand for
     * {@code set(BehaviorKeys.BINDING, ...)}. Default off.
     */
    public SlotSpec binding(boolean enabled) {
        this.binding = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /** Equivalent to {@code binding(true)}. */
    public SlotSpec binding() {
        return binding(true);
    }

    /**
     * Puts every slot in this group in the XP-orb Mending repair pool. Shorthand for
     * {@code set(ContainerKeys.MENDING, ...)}. Default off.
     */
    public SlotSpec mending(boolean enabled) {
        this.mending = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /** Equivalent to {@code mending(true)}. */
    public SlotSpec mending() {
        return mending(true);
    }

    /**
     * Whether shift-clicking a slot in this group may send its stack elsewhere, set for
     * the group ({@code BehaviorKeys.SHIFT_CLICK_OUT}). Default on.
     */
    public SlotSpec shiftClickOut(boolean enabled) {
        this.shiftClickOut = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /**
     * Whether a shift-click elsewhere may put items in this group, set for the group
     * ({@code BehaviorKeys.SHIFT_CLICK_IN}). Default on.
     */
    public SlotSpec shiftClickIn(boolean enabled) {
        this.shiftClickIn = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /**
     * Whether vanilla's double-click collect may pull items out of this group.
     * Shorthand for {@code set(BehaviorKeys.COLLECT, ...)}. Default on, as in
     * vanilla. A storage group the bulk shortcuts must not touch declares
     * {@code .shiftClickOut(false).shiftClickIn(false).collect(false).dragFill(false)}.
     */
    public SlotSpec collect(boolean enabled) {
        this.collect = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /**
     * Whether vanilla's drag-fill (spreading a carried stack across dragged-over
     * slots) may place into this group. Shorthand for
     * {@code set(BehaviorKeys.DRAG_FILL, ...)}. Default on, as in vanilla.
     */
    public SlotSpec dragFill(boolean enabled) {
        this.dragFill = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    // Read by ContainerPanel and ParitySlotRegistry

    @ApiStatus.Internal
    public String groupId()       { return groupId; }
    @ApiStatus.Internal
    public SlotGroupCategory category() { return category; }
    int count()            { return count; }
    // SlotFlowElement owns slot positions. These are only the starting layout
    // ParitySlotRegistry hands to CreatedSlots, which the SlotElement replaces every
    // frame, so it never shows: origin (0,0), one row (columns = count).
    @ApiStatus.Internal
    public int childX()           { return 0; }
    @ApiStatus.Internal
    public int childY()           { return 0; }
    @ApiStatus.Internal
    public int columns()          { return count; }
    @ApiStatus.Internal
    public @Nullable BooleanSupplier revealWhen() { return revealWhen; }
    @ApiStatus.Internal
    public @Nullable String label() { return label; }
    @Nullable Supplier<Component> tooltip() { return tooltip; }

    @ApiStatus.Internal
    public @Nullable Function<Player, Storage> storageFactory() { return storageFactory; }

    // Declared behaviour (null leaves the engine default for that key).
    @Nullable SlotGate gateValue()                 { return gate; }
    @Nullable TriBool bindingValue()               { return binding; }
    @Nullable TriBool mendingValue()               { return mending; }
    @Nullable TriBool shiftClickOutValue()         { return shiftClickOut; }
    @Nullable TriBool shiftClickInValue()          { return shiftClickIn; }
    @Nullable TriBool collectValue()               { return collect; }
    @Nullable TriBool dragFillValue()              { return dragFill; }
}
