5.1.0: Created slots follow MenuKit's slot operations. Requires MenuKit 5.1.0. This release adds API and breaks nothing.

Shift-clicking into or out of a created slot now goes through the same shift-click in and shift-click out operations as a vanilla slot. A locking mod's veto covers pockets and other created slots without any extra work from you.

Created slot groups appear in MenuKit's slot group list without a menu open. Groups on a container panel are listed from mod init. Groups added with `MKCSlots.onto` are listed once a menu carries them, or from init if you call `SlotGroups.declare`.

`SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated and will be removed in 6.0.0. They still work, and `quickMove` now sets the two shift-click operations for you. New code should call `set(BehaviorKeys.SHIFT_CLICK_OUT, ...)` and `set(BehaviorKeys.SHIFT_CLICK_IN, ...)`.
