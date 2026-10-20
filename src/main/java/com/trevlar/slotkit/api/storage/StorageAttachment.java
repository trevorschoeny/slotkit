package com.trevlar.slotkit.api.storage;

import org.jspecify.annotations.Nullable;

import com.trevlar.slotkit.api.slot.DropRule;
import com.trevlar.slotkit.api.storage.Storage;

import com.trevlar.slotkit.api.storage.CustomAttachmentSpec;
import com.trevlar.slotkit.core.attachment.StorageAttachments;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Where a slot group's contents are saved, by owner type.
 *
 * <p>Each factory gives a {@link Storage} bound to one owner. The storage reads and
 * writes wherever the factory keeps its data: a Fabric attachment (player, block
 * entity, entity), a data component (item stack), an in-memory list (ephemeral), or
 * consumer-supplied hooks ({@link CustomAttachmentSpec}).
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // At mod init, declare the attachment.
 * public static final StorageAttachment<BlockEntity, NonNullList<ItemStack>> FURNACE_EXTRA =
 *     StorageAttachment.blockScoped("my-mod", "furnace_extra", 1);
 *
 * // At menu construction, bind to a specific owner instance.
 * Storage storage = FURNACE_EXTRA.bind(furnaceBE);
 * panel.slotGroup("extra").slot(0, 0).build(storage);
 * }</pre>
 *
 * @param <O> owner type (Void for ephemeral, Player, BlockEntity and so on for the
 *            persistent variants)
 * @param <C> content type: a {@link NonNullList} of {@link ItemStack} for most
 *            factories, {@link ItemContainerContents} for {@link #itemContainer(int)},
 *            and consumer-defined for custom specs
 */
public abstract class StorageAttachment<O, C> {

    /** Number of slots the content holds. */
    public abstract int slotCount();

    /**
     * Binds this attachment to one owner, returning a {@link Storage} that reads and
     * writes the content for that owner.
     *
     * <p>Owners whose reference stays the same while the menu is open (Player,
     * BlockEntity, Entity) are passed directly.
     */
    public abstract Storage bind(O owner);

    /**
     * Binds through a supplier, for owners whose reference can change while the menu
     * is open (notably {@link ItemStack}, which vanilla may replace during
     * interactions). The supplier is called on every read and write.
     */
    public Storage bind(Supplier<O> ownerSupplier) {
        // Looks the owner up again on every call and delegates to bind(O). A factory
        // may override this if it can cache across calls.
        StorageAttachment<O, C> self = this;
        return new Storage() {
            @Override public ItemStack getStack(int i) {
                return self.bind(ownerSupplier.get()).getStack(i);
            }
            @Override public void setStack(int i, ItemStack s) {
                self.bind(ownerSupplier.get()).setStack(i, s);
            }
            @Override public int size() { return self.slotCount(); }
            @Override public void markDirty() {
                self.bind(ownerSupplier.get()).markDirty();
            }
        };
    }

    // Player-anchored subtype

    /**
     * A {@link StorageAttachment} with death handling, returned only by the
     * player-anchored factories ({@link #playerAttached} and
     * {@link #customPlayerAttached}). Death handling only means something for player
     * content; content on a block, item, entity or ephemeral storage has no player
     * death. So {@code dropsOnDeath(DropRule)} is here and not on the base type, and
     * calling it on a non-player attachment is a compile error rather than the old
     * runtime {@link UnsupportedOperationException}.
     *
     * @param <C> content type (carried through from the base)
     */
    public abstract static class PlayerStorageAttachment<C>
            extends StorageAttachment<Player, C> {

        /**
         * Sets this attachment's death-drop rule. Player content attachments start at
         * {@link DropRule#DEFAULT}, which matches vanilla. Call this to change it to
         * {@code KEEP} (soulbound), {@code DESTROY} (cursed) or {@code DROP} (always
         * drop, even with {@code keepInventory} on). Chains:
         * {@code playerAttached(...).dropsOnDeath(KEEP)}.
         */
        public abstract PlayerStorageAttachment<C> dropsOnDeath(DropRule rule);
    }

    // Factories

    /**
     * In-memory storage that lasts only while the menu is open. Its content is lost
     * when the menu closes unless the handler's close-drop rule catches it. The same
     * as wrapping an {@link EphemeralStorage}, through the common API.
     *
     * <p>Owner type is {@link Void}; callers pass {@code null} to {@link #bind}.
     */
    public static StorageAttachment<Void, NonNullList<ItemStack>> ephemeral(int slotCount) {
        return new EphemeralAttachment(slotCount);
    }

    /**
     * Saves slot-group content on a {@link Player} in a Fabric attachment. The content
     * stays with the player across sessions and dimensions.
     *
     * <h4>Death handling</h4>
     *
     * Player content attachments start at {@link DropRule#DEFAULT}, which matches
     * vanilla with no setup. On death the content drops at the death spot when
     * {@code keepInventory} is off (Curse of Vanishing destroys it instead), and is
     * kept across respawn when {@code keepInventory} is on. Change it per attachment
     * with {@code dropsOnDeath(DropRule)}, for example {@code KEEP} for a soulbound
     * slot. The attachment registers {@code copyOnDeath} so kept content carries
     * across respawn, and the library does the gamerule-gated drop.
     *
     * @param namespace attachment-id namespace (consumer's mod id)
     * @param path      attachment-id path (unique per consumer-declared attachment)
     * @param slotCount number of item slots the content holds
     */
    public static PlayerStorageAttachment<NonNullList<ItemStack>> playerAttached(
            String namespace, String path, int slotCount) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        AttachmentType<ItemContainerContents> type =
                StorageAttachments.registerPlayerContentAttachment(id, slotCount);
        return new PlayerAttachment(slotCount, type);
    }

    /**
     * {@link #playerAttached(String, String, int)}, with the contents also sent to every
     * client tracking the player, for slots whose items other players see: an elytra
     * worn from its own slot, a cape, anything drawn on the player model. Each client
     * then reads the contents with {@code bind(otherPlayer)} as it does its own.
     *
     * <p>Opt in only for visible gear. A player's own client already gets its slots
     * through the menu, and what's in most slots (pockets, a totem) is nobody else's
     * business. The sync uses Fabric's attachment sync: sent when a client starts
     * tracking the player, then on every change. Since 2.0.0.
     *
     * @param namespace attachment-id namespace (consumer's mod id)
     * @param path      attachment-id path (unique per consumer-declared attachment)
     * @param slotCount number of item slots the content holds
     */
    public static PlayerStorageAttachment<NonNullList<ItemStack>> playerAttachedVisible(
            String namespace, String path, int slotCount) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        AttachmentType<ItemContainerContents> type =
                StorageAttachments.registerPlayerContentAttachment(id, slotCount, true);
        return new PlayerAttachment(slotCount, type);
    }

    /**
     * Saves slot-group content on a {@link BlockEntity} in a Fabric attachment. The
     * content lives as long as the block: breaking it destroys the attachment and
     * drops the content, as for a vanilla container. For containers that go with
     * their block (chest, furnace, hopper and so on).
     *
     * @param namespace attachment-id namespace (consumer's mod id)
     * @param path      attachment-id path (unique per consumer-declared attachment)
     * @param slotCount number of item slots the content holds
     */
    public static StorageAttachment<BlockEntity, NonNullList<ItemStack>> blockScoped(
            String namespace, String path, int slotCount) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        // registerBlockScopedAttachment adds the attachment to the block-scoped
        // registry the drop-on-break mixin reads (see BlockScopedDropHandler). That
        // is what makes the content drop when the block breaks.
        AttachmentType<ItemContainerContents> type =
                StorageAttachments.registerBlockScopedAttachment(id, slotCount);
        return new BlockScopedAttachment(slotCount, type);
    }

    /**
     * Saves slot-group content on any {@link Entity} in a Fabric attachment. The
     * content is saved in the entity's NBT and goes when the entity does, with nothing
     * carried over (unlike {@link #playerAttached}, which has death handling). For
     * containers owned by an entity: a minecart with chest, a donkey, llama or mule,
     * or other entity-scoped slots.
     *
     * <p>Saved on the server. The menu's vanilla slot protocol keeps the client view
     * current (the registered slots write the client-side attachment on each
     * {@code ContainerSetSlot}), the same way {@link #playerAttached} content shows
     * without a separate attachment sync.
     *
     * @param namespace attachment-id namespace (consumer's mod id)
     * @param path      attachment-id path (unique per consumer-declared attachment)
     * @param slotCount number of item slots the content holds
     */
    public static StorageAttachment<Entity, NonNullList<ItemStack>> entityAttached(
            String namespace, String path, int slotCount) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        // A plain persistent attachment, with no copyOnDeath and no block-drop entry.
        // The generic factory works for any Fabric AttachmentTarget, Entity included.
        AttachmentType<ItemContainerContents> type =
                StorageAttachments.registerContainerAttachment(id, slotCount);
        return new EntityAttachment(slotCount, type);
    }

    /**
     * Saves slot-group content on an {@link ItemStack} in vanilla's
     * {@link DataComponents#CONTAINER} component ({@link ItemContainerContents}). The
     * content travels with the item through menus, pickup, dropping and throwing.
     *
     * <p>Always uses {@code DataComponents.CONTAINER}, the component vanilla uses for
     * item-backed contents (shulker items, bundles). A generic
     * {@code itemAttached(DataComponentType<C>, ...)} factory can come later if a
     * consumer needs a different component.
     *
     * <p>Vanilla can replace an ItemStack during interactions (when a slot is emptied,
     * for example), so prefer {@link #bind(Supplier)} over {@link #bind(Object)} for
     * item-backed storage.
     */
    public static StorageAttachment<ItemStack, ItemContainerContents> itemContainer(int slotCount) {
        return new ItemContainerAttachment(slotCount);
    }

    /**
     * Wraps a {@link CustomAttachmentSpec} as a {@code StorageAttachment}. For owner
     * types the built-in factories don't cover, and for consumers whose save and load
     * don't go through a {@code CustomContainerMenu}.
     *
     * <p>This is the non-player path: the returned {@code StorageAttachment} has no
     * {@code dropsOnDeath}, because death handling is player-only. For a
     * player-anchored custom spec that needs death handling, use
     * {@link #customPlayerAttached}, which returns a {@link PlayerStorageAttachment}.
     *
     * @param <O> owner type
     * @param <C> content type
     */
    public static <O, C> StorageAttachment<O, C> custom(CustomAttachmentSpec<O, C> spec) {
        return new CustomAttachmentWrapper<>(spec);
    }

    /**
     * Wraps a player-anchored {@link CustomAttachmentSpec} as a
     * {@link PlayerStorageAttachment}, so it can opt in to death handling with
     * {@link PlayerStorageAttachment#dropsOnDeath(DropRule)}. The spec's owner type
     * must be {@link Player}, which the typed factory checks at compile time (the old
     * base {@code dropsOnDeath} threw at runtime on a non-player spec).
     *
     * <p>The library does the gamerule-gated DROP/DESTROY at the death site. Keeping
     * items across respawn is up to the consumer's storage: set {@code copyOnDeath} on
     * your Fabric attachment, or use a persistent store. The library can't set
     * {@code copyOnDeath} on storage it does not own, which is the point of a custom
     * spec.
     *
     * @param <C> content type
     */
    public static <C> PlayerStorageAttachment<C> customPlayerAttached(
            CustomAttachmentSpec<Player, C> spec) {
        return new CustomPlayerAttachmentWrapper<>(spec);
    }

    // Implementations

    // Owner is Void; bind(null) returns a new EphemeralStorage.
    private static final class EphemeralAttachment
            extends StorageAttachment<Void, NonNullList<ItemStack>> {
        private final int slotCount;
        EphemeralAttachment(int slotCount) { this.slotCount = slotCount; }
        @Override public int slotCount() { return slotCount; }
        @Override public Storage bind(Void ignored) { return EphemeralStorage.of(slotCount); }
    }

    private static final class PlayerAttachment
            extends PlayerStorageAttachment<NonNullList<ItemStack>> {
        private final int slotCount;
        private final AttachmentType<ItemContainerContents> type;
        PlayerAttachment(int slotCount, AttachmentType<ItemContainerContents> type) {
            this.slotCount = slotCount;
            this.type = type;
        }
        @Override public int slotCount() { return slotCount; }
        @Override public Storage bind(Player player) {
            // A PlayerStorage, so shift-click routing in CustomContainerMenu treats
            // this as the player side.
            return new PlayerFabricStorage(slotCount,
                    () -> player.getAttached(type),
                    contents -> player.setAttached(type, contents),
                    () -> { /* Fabric saves player attachments; no setChanged */ });
        }

        @Override
        public PlayerStorageAttachment<NonNullList<ItemStack>> dropsOnDeath(DropRule rule) {
            StorageAttachments.setDeathDropRule(type, rule);
            return this;
        }
    }

    // FabricAttachmentStorage with the PlayerStorage marker, for shift-click routing.
    private static final class PlayerFabricStorage implements PlayerStorage {
        private final FabricAttachmentStorage delegate;
        PlayerFabricStorage(int slotCount,
                             Supplier<@Nullable ItemContainerContents> reader,
                             java.util.function.Consumer<ItemContainerContents> writer,
                             Runnable dirtyMarker) {
            this.delegate = new FabricAttachmentStorage(slotCount, reader, writer, dirtyMarker);
        }
        @Override public ItemStack getStack(int i) { return delegate.getStack(i); }
        @Override public void setStack(int i, ItemStack s) { delegate.setStack(i, s); }
        @Override public int size() { return delegate.size(); }
        @Override public void markDirty() { delegate.markDirty(); }
    }

    private static final class BlockScopedAttachment
            extends StorageAttachment<BlockEntity, NonNullList<ItemStack>> {
        private final int slotCount;
        private final AttachmentType<ItemContainerContents> type;
        BlockScopedAttachment(int slotCount, AttachmentType<ItemContainerContents> type) {
            this.slotCount = slotCount;
            this.type = type;
        }
        @Override public int slotCount() { return slotCount; }
        @Override public Storage bind(BlockEntity be) {
            return new FabricAttachmentStorage(slotCount,
                    () -> be.getAttached(type),
                    contents -> be.setAttached(type, contents),
                    be::setChanged);
        }
    }

    private static final class EntityAttachment
            extends StorageAttachment<Entity, NonNullList<ItemStack>> {
        private final int slotCount;
        private final AttachmentType<ItemContainerContents> type;
        EntityAttachment(int slotCount, AttachmentType<ItemContainerContents> type) {
            this.slotCount = slotCount;
            this.type = type;
        }
        @Override public int slotCount() { return slotCount; }
        @Override public Storage bind(Entity entity) {
            return new FabricAttachmentStorage(slotCount,
                    () -> entity.getAttached(type),
                    contents -> entity.setAttached(type, contents),
                    () -> { /* Fabric saves entity attachments; no setChanged */ });
        }
    }

    private static final class ItemContainerAttachment
            extends StorageAttachment<ItemStack, ItemContainerContents> {
        private final int slotCount;

        /**
         * The live contents of each bound item: one list per stack, shared by every
         * {@code bind} of it ({@code bind(Supplier)} binds again on each access). Vanilla
         * changes a slot's stack in place ({@code grow} when stacking onto it, in a
         * click or a shift-click) and then calls only {@code setChanged}, so the stacks
         * handed out must be the ones that are written back: a fresh copy per
         * {@code getStack} would lose that growth, and the items with it. Reread whenever the
         * item's component is no longer the one last read or written (another mod or a
         * command changed it). Weak keys, so a dropped stack frees its entry.
         */
        private final Map<ItemStack, Live> live = Collections.synchronizedMap(new WeakHashMap<>());

        private static final class Live {
            @Nullable ItemContainerContents component;
            NonNullList<ItemStack> list;
        }

        ItemContainerAttachment(int slotCount) { this.slotCount = slotCount; }
        @Override public int slotCount() { return slotCount; }

        private Live liveOf(ItemStack stack) {
            Live l = live.computeIfAbsent(stack, k -> new Live());
            ItemContainerContents c = stack.get(DataComponents.CONTAINER);
            if (l.list == null || c != l.component) {
                l.list = StorageAttachments.contentsToList(c, slotCount);
                l.component = c;
            }
            return l;
        }

        private static void write(ItemStack stack, Live l) {
            ItemContainerContents c = StorageAttachments.listToContents(l.list);
            stack.set(DataComponents.CONTAINER, c);
            l.component = stack.get(DataComponents.CONTAINER);
        }

        @Override public Storage bind(ItemStack stack) {
            return new Storage() {
                @Override public ItemStack getStack(int i) {
                    if (stack.isEmpty()) return ItemStack.EMPTY;
                    return liveOf(stack).list.get(i);
                }
                @Override public void setStack(int i, ItemStack s) {
                    if (stack.isEmpty()) return;
                    Live l = liveOf(stack);
                    l.list.set(i, s);
                    write(stack, l);
                }
                @Override public int size() { return slotCount; }
                @Override public void markDirty() {
                    // A stack handed out by getStack may have changed in place.
                    if (stack.isEmpty()) return;
                    write(stack, liveOf(stack));
                }
            };
        }
    }

    private static final class CustomAttachmentWrapper<O, C>
            extends StorageAttachment<O, C> {
        private final CustomAttachmentSpec<O, C> spec;
        CustomAttachmentWrapper(CustomAttachmentSpec<O, C> spec) { this.spec = spec; }
        @Override public int slotCount() { return spec.slotCount(); }
        @Override public Storage bind(O owner) { return bindCustomSpec(spec, owner); }
    }

    private static final class CustomPlayerAttachmentWrapper<C>
            extends PlayerStorageAttachment<C> {
        private final CustomAttachmentSpec<Player, C> spec;
        CustomPlayerAttachmentWrapper(CustomAttachmentSpec<Player, C> spec) { this.spec = spec; }
        @Override public int slotCount() { return spec.slotCount(); }
        @Override public Storage bind(Player owner) { return bindCustomSpec(spec, owner); }

        @Override
        public PlayerStorageAttachment<C> dropsOnDeath(DropRule rule) {
            // The typed customPlayerAttached(...) factory guarantees a Player owner,
            // so this is never reached for a non-player spec.
            StorageAttachments.registerCustomPlayerDeathSpec(spec, rule);
            return this;
        }
    }

    // Shared by both custom-spec wrappers.
    private static <O, C> Storage bindCustomSpec(CustomAttachmentSpec<O, C> spec, O owner) {
        return new LiveListStorage<>(spec.slotCount(),
                () -> spec.read(owner),
                spec::toItemList,
                list -> spec.write(owner, spec.fromItemList(list)),
                () -> spec.markDirty(owner));
    }

    // Storage backed by a Fabric attachment. The reader returns null while nothing is attached.
    private static final class FabricAttachmentStorage extends LiveListStorage<ItemContainerContents> {
        FabricAttachmentStorage(int slotCount,
                                 Supplier<@Nullable ItemContainerContents> reader,
                                 java.util.function.Consumer<ItemContainerContents> writer,
                                 Runnable dirtyMarker) {
            super(slotCount, reader,
                    c -> StorageAttachments.contentsToList(c, slotCount),
                    list -> writer.accept(StorageAttachments.listToContents(list)),
                    dirtyMarker);
        }
    }

    /**
     * A storage over saved content that hands out one live list of stacks and writes it
     * back. Vanilla changes a slot's stack in place and then calls {@code setChanged}
     * (a shift-click merging into a partial stack, a split), so the stacks handed out
     * must be the ones that are written back: a fresh copy per {@code getStack} would
     * drop the change, losing or duplicating items. The list is read again whenever the saved
     * content no longer equals what it was read from (another storage over the same
     * owner, a command, death, another mod).
     */
    private static class LiveListStorage<C> implements Storage {
        private final int slotCount;
        private final Supplier<@Nullable C> reader;
        private final java.util.function.Function<@Nullable C, NonNullList<ItemStack>> toList;
        private final java.util.function.Consumer<NonNullList<ItemStack>> writer;
        private final Runnable dirtyMarker;
        private @Nullable NonNullList<ItemStack> list;
        private @Nullable C readFrom;          // the saved content the list was read from

        LiveListStorage(int slotCount, Supplier<@Nullable C> reader,
                        java.util.function.Function<@Nullable C, NonNullList<ItemStack>> toList,
                        java.util.function.Consumer<NonNullList<ItemStack>> writer,
                        Runnable dirtyMarker) {
            this.slotCount = slotCount;
            this.reader = reader;
            this.toList = toList;
            this.writer = writer;
            this.dirtyMarker = dirtyMarker;
        }

        /** The live list, read again if the saved content changed since. */
        private NonNullList<ItemStack> live() {
            C now = reader.get();
            if (list == null || (now != readFrom && !java.util.Objects.equals(now, readFrom))) {
                list = toList.apply(now);
                readFrom = now;
            }
            return list;
        }

        // Writes the list and records what was saved, so the next read keeps this list.
        private void writeBack(NonNullList<ItemStack> l) {
            writer.accept(l);
            readFrom = reader.get();
            dirtyMarker.run();
        }

        @Override public ItemStack getStack(int i) { return live().get(i); }

        @Override public void setStack(int i, ItemStack s) {
            NonNullList<ItemStack> l = live();
            l.set(i, s);
            writeBack(l);
        }

        @Override public int size() { return slotCount; }

        @Override public void markDirty() {
            // A stack handed out by getStack may have changed in place. live() first: if
            // the saved content changed meanwhile, that list is gone and so is the change.
            writeBack(live());
        }
    }
}
