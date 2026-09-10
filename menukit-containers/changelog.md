5.0.0: Every created slot group declares what it is. Requires MenuKit 5.0.0, released alongside it.

A group takes a `SlotGroupCategory` at creation, and MenuKit's slot group registry lists the group under that category on every menu it sits on. Another mod finds the group and knows what it is with MenuKit types only. `SlotSpec.at(groupId, category)` and `MKCSlots.onto(...).category(...)` are the two entry points.

A category carries inherent operations. What a bulk shortcut may do to a group follows from its category unless the group or a single slot says otherwise. `SlotSpec` gains `collect(false)` and `dragFill(false)` next to `quickMove(NONE)`, so a group opts out of double-click collect and drag-fill the same way it opts out of shift-click.

A created group is its own entry in the registry, not folded into its category. It has its own bounding box and cannot move a panel anchored to a different group. `MKCContainerPanel.groupId(panelId, groupId)` and `MKCSlots.groupId(panelId, groupId)` return the `SlotGroupId` another mod passes to `SlotGroupPanelAdapter.onGroup`.

Breaking: `SlotSpec.at(String)` is now `SlotSpec.at(String, SlotGroupCategory)`. `SlotGroup` constructors take a category. `MKCScreenHandler.PanelBuilder.group(...)` takes a category as its second argument. `MKCSlots.Builder.register()` refuses a group with no category. Every consumer adds one category per group. The refusal in `register()` happens at runtime, not compile time, so a mod that compiles without errors can still fail on the first menu it opens. Pick a vanilla category when the group is one of those things, or mint one with `new SlotGroupCategory("mymod", "name")`.
