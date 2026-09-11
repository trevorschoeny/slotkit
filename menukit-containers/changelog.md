5.1.0: Shift-click is two operations, and MenuKit enforces them. Requires MenuKit 5.1.0, released alongside it. Adds API and breaks nothing.

Shift-click routing on a foreign menu now asks MenuKit's `SHIFT_CLICK_OUT` on the source and `SHIFT_CLICK_IN` on each candidate group, through `SlotOperations.allows`, so a veto from a locking mod reaches created slots the same way it reaches vanilla ones.

Deprecated, gone in 6.0.0: `MKCBehaviorKeys.QUICK_MOVE` and `SlotSpec.quickMove(...)`. The verb still works and now declares both shift-click keys at the group rung. The key is still honoured, ANDed with the two, but is no longer listed in `SlotOperations.all()`. Declare `BehaviorKeys.SHIFT_CLICK_OUT` and `SHIFT_CLICK_IN` instead.

Created groups declare themselves into MenuKit's menu-free group list. A container-panel group is declared at `register()`, at init. An `MKCSlots.onto` group is declared when the first menu carrying it is built; declare it at init with `SlotGroups.declare` to list it before that.

Internal: enforcement of the vanilla operations and the acting-player capture moved into MenuKit. `MKCOperationMixin` and `MKCActingPlayerMixin` are deleted; `GatingContext.current()` reads MenuKit's `ActingPlayer`.

5.0.0: Every created slot group declares what it is. Requires MenuKit 5.0.0, released alongside it.

A group takes a `SlotGroupCategory` at creation, and MenuKit's slot group registry lists the group under that category on every menu it sits on. Another mod finds the group and knows what it is with MenuKit types only. `SlotSpec.at(groupId, category)` and `MKCSlots.onto(...).category(...)` are the two entry points.

A category carries inherent operations. What a bulk shortcut may do to a group follows from its category unless the group or a single slot says otherwise. `SlotSpec` gains `collect(false)` and `dragFill(false)` next to `quickMove(NONE)`, so a group opts out of double-click collect and drag-fill the same way it opts out of shift-click.

A created group is its own entry in the registry, not folded into its category. It has its own bounding box and cannot move a panel anchored to a different group. `MKCContainerPanel.groupId(panelId, groupId)` and `MKCSlots.groupId(panelId, groupId)` return the `SlotGroupId` another mod passes to `SlotGroupPanelAdapter.onGroup`.

Breaking: `SlotSpec.at(String)` is now `SlotSpec.at(String, SlotGroupCategory)`. `SlotGroup` constructors take a category. `MKCScreenHandler.PanelBuilder.group(...)` takes a category as its second argument. `MKCSlots.Builder.register()` refuses a group with no category. Every consumer adds one category per group. The refusal in `register()` happens at runtime, not compile time, so a mod that compiles without errors can still fail on the first menu it opens. Pick a vanilla category when the group is one of those things, or mint one with `new SlotGroupCategory("mymod", "name")`.
