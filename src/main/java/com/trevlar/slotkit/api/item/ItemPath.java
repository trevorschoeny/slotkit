package com.trevlar.slotkit.api.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Where one item is, as both sides can name it: a top-level slot in the inventory or the
 * ender chest, then the index inside each container on the way down. A shulker box's
 * index is its slot (0 to 26); a bundle's is the item's position in its contents, in the
 * order the bundle lists them.
 *
 * <p>A client finds paths with {@link ItemSources#find}, from the contents that ride on
 * the item stacks it sees, and sends one to the server ({@link #STREAM_CODEC}). The server
 * turns it back into a live reference with {@link ItemSources#at}, which returns
 * {@code null} if the item isn't there any more.
 *
 * <pre>{@code
 * // Inventory slot 14, a shulker there, its slot 3:
 * new ItemPath(ItemRef.Source.INVENTORY, 14, List.of(3));
 * }</pre>
 *
 * @param source the top-level container
 * @param slot   the top-level slot (0 to 35 in the inventory, hotbar 0 to 8; 0 to 26 in the ender chest)
 * @param inside the index inside each container, outermost first; empty for a top-level item
 */
public record ItemPath(ItemRef.Source source, int slot, List<Integer> inside) {

    public ItemPath {
        inside = List.copyOf(inside);
    }

    /** How many containers deep the item is: 0 for a top-level slot. */
    public int depth() { return inside.size(); }

    /** For a mod's own packet. Paths deeper than 16 containers are refused on decode. */
    public static final StreamCodec<ByteBuf, ItemPath> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> ItemRef.Source.values()[Math.floorMod(i, ItemRef.Source.values().length)],
                    ItemRef.Source::ordinal), ItemPath::source,
            ByteBufCodecs.VAR_INT, ItemPath::slot,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), ItemPath::inside,
            ItemPath::new);
}
