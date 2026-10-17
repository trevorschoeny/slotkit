package com.trevlar.slotkit.network;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What each loaded mod declares about its sides, read from its {@code fabric.mod.json}:
 *
 * <pre>{@code "custom": { "slotkit": { "bothSides": "required" } }}</pre>
 *
 * <ul>
 *   <li>{@link Need#REQUIRED}: the mod builds slots or custom menus. A server with it
 *       admits only clients that have SlotKit and the mod.</li>
 *   <li>{@link Need#PREFERRED}: the mod works one-sided and does more with both. It
 *       never refuses anyone; it is listed in the handshake, so either side can ask
 *       {@code Presence} about it.</li>
 *   <li>No block: a client-tier mod, or one that never asks.</li>
 * </ul>
 *
 * <p>Read before any code runs, so a mismarked mod fails in development, at the
 * {@link #requireBuildsSlots definition check}, rather than desyncing players.
 */
@ApiStatus.Internal
public final class ModSides {

    private ModSides() {}

    /** What a declaring mod needs from the other side. */
    public enum Need { REQUIRED, PREFERRED }

    /** A declaring mod, as the handshake and its messages need it. */
    public record Declared(String id, String name, Need need, @Nullable String homepage) {}

    private static volatile @Nullable SortedMap<String, Declared> declared;

    /** Every loaded mod with a {@code slotkit} block, by id. Read once, on first use. */
    public static SortedMap<String, Declared> declared() {
        SortedMap<String, Declared> d = declared;
        if (d == null) {
            SortedMap<String, Declared> read = new TreeMap<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                Declared one = read(mod.getMetadata());
                if (one != null) read.put(one.id(), one);
            }
            declared = d = Collections.unmodifiableSortedMap(read);
        }
        return d;
    }

    /** What {@code modId} declares, or {@code null} for a mod without a block (or not loaded). */
    public static @Nullable Need of(String modId) {
        Declared d = declared().get(modId);
        return d == null ? null : d.need();
    }

    /** One mod's block, or {@code null} without one. A block that says anything else throws. */
    static @Nullable Declared read(ModMetadata meta) {
        if (!meta.containsCustomValue("slotkit")) return null;
        String id = meta.getId();
        CustomValue block = meta.getCustomValue("slotkit");
        String value = null;
        if (block.getType() == CustomValue.CvType.OBJECT) {
            CustomValue sides = block.getAsObject().get("bothSides");
            if (sides != null && sides.getType() == CustomValue.CvType.STRING) value = sides.getAsString();
        }
        Need need = value == null ? null : switch (value.toLowerCase(Locale.ROOT)) {
            case "required" -> Need.REQUIRED;
            case "preferred" -> Need.PREFERRED;
            default -> null;
        };
        if (need == null) {
            throw new IllegalStateException("Mod '" + id + "' has a \"slotkit\" block in fabric.mod.json, but "
                    + "\"bothSides\" must be \"required\" or \"preferred\": "
                    + "\"custom\": { \"slotkit\": { \"bothSides\": \"required\" } }");
        }
        String homepage = meta.getContact().get("homepage").orElse(null);
        return new Declared(id, meta.getName(), need, homepage);
    }

    /**
     * The namespace an id names its mod by: the text before its first {@code ':'}, or
     * {@code null} when it has none. A created slot's panel id ({@code mymod:pockets:main},
     * a container panel id plus its group) names its mod the same way.
     */
    public static @Nullable String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon <= 0 ? null : id.substring(0, colon);
    }

    /**
     * The definition check: a panel or menu that builds slots must come
     * from a loaded mod that declares {@code bothSides: required}, named by the id's
     * namespace. This is what keeps a server with only {@code preferred} mods safe for
     * vanilla clients: such a server never changes a menu's slot count.
     *
     * @param what "ParityGroup" or "CustomMenu", for the message
     * @param id   the panel or menu id; its namespace must be the defining mod's id
     * @throws IllegalStateException naming the id and the line to add
     */
    public static void requireBuildsSlots(String what, String id) {
        String error = definitionError(what, id, FabricLoader.getInstance()::isModLoaded, ModSides::of);
        if (error != null) throw new IllegalStateException(error);
    }

    /**
     * The definition check as a pure function: the message to throw, or {@code null}
     * when {@code id} may build slots.
     *
     * @param loaded whether a mod id is loaded
     * @param sides  what a loaded mod declares, {@code null} for no block
     */
    public static @Nullable String definitionError(String what, String id, java.util.function.Predicate<String> loaded,
                                                   java.util.function.Function<String, @Nullable Need> sides) {
        String namespace = namespaceOf(id);
        String line = "\"custom\": { \"slotkit\": { \"bothSides\": \"required\" } }";
        if (namespace == null) {
            return what + " '" + id + "': an id that builds slots is namespaced by the id of the mod "
                    + "that defines it ('mymod:" + id + "').";
        }
        if (!loaded.test(namespace)) {
            return what + " '" + id + "': its namespace '" + namespace + "' is not a loaded mod's id. An "
                    + "id that builds slots is namespaced by the id of the mod that defines it, and that "
                    + "mod declares " + line + " in its fabric.mod.json.";
        }
        if (sides.apply(namespace) != Need.REQUIRED) {
            return what + " '" + id + "' builds slots, so SlotKit is needed on both sides wherever '"
                    + namespace + "' is installed. Add to " + namespace + "'s fabric.mod.json: " + line;
        }
        return null;
    }
}
