package com.trevlar.menukit.inject;

/**
 * A vanilla slot's on-screen item box in absolute screen-space pixels — what
 * {@link VanillaSlotResolver#resolve} returns. {@code x}/{@code y} are the
 * slot's top-left including the screen frame's {@code leftPos}/{@code topPos},
 * so a consumer compares the cursor (also absolute) against it directly and
 * places its own decoration relative to it, with no per-screen geometry of its
 * own.
 *
 * <p>{@code width}/{@code height} are the 16×16 item box (vanilla's slot item
 * area). The recessed 18×18 frame is the item box expanded by 1px on each side
 * ({@code x-1}, {@code width+2}) — derive it at the call-site when the frame, not
 * the item, is what matters.
 */
public record SlotScreenRect(int x, int y, int width, int height) {

    /** Left edge of the 18×18 recessed frame (item box minus the 1px inset). */
    public int frameX() { return x - 1; }

    /** Top edge of the 18×18 recessed frame. */
    public int frameY() { return y - 1; }
}
