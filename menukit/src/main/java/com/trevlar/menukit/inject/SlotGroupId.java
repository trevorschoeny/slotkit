package com.trevlar.menukit.inject;

import com.trevlar.menukit.window.KeyStrings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.trevlar.menukit.core.SlotGroupCategory;

import java.util.List;
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
 *
 * <h2>Saving one</h2>
 *
 * {@link #asString()} is the key to save a choice about a group under: a stable
 * text form for a config file, which {@link #parse} reads back; {@link #CODEC} is
 * the same form for codec-based config. Save by group, never by the set a group is
 * listed in ({@code SlotGroups}, "Saving a choice"):
 *
 * <pre>
 * vanilla|menukit|player_inventory
 * created|inventorymax:pocket_0_0|pocket_0_0
 * </pre>
 *
 * Each part is escaped, so no id a mod chooses can split it. The text is as stable
 * as the ids it is made of: a vanilla group is named by its category, a created
 * group by the panel and group ids its mod chose. Renaming either loses what was
 * saved against it, the same contract as renaming a category.
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

    static Created created(String panelId, String groupId) {
        return new Created(panelId, groupId);
    }

    /** The stable text form, for saving a choice made against this group. */
    default String asString() {
        return switch (this) {
            case Vanilla v -> KeyStrings.join("vanilla", v.category().namespace(), v.category().path());
            case Created c -> KeyStrings.join("created", c.panelId(), c.groupId());
        };
    }

    /**
     * Reads {@link #asString()} back.
     *
     * @throws IllegalArgumentException for text {@code asString} did not write
     */
    static SlotGroupId parse(String text) {
        List<String> parts = KeyStrings.split(text);
        if (parts.size() == 3 && parts.get(0).equals("vanilla")) {
            return vanilla(new SlotGroupCategory(parts.get(1), parts.get(2)));
        }
        if (parts.size() == 3 && parts.get(0).equals("created")) {
            return created(parts.get(1), parts.get(2));
        }
        throw new IllegalArgumentException("not a slot group id: " + text);
    }

    /** The {@link #asString()} form as a codec. */
    Codec<SlotGroupId> CODEC = Codec.STRING.comapFlatMap(SlotGroupId::read, SlotGroupId::asString);

    private static DataResult<SlotGroupId> read(String text) {
        try {
            return DataResult.success(parse(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }
}
