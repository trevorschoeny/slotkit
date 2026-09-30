package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The one text form MenuKit's saved identities share ({@code SlotGroupId},
 * {@code SlotGroupSet}, {@link Address}): fields joined by {@code |}, each field
 * escaped so no id a mod chooses can split it ({@code %} as {@code %25}, {@code |}
 * as {@code %7C}, NUL as {@code %00}).
 */
@ApiStatus.Internal
public final class KeyStrings {

    private KeyStrings() {}

    public static String join(String... parts) {
        StringJoiner out = new StringJoiner("|");
        for (String p : parts) out.add(escape(p));
        return out.toString();
    }

    public static List<String> split(String text) {
        Objects.requireNonNull(text, "text");
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\|", -1)) out.add(unescape(raw));
        return out;
    }

    private static String escape(String s) {
        return s.replace("%", "%25").replace("|", "%7C").replace("\0", "%00");
    }

    private static String unescape(String s) {
        return s.replace("%00", "\0").replace("%7C", "|").replace("%25", "%");
    }
}
