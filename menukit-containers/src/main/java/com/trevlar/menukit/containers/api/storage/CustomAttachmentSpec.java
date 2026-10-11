package com.trevlar.menukit.containers.api.storage;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by consumers to extend storage attachments. It has two uses:
 *
 * <ol>
 *   <li>Other owner types. Consumers whose owner type isn't covered by the four
 *       built-in factories (ephemeral, player-attached, block-scoped,
 *       item-attached) supply their own save, load and dirty hooks in this spec
 *       and wrap it with {@code StorageAttachment.custom(spec)}.</li>
 *   <li>Decorating vanilla menus. Consumers who decorate vanilla menus through
 *       {@code ScreenPanelAdapter} don't go through {@code CustomContainerMenu},
 *       so they don't get its save and load lifecycle. They use a custom spec and
 *       trigger save and load from their own mixin.</li>
 * </ol>
 *
 * <p>The library never knows what {@code O} is. The spec handles all owner-specific
 * persistence, and the library only makes the lifecycle calls.
 *
 * <p>The content type {@code C} is up to the consumer. For the common case,
 * {@code NonNullList<ItemStack>}, the spec is simple. For structured content
 * (records, wrappers) the spec methods do the conversions.
 *
 * @param <O> owner type (whatever the consumer's persistence anchors on)
 * @param <C> content type (what the consumer stores per slot group)
 *
 * @see com.trevlar.menukit.containers.api.storage.StorageAttachment
 */
public interface CustomAttachmentSpec<O, C> {

    /**
     * Reads the stored content for this owner. The result is what later
     * {@code Storage} reads see. If nothing has been saved yet, return a default from
     * {@link #defaultFactory()}.
     */
    C read(O owner);

    /**
     * Writes the content for this owner. Called after changes (through
     * Storage.setStack) to save them.
     *
     * <p>A spec backed by a Fabric attachment typically calls
     * {@code owner.setAttached(attachmentType, content)} here.
     */
    void write(O owner, C content);

    /**
     * Marks the owner as needing to be saved. For block entities, call
     * {@code be.setChanged()}. For entities and players this is often empty, since
     * Fabric saves attachment changes on its own.
     */
    void markDirty(O owner);

    /**
     * Returns new default content for when nothing is stored yet. For a
     * {@code NonNullList<ItemStack>} spec with {@code slotCount} slots:
     * <pre>{@code
     *   () -> NonNullList.withSize(slotCount, ItemStack.EMPTY)
     * }</pre>
     */
    C defaultFactory();

    /**
     * Converts stored content to a mutable {@link NonNullList} for the storage. If
     * {@code C} is already {@code NonNullList<ItemStack>}, return the content itself.
     */
    NonNullList<ItemStack> toItemList(C content);

    /**
     * Converts a changed item list back to the content, keeping any fields that are
     * not item stacks. The storage calls it after each {@code setStack} so the change
     * is saved through {@link #write}. If {@code C} is already
     * {@code NonNullList<ItemStack>}, return the list itself.
     */
    C fromItemList(NonNullList<ItemStack> items);

    /**
     * How many slots this content holds, reported as the storage's size.
     */
    int slotCount();

    /**
     * Identifier for this spec, for debug logging and to tell apart specs that share
     * an owner type.
     */
    Identifier id();
}
