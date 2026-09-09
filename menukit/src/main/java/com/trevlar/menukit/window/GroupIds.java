package com.trevlar.menukit.window;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Allocates the {@link Identifier} a library-built {@link GroupKey} is identified
 * by, from a free-form key such as a category name or a {@code panelId/groupId}
 * pair.
 *
 * <p>Two things have to hold at once. An {@code Identifier} path allows only
 * {@code [a-z0-9_.-/]}, and the strings coming in are consumer-authored (a panel id
 * carries a colon, a category may carry anything). And {@link GroupKey} equality is
 * by id alone, so if two distinct keys sanitized to the same id they would silently
 * share declarations — one mod's group inheriting another's. Sanitizing alone gets
 * the first and breaks the second, so allocated ids are also kept distinct here.
 *
 * <p>Internal: the library allocates these for the category and slot-group rungs. A
 * consumer building its own {@code GroupKey} picks its own {@code Identifier}.
 */
@ApiStatus.Internal
public final class GroupIds {

    private GroupIds() {}

    /** Allocated path → the exact key it was allocated for. */
    private static final Map<String, String> CLAIMED = new HashMap<>();

    /**
     * A stable, unique {@code menukit:<prefix>/<key>} identifier for {@code key}.
     * Calling twice with the same {@code prefix} and {@code key} returns the same
     * identifier; two different keys never collide, even if they sanitize alike.
     */
    public static synchronized Identifier of(String prefix, String key) {
        String base = prefix + "/" + sanitize(key);
        String path = base;
        for (int n = 2; ; n++) {
            String claimedBy = CLAIMED.get(path);
            if (claimedBy == null) {
                CLAIMED.put(path, key);
                break;
            }
            if (claimedBy.equals(key)) break;   // same caller, same id
            path = base + "_" + n;              // sanitizing collapsed two distinct keys
        }
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
