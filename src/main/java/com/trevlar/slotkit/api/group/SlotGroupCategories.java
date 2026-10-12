package com.trevlar.slotkit.api.group;

import com.trevlar.slotkit.core.CreatedGroupResolver;
import com.trevlar.slotkit.api.group.SlotGroupCategory;

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
 * The registry of {@link SlotGroupResolver}s, one primary per menu class. Shaped
 * like MenuKit's {@code MenuChrome}: exact-class lookup, one primary resolver per class,
 * SlotKit's resolvers for vanilla classes, and mods registering their own classes
 * through {@link #register}.
 *
 * <p>A mod that adds slots to a vanilla menu and wants its slot group targeted by
 * slot-group panels declares the group's category through {@link #extend}, which
 * only adds categories and leaves the primary resolver's alone.
 *
 * <h3>Exact class only</h3>
 *
 * {@link #of(AbstractContainerMenu)} looks up by {@code menu.getClass()}, with no
 * walk up the class hierarchy. Vanilla's subclassing doesn't predict slot layout
 * ({@code CraftingMenu} extends {@code AbstractCraftingMenu}, which extends
 * {@code RecipeBookMenu}, but each concrete menu orders its slots its own way).
 * Mods register for their own concrete menu classes.
 *
 * <h3>Created slots publish here too</h3>
 *
 * Every created slot group declares a {@link SlotGroupCategory} on its spec, and
 * those groups are reported on whatever menu they sit. So {@link #of} and
 * {@link #categoriesBySlot} name created and vanilla slots alike, using only
 * SlotKit types. This is how one mod finds another's slots without knowing what
 * kind they are.
 *
 * <h3>Primary vs. extensions</h3>
 *
 * Each class has at most one primary resolver (from {@link #register}) and
 * zero or more extension resolvers (from {@link #extend}). {@link #of}
 * runs the primary first, then each extension, merging their output.
 * Extensions can only add categories: one the primary already emitted is dropped
 * from the extension, and one that two extensions both emit is dropped from both,
 * each with a warning. So a category means the same slots in every mod.
 */
public final class SlotGroupCategories {

    private static final Logger LOGGER = LoggerFactory.getLogger("slotkit");

    private SlotGroupCategories() {}

    // Keyed by exact class. An empty result from of() means no slot groups
    // resolved (no resolver registered, or every resolver returned nothing).
    //
    // Plain HashMap: registration happens during Fabric's single-threaded init,
    // and declarations freeze after it, same as MenuChrome. Keep it a HashMap.

    private static final Map<Class<? extends AbstractContainerMenu>, SlotGroupResolver> RESOLVERS
            = new HashMap<>();

    private static final Map<Class<? extends AbstractContainerMenu>, List<SlotGroupResolver>> EXTENSIONS
            = new HashMap<>();

    // Resolvers that run on every menu class, after the class-specific ones. They
    // are for created slots: SlotKit adds them to any menu the player
    // opens, so their categories can't be keyed by menu class. Their contributions
    // merge into a category already present instead of being dropped (a created
    // group declared PLAYER_INVENTORY is player inventory), and the vanilla slots
    // of that category stay ahead of it in the list.

    /** Every category anyone has declared. SlotGroupCategory is a record, so a set dedupes it. */
    private static final Set<SlotGroupCategory> DECLARED = ConcurrentHashMap.newKeySet();

    /**
     * Registers the primary resolver for a concrete menu class. A second
     * registration for the same class throws: which mod won would depend on load
     * order. A second mod adds groups with {@link #extend} instead.
     *
     * <p>Called at mod init. SlotKit registers its vanilla resolvers from its own
     * initializer; mods register from their {@code ModInitializer}.
     *
     * @param menuClass the concrete menu class (typically the same class
     *                  that's registered as a MenuType)
     * @param resolver  the resolver
     * @param <T>       the menu type
     */
    public static <T extends AbstractContainerMenu> void register(
            Class<T> menuClass, SlotGroupResolver resolver) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotGroupCategories.register(" + menuClass.getName() + ")");
        SlotGroupResolver existing = RESOLVERS.get(menuClass);
        if (existing != null) {
            // Which of two mods would win depends on load order, which Fabric does not
            // define, so the second registration is an error, not a silent no-op.
            throw new IllegalStateException("SlotGroupCategories: a resolver for " + menuClass.getName()
                    + " is already registered. One menu class has one primary resolver; a second mod "
                    + "adds groups to it with extend(...).");
        }
        RESOLVERS.put(menuClass, resolver);
        LOGGER.info("[SlotGroupCategories] registered resolver for {}",
                menuClass.getSimpleName());
    }

    /**
     * Registers an extra resolver for a menu class, adding categories without
     * replacing the primary. A class can have several extensions.
     *
     * <p>For a mod that adds a slot to a vanilla menu (a {@code FurnaceMenu}, say)
     * and wants its slot group targeted by slot-group panels under a category of
     * its own. SlotKit's primary resolver for the vanilla menu is already
     * registered and cannot be replaced, so {@code extend} adds to it.
     *
     * <p>An extension adds categories and never redefines one. A category the
     * primary already emits is dropped from the extension at resolve time, and a
     * category two extensions both emit is dropped from both, each with a
     * warning. So {@code FURNACE_INPUT} means the same slots in every mod.
     *
     * <p>A class needs no primary for an extension to work.
     *
     * @param menuClass the concrete menu class
     * @param resolver  the extension resolver
     * @param <T>       the menu type
     */
    public static <T extends AbstractContainerMenu> void extend(
            Class<T> menuClass, SlotGroupResolver resolver) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotGroupCategories.extend(" + menuClass.getName() + ")");
        List<SlotGroupResolver> list = EXTENSIONS.computeIfAbsent(
                menuClass, k -> new ArrayList<>());
        list.add(resolver);
        LOGGER.info("[SlotGroupCategories] extended resolver for {} (extension #{})",
                menuClass.getSimpleName(), list.size());
    }

    /**
     * Publishes {@code category} to the registry so another mod can find it through
     * {@link #all()} without a menu open. Idempotent.
     *
     * <p>SlotKit declares the vanilla constants at init, and a created group's
     * category when the group registers, so a consumer
     * that mints a category only calls this when it wants the category listed before
     * anything is registered against it.
     */
    public static void declare(SlotGroupCategory category) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotGroupCategories.declare(" + category + ")");
        record(category);
    }

    /** A category recorded because a group declared it; see {@link SlotGroups#declareDerived}. */
    static void record(SlotGroupCategory category) {
        DECLARED.add(Objects.requireNonNull(category, "category"));
    }

    /**
     * Every category the registry knows: the vanilla constants, every category
     * a created slot group declared, and anything a mod {@link #declare}d. Sorted by
     * namespace then path so the listing is stable.
     *
     * <p>This is the discovery half of the registry ("what kinds of slot exist?"),
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
     * The category of each slot on {@code menu}, by slot identity: the inverse of
     * {@link #of}, for a consumer walking {@code menu.slots} and asking "what is
     * this one?" A slot in no category is absent. A vanilla slot and a
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
     * slots. This is the anchor view, the one with geometry in it. A panel anchors to one
     * of these; a group has a bounding box and a category does not.
     *
     * <p>Resolution order: the primary resolver for {@code menu.getClass()} (if
     * any), then each extension in registration order, then the created
     * groups on the menu. Each vanilla category yields one group; an
     * extension's category that collides is dropped with a warning, as
     * {@link #extend} describes. Each created contribution yields its own group.
     *
     * <p>Empty for menus where nothing resolves.
     */
    public static List<ResolvedSlotGroup> groups(AbstractContainerMenu menu) {
        if (menu == null) return List.of();
        Class<?> menuClass = menu.getClass();
        SlotGroupResolver primary = RESOLVERS.get(menuClass);
        List<SlotGroupResolver> extensions = EXTENSIONS.getOrDefault(menuClass, List.of());


        List<ResolvedSlotGroup> out = new ArrayList<>();
        Set<SlotGroupCategory> claimed = new HashSet<>();
        if (primary != null) {
            addVanillaGroups(menu, primary.resolve(menu), out, claimed, false);
        }
        // Extensions carry no id to sort by, so a collision between them must not be
        // settled by which registered first (that is load order): a category two
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
        // Created groups sit on whatever menu the player opened, so no menu class names
        // them: they are reported on every menu, each as its own group.
        addCreatedGroups(menu, com.trevlar.slotkit.core.CreatedSlotCategories.resolve(menu), out);
        return List.copyOf(out);
    }

    /**
     * Every category on {@code menu} with its slots. This is the search view, which unions
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
     * claimed is dropped: an extension may add categories, never redefine one.
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
            if (slots.isEmpty()) continue;      // empty means absent, per the resolver contract
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

    /** Index array to slots, skipping out-of-range indices. */
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
