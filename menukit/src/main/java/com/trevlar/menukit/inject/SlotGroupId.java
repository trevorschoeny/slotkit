package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.SlotGroupCategory;

import java.util.Objects;

/**
 * Names <b>one slot group</b> — one coherent run of slots sitting in one place on
 * screen. This is what a panel anchors to.
 *
 * <h2>Why this is not a category</h2>
 *
 * A group has a meaningful bounding box; a <em>category</em> does not. A category
 * is a label that several groups can share, and once created slot groups declare
 * themselves into vanilla categories (a pocket row declaring
 * {@code PLAYER_INVENTORY}), the union of a category's slots spans scattered
 * rectangles and its bounding box means nothing. Anchor to a group; search by
 * category.
 *
 * <h2>The identity already existed, in two shapes</h2>
 *
 * No new names are minted for this:
 *
 * <ul>
 *   <li>{@link Vanilla} — a vanilla resolver emits exactly one contiguous run per
 *       category per menu, so for vanilla the category already <em>is</em> the
 *       group's name. Derived, never hand-authored, so
 *       {@code PLAYER_INVENTORY} keeps being named once and every container type
 *       keeps inheriting it from the shared resolver helper.</li>
 *   <li>{@link Created} — every created slot already carries its
 *       {@code (panelId, groupId)}. Equality is on those two alone; the category a
 *       group declared is a property of the group, not part of its name, so a
 *       group cannot become a different group by being re-categorised.</li>
 * </ul>
 */
public sealed interface SlotGroupId {

    /** The one group a vanilla resolver contributes for {@code category} on a menu. */
    record Vanilla(SlotGroupCategory category) implements SlotGroupId {
        public Vanilla {
            Objects.requireNonNull(category, "category");
        }
    }

    /** A group created through MenuKit-Containers, named by its declaration. */
    record Created(String panelId, String groupId) implements SlotGroupId {
        public Created {
            Objects.requireNonNull(panelId, "panelId");
            Objects.requireNonNull(groupId, "groupId");
        }
    }

    static SlotGroupId vanilla(SlotGroupCategory category) {
        return new Vanilla(category);
    }

    static SlotGroupId created(String panelId, String groupId) {
        return new Created(panelId, groupId);
    }
}
