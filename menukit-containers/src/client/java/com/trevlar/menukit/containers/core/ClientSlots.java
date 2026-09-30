package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.core.OutsideRegion;
import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.inject.ScreenMatcher;
import com.trevlar.menukit.inject.ScreenPanelAdapter;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of {@link MKCSlots#onto} (§0067): presenting a registered slot group
 * on screen. {@code onto(...).register()} adds the synced slot data to a menu; this draws
 * the slots and lets them take input. Call once from the client initializer, not per
 * menu construction.
 */
public final class ClientSlots {

    private ClientSlots() {}

    /**
     * A panel of {@link SlotElement}s for the group, laid out from the panel origin on
     * the 18px pitch, wired through a {@link ScreenPanelAdapter} on every screen
     * {@code screens} accepts. The ids are the ones given to {@code onto(...).panel(..)}
     * and {@code .group(..)}. Each element hides while its slot is inert (its reveal
     * predicate), and resolves the live slot every frame, so one call covers every
     * screen the slots appear on.
     *
     * @param tooltip shown over an empty slot, or {@code null}
     */
    public static void renderGroup(String panelId, String groupId, int count, int columns,
                                   PanelPosition position, int padding, ScreenMatcher screens,
                                   @Nullable Component tooltip) {
        int cols = Math.max(1, columns);
        var group = MKCSlots.groupId(panelId, groupId);
        List<PanelElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SlotElement.Builder b = SlotElement.builder().slot(group, i)
                    .at((i % cols) * MKCSlots.SLOT_PITCH, (i / cols) * MKCSlots.SLOT_PITCH);
            if (tooltip != null) b.tooltip(tooltip);
            elements.add(b.build());
        }
        // Presentation only, under its own id so it never collides with the data panel.
        // Transparent and without a background: a slot is not solid, so the panel claims
        // nothing and never eats input (vanilla owns the slot click).
        Panel panel = Panel.builder(panelId + ":present")
                .elements(elements)
                .style(PanelStyle.NONE)
                .position(position)
                .build()
                .opaque(false);
        new ScreenPanelAdapter(panel, padding).onMatching(screens);
    }

    /** {@link #renderGroup(String, String, int, int, PanelPosition, int, ScreenMatcher, Component)} with no tooltip. */
    public static void renderGroup(String panelId, String groupId, int count, int columns,
                                   PanelPosition position, int padding, ScreenMatcher screens) {
        renderGroup(panelId, groupId, count, columns, position, padding, screens, null);
    }

    /** Placed by region around the menu frame, at the default priority. */
    public static void renderGroup(String panelId, String groupId, int count, int columns,
                                   OutsideRegion region, int padding, ScreenMatcher screens) {
        renderGroup(panelId, groupId, count, columns, PanelPosition.region(region), padding, screens, null);
    }

    /** Placed by region around the menu frame, with a tooltip over empty slots. */
    public static void renderGroup(String panelId, String groupId, int count, int columns,
                                   OutsideRegion region, int padding, ScreenMatcher screens,
                                   @Nullable Component tooltip) {
        renderGroup(panelId, groupId, count, columns, PanelPosition.region(region), padding, screens, tooltip);
    }
}
