package com.trevlar.menukit.containers.core.attachment;

import com.trevlar.menukit.containers.api.storage.CustomAttachmentSpec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.mojang.serialization.Codec;
import com.trevlar.menukit.containers.api.slot.DropRule;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric attachment-type registry for storage attachments. Each consumer-declared
 * {@code StorageAttachment} registers exactly one attachment per owner type with a
 * unique identifier. The library keeps this registry so registrations are idempotent
 * and can be inspected.
 *
 * <p>Attachments store {@link ItemContainerContents} (vanilla's codec-backed item
 * list), and the storage converts to and from a mutable {@link NonNullList} on each
 * read and write. That way vanilla's codec is reused directly instead of building a
 * NonNullList codec.
 *
 * <p>Not public API. Consumers go through {@code StorageAttachment}'s factories,
 * which call into this registry.
 */
public final class StorageAttachments {

    private StorageAttachments() {}

    // Attachment types by identifier. Fabric's registry throws on a duplicate
    // registration, so this cache returns the same type for repeated
    // StorageAttachment declarations with the same id. That is allowed here because
    // consumers may reload static fields during hot reload in dev.
    private static final ConcurrentHashMap<Identifier, AttachmentType<ItemContainerContents>> CACHE =
            new ConcurrentHashMap<>();

    // Slot count per attachment, needed at drop-on-break time to walk the contents.
    private static final ConcurrentHashMap<AttachmentType<ItemContainerContents>, Integer>
            ATTACHMENT_SIZES = new ConcurrentHashMap<>();

    // Block-scoped attachments register here as well as in CACHE. The
    // drop-on-break mixin walks this list, not CACHE, to skip player- and
    // item-attached ones, which are never affected by a block breaking. Filled by
    // registerBlockScopedAttachment(Identifier, int).
    private static final java.util.List<AttachmentType<ItemContainerContents>>
            BLOCK_SCOPED_ATTACHMENTS =
                    new java.util.concurrent.CopyOnWriteArrayList<>();

    // Player content attachments enrolled for death handling. Like
    // BLOCK_SCOPED_ATTACHMENTS, but maps each type to its DropRule. Mutable, so the
    // consumer can change the rule through StorageAttachment.dropsOnDeath() after
    // registration. copyOnDeath is already set at registration, so a change is a
    // plain map update, not a new registration. PlayerDeathDropHandler and the grave
    // adapters both read it. Filled by registerPlayerContentAttachment.
    private static final ConcurrentHashMap<AttachmentType<ItemContainerContents>, DropRule>
            PLAYER_DEATH_DROP = new ConcurrentHashMap<>();

    // Custom (consumer-defined) player-anchored content specs enrolled for death
    // handling. A custom spec has no library-owned AttachmentType to set copyOnDeath
    // on, since the consumer owns the storage, so it can't sit in PLAYER_DEATH_DROP.
    // The library only does the gamerule-gated DROP/DESTROY at the death site,
    // reading and writing through the spec. Keeping items across respawn is the
    // consumer storage's job: it sets copyOnDeath on its own Fabric attachment or
    // uses a persistent store, because the library can't set copyOnDeath on storage
    // it doesn't own. Keyed by spec so dropsOnDeath() can change the rule later.
    private static final ConcurrentHashMap<CustomAttachmentSpec<?, ?>, DropRule>
            CUSTOM_PLAYER_DEATH_DROP = new ConcurrentHashMap<>();

