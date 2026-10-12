# The Amethyst in-code renames, after amethyst-imports.sed (imports alone do not carry these).
# Run with sed -E. Whole tokens only.
s/(^|[^A-Za-z0-9_.])SlotGroup([^A-Za-z0-9_]|$)/\1CreatedSlotGroup\2/g
s/(^|[^A-Za-z0-9_])ContainerKeys\.MENDING([^A-Za-z0-9_]|$)/\1BehaviorKeys.MENDING\2/g
