package com.trevlar.menukit.window;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The <b>operations</b> vocabulary: the open set of bulk and shortcut actions that
 * can be performed <em>on</em> a slot, and what each slot lets through.
 *
 * <h2>What an operation is</h2>
 *
 * A slot has two independent facts about it, and keeping them apart is the whole
 * design:
 *
 * <ul>
 *   <li><b>Category</b> — what the slot <em>is</em>
 *       ({@link SlotGroupCategory}). Identity. A pocket declared
 *       {@code PLAYER_INVENTORY} is inventory storage, and every mod that searches
 *       the inventory should find it.</li>
 *   <li><b>Operations</b> — what may be <em>done to</em> it. Shift-click,
 *       double-click collect, drag-fill, and whatever a third-party inventory
 *       manager adds next.</li>
 * </ul>
 *
 * They are separate because the useful cases cross: a slot that <em>is</em>
 * inventory storage (so searches find it) may still want to sit out the bulk
 * gestures, and a slot in a bespoke category may want full vanilla participation.
 * Declaring the category should not silently decide the operations, and it does
 * not.
 *
 * <h2>The vocabulary is open</h2>
 *
 * An operation is named by a {@link BehaviorKey}, so <b>any mod can add one</b>: it
 * declares its own key, {@link #define(BehaviorKey) defines} it here so other mods
 * can discover it, and consults it in its own operation code. MenuKit needs no
 * change for a new operation to exist, and a slot that wants no part of that
 * operation says so through the same {@code Window.slot(addr).set(key, value)} call
 * it uses for the built-in ones. The built-ins are only the operations
 * <em>vanilla</em> ships:
 *
 * <table>
 *   <tr><th>Operation</th><th>Key</th><th>Vanilla gesture</th></tr>
 *   <tr><td>Collect</td><td>{@link BehaviorKeys#COLLECT}</td><td>double-click to sweep a type</td></tr>
 *   <tr><td>Drag fill</td><td>{@link BehaviorKeys#DRAG_FILL}</td><td>drag a carried stack across slots</td></tr>
 *   <tr><td>Quick move</td><td>{@code MKCBehaviorKeys.QUICK_MOVE}</td><td>shift-click</td></tr>
 * </table>
 *
 * <h2>A category's inherent operations</h2>
 *
 * {@link #inherent} declares what an operation does <em>by default for every slot
 * group in a category</em>. A mod that mints its own category says once that
 * nothing in it may be collected, rather than repeating {@code collect(false)} on
 * every group. A per-slot or per-group declaration still wins: the resolution order
 * is
 *
 * <pre>per-slot declaration  &gt;  category inherent  &gt;  the key's library default</pre>
 *
 * which is the window's ordinary cascade (§0055) with the category sitting at the
 * group level. Membership resolves per query, so a category's inherent operations
 * may be declared before or after the groups in it: registration order does not
 * matter.
 *
 * <p><b>Init-time reads are the one exception.</b> Every built-in operation is
 * SERVER tier, and MenuKit-Containers installs the server tier from its own
 * initializer. Fabric does not order entrypoints between mods, so a consumer whose
 * init runs first will see {@code resolve} answer the key's library default, and a
 * declaration it makes there is buffered until the tier installs. Declare at init
 * and read during play, as consumers normally do, and the question never arises.
 *
 * <p><b>Created slots.</b> Inherent operations reach created slots, whose category
 * is declared with the group and travels with it onto any menu. A <em>vanilla</em>
 * slot's category depends on the menu it is sitting in, which the menu-free window
 * engine cannot ask about, so a vanilla slot resolves an operation from its own
 * declaration or the library default. In practice that is the same answer: the
 * library default of every built-in operation is vanilla's behaviour, which is
 * exactly what a vanilla category's inherent operations would say.
 *
 * <p>Thread-safe; every method is safe to call from mod init on any thread.
 */
public final class SlotOperations {

    private SlotOperations() {}

    /** Operation keys anyone has defined, in definition order. */
    private static final Map<Identifier, BehaviorKey<?>> DEFINED =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** One {@link GroupKey} per category, carrying that category's inherent operations. */
    private static final Map<SlotGroupCategory, GroupKey> CATEGORY_GROUPS = new HashMap<>();

    /** Group ids handed out, so two categories can never share one (GroupKey is id-equal). */
    private static final Map<String, SlotGroupCategory> USED_IDS = new HashMap<>();

    // ── The category port (§0042) ──────────────────────────────────────────

    /**
     * Resolves a created slot's {@link Address} to the category its group declared.
     * MenuKit-Containers installs the implementation; MenuKit alone there are no
     * created slots, so the default answers {@code null} and no category group ever
     * matches.
     */
    @FunctionalInterface
    public interface CategoryLookup {
        @Nullable SlotGroupCategory categoryOf(Address address);
    }

    private static volatile CategoryLookup lookup = address -> null;

    /** MenuKit-Containers installs its created-slot category lookup here at init. */
    @ApiStatus.Internal
    public static void installCategoryLookup(CategoryLookup impl) {
        lookup = Objects.requireNonNull(impl, "impl");
    }

    // ── The vocabulary ─────────────────────────────────────────────────────

    /**
     * Publishes {@code operation} so other mods can discover it through
     * {@link #all()}. Idempotent per id; a second definition of the same id with a
     * different key is refused rather than silently replacing the first, because
     * mods resolve against whichever key they were compiled with.
     *
     * <p>Defining is not required to <em>use</em> a key — the window resolves any
     * {@link BehaviorKey} — it is how an operation becomes part of the shared
     * vocabulary instead of a private one.
     */
    public static void define(BehaviorKey<?> operation) {
        Objects.requireNonNull(operation, "operation");
        synchronized (DEFINED) {
            BehaviorKey<?> existing = DEFINED.get(operation.id());
            if (existing != null && !existing.equals(operation)) {
                throw new IllegalStateException(
                        "SlotOperations: operation '" + operation.id() + "' is already defined by a "
                        + "different key. Two mods have claimed the same operation id; one of them "
                        + "must change its namespace.");
            }
            DEFINED.putIfAbsent(operation.id(), operation);
        }
    }

    /** Every defined operation, in definition order. */
    public static Collection<BehaviorKey<?>> all() {
        synchronized (DEFINED) {
            return List.copyOf(DEFINED.values());
        }
    }

    /** The defined operation with this id, or {@code null}. */
    public static @Nullable BehaviorKey<?> byId(Identifier id) {
        return DEFINED.get(id);
    }

    // ── A category's inherent operations ───────────────────────────────────

    /**
     * Declares {@code value} as the default for {@code operation} on every slot in
     * {@code category} that does not declare its own. See the class doc for the
     * resolution order and for why this reaches created slots only.
     *
     * <p>Declare it wherever you mint the category — typically your mod's init.
     * Order does not matter: membership resolves per query, so groups registered
     * before this call pick it up too.
     */
    public static <V> void inherent(SlotGroupCategory category, BehaviorKey<V> operation, V value) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(value, "value");
        WindowEngine.setGroup(groupFor(category), operation, Decl.set(value));
    }

    /**
     * The {@link GroupKey} standing for "every created slot whose group declared
     * {@code category}". One per category, created on first use, id-unique so two
     * categories can never share a binding.
     */
    private static synchronized GroupKey groupFor(SlotGroupCategory category) {
        GroupKey existing = CATEGORY_GROUPS.get(category);
        if (existing != null) return existing;

        String base = "category/" + sanitize(category.namespace()) + "/" + sanitize(category.path());
        String id = base;
        for (int n = 2; USED_IDS.containsKey(id); n++) {
            id = base + "_" + n;   // sanitizing collapsed two distinct categories; keep them apart
        }
        USED_IDS.put(id, category);

        GroupKey key = new GroupKey(
                Identifier.fromNamespaceAndPath("menukit", id),
                address -> category.equals(lookup.categoryOf(address)));
        CATEGORY_GROUPS.put(category, key);
        return key;
    }

    /** Identifier paths allow {@code [a-z0-9_.-/]}; a category's own strings may not. */
    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
