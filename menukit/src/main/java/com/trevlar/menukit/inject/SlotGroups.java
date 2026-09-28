package com.trevlar.menukit.inject;

import com.trevlar.menukit.window.Token;

import com.trevlar.menukit.window.PanelAddressing;

import com.trevlar.menukit.window.OwnerRef;

import com.trevlar.menukit.window.KindTag;

import com.trevlar.menukit.window.Address;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.trevlar.menukit.window.SlotRef;

import org.jetbrains.annotations.ApiStatus;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every slot group anyone has declared, with no menu open: the menu-free half of
 * the slot group registry. {@link SlotGroupCategories#groups} is the per-menu half,
 * the groups actually on a menu with their slots.
 *
 * <h2>Where declarations come from</h2>
 *
 * <ul>
 *   <li><b>Vanilla</b>: MenuKit declares one group per vanilla category at init
 *       ({@code SlotGroupId.category(category)}), the groups its resolvers produce. A
 *       mod that registers a resolver for its own menu declares its categories'
 *       groups the same way, with {@link #declare}.</li>
 *   <li><b>Created</b>: MenuKit-Containers declares each group as it registers. A
 *       container-panel group ({@code MKCContainerPanel.define(...).register()})
 *       registers at init, so it is listed from the title screen. A group built with
 *       a menu ({@code MKCSlots.onto(menu, player)}) registers when that menu is
 *       built, so it is listed only after the first one; to list it from the title
 *       screen, its mod declares it at init too.</li>
 * </ul>
 *
 * Declaring is idempotent. A group's category is fixed by its first declaration; a
 * later one may add the group to a {@link SlotGroupSet}. A contradiction (a second
 * category, a second set) is logged and ignored, never thrown: a listing is not
 * worth crashing a player's game over.
 *
 * <h2>For a settings screen</h2>
 *
 * {@link #listing()} is the player-facing list: each {@link Entry} is a lone group
 * or a set of groups, with a name and the categories in it (to filter by). It is
 * cached, and rebuilt only after a declaration changes it.
 *
 * <h2>Saving a choice</h2>
 *
 * <b>Save by group: {@link SlotGroupId#asString()} is the persisted key.</b> A choice
 * the player makes on a set's row is a choice for each group in
 * {@link Entry#groups()}; store it under each of their ids. A group's id never
 * changes, whatever sets exist, so a saved choice survives a mod adding, renaming
 * or dropping a set.
 *
 * {@link Entry#key()} is for the listing: it matches a row to what is shown. It is
 * not for saving.
 *
 * <h2>Who may put a group in a set</h2>
 *
 * Only the group's own declaration, in the group's own namespace (a category
 * group's category namespace; a created group's panel-id namespace), and only its
 * first. Anything else is an error (§0063): no mod can move another mod's group
 * between rows, and no row changes under a screen that has already shown it.
 *
 * <h2>Names</h2>
 *
 * Translatable, on keys derived from the id, the way slot operations are named; the
 * declaring mod ships the lines and a missing line shows the key:
 *
 * <pre>
 * slot_group.menukit.player_hotbar                    a vanilla group: slot_group.&lt;category namespace&gt;.&lt;category path&gt;
 * slot_group.inventorymax.equipment_elytra.elytra     a created group: slot_group.&lt;panel id&gt;.&lt;group id&gt;, lowercased, other characters as dots
 * slot_group_set.inventorymax.pockets                 a set: slot_group_set.&lt;namespace&gt;.&lt;path&gt;
 * </pre>
 *
 * A group inside a set is listed under the set's name, so its own line is optional.
 *
 * <h2>Source</h2>
 *
 * {@link #source} names the mod a row comes from, so a screen can say "Pockets
 * (Inventory Max)". It is {@code null} for vanilla's groups. For anything else it is
 * the mod whose namespace the row is named in: a set's namespace, a created group's
 * panel-id namespace (the part before the colon), or a mod's own category's
 * namespace. The name is that mod's display name from its {@code fabric.mod.json},
 * or the namespace itself when no loaded mod has that id. A created group whose
 * panel id has no namespace has no known source, and is {@code null} too.
 *
 * <h2>The group a slot is in</h2>
 *
 * {@link #of(SlotRef)} answers for one slot, the way a {@code SlotOperations} veto
 * sees it: the created group a mod declared for a created slot, the vanilla group
 * of a vanilla slot's category on its menu, and for a player-inventory slot with no
 * menu (world pickup) the vanilla group its index sits in. A veto uses it to tell a
 * hotbar slot from a pocket.
 */
public final class SlotGroups {

    private SlotGroups() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private record Declared(SlotGroupCategory category, @Nullable SlotGroupSet set) {}

    private static final Map<SlotGroupId, Declared> DECLARED = new ConcurrentHashMap<>();

    // A created slot's group, from its address: the address carries its panel's
    // registration key and the group id before its index, so this index (filled as
    // groups are declared) names the group without a menu. The group's category is
    // then the one recorded on the group (§0063).
    private static final Map<String, SlotGroupId.Created> CREATED_BY_ADDRESS = new ConcurrentHashMap<>();

    private static String createdKey(net.minecraft.resources.Identifier regKey, String groupId) {
        return regKey + "\0" + groupId;
    }

    /**
     * The created group a created slot's address belongs to, or {@code null} for
     * anything else (a vanilla slot's group depends on its menu; see
     * {@link #of(com.trevlar.menukit.window.SlotRef)}).
     */
    public static SlotGroupId.@Nullable Created groupOf(Address address) {
        if (address.kind() != KindTag.CREATED_SLOT) return null;
        if (!(address.owner() instanceof OwnerRef.NestedOwner owner)) return null;
        if (!(owner.parentToken() instanceof Token.RegToken reg)) return null;
        if (!(address.token() instanceof Token.DeclToken decl)) return null;
        int sep = decl.declId().lastIndexOf('\0');
        if (sep < 0) return null;
        return CREATED_BY_ADDRESS.get(createdKey(reg.regKey(), decl.declId().substring(0, sep)));
    }

    // Bumped on every change to DECLARED. Readers cache against it (listing() here,
    // SlotOperations.groups(op)), so a declaration invalidates them all at once.
    private static volatile long generation = 0;
    /** Set by the first read of a row: from then on no group may join a set. */
    private static volatile @Nullable List<Entry> listingCache;
    private static long listingGeneration = -1;

    /** How many times the declared groups have changed; a cache key for anything derived from them. */
    @ApiStatus.Internal
    public static long generation() {
        return generation;
    }

    // ── The group a slot is in ─────────────────────────────────────────────

    /**
     * Port (§0042): the created group a live slot belongs to, or {@code null} for a
     * slot that is not a created one. MenuKit-Containers installs it at init; with
     * MenuKit alone there are no created slots.
     */
    @FunctionalInterface
    @ApiStatus.Internal
    public interface CreatedGroupLookup {
        @Nullable SlotGroupId groupOf(Slot slot);
    }

    private static volatile CreatedGroupLookup createdLookup = slot -> null;

    /** MenuKit-Containers installs its created-slot group lookup here at init. */
    @ApiStatus.Internal
    public static void installCreatedGroupLookup(CreatedGroupLookup impl) {
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroups.installCreatedGroupLookup");
        createdLookup = Objects.requireNonNull(impl, "impl");
    }

    /**
     * The slot group {@code ref} is in, or {@code null} when nothing is known: a
     * created slot's own group; a vanilla slot's group on its menu, which is its
     * category's; a player-inventory slot's group by its index when the menu does
     * not say, or when there is no menu (world pickup). A slot in a container no
     * menu resolver covers, reached with no menu, is {@code null}.
     */
    public static @Nullable SlotGroupId of(SlotRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.slot() != null) {
            SlotGroupId created = createdLookup.groupOf(ref.slot());
            if (created != null) return created;
            if (ref.category() != null) return SlotGroupId.category(ref.category());
        }
        if (ref.container() instanceof Inventory) return playerInventoryGroup(ref.containerSlot());
        return null;
    }

    /** Vanilla's layout of the player's own inventory: hotbar, main grid, armour, offhand. */
    private static @Nullable SlotGroupId playerInventoryGroup(int index) {
        if (index < 0) return null;
        SlotGroupCategory category;
        if (index < 9) category = SlotGroupCategory.PLAYER_HOTBAR;
        else if (index < Inventory.INVENTORY_SIZE) category = SlotGroupCategory.PLAYER_INVENTORY;
        else if (index < Inventory.SLOT_OFFHAND) category = SlotGroupCategory.PLAYER_ARMOR;
        else if (index == Inventory.SLOT_OFFHAND) category = SlotGroupCategory.PLAYER_OFFHAND;
        else return null;
        return SlotGroupId.category(category);
    }

    // ── Declaring ──────────────────────────────────────────────────────────

    /** Declares a group and its category. Idempotent; see the class doc. */
    public static void declare(SlotGroupId id, SlotGroupCategory category) {
        declare(id, category, null);
    }

    /**
     * Declares a group, its category, and the set it belongs to ({@code null} for
     * none, or for "no opinion" when another declaration names the set). Declaring
     * a group declares its category in {@link SlotGroupCategories#all()} too.
     */
    public static void declare(SlotGroupId id, SlotGroupCategory category, @Nullable SlotGroupSet set) {
        com.trevlar.menukit.window.Declarations.requireOpen("SlotGroups.declare(" + id.asString() + ")");
        record(id, category, set);
    }

    /**
     * A group declared as a menu is built ({@code MKCSlots.onto}): an index MenuKit
     * fills, idempotent, so it stays legal after declarations freeze (§0063).
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void declareDerived(SlotGroupId id, SlotGroupCategory category) {
        record(id, category, null);
    }

    private static synchronized void record(SlotGroupId id, SlotGroupCategory category, @Nullable SlotGroupSet set) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        // Every conflict is an error at the second declaration (§0063): which of two
        // mods would win otherwise depends on load order, which Fabric does not define.
        if (id instanceof SlotGroupId.Category v && !v.category().equals(category)) {
            throw new IllegalStateException("SlotGroups: " + id.asString() + " is named by its category "
                    + v.category() + " and cannot be declared as " + category);
        }
        if (set != null && !set.namespace().equals(namespaceOf(id))) {
            throw new IllegalStateException("SlotGroups: " + id.asString() + " cannot join set " + set.asString()
                    + ": a group joins a set only from its own namespace (" + namespaceOf(id) + ")");
        }
        Declared was = DECLARED.get(id);
        if (was == null) {
            DECLARED.put(id, new Declared(category, set));
            if (id instanceof SlotGroupId.Created c) CREATED_BY_ADDRESS.put(createdKey(PanelAddressing.regKey(c.panelId()), c.groupId()), c);
            generation++;
            SlotGroupCategories.record(category);
            return;
        }
        if (!was.category().equals(category)) {
            throw new IllegalStateException("SlotGroups: " + id.asString() + " was declared as " + was.category()
                    + " and again as " + category);
        }
        if (set != null && !set.equals(was.set())) {
            throw new IllegalStateException("SlotGroups: " + id.asString() + " joins set " + set.asString()
                    + (was.set() != null ? " but is already in " + was.set().asString()
                    : " after its first declaration; declare the set with the group"));
        }
        // The same declaration again (a menu rebuilt, the derived path): idempotent.
    }

    /** The namespace a group belongs to: its category's for a vanilla group, its panel id's for a created one. */
    private static @Nullable String namespaceOf(SlotGroupId id) {
        return switch (id) {
            case SlotGroupId.Category v -> v.category().namespace();
            case SlotGroupId.Created c -> {
                int colon = c.panelId().indexOf(':');
                yield colon > 0 ? c.panelId().substring(0, colon) : null;
            }
        };
    }

    // ── Reading ────────────────────────────────────────────────────────────

    /** Every declared group: vanilla's in MenuKit's category order, then the rest by id text. */
    public static List<SlotGroupId> all() {
        List<SlotGroupId> out = new ArrayList<>(DECLARED.keySet());
        out.sort(ORDER);
        return List.copyOf(out);
    }

    /** The category {@code id} declared, or {@code null} if it was never declared. */
    public static @Nullable SlotGroupCategory categoryOf(SlotGroupId id) {
        Declared d = DECLARED.get(id);
        return d == null ? null : d.category();
    }

    /** The set {@code id} belongs to, or {@code null}. */
    public static @Nullable SlotGroupSet setOf(SlotGroupId id) {
        Declared d = DECLARED.get(id);
        return d == null ? null : d.set();
    }


    /**
     * One row of the player-facing list.
     *
     * @param key        the row's key, which every member's row shares; for the listing, not for saving
     * @param name       what to show
     * @param set        the set, or {@code null} for a lone group
     * @param groups     the groups the row stands for; one for a lone group
     * @param categories the distinct categories of those groups, to filter by
     * @param source     the mod the row comes from, or {@code null} for vanilla; see {@link #source}
     */
    public record Entry(String key, Component name, @Nullable SlotGroupSet set,
                        List<SlotGroupId> groups, List<SlotGroupCategory> categories,
                        @Nullable Component source) {

        /** The 5.1.0 shape, with no source; kept so code built against it still links. */
        public Entry(String key, Component name, @Nullable SlotGroupSet set,
                     List<SlotGroupId> groups, List<SlotGroupCategory> categories) {
            this(key, name, set, groups, categories, null);
        }
    }

    /**
     * The player-facing list: every lone group, and every set once in place of its
     * groups. Vanilla groups first in MenuKit's category order, then the rest by key.
     */
    public static synchronized List<Entry> listing() {
        long gen = generation;
        List<Entry> cached = listingCache;
        if (cached != null && listingGeneration == gen) return cached;
        List<Entry> built = buildListing();
        listingCache = built;
        listingGeneration = gen;
        return built;
    }

    private static List<Entry> buildListing() {
        List<Entry> out = new ArrayList<>();
        Map<SlotGroupSet, List<SlotGroupId>> sets = new LinkedHashMap<>();
        for (SlotGroupId id : all()) {
            Declared d = DECLARED.get(id);
            if (d.set() != null) {
                sets.computeIfAbsent(d.set(), k -> new ArrayList<>()).add(id);
            } else {
                out.add(new Entry(id.asString(), name(id), null, List.of(id), List.of(d.category()), source(id)));
            }
        }
        sets.forEach((set, ids) -> {
            LinkedHashSet<SlotGroupCategory> categories = new LinkedHashSet<>();
            for (SlotGroupId id : ids) categories.add(DECLARED.get(id).category());
            out.add(new Entry(set.asString(), name(set), set, List.copyOf(ids), List.copyOf(categories),
                    source(set)));
        });
        out.sort(Comparator.comparingInt((Entry e) -> e.set() == null ? rank(e.groups().get(0)) : Integer.MAX_VALUE)
                .thenComparing(Entry::key));
        return List.copyOf(out);
    }

    // ── Names ──────────────────────────────────────────────────────────────

    /** The translation key a group's name lives under; see the class doc. */
    public static String langKey(SlotGroupId id) {
        return switch (id) {
            case SlotGroupId.Category v -> "slot_group." + v.category().namespace() + "." + v.category().path();
            case SlotGroupId.Created c -> "slot_group." + keyPart(c.panelId()) + "." + keyPart(c.groupId());
        };
    }

    /** The translation key a set's name lives under. */
    public static String langKey(SlotGroupSet set) {
        return "slot_group_set." + set.namespace() + "." + set.path();
    }

    /** A group's display name. */
    public static Component name(SlotGroupId id) {
        return Component.translatable(langKey(id));
    }

    /** A set's display name. */
    public static Component name(SlotGroupSet set) {
        return Component.translatable(langKey(set));
    }

    // ── Source ─────────────────────────────────────────────────────────────

    /** The mod a group comes from, or {@code null} for vanilla's; see the class doc. */
    public static @Nullable Component source(SlotGroupId id) {
        return switch (id) {
            case SlotGroupId.Category v -> SlotGroupCategory.vanilla().contains(v.category())
                    ? null : modName(v.category().namespace());
            case SlotGroupId.Created c -> {
                int colon = c.panelId().indexOf(':');
                yield colon > 0 ? modName(c.panelId().substring(0, colon)) : null;
            }
        };
    }

    /** The mod a set comes from: the one its namespace names. */
    public static Component source(SlotGroupSet set) {
        return modName(set.namespace());
    }

    /** A mod's display name from its metadata, or the namespace when no loaded mod has that id. */
    private static Component modName(String namespace) {
        String name = FabricLoader.getInstance().getModContainer(namespace)
                .map(mod -> mod.getMetadata().getName())
                .orElse(namespace);
        return Component.literal(name);
    }

    // Lang keys are lowercase dotted words; a panel id carries a colon. Two ids that
    // flatten alike share a name line, which costs only a label, never identity.
    private static String keyPart(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", ".");
    }

    // ── Order ──────────────────────────────────────────────────────────────

    private static final List<SlotGroupCategory> VANILLA_ORDER = SlotGroupCategory.vanilla();

    /** Vanilla groups by MenuKit's category order (player slots first); everything else after. */
    private static int rank(SlotGroupId id) {
        if (id instanceof SlotGroupId.Category v) {
            int i = VANILLA_ORDER.indexOf(v.category());
            if (i >= 0) return i;
        }
        return VANILLA_ORDER.size();
    }

    private static final Comparator<SlotGroupId> ORDER =
            Comparator.comparingInt(SlotGroups::rank).thenComparing(SlotGroupId::asString);
}
