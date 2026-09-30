package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.GroupKey;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;


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

    /**
     * A stable, unique {@code menukit:<prefix>/<key>} identifier for {@code key}.
     * The key is encoded injectively (every character outside {@code [a-z0-9.-]},
     * {@code _} included, becomes {@code _} and its code in hex, then {@code _}),
     * so two different keys never collide and the id depends on nothing but the
     * key: no allocation order, so no mod load order (§0063).
     */
    public static Identifier of(String prefix, String key) {
        StringBuilder out = new StringBuilder(prefix).append('/');
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-') out.append(ch);
            else out.append('_').append(Integer.toHexString(ch)).append('_');
        }
        return Identifier.fromNamespaceAndPath("menukit", out.toString());
    }
}
