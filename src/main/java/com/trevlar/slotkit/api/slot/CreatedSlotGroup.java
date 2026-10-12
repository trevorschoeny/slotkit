package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A group of slots: their {@link Storage}, shift-click priority, right-click handler
 * and layout.
 *
 * <p>The group owns its slots' structure (storage, layout, pairing, priority) but not
 * their behaviour. Accepting, removing, stack limits, quick-move, binding and
 * mending all resolve from the {@code WindowEngine}, keyed by each slot's
 * {@link CreatedSlot#address()}. So a group declares its structure once and behaviour
 * is set by address, defaulting to plain vanilla. Slots carry only identity, and the
 * group only structure.
 *
 * <p>Hierarchy: Screen, then Panel, then CreatedSlotGroup, then CreatedSlot.
 */
public class CreatedSlotGroup {

    private final String id;
    // What this group is, in the slot group registry's terms. Required, so another
    // mod can find these slots by name (SlotGroupCategories) and decide what to do
    // with them, and a group never silently looks like plain storage to a search
    // over the player's menu. The category says what the slots are; what a gesture
    // may do to them is a window key (SHIFT_CLICK_OUT, SHIFT_CLICK_IN, COLLECT,
    // DRAG_FILL).
    private final SlotGroupCategory category;
    private final Storage storage;
    private final int shiftClickPriority;

    // Layout, read by the screen
    private final int columns;      // grid columns for slot layout
    private final int rowGapAfter;  // 0-indexed row after which to insert a gap (-1 = none)
    private final int rowGapSize;   // gap size in pixels

    // Optional handler called when a slot in this group is right-clicked. It lives
    // on the group because right-click is a group-level capability, not a slot one.
    private @Nullable BiConsumer<Player, CreatedSlot> rightClickHandler;

    // Index range in the handler's slot list, set once while the handler is built.
    // Used by shift-click routing.
    private int flatIndexStart = -1;
    private int flatIndexEnd = -1;

    // Directional pairing for shift-click routing
    private final List<CreatedSlotGroup> pairedWith = new ArrayList<>();

    /**
     * @param id                  unique identifier within the panel
     * @param category            what the group is ({@link SlotGroupCategory}); required
     * @param storage             where items live
     * @param shiftClickPriority  numeric priority (higher = tried first)
     * @param columns             grid columns for slot layout (-1 = auto)
     * @param rowGapAfter         0-indexed row after which to insert a gap (-1 = none)
     * @param rowGapSize          gap size in pixels (only used if rowGapAfter >= 0)
     */
    public CreatedSlotGroup(String id, SlotGroupCategory category, Storage storage, int shiftClickPriority,
                     int columns, int rowGapAfter, int rowGapSize) {
        this.id = id;
        this.category = Objects.requireNonNull(category, "CreatedSlotGroup '" + id + "': category is required");
        this.storage = storage;
        this.shiftClickPriority = shiftClickPriority;
        // Default columns: min(9, storage size)
        this.columns = columns > 0 ? columns : Math.min(9, storage.size());
        this.rowGapAfter = rowGapAfter;
        this.rowGapSize = rowGapSize;
    }

    /**
     * @param id                  unique identifier within the panel
     * @param category            what the group is; required
     * @param storage             where items live
     * @param shiftClickPriority  numeric priority (higher = tried first)
     */
    public CreatedSlotGroup(String id, SlotGroupCategory category, Storage storage, int shiftClickPriority) {
        this(id, category, storage, shiftClickPriority, -1, -1, 0);
    }

    /** With the default priority, 100. */
    public CreatedSlotGroup(String id, SlotGroupCategory category, Storage storage) {
        this(id, category, storage, 100);
    }

    /** Returns this group's unique identifier within its panel. */
    public String id() { return id; }

    /** What this group is, in the registry vocabulary ({@code SlotGroupCategories}). */
    public SlotGroupCategory getCategory() { return category; }

    /** Returns where items live. */
    public Storage getStorage() { return storage; }

    /** Returns the numeric shift-click priority (higher = tried first). */
    public int getShiftClickPriority() { return shiftClickPriority; }

    /** Grid columns for slot layout. Always above 0 (computed if not given). */
    public int getColumns() { return columns; }

    /** 0-indexed row after which to insert a visual gap, or -1 for none. */
    public int getRowGapAfter() { return rowGapAfter; }

    /** Gap size in pixels (only meaningful if rowGapAfter >= 0). */
    public int getRowGapSize() { return rowGapSize; }

    /** Returns the right-click handler for this group, or null. */
    public @Nullable BiConsumer<Player, CreatedSlot> getRightClickHandler() {
        return rightClickHandler;
    }

    /** Sets the right-click handler. Called by the builder. */
    public void setRightClickHandler(@Nullable BiConsumer<Player, CreatedSlot> handler) {
        this.rightClickHandler = handler;
    }

    /** Start of this group's index range in the slot list (inclusive). */
    public int getFlatIndexStart() { return flatIndexStart; }

    /** End of this group's index range in the slot list (exclusive). */
    public int getFlatIndexEnd() { return flatIndexEnd; }

    /** Sets the index range. Called once while the handler is built. */
    public void setFlatIndexRange(int start, int end) {
        this.flatIndexStart = start;
        this.flatIndexEnd = end;
    }

    /**
     * Returns this group's slots, taken from the handler's slot list.
     *
     * <p>Internal: used by the grouping engine, with no consumer callers.
     *
     * @param handler the handler whose slot list contains this group's slots
     * @return unmodifiable list of CreatedSlots in this group
     */
    @ApiStatus.Internal
    public List<CreatedSlot> getSlots(AbstractContainerMenu handler) {
        if (flatIndexStart < 0 || flatIndexEnd < 0) return List.of();
        List<CreatedSlot> result = new ArrayList<>();
        for (int i = flatIndexStart; i < flatIndexEnd; i++) {
            Slot slot = handler.slots.get(i);
            if (slot instanceof CreatedSlot mk) {
                result.add(mk);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Makes shift-click from this group prefer {@code target} over numeric priority.
     * This is how furnace-style routing works: fuel goes to the fuel slot, and
     * smeltable items to the input slot.
     */
    public CreatedSlotGroup pairsWith(CreatedSlotGroup target) {
        pairedWith.add(target);
        return this;
    }

    /** The groups this one shift-clicks into first. */
    public List<CreatedSlotGroup> getPairedWith() {
        return Collections.unmodifiableList(pairedWith);
    }
}
