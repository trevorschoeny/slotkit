package com.trevlar.slotkit.api.slot;

import org.jetbrains.annotations.ApiStatus;

import com.trevlar.slotkit.core.CreatedSlotProjection;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.storage.Storage;

import com.trevlar.slotkit.api.window.TriBool;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * {@link ParityGroup} adds to every container the player opens. It is the recipe
 * half of container parity.
 *
 * <h3>Why a recipe and not a built slot</h3>
 *
 * A real {@link CreatedSlot} belongs to one menu. The player's own
 * {@code InventoryMenu}, every chest or furnace they open, and the creative item
 * picker each carry their own slot instance for the same logical slot. To put a slot
 * on all of them from one registration, the group can't hold a slot. It holds the
 * instructions to build one, applied to each menu and bound to that menu's player.
 * That is this class: plain data plus a {@code Player -> Storage} factory, evaluated
 * the same way on the logical server and the client so the appended slots are
 * byte-identical (see {@link CreatedSlotProjection} on sync safety).
 *
 * <p>It holds the same settings as the per-call parameters on
 * {@link CreatedSlots.Builder} (storage, label, the client reveal predicate), but as
 * reusable data instead of calls against one menu. {@link ParityGroup#register} turns
 * a {@code SlotSpec} into the real slots (every menu, both sides). The mod's own panel
 * shows them ({@code SlotFlowElement.of(group)}, client).
 *
 * <h3>Behaviour methods set it once for the whole group</h3>
 *
 * Gating, shift-click, collect, drag-fill, binding and mending live in the window
 * engine, the same for a vanilla slot and a created slot. The methods here
 * ({@link #gate}, {@link #accepts}, {@link #binding}, {@link #mending},
 * {@link #shiftClickOut}, {@link #shiftClickIn}, {@link #collect}, {@link #dragFill})
 * don't add a second place to store behaviour. They record what the consumer asked
 * for, and {@link ParityGroup#register} sets it in the engine once, on
 * the group's own key ({@code GroupKey.of(group.id())}),
 * one level above any single slot. Behaviour is declared next to the slot group, with
 * no separate step to forget and no per-slot loop. To give one slot something else,
 * set it on that slot's address,
 * {@code Window.slot(scope, Address.createdSlot(group.id(), i))};
 * the slot's own value wins because it is more specific.
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
 * <p>A mutable builder, used once by {@link ParityGroup#register}.
 */
public final class SlotSpec {

    private final String groupId;
    private final SlotGroupCategory category;

    private int count = 1;                                // logical slots in this group
    private @Nullable Function<Player, Storage> storageFactory;   // required
    private @Nullable BooleanSupplier revealWhen = null;  // client-side reveal; null => always
    private @Nullable Integer activeCount = null;        // server-set live count, its start; null => opted out
    private @Nullable String label = null;               // display name; null => capitalized groupId
    private @Nullable Supplier<Component> tooltip = null; // hover tooltip on each slot in the group; null => none
    private @Nullable Identifier emptyIcon = null;       // outline sprite on an empty slot; null => none

    // The behaviour the consumer declared, which register() sets in the engine on the
    // group's key. null leaves the engine default (gating OPEN, shift-click
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
     * @param groupId  slot-group id, unique within its owner ({@link ParityGroup#register})
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
     * Opts the group in to a server-set <b>active count</b>, starting at
     * {@code startCount}: slot {@code i} is live while {@code i < count}, and every
     * slot from the count on is inert on both sides (empty to the client, refusing
     * clicks), so the server opens and closes the group and sizes it per player
     * ({@link ParityGroup#setActiveCount}). Declare the largest size you need with
     * {@link #count}; only the count moves. A group without this is always its full
     * count, with no sync traffic.
     *
     * @param startCount the count a player starts at, on login and after death, from
     *                   {@code 0} to {@link #count}
     */
    public SlotSpec activeCount(int startCount) {
        this.activeCount = startCount;
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

    /**
     * The outline drawn on each empty slot of this group, as vanilla draws the armour
     * and offhand outlines: a GUI sprite id, returned from the slot's
     * {@code getNoItemIcon}, so vanilla draws it in its own slot pass, under any item
     * and in creative too. {@code Identifier.withDefaultNamespace("container/slot/shield")}
     * names a vanilla sprite; {@code Identifier.fromNamespaceAndPath("mymod", "container/slot/elytra")}
     * is a mod's own, at {@code assets/mymod/textures/gui/sprites/container/slot/elytra.png}. Since 1.1.0.
     */
    public SlotSpec emptyIcon(Identifier sprite) {
        this.emptyIcon = sprite;
        return this;
    }

    // Behaviour methods. Each records a value that ParityGroup.register()
    // sets in the engine once for this group; see the class doc.

    /**
     * Sets a {@link SlotGate}: what every slot in this group accepts, releases and caps
     * at, set once for the group. Unset, it is {@link SlotGate#OPEN}, plain vanilla.
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
     * {@code set(BehaviorKeys.MENDING, ...)}. Default off.
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
     *
     * <p>{@code shiftClickIn(true)}, said explicitly, also makes the group a
     * shift-click target in the player's inventory (since 1.1.0). Vanilla's own
     * routing there never reaches a created slot, so SlotKit tries these groups first:
     * partial stacks, then empty slots, each slot judged by the group's gate. What
     * doesn't fit goes where vanilla sends it. {@code .accepts(isElytra).shiftClickIn(true)}
     * is a whole elytra slot. Out of a created slot, shift-click goes to the main
     * inventory, then the hotbar, as vanilla does for any slot past the offhand.
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

    // Read by ParityGroup and ParitySlotRegistry

    @ApiStatus.Internal
    public String groupId()       { return groupId; }
    @ApiStatus.Internal
    public SlotGroupCategory category() { return category; }
    @ApiStatus.Internal
    public int count()     { return count; }
    @ApiStatus.Internal
    public @Nullable BooleanSupplier revealWhen() { return revealWhen; }
    @ApiStatus.Internal
    public @Nullable String label() { return label; }
    /** The active count's start, or {@code null} when the group didn't opt in. */
    @ApiStatus.Internal
    public @Nullable Integer activeCountStart() { return activeCount; }
    @Nullable Supplier<Component> tooltip() { return tooltip; }
    @Nullable Identifier emptyIcon() { return emptyIcon; }

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