    /**
     * Registers (or returns) an attachment storing {@link ItemContainerContents}.
     * Fabric's {@code .persistent(Codec)} handles persistence.
     *
     * <p>For attachments that are not block-scoped (player-attached, item-attached,
     * ephemeral, custom). Block-scoped attachments use
     * {@link #registerBlockScopedAttachment(Identifier, int)}, which does the same
     * registration and also adds the entry for drop-on-break.
     *
     * @param id           the attachment's unique identifier (shows up in NBT paths)
     * @param defaultSlots number of slots in the default empty content
     */
    public static AttachmentType<ItemContainerContents> registerContainerAttachment(
            Identifier id, int defaultSlots) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("a storage attachment");
        AttachmentType<ItemContainerContents> type = CACHE.computeIfAbsent(id, k ->
                AttachmentRegistry.<ItemContainerContents>builder()
                        .persistent(ItemContainerContents.CODEC)
                        .initializer(() -> defaultEmpty(defaultSlots))
                        .buildAndRegister(k));
        ATTACHMENT_SIZES.putIfAbsent(type, defaultSlots);
        return type;
    }

    /**
     * Registers (or returns) a player content attachment. Like
     * {@link #registerContainerAttachment(Identifier, int)}, but with Fabric's
     * {@code copyOnDeath()} flag, so a kept bag carries across respawn, and enrolled
     * in the player death-drop registry at {@link DropRule#DEFAULT}, which matches
     * vanilla with no setup. {@link PlayerDeathDropHandler} walks the registry and
     * does the {@code keepInventory} check. The consumer changes the rule per
     * attachment with {@code StorageAttachment.dropsOnDeath(...)}. Called by
     * {@link com.trevlar.menukit.containers.api.storage.StorageAttachment#playerAttached(String, String, int)}.
     */
    public static AttachmentType<ItemContainerContents> registerPlayerContentAttachment(
            Identifier id, int defaultSlots) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("a storage attachment");
        AttachmentType<ItemContainerContents> type = CACHE.computeIfAbsent(id, k ->
                AttachmentRegistry.<ItemContainerContents>builder()
                        .persistent(ItemContainerContents.CODEC)
                        .copyOnDeath()
                        .initializer(() -> defaultEmpty(defaultSlots))
                        .buildAndRegister(k));
        ATTACHMENT_SIZES.putIfAbsent(type, defaultSlots);
        PLAYER_DEATH_DROP.putIfAbsent(type, DropRule.DEFAULT);
        return type;
    }

    /**
     * Registers a block-scoped attachment. Same as
     * {@link #registerContainerAttachment(Identifier, int)}, plus an entry in the
     * block-scoped registry the drop-on-break mixin reads. Called by
     * {@link com.trevlar.menukit.containers.api.storage.StorageAttachment#blockScoped(String, String, int)}.
     */
    public static AttachmentType<ItemContainerContents> registerBlockScopedAttachment(
            Identifier id, int defaultSlots) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("a storage attachment");
        AttachmentType<ItemContainerContents> type =
                registerContainerAttachment(id, defaultSlots);
        if (!BLOCK_SCOPED_ATTACHMENTS.contains(type)) {
            BLOCK_SCOPED_ATTACHMENTS.add(type);
        }
        return type;
    }

    /** Snapshot of all registered block-scoped attachments, for drop-on-break iteration. */
    public static java.util.List<AttachmentType<ItemContainerContents>> blockScopedAttachments() {
        return java.util.List.copyOf(BLOCK_SCOPED_ATTACHMENTS);
    }

    /**
     * Changes the death-drop rule for a player content attachment. Called by
     * {@code StorageAttachment.dropsOnDeath(...)}. A plain map update, valid after
     * registration because {@code copyOnDeath} was already set when the attachment
     * registered.
     */
    public static void setDeathDropRule(AttachmentType<ItemContainerContents> type, DropRule rule) {
        PLAYER_DEATH_DROP.put(type, rule);
    }

    /** The configured death-drop rule for a type, or {@code null} if it isn't death-enrolled. */
    public static DropRule deathDropRuleOf(AttachmentType<ItemContainerContents> type) {
        return PLAYER_DEATH_DROP.get(type);
    }

    /** Snapshot of all death-droppable player content attachments, for the death handler. */
    public static java.util.Set<AttachmentType<ItemContainerContents>> playerDeathDropAttachments() {
        return java.util.Set.copyOf(PLAYER_DEATH_DROP.keySet());
    }

    /**
     * Enrolls a player-anchored custom spec for death handling at {@code rule}, or
     * changes the rule for a spec already enrolled. Called by
     * {@code StorageAttachment.customPlayerAttached(spec).dropsOnDeath(rule)}. The
     * typed factory makes the owner type {@code Player}, so a non-player spec can
     * never reach this. The library does the gamerule-gated DROP/DESTROY, and the
     * consumer's storage is responsible for keeping items across respawn.
     */
    public static void registerCustomPlayerDeathSpec(CustomAttachmentSpec<?, ?> spec, DropRule rule) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("a death drop spec");
        CUSTOM_PLAYER_DEATH_DROP.put(spec, rule);
    }

    /** Snapshot of the death-enrolled custom player specs and their rules, for the death handler. */
    public static java.util.Map<CustomAttachmentSpec<?, ?>, DropRule> customPlayerDeathSpecs() {
        return java.util.Map.copyOf(CUSTOM_PLAYER_DEATH_DROP);
    }

    /** Slot count for a registered attachment, used at drop-on-break to size the list. */
    public static int slotCountOf(AttachmentType<ItemContainerContents> type) {
        Integer size = ATTACHMENT_SIZES.get(type);
        return size == null ? 0 : size;
    }

    /**
     * The Identifier a content attachment was registered under, or null if it isn't
     * in the cache. {@link PlayerSlotCapture} uses it to tag a captured slot with a
     * stable id that a grave can serialize.
     */
    public static @Nullable Identifier identifierOf(AttachmentType<ItemContainerContents> type) {
        for (var e : CACHE.entrySet()) {
            if (e.getValue() == type) return e.getKey();
        }
        return null;
    }

    /** The content attachment registered under {@code id}, or null. Used when restoring a grave capture. */
    public static @Nullable AttachmentType<ItemContainerContents> typeById(Identifier id) {
        return CACHE.get(id);
    }

    public static ItemContainerContents defaultEmpty(int slotCount) {
        NonNullList<ItemStack> empty = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        return ItemContainerContents.fromItems(empty);
    }

    /**
     * Converts {@link ItemContainerContents} to a mutable {@link NonNullList}. Every
     * call allocates a new list; callers change their copy and write it back with
     * {@link #listToContents}.
     */
    public static NonNullList<ItemStack> contentsToList(ItemContainerContents contents,
                                                         int slotCount) {
        NonNullList<ItemStack> list = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        if (contents != null) contents.copyInto(list);
        return list;
    }

    public static ItemContainerContents listToContents(NonNullList<ItemStack> list) {
        return ItemContainerContents.fromItems(list);
    }
}
