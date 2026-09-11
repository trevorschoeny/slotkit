package com.trevlar.menukit.window;

import com.trevlar.menukit.core.SlotGroupCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

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
 *   <li><b>Category</b> — what the slot <em>is</em>
 *       ({@link SlotGroupCategory}). Identity. A pocket declared
 *       {@code PLAYER_INVENTORY} is inventory storage, and every mod that searches
 *       the inventory should find it.</li>
 *   <li><b>Operations</b> — what may be <em>done to</em> it. Shift-click,
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
 * can discover it, ships a name and a description for it (below), and asks
 * {@link #allows} in its own code before acting. MenuKit needs no change for a new
 * operation to exist. Vanilla's own operations are {@link BehaviorKeys#VANILLA_OPERATIONS},
 * split one key per thing a player can do to a slot, and enforced at vanilla's seams
 * by MenuKit itself.
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
 * <h2>Who says what a slot allows</h2>
 *
 * Two mechanisms, one subtractive:
 *
 * <ol>
 *   <li><b>The cascade</b> is what the slot's author declared, most specific wins:
 *       <pre>per-slot declaration  &gt;  the slot's group  &gt;  the group's category  &gt;  the key's default</pre>
 *       This is the window's ordinary cascade (§0055); the group and the category
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
 *
 * <p><b>Init-time reads are the one exception to "declare anywhere".</b> Every
 * built-in operation is SERVER tier, and MenuKit-Containers installs the server
 * tier from its own initializer. Fabric does not order entrypoints between mods,
 * so a consumer whose init runs first will see {@code resolve} answer the key's
 * library default, and a declaration it makes there is buffered until the tier
 * installs. Declare at init and read during play, as consumers normally do.
 *
 * <p>Thread-safe; every method is safe to call from mod init on any thread.
 */
public final class SlotOperations {

    private SlotOperations() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** Operation keys anyone has defined, in definition order. */
    private static final Map<Identifier, BehaviorKey<?>> DEFINED =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** One {@link GroupKey} per category, carrying that category's inherent operations. */
    private static final Map<SlotGroupCategory, GroupKey> CATEGORY_GROUPS = new HashMap<>();

    // ── The category port (§0042) ──────────────────────────────────────────

    /**
     * Resolves a created slot's {@link Address} to the category its group declared.
     * MenuKit-Containers installs the implementation; MenuKit alone there are no
     * created slots, so the default answers {@code null} and no category group ever
     * matches a created address. (Vanilla slots reach their category through
     * {@link #allows}, which has the menu in hand.)
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

    // ── Name and description ───────────────────────────────────────────────

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

    // ── A category's inherent operations ───────────────────────────────────

    /**
     * Declares {@code value} as the default for {@code operation} on every slot in
     * {@code category} that does not declare its own (the category rung). Reaches
     * created slots by address and vanilla slots through {@link #allows}, which
     * knows the menu.
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
     * The {@link GroupKey} standing for "every slot whose group declared
     * {@code category}". One per category, created on first use, id-unique so two
     * categories can never share a binding. Its membership predicate answers for
     * created slots; a vanilla slot is stated as a member by {@link #allows}.
     */
    private static synchronized GroupKey groupFor(SlotGroupCategory category) {
        return CATEGORY_GROUPS.computeIfAbsent(category, c -> new GroupKey(
                GroupIds.of("category", c.namespace() + "/" + c.path()),
                address -> c.equals(lookup.categoryOf(address)),
                GroupKey.PRECEDENCE_CATEGORY));
    }

    // ── Vetoes ─────────────────────────────────────────────────────────────

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

    private static final List<Veto> VETOES = new CopyOnWriteArrayList<>();

    // A veto that throws is a consumer's bug, and a click must not crash on it: it
    // is logged once and skipped from then on, so the log says which one and the
    // game keeps working with that veto silent.
    private static final Set<Veto> BROKEN = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    /** Registers a veto. Typically once, at your mod's init. */
    public static void veto(Veto veto) {
        VETOES.add(Objects.requireNonNull(veto, "veto"));
    }

    // ── The one question ───────────────────────────────────────────────────

    /**
     * Whether {@code operation} may act on the slot {@code ref} describes: the
     * cascade resolves {@code TRUE} and no {@link Veto} denies it. Every operation,
     * vanilla's and a mod's own, asks this before touching a slot.
     */
    public static boolean allows(SlotRef ref, BehaviorKey<TriBool> operation) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(operation, "operation");
        if (!cascade(ref, operation)) return false;
        for (Veto veto : VETOES) {
            if (BROKEN.contains(veto)) continue;
            try {
                if (veto.denies(ref, operation)) return false;
            } catch (RuntimeException e) {
                BROKEN.add(veto);
                LOGGER.error("[MenuKit] a slot operation veto threw and is disabled from here on: {}", veto, e);
            }
        }
        return true;
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
     * The cascade's answer. A menu slot is addressed through the installed slot
     * addressing rule (kind-aware once Containers is present); an off-menu slot
     * through its container identity, which MenuKit alone cannot mint, so it falls
     * to the key's default and the vetoes decide ({@code docs/limits.md}). A
     * vanilla slot's category, known from the menu, is stated as a membership so
     * the category rung reaches it.
     */
    private static boolean cascade(SlotRef ref, BehaviorKey<TriBool> operation) {
        Address address;
        if (ref.menu() != null && ref.slot() != null) {
            address = ClientSlotAddressing.addressOf(ref.menu(), ref.slot());
        } else {
            Optional<Address> byIdentity = VanillaAddressing.addressOf(ref.container(), ref.containerSlot());
            if (byIdentity.isEmpty()) return operation.libraryDefault().asBoolean();
            address = byIdentity.get();
        }
        Collection<GroupKey> alsoMemberOf =
                ref.category() != null && address.kind() == KindTag.VANILLA_SLOT
                        ? List.of(groupFor(ref.category()))
                        : List.of();
        return WindowEngine.resolve(address, operation, alsoMemberOf).asBoolean();
    }
}
