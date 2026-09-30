package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.core.SlotGroupCategory;
import com.trevlar.menukit.core.SlotGroupLike;
import com.trevlar.menukit.core.Storage;
import com.trevlar.menukit.core.VirtualSlotGroup;

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
 * Where structure lives. A SlotGroup is the composition of {@link Storage},
 * shift-click priority, capabilities (right-click), and layout metadata.
 *
 * <p>The group owns its slots' <em>structure</em> — storage, layout, pairing,
 * priority. It no longer owns the behavioral contract (accept/remove/stack-cap,
 * quick-move, binding, mending): under THE ONE WINDOW those all resolve from
 * the {@code WindowEngine} keyed by each slot's {@link MKCSlot#address()}, so a
 * group declares structure once and behavior is armed by Address (default = pure
 * vanilla). This keeps slots thin (identity only) and the group structure-only.
 *
 * <p>Implements {@link SlotGroupLike} so consumer code can program against
 * a uniform interface shared with {@link VirtualSlotGroup} (observed screens).
 *
 * <p>Part of the canonical MenuKit hierarchy:
 * Screen → Panel → SlotGroup → MKCSlot
 */
public class SlotGroup implements SlotGroupLike {

    private final String id;
    // What this group IS, in MenuKit core's registry vocabulary — required, so
    // another mod can find these slots by name (SlotGroupCategories) and decide
    // what to do with them, and a group can never silently become "just
    // storage" to a search that walks the player's menu. Identity, not
    // behaviour: the category says what the slots are; what a gesture may do
    // to them is a window key (SHIFT_CLICK_OUT, SHIFT_CLICK_IN, COLLECT, DRAG_FILL).
    private final SlotGroupCategory category;
    private final Storage storage;
    private final int shiftClickPriority;

    // ── Layout metadata (declarative, read by the screen) ──────────────
    private final int columns;      // grid columns for slot layout
    private final int rowGapAfter;  // 0-indexed row after which to insert a gap (-1 = none)
    private final int rowGapSize;   // gap size in pixels

    // ── Right-click handler (group-level capability) ───────────────────
    // Optional handler invoked when a slot in this group is right-clicked.
    // Lives on the group per the canonical story — right-click is a
    // group-level capability, not a slot-level one.
    private @Nullable BiConsumer<Player, MKCSlot> rightClickHandler;

    // Flat index range in the handler's slot list — set once during
    // handler construction, then frozen. Used by shift-click routing.
    private int flatIndexStart = -1;
    private int flatIndexEnd = -1;

    // Directional pairing for shift-click routing
    private final List<SlotGroup> pairedWith = new ArrayList<>();

    /**
     * Full constructor with layout metadata.
     *
     * @param id                  unique identifier within the panel
     * @param category            what the group is ({@link SlotGroupCategory}); required
     * @param storage             where items live
     * @param shiftClickPriority  numeric priority (higher = tried first)
     * @param columns             grid columns for slot layout (-1 = auto)
     * @param rowGapAfter         0-indexed row after which to insert a gap (-1 = none)
     * @param rowGapSize          gap size in pixels (only used if rowGapAfter >= 0)
     */
    public SlotGroup(String id, SlotGroupCategory category, Storage storage, int shiftClickPriority,
                     int columns, int rowGapAfter, int rowGapSize) {
        this.id = id;
        this.category = Objects.requireNonNull(category, "SlotGroup '" + id + "': category is required");
        this.storage = storage;
        this.shiftClickPriority = shiftClickPriority;
        // Auto-compute columns: min(9, storage size) if not specified
        this.columns = columns > 0 ? columns : Math.min(9, storage.size());
        this.rowGapAfter = rowGapAfter;
        this.rowGapSize = rowGapSize;
    }

    /**
     * @param id                  unique identifier within the panel
     * @param storage             where items live
     * @param shiftClickPriority  numeric priority (higher = tried first)
     */
    public SlotGroup(String id, SlotGroupCategory category, Storage storage, int shiftClickPriority) {
        this(id, category, storage, shiftClickPriority, -1, -1, 0);
    }

