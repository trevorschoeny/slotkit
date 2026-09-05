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
 */
public final class GroupKey {

    private final net.minecraft.resources.Identifier id;
    private final Predicate<Address> membership;

    public GroupKey(net.minecraft.resources.Identifier id, Predicate<Address> membership) {
        this.id = Objects.requireNonNull(id, "id");
        this.membership = Objects.requireNonNull(membership, "membership");
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
