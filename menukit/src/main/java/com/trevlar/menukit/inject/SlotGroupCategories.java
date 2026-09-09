package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Library-owned registry of per-menu-class {@link SlotGroupResolver}s.
 * Parallel to {@link MenuChrome} in shape — exact-class-only resolution,
 * first-registration-wins for the primary resolver, library-shipped
 * providers for vanilla classes, modded consumers register for their own
 * classes via {@link #register}.
 *
 * <p>Consumers registering into a vanilla menu (M4 slot-injection pattern) and
 * wanting their registered slot group to participate in SlotGroupContext
 * dispatch declare their category via {@link #extend} — additive,
 * collision-rejecting, preserves the library's first-wins guarantee for
 * {@code register}.
 *
 * <p>See {@code Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §5.3 for
 * the resolver design and §6 for the full vanilla coverage catalog.
 * See {@code Design Docs/Phase 12.5/V5_7_EXTEND_RESOLVER_FIX.md} for the
 * {@code extend} design note + collision policy.
 *
 * <h3>Resolution — exact-class only</h3>
 *
 * {@link #of(AbstractContainerMenu)} looks up by {@code menu.getClass()} —
 * no inheritance walk. Rationale matches {@code MenuChrome}: vanilla
 * subclass relationships don't reliably predict slot-layout relationships
 * (e.g., {@code CraftingMenu} extends {@code AbstractCraftingMenu} which
 * extends {@code RecipeBookMenu}, but each concrete menu has its own slot
 * ordering). Modded consumers register for their own concrete menu classes.
 *
 * <h3>Created slots publish here too</h3>
 *
 * Every MenuKit-Containers slot group declares a {@link SlotGroupCategory} on its
 * spec, and Containers registers one universal resolver ({@link #extendEvery})
 * that reports those groups on whatever menu they sit. So {@link #of} and
 * {@link #categoriesBySlot} name created and vanilla slots alike, with nothing but
 * MK types — the registry is the kind-blind way for one mod to find another's
 * slots.
 *
 * <h3>Primary vs. extensions</h3>
 *
 * Each class has at most one primary resolver (from {@link #register}) and
 * zero or more extension resolvers (from {@link #extend}). {@link #of}
 * runs the primary first, then each extension in registration order,
 * merging outputs. Extensions can only ADD new categories — an extension
 * emitting a category the primary (or an earlier extension) already
 * emitted is dropped with a warn log. This preserves library-defined
 * category meaning across the mod ecosystem.
 */
public final class SlotGroupCategories {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private SlotGroupCategories() {}

    // Exact-class-only maps — no inheritance walk. Empty return from
    // {@link #of} means "no slot groups resolved" (no resolver registered
    // or all resolvers returned empty).
    //
    // Plain HashMap, not ConcurrentHashMap — registration happens during
    // Fabric's single-threaded init phase. Matches existing library
    // registries (MenuChrome, SlotGroupCategories' original shape). Don't
    // drift to ConcurrentHashMap here.

    private static final Map<Class<? extends AbstractContainerMenu>, SlotGroupResolver> RESOLVERS
            = new HashMap<>();

    private static final Map<Class<? extends AbstractContainerMenu>, List<SlotGroupResolver>> EXTENSIONS
            = new HashMap<>();

    // Resolvers that run on EVERY menu class, after the class-specific ones. The
    // seat for created slots: MenuKit-Containers appends them to any menu the
    // player opens, so their categories can't be keyed by menu class. Their
    // contributions MERGE into an already-present category rather than being
    // dropped — a created group declared PLAYER_INVENTORY is player inventory,
    // and the vanilla slots of that category stay ahead of it in the list.
    private static final List<SlotGroupResolver> UNIVERSAL = new ArrayList<>();

    /**
     * Registers a resolver for a concrete menu class. First registration
     * wins; subsequent calls with the same class are no-ops with a warning
     * log (mirrors {@link MenuChrome#register}).
     *
     * <p>Called at mod init — library-shipped resolvers register during
     * {@code MKClient.onInitializeClient}; modded consumers register
     * from their own {@code ModInitializer}.
     *
     * @param menuClass the concrete menu class (typically the same class
     *                  that's registered as a MenuType)
     * @param resolver  the resolver
     * @param <T>       the menu type
     */
    /** Every category anyone has declared. Set semantics; SlotGroupCategory is a record. */
    private static final Set<SlotGroupCategory> DECLARED = ConcurrentHashMap.newKeySet();

    public static <T extends AbstractContainerMenu> void register(
            Class<T> menuClass, SlotGroupResolver resolver) {
        SlotGroupResolver existing = RESOLVERS.get(menuClass);
        if (existing != null) {
            LOGGER.warn("[SlotGroupCategories] resolver for {} already registered — ignoring " +
                    "second registration", menuClass.getName());
            return;
        }
        RESOLVERS.put(menuClass, resolver);
        LOGGER.info("[SlotGroupCategories] registered resolver for {}",
                menuClass.getSimpleName());
    }

    /**
     * Registers an additional resolver for a menu class, contributing
     * extra categories without replacing the primary. Multiple extensions
     * per class are allowed, applied in registration order.
     *
     * <p><b>Use case.</b> A consumer slots a slot into a vanilla menu
     * (e.g., {@code FurnaceMenu}) via M4 slot-injection and wants their
     * registered slot group to participate in SlotGroupContext dispatch
     * under a consumer-owned category. The library's primary resolver
     * for the vanilla menu is already registered and first-wins blocks
     * a replacement; {@code extend} is the additive path.
     *
     * <p><b>Collision policy — additive only, no redefinition.</b> If
     * this extension emits a {@link SlotGroupCategory} that the primary
     * resolver or an earlier extension already emitted for the same
     * class, the duplicate entry is dropped at resolve time with a warn
     * log. Earlier entry wins. This preserves library-defined category
     * meaning (e.g., {@code FURNACE_INPUT} means one slot list, consistent
     * across mods) and matches the first-wins-with-warn pattern of
     * {@link #register}.
     *
     * <p>The class does not need a primary registered first — extensions
     * can stand alone.
     *
     * @param menuClass the concrete menu class
     * @param resolver  the extension resolver
     * @param <T>       the menu type
     */
    public static <T extends AbstractContainerMenu> void extend(
            Class<T> menuClass, SlotGroupResolver resolver) {
        List<SlotGroupResolver> list = EXTENSIONS.computeIfAbsent(
                menuClass, k -> new ArrayList<>());
        list.add(resolver);
        LOGGER.info("[SlotGroupCategories] extended resolver for {} (extension #{})",
                menuClass.getSimpleName(), list.size());
    }

    /**
     * Registers a resolver that runs on <b>every</b> menu, after the class-specific
     * primary and extensions. Its categories merge: one the menu already resolved
     * gains the resolver's slots appended after the existing ones; a new category is
     * added. This is how created slots (MenuKit-Containers) publish their groups —
     * they sit on whatever menu the player opened, so no menu class names them.
     * Library-internal seat; consumers declare a created group's category on its
     * {@code SlotSpec} and never call this.
     */
    @ApiStatus.Internal
    public static void extendEvery(SlotGroupResolver resolver) {
        UNIVERSAL.add(resolver);
        LOGGER.info("[SlotGroupCategories] universal resolver #{} registered", UNIVERSAL.size());
    }

    /**
     * Publishes {@code category} to the registry so another mod can find it through
     * {@link #all()} without a menu open. Idempotent.
     *
     * <p>MenuKit declares its own vanilla constants at init, and MenuKit-Containers
     * declares a created group's category when the group registers, so a consumer
     * that mints a category only calls this when it wants the category listed before
     * anything is registered against it.
     */
    public static void declare(SlotGroupCategory category) {
        DECLARED.add(Objects.requireNonNull(category, "category"));
    }

    /**
     * Every category the registry knows: MenuKit's vanilla constants, every category
     * a created slot group declared, and anything a mod {@link #declare}d. Sorted by
     * namespace then path so the listing is stable.
     *
     * <p>This is the discovery half of the registry — "what kinds of slot exist?" —
     * and it needs no open menu. The other half is per-menu: {@link #of} for the
     * slots in each category right now, {@link #categoriesBySlot} for the inverse.
     */
    public static List<SlotGroupCategory> all() {
        List<SlotGroupCategory> out = new ArrayList<>(DECLARED);
        out.sort(Comparator.comparing(SlotGroupCategory::namespace)
                .thenComparing(SlotGroupCategory::path));
        return Collections.unmodifiableList(out);
    }

    /**
     * The category of each slot on {@code menu}, by slot identity — the inverse of
     * {@link #of}, for a consumer walking {@code menu.slots} and asking "what is
     * this one?" A slot in no category is absent. Kind-blind: a vanilla slot and a
     * created slot answer the same way, so a consumer can decide per category
     * ("search PLAYER_INVENTORY and this mod's pockets; leave its elytra slot
     * alone") without knowing which library put a slot there.
     */
    public static Map<Slot, SlotGroupCategory> categoriesBySlot(AbstractContainerMenu menu) {
        Map<SlotGroupCategory, List<Slot>> byCategory = of(menu);
        if (byCategory.isEmpty()) return Map.of();
        Map<Slot, SlotGroupCategory> out = new java.util.IdentityHashMap<>();
        for (Map.Entry<SlotGroupCategory, List<Slot>> e : byCategory.entrySet()) {
            for (Slot slot : e.getValue()) out.putIfAbsent(slot, e.getKey());
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Resolves the given menu instance's slot groups. Exact-class match on
     * {@code menu.getClass()} for the primary and extensions, then every
     * universal resolver ({@link #extendEvery}); returns an empty map for menus
     * where nothing resolves.
     *
     * <p>Runs the primary resolver first (if any), then each extension in
     * registration order. Extension-emitted categories that collide with
     * the accumulated output are dropped with a warn log per
     * {@link #extend}'s collision policy.
     *
     * <p>Called per-screen-open from
     * {@link ScreenPanelRegistry#onScreenInit}. The result is effectively
     * cached for the screen's lifetime.
     */
    public static Map<SlotGroupCategory, List<Slot>> of(AbstractContainerMenu menu) {
        if (menu == null) return Map.of();
        Class<?> menuClass = menu.getClass();
        SlotGroupResolver primary = RESOLVERS.get(menuClass);
        List<SlotGroupResolver> extensions = EXTENSIONS.getOrDefault(menuClass, List.of());

        if (primary == null && extensions.isEmpty() && UNIVERSAL.isEmpty()) return Map.of();

        // Resolvers speak in indices (the consumer never holds a raw Slot); the
        // library dereferences index → Slot here, where it already has the menu.
        Map<SlotGroupCategory, List<Slot>> out = new HashMap<>();
        if (primary != null) {
            deref(menu, primary.resolve(menu), out, Merge.DROP_COLLISION_SILENT);
        }
        for (SlotGroupResolver ext : extensions) {
            deref(menu, ext.resolve(menu), out, Merge.DROP_COLLISION_WARN);
        }
        for (SlotGroupResolver universal : UNIVERSAL) {
            deref(menu, universal.resolve(menu), out, Merge.APPEND);
        }
        return Map.copyOf(out);
    }

    private static boolean containsIdentity(List<Slot> slots, Slot slot) {
        for (Slot s : slots) if (s == slot) return true;
        return false;
    }

    /** How a resolver's category that the menu already resolved is treated. */
    private enum Merge { DROP_COLLISION_SILENT, DROP_COLLISION_WARN, APPEND }

    /**
     * Dereferences a resolver's category→index-array map onto {@code out} as
     * category→{@code List<Slot>}, applying the additive collision policy: an
     * extension category that collides with an already-accumulated one is dropped
     * with a warn log (earlier entry wins). Out-of-range and empty index arrays
     * are skipped defensively, matching {@link SlotGroupResolver}'s contract that
     * categories the menu doesn't contain are simply absent.
     */
    private static void deref(AbstractContainerMenu menu,
            Map<SlotGroupCategory, int[]> indices,
            Map<SlotGroupCategory, List<Slot>> out, Merge merge) {
        int slotCount = menu.slots.size();
        for (Map.Entry<SlotGroupCategory, int[]> entry : indices.entrySet()) {
            boolean present = out.containsKey(entry.getKey());
            if (present && merge != Merge.APPEND) {
                if (merge == Merge.DROP_COLLISION_WARN) {
                    LOGGER.warn("[SlotGroupCategories] extension for {} tried to redefine " +
                            "category {} — dropping (earlier entry wins)",
                            menu.getClass().getName(), entry.getKey());
                }
                continue;
            }
            int[] idx = entry.getValue();
            if (idx == null || idx.length == 0) continue;   // empty omitted per contract
            List<Slot> slots = new ArrayList<>(idx.length);
            if (present) slots.addAll(out.get(entry.getKey()));   // APPEND: existing first
            for (int i : idx) {
                if (i < 0 || i >= slotCount) continue;
                Slot slot = menu.slots.get(i);
                // APPEND never lists a slot twice: a consumer's own class resolver may
                // already name a created slot under the same category it declared.
                if (present && containsIdentity(slots, slot)) continue;
                slots.add(slot);
            }
            if (!slots.isEmpty()) out.put(entry.getKey(), List.copyOf(slots));
        }
    }
}
