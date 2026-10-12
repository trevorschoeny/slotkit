package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.state.PortableSlotState;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.fabricmc.fabric.impl.registry.sync.validate.RegistryCustomContentState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells Fabric API's missing-content check that {@code menukit:portable_slot_state} is
 * retired, not missing. Containers 6.x registered it as a data component type, so every
 * world that ran 6.x lists it in {@code data/fabric/registry_entries.dat}. SlotKit
 * registers no component (decision 8) and renames the old one as it reads
 * ({@code SKComponentPatchLegacyMixin}), so nothing is lost, but without this the client
 * shows "Missing content detected!" on opening such a world. The one job a data fixer
 * would do, like the rename. Permanent, for the same reason.
 *
 * <p>Fabric API 0.162.0 has no supported way to retire an entry. {@code validate} is the
 * only reader of the saved snapshot (the client calls it in {@code WorldOpenFlows} before
 * opening a world; a server only rewrites the file on start, so its join path never
 * passes here). Only that one id in that one registry is dropped from the verdict:
 * anything else missing still shows the screen, as does the same id under any other
 * registry.
 */
@ApiStatus.Internal
@Mixin(RegistryCustomContentState.class)
public abstract class SKRetiredRegistryEntryMixin {

    @ModifyReturnValue(method = "validate", at = @At("RETURN"))
    private RegistryCustomContentState.Missing sk$retirePortableSlotState(RegistryCustomContentState.Missing missing) {
        Identifier registry = Registries.DATA_COMPONENT_TYPE.identifier();
        Identifier retired = Identifier.parse(PortableSlotState.KEY);
        List<Identifier> ids = missing.entries().get(registry);
        if (ids == null || !ids.contains(retired)) return missing;

        // Same verdict, minus the retired id; the registry's entry goes if nothing else is missing in it.
        Map<Identifier, List<Identifier>> entries = new HashMap<>(missing.entries());
        List<Identifier> rest = ids.stream().filter(id -> !id.equals(retired)).toList();
        if (rest.isEmpty()) entries.remove(registry);
        else entries.put(registry, rest);
        return new RegistryCustomContentState.Missing(entries, missing.registries(), missing.status());
    }
}
