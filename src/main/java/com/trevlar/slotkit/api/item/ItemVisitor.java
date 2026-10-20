package com.trevlar.slotkit.api.item;

/** Called by {@link ItemSources#walk} for each item, nearest first. */
@FunctionalInterface
public interface ItemVisitor {
    /** Returns {@code true} to keep walking, {@code false} to stop. */
    boolean visit(ItemRef ref);
}
