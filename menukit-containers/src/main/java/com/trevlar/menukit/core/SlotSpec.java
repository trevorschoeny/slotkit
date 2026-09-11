package com.trevlar.menukit.core;

import com.trevlar.menukit.window.TriBool;

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
 * A side-neutral, declarative description of one registered-slot group that a
 * {@link MKCContainerPanel} projects onto every container the player opens —
 * the "recipe" half of container parity.
 *
 * <h3>Why a recipe, not a built slot</h3>
 *
 * A real {@link MKCSlot} is a per-menu object: the player's own
 * {@code InventoryMenu}, every chest/furnace they open, and the creative
 * item-picker each carry their <em>own</em> slot instance for the same logical
 * slot. To make a slot appear on all of them from one registration, the panel
 * can't hold a slot — it holds the <em>instructions to build one</em>, applied
 * fresh to each menu (and bound to that menu's player). That is this class: pure
 * data plus a {@code Player -> Storage} factory, evaluated identically on the
 * logical server and the client so the appended slots are byte-identical (the
 * sync-safety contract {@link MKCSlotProjection} documents).
 *
 * <p>It is the parity analogue of the per-call parameters on
 * {@link MKCSlots.Builder}: storage, layout, and the client reveal predicate,
 * expressed once as reusable data rather than imperatively against a single menu.
 * {@link MKCContainerPanel} turns each {@code SlotSpec} into both the real slots
 * (every menu, both sides) and the {@link SlotElement}s that present them (client).
 *
 * <h3>Inline behavior verbs are sugar over the by-address engine path</h3>
 *
 * Gating, quick-move, collect, drag-fill, binding, and mending are armed in THE ONE WINDOW engine by
 * the slot's {@link com.trevlar.menukit.window.Address} — that is where slot
 * behavior <em>lives</em>, identically for a vanilla slot and a created slot. The
 * inline verbs here ({@link #gate}, {@link #accepts}, {@link #binding},
 * {@link #mending}, {@link #quickMove}, {@link #collect}, {@link #dragFill}) do not introduce a second behavior store:
 * they record the consumer's intent on the spec, and
 * {@link MKCContainerPanel.Builder#register()} arms the engine by address for every
 * local index in the group — exactly {@code Window.slot(MKCContainerPanel.address(
 * panelId, groupId, i)).set(KEY, value)}, the same call a consumer could make by
 * hand. The win is purely ergonomic: behavior is declared where the slot group is
 * declared (no separate forgettable arming pass), and the consumer never computes
 * an address or hand-rolls a per-slot loop. The by-address path stays fully usable;
 * inline is sugar, not a replacement.
 *
 * <h3>Storage is a factory, not a bound storage</h3>
 *
 * {@link MKCSlots.Builder#storage} takes an already-{@code player}-bound
 * {@code Storage} because it runs inside a mixin where the player is in scope.
 * A recipe is registered once at init with no player in scope and is applied to
 * many players' menus, so it takes a {@code Function<Player, Storage>} — almost
 * always {@code player -> SOME_ATTACHMENT.bind(player)}. The factory MUST be
 * deterministic and yield the same storage <em>size</em> for a given player on
 * both sides, or the appended slot block desyncs.
 *
 * <p>Mutable fluent builder, consumed once by {@link MKCContainerPanel.Builder#addSlot}.
 */
public final class SlotSpec {

    private final String groupId;
    private final SlotGroupCategory category;

    private int count = 1;                                // logical slots in this group
    private @Nullable Function<Player, Storage> storageFactory;   // required
    private @Nullable BooleanSupplier revealWhen = null;  // client-side reveal; null => always
    private @Nullable String label = null;               // MK display name; null => capitalized groupId
    private @Nullable Supplier<Component> tooltip = null; // hover tooltip on each slot in the group; null => none

    // ── Inline behavior intent (armed by Address in register(); null => default) ──
    // These hold the consumer's declared behavior so register() can arm the engine
    // by each slot's Address. null means "leave the engine default" (gating OPEN,
    // quick-move BOTH, binding/mending FALSE), so an un-declared slot is exactly
    // vanilla — same as never arming it.
    private @Nullable SlotGate gate = null;
    private @Nullable TriBool binding = null;
    private @Nullable TriBool mending = null;
    private @Nullable QuickMoveParticipation quickMove = null;
    private @Nullable TriBool collect = null;
    private @Nullable TriBool dragFill = null;

    private SlotSpec(String groupId, SlotGroupCategory category) {
        this.groupId = groupId;
        this.category = Objects.requireNonNull(category,
                "SlotSpec '" + groupId + "': a SlotGroupCategory is required");
    }

    /**
     * Begins a slot-group spec. Position is reactive (Movement ④) — the group's
     * slots flow + wrap into the owning panel's width via {@link SlotFlowElement},
     * so a spec declares only WHAT a group is (storage, count, reveal, behavior),
     * never where it sits.
     *
     * <p><b>The category is required, and it is identity.</b> It is how any other
     * mod finds these slots ({@code SlotGroupCategories.of(menu)} lists created
     * groups next to the vanilla ones) and decides what they are: a pocket group
     * declared {@link SlotGroupCategory#PLAYER_INVENTORY} is player inventory to a
     * search; an elytra slot declared under the mod's own category is not. There
     * is no default on purpose — a group that forgot to say what it is would
     * silently become "just storage" to every consumer, which is the dangerous
     * outcome. A category name is a public contract once another mod depends on
     * it; renaming one is a breaking change.
     *
     * @param groupId  slot-group id, unique within the owning {@link MKCContainerPanel}
     * @param category what the group is — a vanilla category, or one the mod
     *                 declares ({@code new SlotGroupCategory("mymod", "pockets")})
     */
    public static SlotSpec at(String groupId, SlotGroupCategory category) {
        return new SlotSpec(groupId, category);
    }

    /**
     * Where items live, as a per-player factory. Required. Use a
     * {@link StorageAttachment}-bound factory for content persistence:
     * {@code .storage(player -> POCKETS.bind(player))}.
     */
    public SlotSpec storage(Function<Player, Storage> storageFactory) {
        this.storageFactory = storageFactory;
        return this;
    }

    /**
     * Number of logical slots in this group (default {@code 1}). The
     * {@code storage} factory MUST produce a storage of exactly this size for
     * every player, or the appended slot block desyncs across sides.
     */
    public SlotSpec count(int count) {
        this.count = Math.max(1, count);
        return this;
    }

    /**
     * Client-side reveal predicate. The group is visible + interactive on the
     * client only while this returns true; the server keeps it syncing
     * regardless (see {@link MKCSlots.Builder#revealWhen}). MUST be client-safe.
     * If unset, the group is always visible.
     */
    public SlotSpec revealWhen(BooleanSupplier clientReveal) {
        this.revealWhen = clientReveal;
        return this;
    }

    /**
     * MK display name for the group's slots, applied through the naming layer like
     * {@link MKCSlots.Builder#label}: each slot is named {@code "{label} {n}"}
     * (1-based) for a multi-slot group, or just {@code "{label}"} for a single
     * slot. If unset, the slots fall back to the capitalized group id. Naming is a
     * client-side display concern (the build seam applies it client-guarded).
     */
    public SlotSpec label(String label) {
        this.label = label;
        return this;
    }

    /**
     * Attaches a hover tooltip to every slot in this group. The library applies
     * it to each {@link SlotElement} it builds (the consumer never touches those
     * instances). Fires only over an EMPTY slot — a slot holding an item shows
     * vanilla's item tooltip instead. Chainable.
     */
    public SlotSpec tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Supplier-driven variant of {@link #tooltip(Component)}. */
    public SlotSpec tooltip(@Nullable Supplier<Component> supplier) {
        this.tooltip = supplier;
        return this;
    }

    // ── Inline behavior verbs (sugar; armed by Address in register()) ───
    // See the class doc: each verb records intent that
    // MKCContainerPanel.Builder.register() arms onto the engine by the slot's
    // Address for every local index in this group. Behavior still lives in the
    // engine — this is exactly Window.slot(addr).set(KEY, value), just declared
    // where the group is declared.

    /**
     * Arms a {@link SlotGate} (what every slot in this group accepts / releases /
     * caps) — sugar for {@code Window.slot(addr).set(MKCBehaviorKeys.GATING, gate)}
     * on each local index. Default (unset) is {@link SlotGate#OPEN} — pure vanilla.
     */
    public SlotSpec gate(SlotGate gate) {
        this.gate = gate;
        return this;
    }

    /**
     * Convenience over {@link #gate}: a place-only filter. Builds a {@link SlotGate}
     * that admits a stack iff {@code accept} passes (pickup stays open, stack cap
     * stays vanilla). For a richer policy (pickup rule, stack cap) declare a full
     * {@link SlotGate} via {@link #gate}.
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
     * Enrolls every slot in this group in Curse-of-Binding enforcement (a bound
     * item can't be taken out while alive, survival only; creative bypasses, §0051)
     * — sugar for {@code set(MKCBehaviorKeys.BINDING, ...)}. Default off.
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
     * Opts every slot in this group into the XP-orb Mending repair pool (§0053) —
     * sugar for {@code set(MKCBehaviorKeys.MENDING, ...)}. Default off.
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
     * Sets how every slot in this group participates in shift-click routing.
     * Since MenuKit 5.1.0 shift-click is two operations, and this is sugar for
     * declaring both at the group rung: {@code exports()} becomes
     * {@link com.trevlar.menukit.window.BehaviorKeys#SHIFT_CLICK_OUT} and
     * {@code imports()} becomes {@link com.trevlar.menukit.window.BehaviorKeys#SHIFT_CLICK_IN}.
     * Prefer {@code set(BehaviorKeys.SHIFT_CLICK_OUT, ...)} / {@code SHIFT_CLICK_IN}
     * directly; this verb and {@code MKCBehaviorKeys.QUICK_MOVE} go in 6.0.0.
     *
     * @deprecated use the two shift-click operation keys.
     */
    @Deprecated(since = "5.1.0", forRemoval = true)
    public SlotSpec quickMove(QuickMoveParticipation participation) {
        this.quickMove = participation;
        return this;
    }

    /**
     * Whether vanilla's double-click collect may sweep items out of this group —
     * sugar for {@code set(BehaviorKeys.COLLECT, ...)}. Default on (vanilla). A
     * group that is storage but must not be raided by the bulk shortcuts declares
     * {@code .quickMove(NONE).collect(false).dragFill(false)}.
     */
    public SlotSpec collect(boolean enabled) {
        this.collect = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    /**
     * Whether vanilla's drag-fill (spreading a carried stack across dragged-over
     * slots) may place into this group — sugar for {@code set(BehaviorKeys.DRAG_FILL, ...)}.
     * Default on (vanilla).
     */
    public SlotSpec dragFill(boolean enabled) {
        this.dragFill = enabled ? TriBool.TRUE : TriBool.FALSE;
        return this;
    }

    // ── Accessors (read by MKCContainerPanel / ParitySlotRegistry) ──────

    String groupId()       { return groupId; }
    SlotGroupCategory category() { return category; }
    int count()            { return count; }
    // Movement ④ — slots flow reactively (SlotFlowElement owns positions). These
    // remain only as the off-panel SEED ParitySlotRegistry hands MKCSlots; the
    // SlotElement overrides them per frame, so the seed never shows. Origin (0,0),
    // one-row seed (columns = count).
    int childX()           { return 0; }
    int childY()           { return 0; }
    int columns()          { return count; }
    @Nullable BooleanSupplier revealWhen() { return revealWhen; }
    @Nullable String label() { return label; }
    @Nullable Supplier<Component> tooltip() { return tooltip; }

    @Nullable Function<Player, Storage> storageFactory() { return storageFactory; }

    // Inline behavior intent (null => leave the engine default for that key).
    @Nullable SlotGate gateValue()                 { return gate; }
    @Nullable TriBool bindingValue()               { return binding; }
    @Nullable TriBool mendingValue()               { return mending; }
    @Nullable QuickMoveParticipation quickMoveValue() { return quickMove; }
    @Nullable TriBool collectValue()               { return collect; }
    @Nullable TriBool dragFillValue()              { return dragFill; }
}
