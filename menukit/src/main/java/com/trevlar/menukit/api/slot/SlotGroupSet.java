package com.trevlar.menukit.api.slot;

import com.trevlar.menukit.window.KeyStrings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.Objects;

/**
 * A player-facing name for several slot groups that are one thing to the player.
 *
 * <p>Group granularity is an author's layout choice: a group is the unit a panel
 * anchors to, so a mod that anchors per slot declares one group per slot
 * (Inventory Max's pockets are 27). A player should see one entry, "Pockets", and a
 * choice made against it should reach all 27. Groups join a set with
 * {@link SlotGroups#declare(SlotGroupId, com.trevlar.menukit.api.slot.SlotGroupCategory, SlotGroupSet)};
 * {@link SlotGroups#listing()} then lists the set once.
 *
 * <p>Named like a category, {@code namespace:path}. Its display name is the
 * translation key {@code slot_group_set.<namespace>.<path>}, which the declaring
 * mod ships. {@link #asString()} and {@link #CODEC} save it.
 */
public record SlotGroupSet(String namespace, String path) {

    public SlotGroupSet {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
    }

    /** The stable text form, {@code set|namespace|path}, for saving a choice made against this set. */
    public String asString() {
        return KeyStrings.join("set", namespace, path);
    }

    /**
     * Reads {@link #asString()} back.
     *
     * @throws IllegalArgumentException for text {@code asString} did not write
     */
    public static SlotGroupSet parse(String text) {
        List<String> parts = KeyStrings.split(text);
        if (parts.size() == 3 && parts.get(0).equals("set")) return new SlotGroupSet(parts.get(1), parts.get(2));
        throw new IllegalArgumentException("not a slot group set: " + text);
    }

    /** The {@link #asString()} form as a codec. */
    public static final Codec<SlotGroupSet> CODEC = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(parse(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }, SlotGroupSet::asString);

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
