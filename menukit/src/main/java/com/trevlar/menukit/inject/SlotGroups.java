package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;

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
 *       ({@code SlotGroupId.vanilla(category)}), the groups its resolvers produce. A
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
 * or a set of groups, with a name, the categories in it (to filter by), and a
 * {@link Entry#key() key} to save a choice under. On a live menu,
 * {@link #entryKey(SlotGroupId)} turns any {@link ResolvedSlotGroup#id()} into the
 * key its choice was saved under, so a choice made against a set reaches every
 * group in it.
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
 */
public final class SlotGroups {

    private SlotGroups() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private record Declared(SlotGroupCategory category, @Nullable SlotGroupSet set) {}

    private static final Map<SlotGroupId, Declared> DECLARED = new ConcurrentHashMap<>();

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
    public static synchronized void declare(SlotGroupId id, SlotGroupCategory category, @Nullable SlotGroupSet set) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (id instanceof SlotGroupId.Vanilla v && !v.category().equals(category)) {
            LOGGER.warn("[SlotGroups] vanilla group {} is named by its category and cannot be declared as {}; ignored",
                    v.category(), category);
            return;
        }
        Declared was = DECLARED.get(id);
        if (was == null) {
            DECLARED.put(id, new Declared(category, set));
            SlotGroupCategories.declare(category);
            return;
        }
        if (!was.category().equals(category)) {
            LOGGER.warn("[SlotGroups] group {} was declared as {} and again as {}; keeping {}",
                    id.asString(), was.category(), category, was.category());
        }
        if (set == null || set.equals(was.set())) return;
        if (was.set() != null) {
            LOGGER.warn("[SlotGroups] group {} was put in set {} and again in {}; keeping {}",
                    id.asString(), was.set(), set, was.set());
            return;
        }
        DECLARED.put(id, new Declared(was.category(), set));
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
     * The key a choice about {@code id} is saved under: its set's
     * {@link SlotGroupSet#asString()} when it is in one, else its own
     * {@link SlotGroupId#asString()}. The same key as its {@link Entry}.
     */
    public static String entryKey(SlotGroupId id) {
        SlotGroupSet set = setOf(id);
        return set != null ? set.asString() : id.asString();
    }

    /**
     * One row of the player-facing list.
     *
     * @param key        what to save a choice under; {@link #entryKey} answers it for any member
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
    public static List<Entry> listing() {
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
            case SlotGroupId.Vanilla v -> "slot_group." + v.category().namespace() + "." + v.category().path();
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
            case SlotGroupId.Vanilla v -> SlotGroupCategory.vanilla().contains(v.category())
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
        if (id instanceof SlotGroupId.Vanilla v) {
            int i = VANILLA_ORDER.indexOf(v.category());
            if (i >= 0) return i;
        }
        return VANILLA_ORDER.size();
    }

    private static final Comparator<SlotGroupId> ORDER =
            Comparator.comparingInt(SlotGroups::rank).thenComparing(SlotGroupId::asString);
}
