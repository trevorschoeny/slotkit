package com.trevlar.slotkit.api.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where one item is, as both sides can name it: a top-level slot in the inventory, the
 * ender chest, a supplied container ({@link ItemRef.Source#CONTAINER}, such as an open
 * chest) or a carried attachment ({@link ItemRef.Source#CARRIED}, named by its id), then
 * the index inside each container on the way down. A shulker box's index is its slot (0
 * to 26); a bundle's is the item's position in its contents, in the order the bundle
 * lists them.
 *
 * <p>A client finds paths with {@link ItemSources#find}, from the contents that ride on
 * the item stacks it sees, and sends one to the server ({@link #STREAM_CODEC}). The server
 * turns it back into a live reference with {@link ItemSources#at}, which returns
 * {@code null} if the item isn't there any more.
 *
 * <pre>{@code
 * // Inventory slot 14, a shulker there, its slot 3:
 * new ItemPath(ItemRef.Source.INVENTORY, 14, List.of(3));
 * // Slot 0 of the carried attachment mymod:pocket:
 * new ItemPath(ItemRef.Source.CARRIED, 0, List.of(), Identifier.fromNamespaceAndPath("mymod", "pocket"));
 * }</pre>
 *
 * @param source     the top-level container
 * @param slot       the top-level slot (0 to 35 in the inventory, hotbar 0 to 8; 0 to 26 in
 *                   the ender chest; the container's or attachment's own slot index otherwise)
 * @param inside     the index inside each container, outermost first; empty for a top-level item
 * @param attachment the carried attachment's id when the source is {@code CARRIED}, else
 *                   null. Since 2.2.0.
 */
public record ItemPath(ItemRef.Source source, int slot, List<Integer> inside, @Nullable Identifier attachment) {

    public ItemPath {
        inside = List.copyOf(inside);
        if ((source == ItemRef.Source.CARRIED) != (attachment != null)) {
            throw new IllegalArgumentException("A CARRIED path names its attachment, and only a CARRIED path does");
        }
    }

    /** A path in the inventory, the ender chest or a supplied container (the 2.1 constructor). */
    public ItemPath(ItemRef.Source source, int slot, List<Integer> inside) {
        this(source, slot, inside, null);
    }

    /** How many containers deep the item is: 0 for a top-level slot. */
    public int depth() { return inside.size(); }

    private static final StreamCodec<ByteBuf, List<Integer>> INSIDE = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16));

    /**
     * For a mod's own packet. Paths deeper than 16 containers are refused on decode. The
     * attachment id is written only for a {@code CARRIED} path, so every other path
     * encodes as it did in 2.1.
     */
    public static final StreamCodec<ByteBuf, ItemPath> STREAM_CODEC = StreamCodec.of(
            (buf, path) -> {
                ByteBufCodecs.VAR_INT.encode(buf, path.source().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, path.slot());
                INSIDE.encode(buf, path.inside());
                if (path.attachment() != null) Identifier.STREAM_CODEC.encode(buf, path.attachment());
            },
            buf -> {
                ItemRef.Source[] sources = ItemRef.Source.values();
                ItemRef.Source source = sources[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), sources.length)];
                int slot = ByteBufCodecs.VAR_INT.decode(buf);
                List<Integer> inside = INSIDE.decode(buf);
                return new ItemPath(source, slot, inside,
                        source == ItemRef.Source.CARRIED ? Identifier.STREAM_CODEC.decode(buf) : null);
            });
}
