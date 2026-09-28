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
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final List<CreatedGroupResolver> UNIVERSAL = new ArrayList<>();

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
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroupCategories.register(" + menuClass.getName() + ")");
        SlotGroupResolver existing = RESOLVERS.get(menuClass);
        if (existing != null) {
            // Which of two mods would win depends on load order, which Fabric does not
            // define (§0063): the second registration is an error, not a silent no-op.
            throw new IllegalStateException("SlotGroupCategories: a resolver for " + menuClass.getName()
                    + " is already registered. One menu class has one primary resolver; a second mod "
                    + "adds groups to it with extend(...).");
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
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroupCategories.extend(" + menuClass.getName() + ")");
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
     * Each contribution becomes its own {@link ResolvedSlotGroup}, never folded into
     * the category it declares — several created groups can share one category and
     * they do not share a bounding box. Library-internal seat; consumers declare a
     * created group's category on its {@code SlotSpec} and never call this.
     */
    @ApiStatus.Internal
    public static void extendEvery(CreatedGroupResolver resolver) {
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroupCategories.extendEvery");
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
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroupCategories.declare(" + category + ")");
        record(category);
    }

    /** A category recorded because a group declared it; see {@link SlotGroups#declareDerived}. */
    static void record(SlotGroupCategory category) {
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
     * Every slot group on {@code menu}, each with its own identity and its own
     * slots — the ANCHOR view, the one with geometry in it. A panel anchors to one
     * of these; a group has a bounding box and a category does not.
     *
     * <p>Resolution order: the primary resolver for {@code menu.getClass()} (if
     * any), then each extension in registration order, then every universal
     * resolver ({@link #extendEvery}). Each vanilla category yields one group;
     * an extension category that collides with an already-claimed one is dropped
     * with a warn log per {@link #extend}'s policy. Each created contribution
     * yields its own group.
     *
     * <p>Empty for menus where nothing resolves.
     */
    public static List<ResolvedSlotGroup> groups(AbstractContainerMenu menu) {
        if (menu == null) return List.of();
        Class<?> menuClass = menu.getClass();
        SlotGroupResolver primary = RESOLVERS.get(menuClass);
        List<SlotGroupResolver> extensions = EXTENSIONS.getOrDefault(menuClass, List.of());

        if (primary == null && extensions.isEmpty() && UNIVERSAL.isEmpty()) return List.of();

        List<ResolvedSlotGroup> out = new ArrayList<>();
        Set<SlotGroupCategory> claimed = new HashSet<>();
        if (primary != null) {
            addVanillaGroups(menu, primary.resolve(menu), out, claimed, false);
        }
        // Extensions carry no id to sort by, so a collision between them must not be
        // settled by which registered first (load order, §0063): a category two
        // extensions both claim is dropped from both. The primary still wins over any.
        if (!extensions.isEmpty()) {
            List<Map<SlotGroupCategory, int[]>> resolved = new ArrayList<>(extensions.size());
            Map<SlotGroupCategory, Integer> claims = new java.util.HashMap<>();
            for (SlotGroupResolver ext : extensions) {
                Map<SlotGroupCategory, int[]> m = ext.resolve(menu);
                resolved.add(m);
                for (SlotGroupCategory c : m.keySet()) claims.merge(c, 1, Integer::sum);
            }
            for (Map<SlotGroupCategory, int[]> m : resolved) {
                Map<SlotGroupCategory, int[]> kept = new java.util.LinkedHashMap<>();
                for (Map.Entry<SlotGroupCategory, int[]> e : m.entrySet()) {
                    if (claims.get(e.getKey()) > 1) {
                        LOGGER.warn("[SlotGroupCategories] two extensions for {} claim category {}; dropped from both",
                                menu.getClass().getName(), e.getKey());
                    } else {
                        kept.put(e.getKey(), e.getValue());
                    }
                }
                addVanillaGroups(menu, kept, out, claimed, true);
            }
        }
        for (CreatedGroupResolver universal : UNIVERSAL) {
            addCreatedGroups(menu, universal.resolve(menu), out);
        }
        return List.copyOf(out);
    }

    /**
     * Every category on {@code menu} with its slots — the SEARCH view, which unions
     * a category's groups because "find me every inventory slot" wants the union.
     * Vanilla slots first, created ones appended, never listed twice.
     *
     * <p>For geometry use {@link #groups} instead: a category's union spans
     * scattered rectangles, so its bounding box means nothing.
     */
    public static Map<SlotGroupCategory, List<Slot>> of(AbstractContainerMenu menu) {
        List<ResolvedSlotGroup> resolved = groups(menu);
        if (resolved.isEmpty()) return Map.of();
        Map<SlotGroupCategory, List<Slot>> acc = new LinkedHashMap<>();
        for (ResolvedSlotGroup group : resolved) {
            List<Slot> slots = acc.computeIfAbsent(group.category(), k -> new ArrayList<>());
            for (Slot slot : group.slots()) {
                if (!containsIdentity(slots, slot)) slots.add(slot);
            }
        }
        Map<SlotGroupCategory, List<Slot>> frozen = new LinkedHashMap<>();
        acc.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * One group per category a vanilla resolver contributes. A category already
     * claimed is dropped — an extension may add categories, never redefine one.
     */
    private static void addVanillaGroups(AbstractContainerMenu menu,
            Map<SlotGroupCategory, int[]> indices, List<ResolvedSlotGroup> out,
            Set<SlotGroupCategory> claimed, boolean warnOnCollision) {
        // In slot order, whatever map the resolver returned: a HashMap's or
        // Map.copyOf's iteration order varies per JVM run, and the groups' order is
        // what listings, stacking and "first group" readers see.
        List<Map.Entry<SlotGroupCategory, int[]>> ordered = new ArrayList<>(indices.entrySet());
        ordered.sort(java.util.Comparator.comparingInt(e -> e.getValue().length == 0 ? Integer.MAX_VALUE
                : java.util.Arrays.stream(e.getValue()).min().getAsInt()));
        for (Map.Entry<SlotGroupCategory, int[]> entry : ordered) {
            SlotGroupCategory category = entry.getKey();
            if (claimed.contains(category)) {
                if (warnOnCollision) {
                    LOGGER.warn("[SlotGroupCategories] extension for {} tried to redefine " +
                            "category {} the primary resolver claims; dropping it",
                            menu.getClass().getName(), category);
                }
                continue;
            }
            List<Slot> slots = deref(menu, entry.getValue());
            if (slots.isEmpty()) continue;      // absent, per the resolver contract
            claimed.add(category);
            out.add(new ResolvedSlotGroup(SlotGroupId.category(category), category, slots));
        }
    }

    /** One group per created contribution, named by its own declaration. */
    private static void addCreatedGroups(AbstractContainerMenu menu,
            List<CreatedGroupResolver.Contribution> contributions, List<ResolvedSlotGroup> out) {
        for (CreatedGroupResolver.Contribution c : contributions) {
            List<Slot> slots = deref(menu, c.slotIndices());
            if (slots.isEmpty()) continue;
            out.add(new ResolvedSlotGroup(
                    SlotGroupId.created(c.panelId(), c.groupId()), c.category(), slots));
        }
    }

    /** Index array to slots, skipping out-of-range indices defensively. */
    private static List<Slot> deref(AbstractContainerMenu menu, int[] indices) {
        if (indices == null || indices.length == 0) return List.of();
        int slotCount = menu.slots.size();
        List<Slot> slots = new ArrayList<>(indices.length);
        for (int i : indices) {
            if (i < 0 || i >= slotCount) continue;
            slots.add(menu.slots.get(i));
        }
        return slots;
    }

    private static boolean containsIdentity(List<Slot> slots, Slot slot) {
        for (Slot s : slots) if (s == slot) return true;
        return false;
    }
}
