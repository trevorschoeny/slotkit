# Getting started

This page adds SlotKit to a Fabric mod that already uses MenuKit. It does two things: a panel beside the player inventory, which needs SlotKit on the client only, and nine real slots, which need SlotKit on both sides. Stop after step 2 if your mod builds no slots.

Prerequisites: a Fabric mod on Minecraft 26.3 with MenuKit 7 set up ([MenuKit's getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md)), and a client entry point.

## 1. Add the dependency

Add SlotKit beside MenuKit in `build.gradle`. Minecraft 26.3 is unobfuscated, so the dependency goes on `implementation`:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.0.0+26.3'
    implementation 'maven.modrinth:xQUBXV8I:1.0.0+26.3'   // SlotKit
}
```

`xQUBXV8I` is SlotKit's Modrinth project id. It resolves whatever the project's slug is.

Declare both in `fabric.mod.json`, each against its own version:

```json
"depends": { "menukit": ">=7.0.0 <8.0.0", "slotkit": ">=1.0.0 <2.0.0" }
```

Separate the bounds with a space. Fabric splits on spaces, so a comma leaves one malformed bound and the mod does not load. [Versioning](versioning.md) has the full contract. Import only from the `api` packages (`com.trevlar.slotkit.api.*`).

## 2. Put a panel beside the player inventory

Call this once from the client entry point. It uses the client tier, so it works on any server, vanilla included.

```java
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;

Panel panel = Panel.builder("mymod:toolbar")
        .style(PanelStyle.NONE)
        .elements(toolbarButtons)
        .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
        .visibleWhen(() -> config.toolbar)
        .build();
new SlotGroupPanelAdapter(panel)
        .on(SlotGroupCategory.PLAYER_INVENTORY);
```

Result: while `config.toolbar` is on, the buttons in `toolbarButtons` render above the right edge of the player inventory grid. They appear on every screen that shows that grid: the survival inventory, every chest, and every other container.

The panel is MenuKit's: build its elements as MenuKit does. `SlotGroupPanelAdapter` places it against a slot group instead of a screen's frame. A mod that stops here declares no `slotkit` block.

## 3. Add nine real slots

Real slots need SlotKit on both sides. Declare that in `fabric.mod.json`:

```json
"custom": { "slotkit": { "bothSides": "required" } }
```

Declare the storage once, at common init:

```java
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.storage.StorageAttachment.PlayerStorageAttachment;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);
```

Then register the panel from the common entry point:

```java
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.ContainerPanel;
import com.trevlar.slotkit.api.slot.SlotSpec;

ContainerPanel.define("mymod:pockets")
        .at(OutsideRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

Result: nine slots render in the top left gutter of the survival inventory, the creative inventory, and every container screen. What a player puts in them persists on the player and syncs like vanilla's slots.

Two rules come with real slots:

- The panel id's namespace is your mod id. Here the mod id is `mymod`. `define` throws at startup when the namespace is not a loaded mod, or when that mod does not declare `required`. The message names the panel and the line to add.
- A server running your mod admits only players who have SlotKit and your mod. Others are refused before the world loads, with a message naming both and linking each mod's `contact.homepage`. Set `contact.homepage` in your `fabric.mod.json` so the link is there.

## 4. Run

Start the client with the mod's `runClient` task and open the inventory to see the toolbar and the slots. To see the join check, start a dedicated server with `runServer`, then join it from a client without your mod.

## Next

- [concepts.md](concepts.md) defines slot groups, operations, created slots, addresses, slot state, and declaring sides.
- [recipes.md](recipes.md) covers the common tasks.
- [limits.md](limits.md) lists what SlotKit does not do.
- [upgrading.md](upgrading.md) moves a mod from MenuKit: Containers or MenuKit 6's slot code.
