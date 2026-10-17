package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.core.CreatedSlotProjection;
import com.trevlar.slotkit.core.ParitySlotRegistry;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.Decl;
import com.trevlar.slotkit.api.window.GroupKey;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.TriBool;
import com.trevlar.slotkit.api.window.WindowEngine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A slot group with container parity: real, synced slots on every screen that shows
 * the player inventory (survival, creative, and every chest, furnace or modded
 * container), registered in one call with no mixin.
 *
 * <pre>{@code
 * // Common initializer (runs on both sides): the slots and how they behave.
 * public static final ParityGroup POCKETS = ParityGroup.register("mymod:pockets",
 *         SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
 *                 .storage(player -> POCKET_STORAGE.bind(player)));
 *
 * // Client initializer: the mod's own MenuKit panel shows them.
 * Panel panel = Panel.builder("mymod:pockets")
 *         .add(SlotFlowElement.of(POCKETS))
 *         .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP))
 *         .build()
 *         .visibleWhen(() -> ClientSlots.isShown(POCKETS.id()));
 * new ScreenPanelAdapter(panel).onMatching(ScreenMatcher.all());
 * }</pre>
 *
 * <h3>What registering does</h3>
 * <ol>
 *   <li>builds the {@link CreatedSlot}s on the player's own {@code InventoryMenu}
 *       (the library's {@code SKInventoryMenuMixin}, both sides);</li>
 *   <li>projects the same slots onto every other container menu
 *       ({@link CreatedSlotProjection}, both sides);</li>
 *   <li>covers the creative item picker (its wrapper wraps whatever created slots sit
 *       on {@code player.inventoryMenu});</li>
 *   <li>arms the behaviour the {@link SlotSpec} declared, for the whole group, and
 *       lists the group in the slot group registry under its category.</li>
 * </ol>
 *
 * <h3>What it doesn't do: the panel</h3>
 * SlotKit builds no panel. Where the slots appear, how the panel looks, what sits
 * next to them and which screens show it are the mod's, declared with MenuKit's
 * {@code Panel} and an adapter from the client initializer. A slot no panel shows
 * stays on the menu, parked off-screen: not drawn, not clickable, its items safe in
 * its storage.
 *
 * <h3>Identity</h3>
 * The group's id is {@code SlotGroupId.created(owner + ":" + groupId, groupId)},
 * exactly what {@code ContainerPanel.groupId(owner, groupId)} gave in 1.x, so
 * addresses, lang keys, slot state and other mods' settings made against a 1.x
 * container panel's group still name the same group.
 */
public final class ParityGroup {

    // The one foreign-menu projection source, registered on the first register().
    // One source covers every group because its factory applies the whole registry.
    private static volatile boolean projectionSourceRegistered = false;

    // Groups whose spec declared shiftClickIn(true): the shift-click targets on the
    // player's inventory menu (SKInventoryQuickMoveMixin). Opt-in, so a group that
    // never said so keeps vanilla's behaviour there.
    private static final Set<SlotGroupId> SHIFT_CLICK_TARGETS = ConcurrentHashMap.newKeySet();

    // Each group's SlotSpec.emptyIcon, read once by every CreatedSlot it builds.
    private static final Map<SlotGroupId, Identifier> EMPTY_ICONS = new ConcurrentHashMap<>();

    private final SlotGroupId.Created id;
    private final SlotSpec spec;

    private ParityGroup(SlotGroupId.Created id, SlotSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    /**
     * Registers a parity group. Call once at mod init, from the common initializer.
     *
     * @param owner the id the group's identity is built from, namespaced by the
     *              defining mod ({@code "mymod:pockets"}); in 1.x this was the
     *              container panel id
     * @param spec  the group: its slots, storage and behaviour
     * @throws IllegalStateException after MenuKit's declarations froze, when the spec
     *         has no storage, when the owner's mod doesn't declare SlotKit on both
     *         sides, or when the same group is registered twice
     */
    public static ParityGroup register(String owner, SlotSpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        com.trevlar.slotkit.network.ModSides.requireBuildsSlots("ParityGroup", owner);
        com.trevlar.menukit.api.Declarations.requireOpen("ParityGroup " + owner + " register()");
        if (spec.storageFactory() == null) {
            throw new IllegalStateException("ParityGroup '" + owner + "': slot group '"
                    + spec.groupId() + "' needs .storage(...) before register().");
        }
        SlotGroupId.Created id = idOf(owner, spec.groupId());

        // The recipe both build hooks read, under the slot panel id the built
        // CreatedSlots carry and SlotElements resolve against.
        ParitySlotRegistry.register(id.panelId(), spec);
        armInlineBehavior(id, spec);
        ensureProjectionSource();
        return new ParityGroup(id, spec);
    }

    /**
     * The id of the group {@code groupId} registered under {@code owner}, without the
     * handle: what another mod names it by ({@code SlotGroupPanelAdapter.onGroup},
     * {@code Address.createdSlot(id, i)}). The same id 1.x's
     * {@code ContainerPanel.groupId(owner, groupId)} gave.
     */
    public static SlotGroupId.Created idOf(String owner, String groupId) {
        return SlotGroupId.created(owner + ":" + groupId, groupId);
    }

    /** The group's id. */
    public SlotGroupId.Created id() { return id; }

    /** The spec it was registered with. */
    public SlotSpec spec() { return spec; }

    /** How many slots the group has. */
    public int count() { return spec.count(); }

    /** The empty-slot outline {@code group} declared, or {@code null}. Library use. */
    @ApiStatus.Internal
    public static @Nullable Identifier emptyIconOf(SlotGroupId group) {
        return EMPTY_ICONS.get(group);
    }

    /** Whether {@code group} declared {@code SlotSpec.shiftClickIn(true)}. Library use. */
    @ApiStatus.Internal
    public static boolean isShiftClickTarget(SlotGroupId group) {
        return SHIFT_CLICK_TARGETS.contains(group);
    }

    /**
     * Publishes the spec's category for the group and sets whatever behaviour the spec
     * declared, at the group level. A null value leaves the engine default, so an
     * undeclared slot stays plain vanilla.
     *
     * <p>Set for the group, not per address: a consumer that overrides one slot with
     * {@code Window.slot(Address.createdSlot(id, i)).set(KEY, value)} wins because it
     * is more specific, not because its mod initialised last. Above the group sits the
     * category.
     */
    private static void armInlineBehavior(SlotGroupId.Created id, SlotSpec spec) {
        // Declared whether or not the spec declares behaviour: the category recorded
        // on the group is what lets the category's own values reach its slots, and
        // registering at init lists the group from the title screen.
        com.trevlar.slotkit.api.group.SlotGroups.declare(id, spec.category());
        if (spec.emptyIcon() != null) EMPTY_ICONS.put(id, spec.emptyIcon());

        GroupKey group = GroupKey.of(id);
        SlotGate gate = spec.gateValue();
        if (gate != null) WindowEngine.setGroup(group, BehaviorKeys.GATING, Decl.set(gate));
        set(group, BehaviorKeys.BINDING, spec.bindingValue());
        set(group, BehaviorKeys.MENDING, spec.mendingValue());
        set(group, BehaviorKeys.SHIFT_CLICK_OUT, spec.shiftClickOutValue());
        set(group, BehaviorKeys.SHIFT_CLICK_IN, spec.shiftClickInValue());
        set(group, BehaviorKeys.COLLECT, spec.collectValue());
        set(group, BehaviorKeys.DRAG_FILL, spec.dragFillValue());
        if (spec.shiftClickInValue() == TriBool.TRUE) SHIFT_CLICK_TARGETS.add(id);
    }

    private static void set(GroupKey group, BehaviorKey<TriBool> key, @Nullable TriBool value) {
        if (value != null) WindowEngine.setGroup(group, key, Decl.set(value));
    }

    private static synchronized void ensureProjectionSource() {
        if (projectionSourceRegistered) return;
        projectionSourceRegistered = true;
        // The player's own InventoryMenu is served by the library's inventory mixin.
        CreatedSlotProjection.register(
                menu -> !(menu instanceof InventoryMenu),
                ParitySlotRegistry::applyTo);
    }
}
