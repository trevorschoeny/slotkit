package com.trevlar.menukit.window;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A bulk-addressing group: a stable id plus a membership predicate over
 * {@link Address}es. The per-group level of the cascade — a default declared at a
 * {@code GroupKey} applies to every address the predicate accepts, unless a
 * per-slot declaration overrides it.
 *
 * <h2>Window-side, not creation-bound (RULED #2 property c)</h2>
 *
 * Membership is evaluated against an address, so a group can bulk-address vanilla
 * slots, created slots, elements, or panels alike — it is NOT a creation
 * construct welded to a slot group. Membership is evaluated per-resolve, so an
 * address that comes to match later (a slot that appears after the group was
 * declared) joins automatically.
 *
 * <h2>Identity</h2>
 *
 * Equality is by {@code id} alone (a predicate has no useful equality), so a
 * group is a stable handle you can re-declare against. Two {@code GroupKey}s with
 * the same id are the same group.
 *
 * <h2>Precedence — the rungs inside the per-group level</h2>
 *
 * Several groups can match one address, and they are not equally specific: a slot
 * group's own declaration should outrank the inherent declarations of the category
 * that group belongs to. {@link #precedence()} orders them, <b>higher wins</b>, with
 * a tie at one precedence going to the id that sorts last (never load order). The library uses
 * {@link #PRECEDENCE_CATEGORY} and {@link #PRECEDENCE_GROUP}; a consumer bulk-group
 * defaults to {@link #PRECEDENCE_DEFAULT}, below both, because a hand-declared group
 * is the broadest thing in the picture unless it says otherwise.
 *
 * <p>So the full specificity order a slot resolves through is: per-address
 * declaration, then owner-chain ancestors, then matching groups by precedence, then
 * the key's library default.
 */
public final class GroupKey {

    /** A consumer-declared bulk group, unless it asks for another rung. */
    public static final int PRECEDENCE_DEFAULT = 0;

    /** A category's inherent declarations, applying to every group in it. */
    public static final int PRECEDENCE_CATEGORY = 100;

    /** A slot group's own declaration — outranks the category it belongs to. */
    public static final int PRECEDENCE_GROUP = 200;

    private final net.minecraft.resources.Identifier id;
    private final Predicate<Address> membership;
    private final int precedence;

    private static final java.util.Map<com.trevlar.menukit.inject.SlotGroupId, GroupKey> OF =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The cascade group of a slot group: one identity for the registry's group and
     * the engine's (§0063), at {@link #PRECEDENCE_GROUP}. A created group's
     * membership follows from a slot's address, so it holds wherever the slot is
     * resolved, hoppers and pickup included. A category group's membership depends
     * on the menu, so the seam that has the menu states it
     * ({@code SlotOperations.allows} does); without a menu it has no members.
     */
    public static GroupKey of(com.trevlar.menukit.inject.SlotGroupId group) {
        return OF.computeIfAbsent(group, g -> new GroupKey(
                GroupIds.of("group", g.asString()),
                switch (g) {
                    case com.trevlar.menukit.inject.SlotGroupId.Created c ->
                            address -> c.equals(com.trevlar.menukit.inject.SlotGroups.groupOf(address));
                    case com.trevlar.menukit.inject.SlotGroupId.Category k -> address -> false;
                },
                PRECEDENCE_GROUP));
    }

    /** A group at {@link #PRECEDENCE_DEFAULT}. */
    public GroupKey(net.minecraft.resources.Identifier id, Predicate<Address> membership) {
        this(id, membership, PRECEDENCE_DEFAULT);
    }

    public GroupKey(net.minecraft.resources.Identifier id, Predicate<Address> membership, int precedence) {
        this.id = Objects.requireNonNull(id, "id");
        this.membership = Objects.requireNonNull(membership, "membership");
        this.precedence = precedence;
    }

    /** Where this group sits inside the per-group level; higher wins. */
    public int precedence() {
        return precedence;
    }

    public net.minecraft.resources.Identifier id() {
        return id;
    }

    /** Whether {@code address} is a member of this group. */
    public boolean contains(Address address) {
        return membership.test(address);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GroupKey other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GroupKey[" + id + "]";
    }
}
