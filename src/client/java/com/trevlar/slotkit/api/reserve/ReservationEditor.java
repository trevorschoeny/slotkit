package com.trevlar.slotkit.api.reserve;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.Section;
import com.trevlar.menukit.api.element.Slider;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.element.TextField;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.layout.CrossAlign;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.reserve.Reservation.Match;
import com.trevlar.slotkit.api.window.SlotRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The reserve popup: one slot's {@link Reservation} edited over the screen it is on.
 * Inventory Plus's edit mode and Shulker Palette open it; it is the only way a player
 * reserves a slot.
 *
 * <pre>{@code
 * // Right-click in edit mode:
 * ReservationEditor.open(SlotRef.of(menu, slot, player));
 * }</pre>
 *
 * <p>Top to bottom: a search box over a grid of every item (the creative search tab's,
 * each variant its own entry), exactly one of them selected; an Advanced fold holding
 * Count (a slider from 1 to the item's stack size) and the three {@link Match} fields;
 * Remove and Done. It opens pre-filled from the slot's reservation,
 * or with no reservation from the item in the slot, so that item's name and enchantments
 * are there to match. Done applies it through {@link Reservations#reserve}, Remove clears
 * it, Escape leaves it as it was. A new screen closes it unapplied.
 *
 * @since 2.4.0
 */
public final class ReservationEditor {

    private ReservationEditor() {}

    private static final int COLUMNS = 9, ROWS = 3, SIZE = SlotRendering.DEFAULT_SIZE, GAP = 4;

    // Vanilla's slot highlight, behind and in front of the selected item.
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

    // The popup's state. One popup at a time, so plain statics; open only on the screen it
    // opened on, so a screen change closes it without a hook.
    private static @Nullable Screen screen;
    private static @Nullable Slot slot;
    private static ItemStack item = ItemStack.EMPTY;
    private static int cap;
    private static final EnumSet<Match> match = EnumSet.noneOf(Match.class);
    private static String query = "";
    private static double scroll;
    private static boolean advanced;

    private static @Nullable TextField search;

    // Opening and closing

    /**
     * Opens the popup over the current screen for {@code ref}'s slot, a slot of the open
     * menu ({@link SlotRef#slot()}). Does nothing without one.
     */
    public static void open(SlotRef ref) {
        Minecraft mc = Minecraft.getInstance();
        if (ref.slot() == null || mc.player == null || mc.gui.screen() == null) return;
        Reservation r = Reservations.get(ref);
        slot = ref.slot();
        // An empty reservation starts from what the slot holds, so its name and
        // enchantments are the ghost's when Advanced asks to match them.
        item = r.isNone() ? slot.getItem().copyWithCount(1) : r.item();
        cap = r.cap();
        match.clear();
        match.addAll(r.match());
        advanced = !match.isEmpty();
        query = "";
        // The grid opens on the selection's row, so a pre-filled popup shows what it holds.
        int at = item.isEmpty() ? -1 : indexOf(entries(""), item);
        scroll = at < 0 ? 0 : (double) (at / COLUMNS) * SIZE;
        screen = mc.gui.screen();
        // Typing goes straight to the search.
        Focus.clear(screen);
        if (search != null && search.widget() != null) screen.setFocused(search.widget());
    }

    private static int indexOf(List<ItemStack> entries, ItemStack item) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).is(item.getItem())) return i;
        return -1;
    }

    /** Whether the popup is open on the current screen. */
    public static boolean isOpen() {
        return screen != null && Minecraft.getInstance().gui.screen() == screen;
    }

    /** The reservation the popup would apply now: {@link Reservation#NONE} with nothing selected. */
    public static Reservation draft() {
        return item.isEmpty() ? Reservation.NONE : new Reservation(item, cap, match);
    }

    /** Done: applies {@link #draft} to the slot and closes. */
    public static void done() {
        apply(draft());
    }

    /** Remove: clears the slot's reservation and closes. */
    public static void remove() {
        apply(Reservation.NONE);
    }

    /** Escape: closes, leaving the slot as it was. */
    public static void close() {
        screen = null;
        slot = null;
    }

    private static void apply(Reservation r) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (isOpen() && slot != null && player != null) Reservations.reserve(player, slot, r);
        close();
    }

    // Editing; what the popup's controls call, public for a consumer's own checks.

    /** Selects {@code entry}, one of it; a cap above its stack size comes down to it. */
    public static void pick(ItemStack entry) {
        item = entry.copyWithCount(1);
        cap = Math.min(cap, item.getMaxStackSize());
    }

    /** Count's slider: sets the cap directly, clamped to 1..the item's stack size. */
    public static void setCount(int count) {
        if (item.isEmpty()) return;
        cap = Math.max(1, Math.min(item.getMaxStackSize(), count));
    }

    /** Count as a fraction of the slider's track (1 at the left, the item's stack size at the right). */
    private static double countFraction() {
        int max = item.getMaxStackSize();
        if (max <= 1) return 0;
        int count = cap == 0 ? max : cap;
        return (count - 1) / (double) (max - 1);
    }

    /** The slider's drag/key callback: {@code f} back to a count, applied through {@link #setCount}. */
    private static void setCountFraction(double f) {
        int max = item.getMaxStackSize();
        if (max <= 1) return;
        setCount(1 + (int) Math.round(f * (max - 1)));
    }

    /** Turns an Advanced field on or off. */
    public static void setMatch(Match field, boolean on) {
        if (on) match.add(field);
        else match.remove(field);
    }

    /** The grid's entries for {@code q}: every item whose name or id has each word. */
    public static List<ItemStack> entries(String q) {
        Collection<ItemStack> tab = searchTab();
        // The grid asks several times a frame; search again only when the query or the tab changes.
        if (q.equals(cachedQuery) && tab == cachedFrom) return cached;
        cachedQuery = q;
        cachedFrom = tab;
        cached = search(List.copyOf(tab), q);
        return cached;
    }

    private static String cachedQuery = "";
    private static @Nullable Collection<ItemStack> cachedFrom;
    private static List<ItemStack> cached = List.of();

    private static List<ItemStack> search(List<ItemStack> all, String q) {
        String[] words = q.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (words.length == 1 && words[0].isEmpty()) return all;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : all) {
            String text = (stack.getHoverName().getString() + " "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem())).toLowerCase(Locale.ROOT);
            boolean every = true;
            for (String w : words) every &= text.contains(w);
            if (every) out.add(stack);
        }
        return out;
    }

    // ponytail: name and id only, built here from vanilla's creative search tab. MenuKit's
    // item picker searches tooltips too (an enchanted book by its enchantment), but its
    // search is internal; switch to it once MenuKit makes it public.
    private static Collection<ItemStack> searchTab() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return List.of();
        CreativeModeTabs.tryRebuildTabContents(p.connection.enabledFeatures(),
                p.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get(), p.level().registryAccess());
        return CreativeModeTabs.searchTab().getDisplayItems();
    }

    // The panel

    /** SlotKit's client init: declares the popup on every container screen, shown while open. */
    @ApiStatus.Internal
    public static void register() {
        search = TextField.builder().size(COLUMNS * SIZE, 20).fill()
                .maxLength(64)
                .hint(Component.translatable("slotkit.reserve.search"))
                .label(Component.translatable("slotkit.reserve.search"))
                .state(() -> query, q -> {
                    query = q;
                    scroll = 0;   // a new search starts at the top
                })
                // Enter picks the first match.
                .onSubmit(q -> entries(q).stream().findFirst().ifPresent(ReservationEditor::pick))
                // Escape reaches the focused box first: it closes the popup, as on the panel.
                .onCancel(ReservationEditor::close)
                .build();
        ScrollContainer grid = ScrollContainer.builder().fitWidth().height(ROWS * SIZE)
                .content(List.of(new Grid()))
                .state(() -> scroll, v -> scroll = v)
                .build();

        Slider<Double> countSlider = Slider.builder().size(120, 16)
                .state(ReservationEditor::countFraction, ReservationEditor::setCountFraction)
                .label(f -> Component.literal(String.valueOf(1 + (int) Math.round(f * (item.getMaxStackSize() - 1)))))
                .build();
        Flow count = Flow.builder().gap(GAP).crossAlign(CrossAlign.CENTER)
                // An item that doesn't stack has no count to cap.
                .visibleWhen(() -> item.getMaxStackSize() > 1)
                .add(TextLabel.builder().text(Component.translatable("slotkit.reserve.count")).build())
                .add(countSlider)
                .build();

        Section fold = Section.builder(Component.translatable("slotkit.reserve.advanced"))
                .state(() -> advanced, v -> advanced = v)
                .add(Flow.builder().vertical().gap(GAP)
                        .add(count)
                        .add(matchBox(Match.NAME, "slotkit.reserve.match_name"))
                        .add(matchBox(Match.ENCHANTMENTS, "slotkit.reserve.match_enchantments"))
                        .add(matchBox(Match.COMPONENTS, "slotkit.reserve.match_components"))
                        .build())
                .build();

        Flow buttons = Flow.builder().gap(GAP)
                .add(Flow.spacer())
                .add(Button.builder().label(Component.translatable("slotkit.reserve.remove")).size(60, 20)
                        .onClick(ReservationEditor::remove).build())
                .add(Button.builder().label(Component.translatable("gui.done")).size(60, 20)
                        .onClick(ReservationEditor::done).build())
                .build();

        Column.Builder column = Column.at(0, 0).spacing(GAP)
                // The selection, by name: the grid may have scrolled it out of sight.
                .add(TextLabel.builder().text(() -> item.isEmpty() ? Component.translatable("slotkit.reserve.title")
                        : Component.translatable("slotkit.reserve.title_item", item.getHoverName())).build())
                .add(search)
                .add(grid)
                .add(Divider.horizontal().build())
                .add(fold)
                .add(buttons);
        Panel panel = Panel.builder("slotkit:reserve")
                .elements(column.build())
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.center()))
                .build()
                .modal()
                .onEscape(ReservationEditor::close)
                .visibleWhen(ReservationEditor::isOpen);
        new ScreenPanelAdapter(panel);
    }

    private static Checkbox matchBox(Match field, String key) {
        return Checkbox.builder().label(Component.translatable(key))
                .state(() -> match.contains(field), on -> setMatch(field, on))
                .build();
    }

    /** The item grid: nine columns of every entry the search finds, the selected one highlighted. */
    private static final class Grid extends AbstractPanelElement {

        Grid() {
            this.width = this.authoredWidth = COLUMNS * SIZE;
        }

        private List<ItemStack> shown() {
            return entries(query);
        }

        @Override public int getHeight() { return Math.max(1, (shown().size() + COLUMNS - 1) / COLUMNS) * SIZE; }
        @Override public void layoutWithin(int budget) { width = COLUMNS * SIZE; }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private @Nullable ItemStack entryAt(int ox, int oy, double mx, double my) {
            double lx = mx - (ox + childX), ly = my - (oy + childY);
            if (lx < 0 || ly < 0 || lx >= COLUMNS * SIZE) return null;
            int i = (int) (ly / SIZE) * COLUMNS + (int) (lx / SIZE);
            List<ItemStack> shown = shown();
            return i < shown.size() ? shown.get(i) : null;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            Minecraft mc = Minecraft.getInstance();
            int gx = ctx.originX() + childX, gy = ctx.originY() + childY;
            List<ItemStack> shown = shown();
            if (shown.isEmpty()) {
                g.text(mc.font, Component.translatable("slotkit.reserve.no_match"), gx + 2, gy + 5,
                        textColor(ctx, TextRole.MUTED), false);
                return;
            }
            ItemStack hovered = ctx.hasMouseInput() && g.containsPointInScissor(ctx.mouseX(), ctx.mouseY())
                    ? entryAt(ctx.originX(), ctx.originY(), ctx.mouseX(), ctx.mouseY()) : null;
            // Only the rows in view: the scroll is in pixels from the top.
            int first = (int) (scroll / SIZE);
            int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
            for (int row = first; row < Math.min(rows, first + ROWS + 1); row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int i = row * COLUMNS + col;
                    if (i >= shown.size()) break;
                    ItemStack entry = shown.get(i);
                    int x = gx + col * SIZE, y = gy + row * SIZE;
                    boolean lit = entry == hovered || ItemStack.isSameItemSameComponents(entry, item);
                    SlotRendering.drawSlotBackground(g, x, y, SIZE, false);
                    if (lit) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - 3, y - 3, 24, 24);
                    g.fakeItem(entry, x + 1, y + 1);
                    if (lit) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - 3, y - 3, 24, 24);
                }
            }
            if (hovered != null) g.setTooltipForNextFrame(mc.font, hovered, ctx.mouseX(), ctx.mouseY());
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            ItemStack entry = entryAt(in.originX(), in.originY(), in.mouseX(), in.mouseY());
            if (entry == null) return false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            pick(entry);
            return true;
        }
    }
}
