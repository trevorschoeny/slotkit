package com.trevlar.slotkit.compat;

import com.trevlar.slotkit.core.attachment.PlayerSlotCapture;
import com.trevlar.slotkit.core.attachment.PlayerSlotCapture.CapturedSlot;

import eu.pb4.graves.GravesApi;
import eu.pb4.graves.grave.GraveInventoryMask;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Universal Graves adapter. Puts the contents of Containers' player slots into a
 * Universal Graves grave and restores them on collect, for every death-droppable
 * player content slot, through {@link PlayerSlotCapture}.
 *
 * <p>Optional dependency. This class references Universal Graves types
 * ({@code eu.pb4.graves.*}, compile-only) and loads only behind a
 * {@code FabricLoader.isModLoaded("universal-graves")} guard in
 * {@code SlotKit.init}. When Universal Graves is absent the class never
 * loads, and the fallback drops the slots beside the death spot.
 *
 * <p>Universal Graves' mask captures before vanilla's inventory drop (it injects
 * ahead of {@code dropEquipment}), so {@link PlayerSlotCapture#captureAndClear} here
 * empties the slots before the {@code dropEquipment} fallback runs. The fallback
 * finds them empty and drops nothing, so nothing is duplicated.
 *
 * <p>A captured slot's real identity is {@code (attachmentId, index)}, carried in
 * Universal Graves' per-item {@link Tag}. Its own {@code slot} int is only a unique
 * per-item key, since one mask spans several attachments. On collect, restore
 * decodes the tag and writes each stack back to the exact slot.
 */
public final class UniversalGravesAdapter implements GraveInventoryMask {

    private static final Identifier MASK_ID =
            Identifier.fromNamespaceAndPath("menukit", "player_slots");
    private static final String K_ATTACHMENT = "MKAttachment";
    private static final String K_INDEX = "MKIndex";

    private UniversalGravesAdapter() {}

    /** Registers the mask with Universal Graves. Call only when {@code universal-graves} is loaded. */
    public static void register() {
        GravesApi.registerInventoryMask(MASK_ID, new UniversalGravesAdapter());
    }

    @Override
    public void addToGrave(ServerPlayer player, ItemConsumer consumer) {
        List<CapturedSlot> captured = PlayerSlotCapture.captureAndClear(player);
        int graveSlot = 0;
        for (CapturedSlot cs : captured) {
            CompoundTag tag = new CompoundTag();
            tag.putString(K_ATTACHMENT, cs.attachmentId().toString());
            tag.putInt(K_INDEX, cs.index());
            consumer.addItem(cs.stack(), graveSlot++, tag);
        }
    }

    @Override
    public boolean moveToPlayerExactly(ServerPlayer player, ItemStack stack, int slot,
                                       @Nullable Tag optionalData) {
        return restore(player, stack, optionalData);
    }

    @Override
    public boolean moveToPlayerClosest(ServerPlayer player, ItemStack stack, int slot,
                                       @Nullable Tag optionalData) {
        return restore(player, stack, optionalData);
    }

    private static boolean restore(ServerPlayer player, ItemStack stack, @Nullable Tag optionalData) {
        if (!(optionalData instanceof CompoundTag tag)) return false;
        Identifier id = Identifier.tryParse(tag.getStringOr(K_ATTACHMENT, ""));
        int index = tag.getIntOr(K_INDEX, -1);
        if (id == null || index < 0) return false;
        return PlayerSlotCapture.restore(player, new CapturedSlot(id, index, stack));
    }
}
