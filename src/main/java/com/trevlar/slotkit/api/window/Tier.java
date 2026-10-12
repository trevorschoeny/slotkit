package com.trevlar.slotkit.api.window;

/**
 * Which authority tier a {@link BehaviorKey} belongs to. This is where the window's
 * client/server split lives.
 *
 * <ul>
 *   <li>{@link #CLIENT}, presentation behavior with no game authority (read,
 *       decorate, hover, visibility, panel properties). Resolves on any server.</li>
 *   <li>{@link #SERVER}, authoritative behavior (what a slot accepts/releases,
 *       persistence, server reactions). Held in the server tier, which is
 *       always present, and resolved above the client tier.</li>
 * </ul>
 *
 * <p>In the cascade, authority sorts above specificity: a server-tier declaration
 * outranks a client wish for the same key. A key carries exactly one tier.
 */
public enum Tier {
    CLIENT,
    SERVER
}
