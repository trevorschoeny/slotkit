package com.trevorschoeny.menukit.window;

/**
 * Which authority tier a {@link BehaviorKey} belongs to — the seam where THE ONE
 * WINDOW's client/server split lives.
 *
 * <ul>
 *   <li>{@link #CLIENT} — presentation behavior with no game authority (read,
 *       decorate, hover, visibility, panel properties). Resolves with MK alone.</li>
 *   <li>{@link #SERVER} — authoritative behavior (what a slot accepts/releases,
 *       persistence, server reactions). Unlocked by MKC; with MKC absent a
 *       server-tier key resolves to its library default (the NoServerTier
 *       null-object), so authoring it client-only is a safe no-op.</li>
 * </ul>
 *
 * <p>In the cascade, authority sorts ABOVE specificity: a server-tier declaration
 * outranks a client wish for the same key. A key carries exactly one tier.
 */
public enum Tier {
    CLIENT,
    SERVER
}
