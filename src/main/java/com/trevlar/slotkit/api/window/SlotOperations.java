package com.trevlar.slotkit.api.window;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.window.ClickTags;
import com.trevlar.slotkit.window.GroupIds;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroupSet;
import com.trevlar.slotkit.api.group.SlotGroups;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The <b>slot operations</b> vocabulary: the open set of things that can be done
 * <em>to</em> a slot, what each slot lets through, and the one question every
 * operation asks before it acts.
 *
 * <h2>What an operation is</h2>
 *
 * A slot has two independent facts about it, and keeping them apart is the whole
 * design:
 *
 * <ul>
 *   <li><b>Category</b>, what the slot <em>is</em>
 *       ({@link SlotGroupCategory}). Identity. A pocket declared
 *       {@code PLAYER_INVENTORY} is inventory storage, and every mod that searches
 *       the inventory should find it.</li>
 *   <li><b>Operations</b>, what may be <em>done to</em> it. Shift-click,
 *       double-click collect, drag-fill, a pickup landing in it, and whatever an
 *       inventory mod adds next.</li>
 * </ul>
 *
 * They are separate because the useful cases cross: a slot that <em>is</em>
 * inventory storage (so searches find it) may still want to sit out the bulk
 * gestures, and a slot in a bespoke category may want full vanilla participation.
 * Declaring the category does not decide the operations.
 *
 * <h2>The vocabulary is open</h2>
 *
 * An operation is named by a {@link BehaviorKey}, so <b>any mod can add one</b>: it
 * declares its own key, {@link #define(BehaviorKey) defines} it here so other mods
 * can discover it, ships a name and a description for it (below), asks
 * {@link #allows} in its own code before acting, and sends any clicks it performs
 * under {@link #as}. SlotKit needs no change for a new
 * operation to exist. Vanilla's own operations are {@link BehaviorKeys#VANILLA_OPERATIONS},
 * split one key per thing a player can do to a slot, and enforced at vanilla's seams
 * by SlotKit itself.
 *
 * <h2>Name and description</h2>
 *
 * A settings screen that lists operations needs words for them. {@link #name} and
 * {@link #description} are translatable components on keys derived from the id,
 * the way keybinds work; a mod ships the lines in its own lang file:
 *
 * <pre>
 * "slot_operation.mymod.restock_take": "Restock takes from",
 * "slot_operation.mymod.restock_take.description": "Auto-restock may pull a refill out of this slot."
 * </pre>
 *
 * A missing line shows the key itself, Minecraft's own fallback.
 *
 * <h2>Role</h2>
 *
 * {@link #define(BehaviorKey, Role)} records whether an operation takes items out
 * of a slot, puts items in, or both ({@link #role}). A lock that protects an item
 * already in a slot only cares about what takes it out, so a settings screen can
 * hide the rest for any mod's operations, not just vanilla's. An operation that
 * moves items between two slots is two keys, one {@code TAKE} and one {@code PUT}.
 *
 * <h2>Where an operation applies</h2>
 *
 * {@link #define(BehaviorKey, Role, AppliesTo)} also records which slot groups the
 * operation can act on at all, so a settings screen never offers a box the
 * operation could never touch ({@link #appliesTo(BehaviorKey, SlotGroupId)},
 * {@link #groups(BehaviorKey)}). An operation names the <em>vanilla</em> groups it
 * applies to; every group a mod adds applies automatically, unless the operation
 * opts out of it by id or by {@link SlotGroupSet}. An operation defined without
 * saying applies to every group, so nothing defined before this existed changes.
 * SlotKit declares vanilla's own operations from what vanilla does: nothing puts
 * into an output slot, the crafter's result can't be touched at all, double-click
 * collect skips the results vanilla's menus exclude, and a world pickup lands only
 * in the hotbar, the main inventory and the offhand.
 *
 * <p>This is a declaration, not a rule SlotKit enforces: vanilla's seams already act
 * only where vanilla does, and a mod's own operation acts where its code does. It is
 * what a consumer reads to know where an operation <em>can</em> reach.
 *
 * <h2>With no screen open</h2>
 *
 * Q, Ctrl-Q and F while playing are the same {@code DROP}, {@code DROP_STACK} and
 * {@code OFFHAND_SWAP} operations, on the selected hotbar slot; an offhand swap
 * also needs the offhand slot to allow it. To the player Q is Q, screen or not.
 *
 * <h2>Who says what a slot allows</h2>
 *
 * Two mechanisms, one subtractive:
 *
 * <ol>
 *   <li><b>The cascade</b> is what the slot's author declared, most specific wins:
 *       <pre>per-slot declaration  &gt;  the slot's group  &gt;  the group's category  &gt;  the key's default</pre>
 *       This is the window's ordinary cascade; the group and the category
 *       are two rungs inside its per-group level, ordered by
 *       {@link GroupKey#precedence()}. {@link #inherent} declares the category rung.
 *       A vanilla slot's group and category are one rung: vanilla contributes one
 *       group per category on a menu. Every built-in operation defaults to
 *       {@code TRUE}, so a slot nobody declared on is exactly vanilla.</li>
 *   <li><b>A veto</b> is a rule some other mod lays on top, and it can only say
 *       no. A player's lock is the case: the player's policy over whatever the
 *       slot's author allowed. Written as a per-slot declaration it would overwrite
 *       the author's own settings and have nothing to restore on unlock; as a
 *       {@link Veto} it sits beside the cascade and only subtracts.</li>
 * </ol>
 *
 * {@link #allows} is the one question: the cascade says yes and no veto says no.
 * A veto that decides by slot group registers as a {@link GroupVeto}: it is handed
 * the slot's group, resolved once per question ({@link SlotGroups#of}), rather than
 * working it out again on every slot of every click.
 *
 * <p>A veto that needs to see the item coming in registers as a {@link PutVeto}.
 * It is asked only by {@link #allowsPut}, which every put seam uses with the
 * stack it is about to place: "only into a container that already holds this
 * item" is a put veto.
 *
 * <h2>Simulated clicks</h2>
 *
 * A mod that performs its operation by sending vanilla clicks wraps them:
 *
 * <pre>SlotOperations.as(RESTOCK_TAKE, RESTOCK_PUT, () -&gt; sendClicks());</pre>
 *
 * Each click sent inside the block carries the operation it serves: the first for
 * the slot it takes from, the second for the slot it puts into. The vetoes judge
 * the carried operation, so a lock that refuses shift-click but allows restock lets
 * the restock's shift-click through. The slot's author still judges the gesture:
 * a group that sits out shift-click refuses a restock that arrives as a shift-click.
 * A mod that wants to know first asks {@code allows} about both. The tag reaches
 * the integrated server too. Clicks must be sent inside the block, on the calling
 * thread; a click scheduled for later is not tagged. A simulated click that is not
 * wrapped counts as the gesture it looks like, a manual click included.
 *
 * <p><b>Init-time reads are the one exception to "declare anywhere".</b> Every
 * built-in operation is SERVER tier, and SlotKit installs the server
 * tier from its own initializer. Fabric does not order entrypoints between mods,
 * so a consumer whose init runs first will see {@code resolve} answer the key's
 * library default, and a declaration it makes there is buffered until the tier
 * installs. Declare at init and read during play, as consumers normally do.
 *
 * <p>Thread-safe; every method is safe to call from mod init on any thread.
 */
public final class SlotOperations {

    private SlotOperations() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("slotkit");

    /**
     * What an operation does to the slot it acts on. A settings screen reads it to
     * know which operations matter for which kind of lock: a lock that protects an
     * item already in a slot cares what takes it out, and not what puts things in.
     */
    public enum Role {
        /** Takes items out of the slot: a click pickup, shift-click out, a drop. */
        TAKE,
        /** Puts items into the slot: a click place, shift-click in, a pickup landing. */
        PUT,
        /** Both, as a swap or a rotation does; also what an operation that does not say is taken to do. */
        BOTH
    }

    /**
     * Which slot groups an operation can act on. The vanilla groups are listed; any
     * other group applies unless it is excepted, by its id or by the set it is in.
     * "Other" is every group that is not one of vanilla's: a created group, and a
     * group named by a category a mod minted for its own menu.
     *
     * <pre>
     * AppliesTo.EVERY_GROUP                                            // the default
     * AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_INVENTORY)               // two vanilla groups, and every mod group
     * AppliesTo.vanillaExcept(FURNACE_OUTPUT).except(POCKETS_SET)      // every vanilla group but one; not pockets
     * AppliesTo.only(PLAYER_HOTBAR, PLAYER_INVENTORY)                  // two vanilla groups, and mod groups declared as one of them
     * </pre>
     *
     * @param vanilla      the vanilla categories whose groups this applies to
     * @param exceptGroups groups a mod added that this does not apply to
     * @param exceptSets   sets of groups a mod added that this does not apply to
     * @param only         a mod's group applies only when it declared one of the
     *                     {@code vanilla} categories (see {@link #only}); since 1.1.0
     */
    public record AppliesTo(Set<SlotGroupCategory> vanilla, Set<SlotGroupId> exceptGroups,
                            Set<SlotGroupSet> exceptSets, boolean only) {

        /** Every vanilla group and every group a mod adds. What an operation that does not say applies to. */
        public static final AppliesTo EVERY_GROUP =
                new AppliesTo(Set.copyOf(SlotGroupCategory.vanilla()), Set.of(), Set.of());

        /** The 1.0 form: a mod's own groups apply unless excepted. */
        public AppliesTo(Set<SlotGroupCategory> vanilla, Set<SlotGroupId> exceptGroups,
                         Set<SlotGroupSet> exceptSets) {
            this(vanilla, exceptGroups, exceptSets, false);
        }

        public AppliesTo {
            vanilla = Set.copyOf(vanilla);
            exceptGroups = Set.copyOf(exceptGroups);
            exceptSets = Set.copyOf(exceptSets);
            for (SlotGroupCategory c : vanilla) {
                if (!SlotGroupCategory.vanilla().contains(c)) {
                    throw new IllegalArgumentException("AppliesTo: " + c + " is not a vanilla category; a mod's "
                            + "own groups apply automatically, and opt out with except(...)");
                }
            }
        }

        /** These vanilla groups, and every group a mod adds. */
        public static AppliesTo vanilla(SlotGroupCategory... categories) {
            return new AppliesTo(Set.of(categories), Set.of(), Set.of());
        }

        /**
         * These vanilla groups, and a group a mod added only when its declared category
         * is one of them. A pocket declared {@code PLAYER_INVENTORY} applies under
         * {@code only(PLAYER_HOTBAR, PLAYER_INVENTORY)}; an elytra slot in its mod's own
         * category does not, so neither mod names the other. Since 1.1.0.
         */
        public static AppliesTo only(SlotGroupCategory... categories) {
            return new AppliesTo(Set.of(categories), Set.of(), Set.of(), true);
        }

        /** Every vanilla group but these, and every group a mod adds. */
        public static AppliesTo vanillaExcept(SlotGroupCategory... categories) {
            Set<SlotGroupCategory> in = new java.util.LinkedHashSet<>(SlotGroupCategory.vanilla());
            java.util.Arrays.asList(categories).forEach(in::remove);
            return new AppliesTo(in, Set.of(), Set.of());
        }

        /** The same, but not these groups a mod added. */
        public AppliesTo except(SlotGroupId... groups) {
            Set<SlotGroupId> out = new java.util.HashSet<>(exceptGroups);
            out.addAll(java.util.Arrays.asList(groups));
            return new AppliesTo(vanilla, out, exceptSets, only);
        }

        /** The same, but not the groups in these sets. */
        public AppliesTo except(SlotGroupSet... sets) {
            Set<SlotGroupSet> out = new java.util.HashSet<>(exceptSets);
            out.addAll(java.util.Arrays.asList(sets));
            return new AppliesTo(vanilla, exceptGroups, out, only);
        }

        /**
         * Whether this applies to {@code group}. A vanilla group answers by its
         * category. A group a mod added that declared a vanilla category is judged
         * by that category first (a pocket declared {@code PLAYER_INVENTORY} goes
         * where the inventory goes, no further), then by the exceptions. A group in
         * a category of its mod's own is judged by the exceptions alone, or, under
         * {@link #only}, never applies. The set is
         * read from what the group declared ({@link SlotGroups#setOf}).
         */
        public boolean test(SlotGroupId group) {
            if (group instanceof SlotGroupId.Category v && SlotGroupCategory.vanilla().contains(v.category())) {
                return vanilla.contains(v.category());
            }
            SlotGroupCategory declared = SlotGroups.categoryOf(group);
            boolean declaredVanilla = declared != null && SlotGroupCategory.vanilla().contains(declared);
            if (declaredVanilla && !vanilla.contains(declared)) return false;
            if (only && !declaredVanilla) return false;   // its own category: only() never names it
            if (exceptGroups.contains(group)) return false;
            SlotGroupSet set = SlotGroups.setOf(group);
            return set == null || !exceptSets.contains(set);
        }
    }

    private record Definition(BehaviorKey<?> key, Role role, AppliesTo appliesTo) {}

    /** Operations anyone has defined, in definition order. */
    private static final Map<Identifier, Definition> DEFINED =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** One {@link GroupKey} per category that has inherent operations; minted by {@link #inherent} only. */
    private static final Map<SlotGroupCategory, GroupKey> CATEGORY_GROUPS = new ConcurrentHashMap<>();

    // The vocabulary

    /**
     * Publishes {@code operation} so other mods can discover it through
     * {@link #all()}, with the {@link Role} it plays on a slot. Idempotent per id;
     * a second definition of the same id with a different key or a different role
     * is refused rather than silently replacing the first, because mods resolve
     * against whichever key they were compiled with.
     *
     * <p>Defining is not required to <em>use</em> a key, the window resolves any
     * {@link BehaviorKey}, it is how an operation becomes part of the shared
     * vocabulary instead of a private one.
     */
    public static void define(BehaviorKey<?> operation, Role role) {
        define(operation, role, AppliesTo.EVERY_GROUP);
    }

    /**
     * {@link #define(BehaviorKey, Role)} with the slot groups the operation can act
     * on; see "Where an operation applies" in the class doc. A second definition
     * with a different {@code appliesTo} is refused, as a different role is.
     */
    public static void define(BehaviorKey<?> operation, Role role, AppliesTo appliesTo) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotOperations.define(" + operation.id() + ")");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(appliesTo, "appliesTo");
        synchronized (DEFINED) {
            Definition existing = DEFINED.get(operation.id());
            if (existing != null && !existing.key().equals(operation)) {
                throw new IllegalStateException(
                        "SlotOperations: operation '" + operation.id() + "' is already defined by a "
                        + "different key. Two mods have claimed the same operation id; one of them "
                        + "must change its namespace.");
            }
            if (existing != null && existing.role() != role) {
                throw new IllegalStateException(
                        "SlotOperations: operation '" + operation.id() + "' is already defined with role "
                        + existing.role() + ", not " + role + ".");
            }
            if (existing != null && !existing.appliesTo().equals(appliesTo)) {
                throw new IllegalStateException(
                        "SlotOperations: operation '" + operation.id() + "' is already defined with different "
                        + "slot groups it applies to.");
            }
            DEFINED.putIfAbsent(operation.id(), new Definition(operation, role, appliesTo));
        }
    }

    /**
     * {@link #define(BehaviorKey, Role)} for an operation that does not say what it
     * does to a slot, which is read as {@link Role#BOTH}. A no-op for an operation
     * already defined, whatever its role.
     */
    public static void define(BehaviorKey<?> operation) {
        Objects.requireNonNull(operation, "operation");
        synchronized (DEFINED) {
            Definition existing = DEFINED.get(operation.id());
            if (existing != null && existing.key().equals(operation)) return;
            define(operation, Role.BOTH);
        }
    }

    /** Every defined operation, in definition order. */
    public static Collection<BehaviorKey<?>> all() {
        synchronized (DEFINED) {
            List<BehaviorKey<?>> out = new java.util.ArrayList<>(DEFINED.size());
            for (Definition d : DEFINED.values()) out.add(d.key());
            // Sorted by namespace, never by which mod loaded first. Within one namespace
            // the stable sort keeps the mod's own definition order, which is deliberate:
            // vanilla's eleven are ordered for a list.
            out.sort(java.util.Comparator.comparing(k -> k.id().getNamespace()));
            return List.copyOf(out);
        }
    }

    /** The defined operation with this id, or {@code null}. */
    public static @Nullable BehaviorKey<?> byId(Identifier id) {
        Definition d = DEFINED.get(id);
        return d == null ? null : d.key();
    }

    /** The role {@code operation} was defined with, or {@code null} if it was never defined. */
    public static @Nullable Role role(BehaviorKey<?> operation) {
        Definition d = DEFINED.get(operation.id());
        return d != null && d.key().equals(operation) ? d.role() : null;
    }

    /**
     * The slot groups {@code operation} was defined to apply to; {@link AppliesTo#EVERY_GROUP}
     * for one defined without saying, or never defined.
     */
    public static AppliesTo appliesTo(BehaviorKey<?> operation) {
        Definition d = DEFINED.get(operation.id());
        return d != null && d.key().equals(operation) ? d.appliesTo() : AppliesTo.EVERY_GROUP;
    }

    /** Whether {@code operation} can act on slots in {@code group}. */
    public static boolean appliesTo(BehaviorKey<?> operation, SlotGroupId group) {
        return appliesTo(operation).test(group);
    }

    /**
     * Every declared slot group ({@link SlotGroups#all()}) that {@code operation}
     * applies to, in that order: the list a settings screen offers for it. To filter
     * {@link SlotGroups#listing()} instead, keep an entry when any of its groups applies.
     */
    public static List<SlotGroupId> groups(BehaviorKey<?> operation) {
        // Cached per operation against the registry's generation, so a declaration
        // (or a group joining a set) since the last read rebuilds it and nothing else does.
        long gen = SlotGroups.generation();
        CachedGroups c = GROUPS_CACHE.get(operation.id());
        if (c != null && c.generation() == gen && c.key().equals(operation)) return c.groups();
        AppliesTo a = appliesTo(operation);
        List<SlotGroupId> out = new java.util.ArrayList<>();
        for (SlotGroupId id : SlotGroups.all()) if (a.test(id)) out.add(id);
        List<SlotGroupId> groups = List.copyOf(out);
        GROUPS_CACHE.put(operation.id(), new CachedGroups(operation, gen, groups));
        return groups;
    }

    private record CachedGroups(BehaviorKey<?> key, long generation, List<SlotGroupId> groups) {}

    private static final Map<Identifier, CachedGroups> GROUPS_CACHE = new ConcurrentHashMap<>();

    // Name and description

    /** The translation key an operation's name lives under: {@code slot_operation.<namespace>.<path>}. */
    public static String langKey(BehaviorKey<?> operation) {
        return "slot_operation." + operation.id().getNamespace() + "." + operation.id().getPath();
    }

    /** The operation's display name, for a settings list. */
    public static Component name(BehaviorKey<?> operation) {
        return Component.translatable(langKey(operation));
    }

    /** One or two sentences on what the operation does to a slot, for a settings list. */
    public static Component description(BehaviorKey<?> operation) {
        return Component.translatable(langKey(operation) + ".description");
    }

    // A category's inherent operations

    /**
     * Declares {@code value} as the default for {@code operation} on every slot in
     * {@code category} that does not declare its own (the category rung), while the
     * acting player's mode is in {@code scope}. A {@code SURVIVAL} or {@code CREATIVE}
     * declaration wins over a {@code BOTH} one on the same category in its mode. Reaches
     * created slots by address and vanilla slots through {@link #allows}, which
     * knows the menu.
     *
     * <p>Declare it wherever you mint the category, typically your mod's init.
     * Order does not matter: membership resolves per query, so groups registered
     * before this call pick it up too.
     */
    public static <V> void inherent(ModeScope scope, SlotGroupCategory category, BehaviorKey<V> operation, V value) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(value, "value");
        WindowEngine.setGroup(scope, mintGroupFor(category), operation, Decl.set(value));
    }

    /**
     * {@link #inherent(ModeScope, SlotGroupCategory, BehaviorKey, Object)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code inherent(ModeScope.BOTH, category, operation, value)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static <V> void inherent(SlotGroupCategory category, BehaviorKey<V> operation, V value) {
        inherent(ModeScope.BOTH, category, operation, value);
    }

    // The GroupKey for "every slot whose group declared this category", minted once per
    // category so two categories never share a binding. Its predicate answers for created
    // slots; allows() states a vanilla slot's membership itself.
    private static GroupKey mintGroupFor(SlotGroupCategory category) {
        return CATEGORY_GROUPS.computeIfAbsent(category, c -> new GroupKey(
                GroupIds.of("category", c.namespace() + "/" + c.path()),
                address -> {
                    // The category is recorded once, on the group.
                    com.trevlar.slotkit.api.group.SlotGroupId.Created g =
                            com.trevlar.slotkit.api.group.SlotGroups.groupOf(address);
                    return g != null && c.equals(com.trevlar.slotkit.api.group.SlotGroups.categoryOf(g));
                },
                GroupKey.PRECEDENCE_CATEGORY));
    }

    // Vetoes

    /**
     * A rule that refuses an operation on a slot, laid over the cascade by a mod
     * that is not the slot's author. Can only say no: returning {@code false}
     * leaves the cascade's answer alone. Consulted on every operation on every
     * slot, so keep it a lookup, not a scan.
     */
    @FunctionalInterface
    public interface Veto {
        boolean denies(SlotRef slot, BehaviorKey<?> operation);
    }

    /**
     * A {@link Veto} that is handed the slot's group: {@link SlotGroups#of} of the
     * slot, resolved once per question and shared by every group veto, or
     * {@code null} when the slot is in no known group. For a rule by group ("not in
     * my pockets", "a lock group"), which would otherwise work the group out again
     * on every slot of every click. A separate interface rather than a third
     * parameter on {@code Veto}, so every veto written against 5.1.0 still compiles
     * and still links.
     */
    @FunctionalInterface
    public interface GroupVeto {
        boolean denies(SlotRef slot, @Nullable SlotGroupId group, BehaviorKey<?> operation);
    }

    /**
     * A veto on putting a particular item into a slot, handed the stack about to
     * land ({@code incoming}, never empty; read it, don't change it) along with the
     * slot's group as a {@link GroupVeto} gets it. Asked only by
     * {@link #allowsPut}, which vanilla's put seams use: a click place, shift-click
     * in, drag-fill, a world pickup, and a click a mod sends under {@link #as}.
     * Swaps ask it on both slots. Since 2.3.0.
     */
    @FunctionalInterface
    public interface PutVeto {
        boolean denies(SlotRef slot, @Nullable SlotGroupId group, BehaviorKey<?> operation, ItemStack incoming);
    }

    /**
     * A cap on how many of an item a slot may hold, below its stack size: "at most 16
     * here". Handed the slot, its group as a {@link GroupVeto} gets it, and the stack
     * about to land ({@code incoming}, never empty; read it, don't change it). Answers
     * {@link #NO_LIMIT} when it has nothing to say. Every registered limit is asked and
     * the lowest answer wins, at every hand that fills a slot: a click, a shift-click, a
     * drag, a pickup, automation ({@link Automation#room}), and slots inside a box item.
     * A slot already holding that many of the item refuses the put. Since 3.0.0.
     */
    @FunctionalInterface
    public interface Limit {
        int max(SlotRef slot, @Nullable SlotGroupId group, ItemStack incoming);
    }

    /** What a {@link Limit} answers when it doesn't cap the slot. */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    private static final List<Limit> LIMITS = new CopyOnWriteArrayList<>();

    /**
     * Registers a {@link Limit} that applies while the acting player's mode is in
     * {@code scope}, read per question as {@link #veto(ModeScope, Veto)} is. Since 3.0.0.
     */
    public static void limit(ModeScope scope, Limit limit) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotOperations.limit");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(limit, "limit");
        LIMITS.add(scope == ModeScope.BOTH ? limit : new ScopedLimit(scope, limit));
    }

    /**
     * The most of {@code incoming} the slot may hold under every registered {@link Limit}:
     * the lowest answer, or {@link #NO_LIMIT} when none caps it (an empty {@code incoming}
     * included). Not clamped to the stack size; the caller takes the smaller. Since 3.0.0.
     */
    public static int limitOf(SlotRef ref, ItemStack incoming) {
        if (LIMITS.isEmpty() || incoming.isEmpty()) return NO_LIMIT;
        return limitOf(ref, SlotGroups.of(Objects.requireNonNull(ref, "ref")), incoming);
    }

    private static int limitOf(SlotRef ref, @Nullable SlotGroupId group, ItemStack incoming) {
        int max = NO_LIMIT;
        for (Limit limit : LIMITS) {
            if (BROKEN.contains(limit)) continue;
            try {
                max = Math.min(max, limit.max(ref, group, incoming));
            } catch (RuntimeException e) {
                disable(limit, e);
            }
        }
        return Math.max(0, max);
    }

    /** A limit that only speaks while the acting player's mode is in its scope. */
    private record ScopedLimit(ModeScope scope, Limit limit) implements Limit {
        @Override
        public int max(SlotRef slot, @Nullable SlotGroupId group, ItemStack incoming) {
            return scope.includes(slot.player()) ? limit.max(slot, group, incoming) : NO_LIMIT;
        }

        @Override
        public String toString() {
            return limit + " (" + scope + ")";
        }
    }

    /**
     * A preferred home for an item: "pickups of this item land here first". Handed the
     * slot, its group as a {@link GroupVeto} gets it, and the stack about to land
     * ({@code incoming}, never empty; read it, don't change it). Only reorders: within
     * each pass that picks a slot (a pickup's partial stacks, then its empty slots; a
     * shift-click's merge, then its empty slots), preferred slots are tried first and
     * the rest follow in vanilla's order. Vetoes and limits still judge every slot, so
     * a preferred slot that refuses is passed over. Consulted per slot per move, so
     * keep it a lookup. Since 3.0.0.
     */
    @FunctionalInterface
    public interface Preference {
        boolean prefers(SlotRef slot, @Nullable SlotGroupId group, ItemStack incoming);
    }

    private static final List<Preference> PREFERENCES = new CopyOnWriteArrayList<>();

    /**
     * Registers a {@link Preference} that applies while the acting player's mode is in
     * {@code scope}, read per question as {@link #veto(ModeScope, Veto)} is. Since 3.0.0.
     */
    public static void prefer(ModeScope scope, Preference preference) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotOperations.prefer");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(preference, "preference");
        PREFERENCES.add(scope == ModeScope.BOTH ? preference
                : (slot, group, incoming) -> scope.includes(slot.player()) && preference.prefers(slot, group, incoming));
    }

    /** Whether any registered {@link Preference} prefers this slot for {@code incoming}. Since 3.0.0. */
    public static boolean prefers(SlotRef ref, ItemStack incoming) {
        if (PREFERENCES.isEmpty() || incoming.isEmpty()) return false;
        SlotGroupId group = SlotGroups.of(Objects.requireNonNull(ref, "ref"));
        for (Preference preference : PREFERENCES) {
            if (BROKEN.contains(preference)) continue;
            try {
                if (preference.prefers(ref, group, incoming)) return true;
            } catch (RuntimeException e) {
                disable(preference, e);
            }
        }
        return false;
    }

    /** Whether any {@link Preference} is registered, so a move with none keeps vanilla's order untouched. */
    public static boolean anyPreference() {
        return !PREFERENCES.isEmpty();
    }

    private static final List<GroupVeto> VETOES = new CopyOnWriteArrayList<>();
    private static final List<PutVeto> PUT_VETOES = new CopyOnWriteArrayList<>();
    /** Whether any veto asked for the group, so a question with none never resolves it. */
    private static volatile boolean anyGroupVeto = false;

    // A veto that throws is a consumer's bug, and a click must not crash on it: it
    // is logged once and skipped from then on, so the log says which one and the
    // game keeps working with that veto silent.
    private static final Set<Object> BROKEN = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * Registers a veto that applies while the acting player's mode is in {@code scope}.
     * The mode is read on every question, so {@code /gamemode} with a screen open flips
     * it on the next click. With no acting player (automation) only a {@code BOTH} veto
     * applies. Typically once, at your mod's init.
     */
    public static void veto(ModeScope scope, Veto veto) {
        Objects.requireNonNull(veto, "veto");
        register(scope, new PlainVeto(veto));
    }

    /** {@link #veto(ModeScope, Veto)} for a veto that is handed the slot's group. */
    public static void veto(ModeScope scope, GroupVeto veto) {
        register(scope, Objects.requireNonNull(veto, "veto"));
        anyGroupVeto = true;
    }

    /**
     * {@link #veto(ModeScope, Veto)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code veto(ModeScope.BOTH, veto)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static void veto(Veto veto) {
        veto(ModeScope.BOTH, veto);
    }

    /**
     * {@link #veto(ModeScope, GroupVeto)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code veto(ModeScope.BOTH, veto)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static void veto(GroupVeto veto) {
        veto(ModeScope.BOTH, veto);
    }

    /**
     * Registers a {@link PutVeto} that applies while the acting player's mode is in
     * {@code scope}, read per question as {@link #veto(ModeScope, Veto)} is. Since 2.3.0.
     */
    public static void vetoPut(ModeScope scope, PutVeto veto) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotOperations.vetoPut");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(veto, "veto");
        PUT_VETOES.add(scope == ModeScope.BOTH ? veto : new ScopedPutVeto(scope, veto));
    }

    /** {@link #vetoPut(ModeScope, PutVeto)} in both modes. Since 2.3.0. */
    public static void vetoPut(PutVeto veto) {
        vetoPut(ModeScope.BOTH, veto);
    }

    /** A put veto that only speaks while the acting player's mode is in its scope. */
    private record ScopedPutVeto(ModeScope scope, PutVeto veto) implements PutVeto {
        @Override
        public boolean denies(SlotRef slot, @Nullable SlotGroupId group, BehaviorKey<?> operation, ItemStack incoming) {
            return scope.includes(slot.player()) && veto.denies(slot, group, operation, incoming);
        }

        @Override
        public String toString() {
            return veto + " (" + scope + ")";
        }
    }

    private static void register(ModeScope scope, GroupVeto veto) {
        com.trevlar.menukit.api.Declarations.requireOpen("SlotOperations.veto");
        Objects.requireNonNull(scope, "scope");
        VETOES.add(scope == ModeScope.BOTH ? veto : new ScopedVeto(scope, veto));
    }

    /** A veto that only speaks while the acting player's mode is in its scope. */
    private record ScopedVeto(ModeScope scope, GroupVeto veto) implements GroupVeto {
        @Override
        public boolean denies(SlotRef slot, @Nullable SlotGroupId group, BehaviorKey<?> operation) {
            return scope.includes(slot.player()) && veto.denies(slot, group, operation);
        }

        @Override
        public String toString() {
            return veto + " (" + scope + ")";
        }
    }

    /** A {@link Veto} run as a group veto, ignoring the group; logged under the consumer's own class. */
    private record PlainVeto(Veto veto) implements GroupVeto {
        @Override
        public boolean denies(SlotRef slot, @Nullable SlotGroupId group, BehaviorKey<?> operation) {
            return veto.denies(slot, operation);
        }

        @Override
        public String toString() {
            return veto.toString();
        }
    }

    // Simulated clicks

    /** Sends {@code clicks} as {@code operation}, for both the slots they take from and put into. */
    public static void as(BehaviorKey<TriBool> operation, Runnable clicks) {
        as(operation, operation, clicks);
    }

    /**
     * Sends {@code clicks} as the operation pair: {@code take} for the slots they
     * take from, {@code put} for the slots they put into. See the class doc.
     */
    public static void as(BehaviorKey<TriBool> take, BehaviorKey<TriBool> put, Runnable clicks) {
        Objects.requireNonNull(clicks, "clicks");
        ClickTags.run(new ClickTags.Tag(take, put), clicks);
    }

    // The one question

    /**
     * Whether {@code operation} may act on the slot {@code ref} describes: the
     * cascade resolves {@code TRUE} and no {@link Veto} denies it. Every operation,
     * vanilla's and a mod's own, asks this before touching a slot.
     */
    public static boolean allows(SlotRef ref, BehaviorKey<TriBool> operation) {
        return allows(ref, operation, ItemStack.EMPTY);
    }

    /**
     * {@link #allows}, then every {@link PutVeto} with the stack about to land.
     * What a put asks when it knows the item; an empty {@code incoming} puts nothing,
     * so no put veto is asked. Since 2.3.0.
     */
    public static boolean allowsPut(SlotRef ref, BehaviorKey<TriBool> operation, ItemStack incoming) {
        return allows(ref, operation, Objects.requireNonNull(incoming, "incoming"));
    }

    private static boolean allows(SlotRef ref, BehaviorKey<TriBool> operation, ItemStack incoming) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(operation, "operation");
        if (!cascade(ref, operation)) return false;
        boolean askPut = !incoming.isEmpty() && (!PUT_VETOES.isEmpty() || !LIMITS.isEmpty());
        SlotGroupId group = anyGroupVeto || askPut ? SlotGroups.of(ref) : null;   // once per question
        for (GroupVeto veto : VETOES) {
            if (BROKEN.contains(veto)) continue;
            try {
                if (veto.denies(ref, group, operation)) return false;
            } catch (RuntimeException e) {
                disable(veto, e);
            }
        }
        if (!askPut) return true;
        for (PutVeto veto : PUT_VETOES) {
            if (BROKEN.contains(veto)) continue;
            try {
                if (veto.denies(ref, group, operation, incoming)) return false;
            } catch (RuntimeException e) {
                disable(veto, e);
            }
        }
        // A slot at its limit has no room: refused, so a pickup or a shift-click looks
        // for the next slot rather than stopping on this one.
        if (LIMITS.isEmpty()) return true;
        ItemStack there = ref.container().getItem(ref.containerSlot());
        return !ItemStack.isSameItemSameComponents(there, incoming) || there.getCount() < limitOf(ref, group, incoming);
    }

    private static void disable(Object veto, RuntimeException e) {
        BROKEN.add(veto);
        LOGGER.error("[SlotKit] a slot operation veto, limit or preference threw and is disabled from here on: {}", veto, e);
    }

    /** {@link #allows(SlotRef, BehaviorKey)} for a slot on an open menu. */
    public static boolean allows(AbstractContainerMenu menu, Slot slot, @Nullable Player player,
                                 BehaviorKey<TriBool> operation) {
        return allows(SlotRef.of(menu, slot, player), operation);
    }

    /** {@link #allows(SlotRef, BehaviorKey)} for a slot reached with no menu open. */
    public static boolean allows(Container container, int containerSlot, @Nullable Player player,
                                 BehaviorKey<TriBool> operation) {
        return allows(SlotRef.of(container, containerSlot, player), operation);
    }

    /**
     * What a vanilla seam asks for the gesture it is running: {@link #allows}, or,
     * when the click on this thread carries an operation ({@link #as}), the slot's
     * cascade on the gesture and {@code allows} on the carried operation for the
     * gesture's role. Library seams only; a mod asks {@link #allows} about its own
     * operation.
     */
    @ApiStatus.Internal
    public static boolean allowsGesture(SlotRef ref, BehaviorKey<TriBool> gesture) {
        return allowsPutGesture(ref, gesture, ItemStack.EMPTY);
    }

    /**
     * {@link #allowsGesture} for a put seam that knows the stack about to land:
     * the put vetoes judge it along with the operation the gesture serves. Library
     * seams only; a mod asks {@link #allowsPut}. Since 2.3.0.
     */
    @ApiStatus.Internal
    public static boolean allowsPutGesture(SlotRef ref, BehaviorKey<TriBool> gesture, ItemStack incoming) {
        Objects.requireNonNull(incoming, "incoming");
        ClickTags.Tag tag = ClickTags.current();
        List<BehaviorKey<TriBool>> carried = tag == null ? null : ClickTags.carriedFor(tag, gesture);
        if (carried == null) return allows(ref, gesture, incoming);
        if (!cascade(ref, gesture)) return false; // the slot's author judges the gesture
        for (BehaviorKey<TriBool> op : carried) {
            if (!allows(ref, op, incoming)) return false;   // everyone else judges the operation it serves
        }
        return true;
    }

    /** {@link #allowsGesture(SlotRef, BehaviorKey)} for a slot on an open menu. */
    @ApiStatus.Internal
    public static boolean allowsGesture(AbstractContainerMenu menu, Slot slot, @Nullable Player player,
                                        BehaviorKey<TriBool> gesture) {
        return allowsGesture(SlotRef.of(menu, slot, player), gesture);
    }

    // A menu slot is addressed through the installed addressing rule. An off-menu slot
    // has an address only when its container has an identity; without one it falls to
    // the key's default and the vetoes decide.
    private static boolean cascade(SlotRef ref, BehaviorKey<TriBool> operation) {
        Address address = ref.address();
        if (address == null) return operation.libraryDefault().asBoolean(); // no identity to declare on
        // A vanilla slot's group and category come from its menu, so they are
        // stated here: its group rung (the one group per category on the menu) and,
        // when anyone declared inherent operations for it, its category rung. Read,
        // never mint, for the category: no binding means no membership to state.
        Collection<GroupKey> alsoMemberOf = List.of();
        if (ref.category() != null && address.kind() == KindTag.VANILLA_SLOT) {
            GroupKey group = GroupKey.of(com.trevlar.slotkit.api.group.SlotGroupId.category(ref.category()));
            GroupKey categoryGroup = CATEGORY_GROUPS.get(ref.category());
            alsoMemberOf = categoryGroup != null ? List.of(group, categoryGroup) : List.of(group);
        }
        return WindowEngine.resolve(address, operation, alsoMemberOf, ref.player()).asBoolean();
    }
}
