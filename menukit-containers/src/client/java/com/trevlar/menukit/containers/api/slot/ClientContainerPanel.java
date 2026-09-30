package com.trevlar.menukit.containers.api.slot;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.window.Declarations;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The client half of a {@link ContainerPanel} (§0067): what only the client has,
 * declared from the client initializer.
 *
 * <pre>{@code
 * ClientContainerPanel.of("inventory-plus:pockets")
 *     .chrome(() -> List.of(Button.builder()...build()))   // the panel's own elements
 *     .parity(ScreenMatcher.allExcept(...));               // default: every container screen
 * }</pre>
 *
 * A container panel with neither needs no call here: its slots are presented on every
 * container screen. Containers builds each panel (chrome, then the slots as a
 * {@link SlotFlowElement}) and wires its adapter at client start, after every client
 * initializer has run.
 */
public final class ClientContainerPanel {

    private static final Map<String, ClientContainerPanel> BY_ID = new ConcurrentHashMap<>();

    private final String panelId;
    private volatile Supplier<List<PanelElement>> chrome = List::of;
    private volatile ScreenMatcher parity = ScreenMatcher.all();

    private ClientContainerPanel(String panelId) {
        this.panelId = panelId;
    }

    /** The client half of the container panel {@code panelId} (the id given to {@code define}). */
    public static ClientContainerPanel of(String panelId) {
        Declarations.requireOpen("ClientContainerPanel " + panelId);
        return BY_ID.computeIfAbsent(Objects.requireNonNull(panelId, "panelId"), ClientContainerPanel::new);
    }

    /**
     * The panel's own elements (buttons, labels, decorations), built once when the
     * panel is wired. The slots are added after them automatically.
     */
    public ClientContainerPanel chrome(Supplier<List<PanelElement>> chrome) {
        Declarations.requireOpen("ClientContainerPanel " + panelId + " chrome");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        return this;
    }

    /**
     * The screens that show the panel. Default {@link ScreenMatcher#all()}; opt one out
     * with {@link ScreenMatcher#allExcept}. The slot data is on every container menu
     * either way; this scopes only where it is shown.
     */
    public ClientContainerPanel parity(ScreenMatcher scope) {
        Declarations.requireOpen("ClientContainerPanel " + panelId + " parity");
        this.parity = Objects.requireNonNull(scope, "scope");
        return this;
    }

    /**
     * Builds each registered container panel (chrome, then its slots flowing and
     * wrapping to the panel's width) and wires a {@link ScreenPanelAdapter} scoped by its
     * parity. Called by Containers at client start, before declarations freeze.
     */
    @ApiStatus.Internal
    public static void wireAll() {
        for (ContainerPanel.Definition def : ContainerPanel.DEFINITIONS) {
            ClientContainerPanel client = BY_ID.getOrDefault(def.panelId(), new ClientContainerPanel(def.panelId()));
            List<PanelElement> elements = new ArrayList<>(client.chrome.get());

            // One SlotElement per slot, in declared order; the flow owns their
            // per-frame positions and wraps the visible ones to the panel's width.
            List<SlotElement> slots = new ArrayList<>();
            for (SlotSpec spec : def.slots()) {
                var group = ContainerPanel.groupId(def.panelId(), spec.groupId());
                for (int i = 0; i < spec.count(); i++) {
                    SlotElement.Builder b = SlotElement.builder().slot(group, i);
                    if (spec.tooltip() != null) b.tooltip(spec.tooltip());
                    slots.add(b.build());
                }
            }
            if (!slots.isEmpty()) elements.add(SlotFlowElement.builder().addAll(slots).build());

            Panel panel = Panel.builder(def.panelId())
                    .elements(elements)
                    .style(def.style())
                    .position(def.position())
                    .build()
                    .opaque(def.opaque());
            if (def.visibleWhen() != null) panel.visibleWhen(def.visibleWhen());
            if (def.pinnedHeight() >= 0) panel.pinnedHeight(def.pinnedHeight());
            if (def.pinnedWidth() >= 0) panel.pinnedWidth(def.pinnedWidth());
            new ScreenPanelAdapter(panel, def.padding()).onMatching(client.parity);
        }
    }
}
