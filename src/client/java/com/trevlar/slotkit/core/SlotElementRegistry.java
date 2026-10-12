package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.SlotElement;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The {@link SlotElement}s currently attached to an open screen. SlotKit's screen hook
 * uses it to resolve hover and click for slots hosted in panels.
 *
 * <p>The {@code getHoveredSlot} interception goes through {@link CreatedSlotScreenHook},
 * which needs to know which panels currently host a live {@link SlotElement} so it
 * resolves only those. This registry supplies that set, and the hook resolves them
 * through {@link CreatedSlotInput#resolveHoveredSlot}, which handles creative. So a
 * slot in a panel takes clicks and hovers correctly with no extra code.
 *
 * <p>A {@link SlotElement} adds itself in {@code onAttach} (when its panel is matched
 * to an opening screen) and removes itself in {@code onDetach} (when the screen
 * closes). The panel lifecycle keeps those balanced, so only the live screen's
 * SlotElements are ever here and a plain identity set is enough.
 *
 * <p>Client only: attach, detach and hover resolution all run on the render and
 * input thread, and the server never touches it.
 */
public final class SlotElementRegistry {

    // Identity-keyed so two SlotElements around equal-looking slots never collide.
    // Synchronized: rare writes (screen open and close), reads on the input thread.
    private static final Set<SlotElement> ACTIVE =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private SlotElementRegistry() {}

    public static void add(SlotElement element) {
        ACTIVE.add(element);
    }

    /** Removes a SlotElement when the screen detaches it. Safe to call twice. */
    public static void remove(SlotElement element) {
        ACTIVE.remove(element);
    }

    /** Whether any panel-hosted slot is currently attached. */
    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    /**
     * End of frame: parks every attached element's slot that no panel showed this
     * frame, and clears the marks for the next one. So a slot whose panel is hidden,
     * out of its region, or hidden by the window sits where vanilla neither draws nor
     * hit-tests it. A slot a panel did show keeps the position that panel wrote, so
     * between frames input matches what is on screen.
     */
    static void parkUnpresented(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        synchronized (ACTIVE) {
            for (SlotElement element : ACTIVE) {
                element.endFrame(menu);
            }
        }
    }

    /**
     * The panel ids that currently have at least one attached {@link SlotElement}.
     * The screen hook passes these as the filter for hover resolution, so only slots
     * hosted in those panels are resolved.
     */
    public static Set<String> activePanelIds() {
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) return Set.of();
            Set<String> ids = new HashSet<>(ACTIVE.size() * 2);
            for (SlotElement element : ACTIVE) {
                ids.add(element.panelId());
            }
            return ids;
        }
    }
}
