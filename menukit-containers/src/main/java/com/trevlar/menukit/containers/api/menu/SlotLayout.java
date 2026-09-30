package com.trevlar.menukit.containers.api.menu;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.Storage;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.containers.api.slot.SlotGroup;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The slot layout of a {@link CustomContainerMenu}: which panels it has, which slot
 * groups each holds, and how shift-click pairs them (§0067). Structure only; runs on
 * both sides with the same declaration, so both build the same slots.
 *
 * <pre>{@code
 * CustomContainerMenu.builder(type)
 *     .panel("mymod:menu:main", p -> p.group("items", MY_CATEGORY, storage))
 *     .panel("mymod:menu:side", p -> p.group("extra", MY_CATEGORY, extra).hidden().toggleable())
 *     .build(syncId);
 * }</pre>
 *
 * How each panel looks (its elements, style, placement, toggle key) is declared on the
 * client with MenuKit's {@code Panel.Builder}:
 * {@code ClientMenu.of(MENU).panel("mymod:menu:main", p -> p.position(PanelPosition.main()).add(...))}.
 *
 * <h3>Namespace your panel ids</h3>
 * A created slot's address is keyed by (panel id, group id, index) and is global, not
 * scoped by the menu, so two mods naming a panel {@code "main"} would share addresses
 * and arm each other's slots. Name panels per mod: {@code "mymod:menu:main"}.
 */
public final class SlotLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    /**
     * One declared panel, as built: its id, whether it starts shown, whether a player
     * may toggle it, and its slot groups in order.
     */
    public record PanelSlots(String id, boolean startsShown, boolean toggleable, List<SlotGroup> groups) {
        public PanelSlots {
            Objects.requireNonNull(id, "id");
            groups = List.copyOf(groups);
        }
    }

    private final MenuType<?> menuType;
    private final List<PanelBuilder> panels = new ArrayList<>();

    SlotLayout(MenuType<?> menuType) {
        this.menuType = Objects.requireNonNull(menuType, "menuType");
    }

    /** Adds a panel, configured by {@code config}. Declaration order is slot order. */
    public SlotLayout panel(String id, Consumer<PanelBuilder> config) {
        PanelBuilder pb = new PanelBuilder(id);
        config.accept(pb);
        panels.add(pb);
        return this;
    }

    /** Builds the handler: its groups, their pairings, then its slots. */
    public CustomContainerMenu build(int syncId) {
        List<PanelSlots> declared = new ArrayList<>();
        Map<String, SlotGroup> byRef = new HashMap<>();   // "panelId.groupId" -> group
        for (PanelBuilder pb : panels) {
            List<SlotGroup> groups = new ArrayList<>();
            for (GroupConfig gc : pb.groups) {
                SlotGroup group = new SlotGroup(gc.id, gc.category, gc.storage, gc.priority,
                        gc.columns, gc.rowGapAfter, gc.rowGapSize);
                if (gc.rightClick != null) group.setRightClickHandler(gc.rightClick);
                groups.add(group);
                byRef.put(pb.id + "." + gc.id, group);
            }
            declared.add(new PanelSlots(pb.id, !pb.hidden, pb.toggleable, groups));
        }
        // Directional pairings, by reference. A typo is warned, not thrown: a missing
        // pairing only changes shift-click order.
        for (PanelBuilder pb : panels) {
            for (GroupConfig gc : pb.groups) {
                SlotGroup source = byRef.get(pb.id + "." + gc.id);
                for (String ref : gc.pairs) {
                    SlotGroup target = byRef.get(ref);
                    if (target != null) {
                        source.pairsWith(target);
                    } else {
                        LOGGER.warn("[SlotLayout] pairsWith target '{}' not found for group '{}'; "
                                + "check the panel and group ids; pairing skipped", ref, pb.id + "." + gc.id);
                    }
                }
            }
        }
        return new CustomContainerMenu(menuType, syncId, declared);
    }

    /** One panel's slot groups and its server-side flags. */
    public static final class PanelBuilder {
        private final String id;
        private final List<GroupConfig> groups = new ArrayList<>();
        private boolean hidden = false;
        private boolean toggleable = false;

        PanelBuilder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /**
         * Adds a slot group (priority 100, columns from its size). Structure only:
         * per-slot rules (gate, binding, mending, shift-click) are declared by address
         * in the engine, ideally in {@link CustomMenu.Builder#arm}.
         */
        public PanelBuilder group(String id, SlotGroupCategory category, Storage storage) {
            return group(id, category, storage, 100, -1, -1, 0);
        }

        /** Adds a slot group with a shift-click priority (higher is tried first). */
        public PanelBuilder group(String id, SlotGroupCategory category, Storage storage, int priority) {
            return group(id, category, storage, priority, -1, -1, 0);
        }

        /** Adds a slot group with a priority and a column count. */
        public PanelBuilder group(String id, SlotGroupCategory category, Storage storage, int priority, int columns) {
            return group(id, category, storage, priority, columns, -1, 0);
        }

        /** Adds a slot group with a priority, a column count and a gap of {@code rowGapSize} after row {@code rowGapAfter}. */
        public PanelBuilder group(String id, SlotGroupCategory category, Storage storage, int priority, int columns,
                                  int rowGapAfter, int rowGapSize) {
            groups.add(new GroupConfig(id, category, storage, priority, columns, rowGapAfter, rowGapSize));
            return this;
        }

        /**
         * A right-click handler for the last-added group; a right click on one of its
         * slots runs it instead of vanilla's click. It runs on both sides, like any click:
         * the client's run is its prediction and the server's is the one that counts. Put
         * an effect that belongs to one side behind {@code player.level().isClientSide()}.
         */
        public PanelBuilder rightClick(BiConsumer<Player, CreatedSlot> handler) {
            last().rightClick = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * Pairs the last-added group with a target group, tried first when shift-clicking
         * out of it ({@code "panelId.groupId"} resolved at build). Call once per target.
         */
        public PanelBuilder pairsWith(String targetPanelId, String targetGroupId) {
            last().pairs.add(targetPanelId + "." + targetGroupId);
            return this;
        }

        /** The panel starts hidden; the server shows it ({@link CustomContainerMenu#setPanelVisible}). */
        public PanelBuilder hidden() {
            this.hidden = true;
            return this;
        }

        /**
         * A player may show and hide this panel (its toggle key or
         * {@code CustomContainerScreen.togglePanel}). Without it the server refuses the request.
         */
        public PanelBuilder toggleable() {
            this.toggleable = true;
            return this;
        }

        private GroupConfig last() {
            if (groups.isEmpty()) {
                throw new IllegalStateException("SlotLayout panel '" + id + "': add a group(...) before rightClick or pairsWith");
            }
            return groups.get(groups.size() - 1);
        }
    }

    private static final class GroupConfig {
        final String id;
        final SlotGroupCategory category;
        final Storage storage;
        final int priority, columns, rowGapAfter, rowGapSize;
        final List<String> pairs = new ArrayList<>();
        @Nullable BiConsumer<Player, CreatedSlot> rightClick;

        GroupConfig(String id, SlotGroupCategory category, Storage storage, int priority, int columns,
                    int rowGapAfter, int rowGapSize) {
            this.id = id;
            this.category = category;
            this.storage = storage;
            this.priority = priority;
            this.columns = columns;
            this.rowGapAfter = rowGapAfter;
            this.rowGapSize = rowGapSize;
        }
    }
}
