# Versioning

SlotKit follows semantic versioning. The major number is the compatibility contract. SlotKit's version is its own: MenuKit and SlotKit each keep their own version, and neither re-versions the other.

## What the contract covers

The contract covers the public API: every type in a package named `api` (`com.trevlar.slotkit.api.*`), and every public member of those types that is not marked `@ApiStatus.Internal`. It also covers the saved ids SlotKit reads and writes ([concepts](concepts.md#saved-ids)), the `slotkit` block in `fabric.mod.json`, and what the handshake admits and refuses.

Everything else is internal: the other packages (each marked `@ApiStatus.Internal` on the package), mixins, network payloads, and any member marked `@ApiStatus.Internal`. Internal code can change or disappear in any release, a patch included. A mod that calls it can break without warning. A task that seems to need an internal type is a gap in the API, and the fix is a public replacement. Open an issue for it.

Behaviour counts too. A change to what a public method does that could break a caller is a breaking change, the same as removing it.

## What each part means

A **major** bump means something public was removed or renamed, or its behaviour changed in a way that can break a caller. Code built against the previous major may not link.

A **minor** bump adds API. Code built against an earlier minor of the same major keeps working.

A **patch** bump fixes behaviour without changing the API.

The `+26.3` suffix is the Minecraft target. It is build metadata and takes no part in version comparison. A range written against `2.0.0` still matches `2.0.0+26.4` when SlotKit is rebuilt for a newer Minecraft.

## What to declare

Pin each Kit against its own version, to the major.minor the mod was built against:

```json
"depends": {
  "menukit": ">=7.3.0 <8.0.0",
  "slotkit": ">=2.0.0 <3.0.0"
}
```

A minor release can add API. A mod can call something a later minor added while declaring only the major's first release as its floor. That mod loads on the older SlotKit and crashes with `NoSuchMethodError` the first time it calls the new method. Set the lower bound to the version the mod was built and tested against, not the oldest one believed to still work.

SlotKit 2.0.0 needs MenuKit `>=7.3.0 <8.0.0`. Your mod's `menukit` range sits inside that.

## Two mistakes

### A comma between the bounds

Fabric splits the bounds on a space. A comma is not a separator, so `">=2.0.0,<3.0.0"` is read as one bound with a malformed version, and the mod does not load. Use a space.

### A missing upper bound

SlotKit declares `breaks` against mods known to be incompatible, but it can only name versions that existed when it shipped. Nothing on the SlotKit side protects a mod from a SlotKit released after it. The upper bound is the mod's to set.

## Your mod's own version and its slots

The join check compares the slot groups each `required` mod registers through `ParityGroup`, not its version number ([concepts](concepts.md#declaring-sides)). A release of your mod that changes those groups refuses players on the other release, with a message. A release that changes a custom menu's slots, or a group built with `CreatedSlots.onto`, is not caught. Change that menu's or group's id in the same release.

## When SlotKit takes a new major

Update the code for the new API, change both `slotkit` bounds to the new major, and release under a new version of the mod. Do not ship different requirements under a version number already published. Two jars with that number claim to be the same release, and only one of them works.
