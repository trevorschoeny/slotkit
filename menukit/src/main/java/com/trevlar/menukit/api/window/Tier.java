package com.trevlar.menukit.api.window;

/**
 * Which authority tier a {@link BehaviorKey} belongs to, the seam where THE ONE
 * WINDOW's client/server split lives.
 *
 * <ul>
 *   <li>{@link #CLIENT}, presentation behavior with no game authority (read,
 *       decorate, hover, visibility, panel properties). Resolves with MK alone.</li>
 *   <li>{@link #SERVER}, authoritative behavior (what a slot accepts/releases,
 *       persistence, server reactions). Held in the server tier, which is
 *       always present (§0062); resolved above the client tier.</li>
 * </ul>
 *
 * <p>In the cascade, authority sorts ABOVE specificity: a server-tier declaration
 * outranks a client wish for the same key. A key carries exactly one tier.
 */
public enum Tier {
    CLIENT,
    SERVER
}