    /** Convenience: default priority (100). */
    public SlotGroup(String id, SlotGroupCategory category, Storage storage) {
        this(id, category, storage, 100);
    }

    // ── Identity ────────────────────────────────────────────────────────

    /** Returns this group's unique identifier within its panel. */
    @Override public String getId() { return id; }

    /** What this group is, in the registry vocabulary ({@code SlotGroupCategories}). */
    public SlotGroupCategory getCategory() { return category; }

    // ── Axes ────────────────────────────────────────────────────────────

    /** Returns where items live. */
    @Override public Storage getStorage() { return storage; }

    /** Returns the numeric shift-click priority (higher = tried first). */
    @Override public int getShiftClickPriority() { return shiftClickPriority; }

    // ── Layout Metadata ────────────────────────────────────────────────

    /** Grid columns for slot layout. Always > 0 (auto-computed if not specified). */
    public int getColumns() { return columns; }

    /** 0-indexed row after which to insert a visual gap, or -1 for none. */
    public int getRowGapAfter() { return rowGapAfter; }

    /** Gap size in pixels (only meaningful if rowGapAfter >= 0). */
    public int getRowGapSize() { return rowGapSize; }

    // ── Right-Click Handler ────────────────────────────────────────────

    /** Returns the right-click handler for this group, or null. */
    public @Nullable BiConsumer<Player, MKCSlot> getRightClickHandler() {
        return rightClickHandler;
    }

    /** Sets the right-click handler. Called during builder construction. */
    public void setRightClickHandler(@Nullable BiConsumer<Player, MKCSlot> handler) {
        this.rightClickHandler = handler;
    }

    // ── Flat Index Range ───────────────────────────────────────────────

    /** Returns the start of this group's flat index range (inclusive). */
    public int getFlatIndexStart() { return flatIndexStart; }

    /** Returns the end of this group's flat index range (exclusive). */
    public int getFlatIndexEnd() { return flatIndexEnd; }

    /** Sets the flat index range. Called once during handler construction. */
    public void setFlatIndexRange(int start, int end) {
        this.flatIndexStart = start;
        this.flatIndexEnd = end;
    }

    /**
     * Returns this group's slots as a list, extracted from the handler's
     * flat slot list. Convenience for consumers that need to iterate
     * a group's slots without walking the flat index range manually.
     *
     * <p>Returns {@code List<MKCSlot>} — a valid covariant override of
     * {@link SlotGroupLike#getSlots}'s {@code List<? extends Slot>}.
     *
     * <p><b>Internal plumbing</b> (see {@link SlotGroupLike#getSlots}) — returns
     * live slots for the grouping engine, no consumer caller.
     *
     * @param handler the handler whose slot list contains this group's slots
     * @return unmodifiable list of MKCSlots in this group
     */
    @ApiStatus.Internal
    @Override
    public List<MKCSlot> getSlots(AbstractContainerMenu handler) {
        if (flatIndexStart < 0 || flatIndexEnd < 0) return List.of();
        List<MKCSlot> result = new ArrayList<>();
        for (int i = flatIndexStart; i < flatIndexEnd; i++) {
            Slot slot = handler.slots.get(i);
            if (slot instanceof MKCSlot mk) {
                result.add(mk);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ── Directional Pairing ─────────────────────────────────────────────

    /**
     * Declares that shift-click from this group should prefer the target
     * group over numeric priority. This is how furnace-style affinities
     * work: fuel items target the fuel slot, smeltable items target the
     * input slot.
     */
    public SlotGroup pairsWith(SlotGroup target) {
        pairedWith.add(target);
        return this;
    }

    /** Returns the list of directional pairing targets. */
    public List<SlotGroup> getPairedWith() {
        return Collections.unmodifiableList(pairedWith);
    }
}
