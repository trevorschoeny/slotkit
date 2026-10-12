# The in-code renames, after amethyst-imports.sed (imports alone do not carry these).
# Run with sed -E, only on the files that use Containers' types: a mod may have a class
# of its own named SlotGroup (Inventory Plus does), which must not be renamed. After the
# import map, those are the files importing from com.trevlar.slotkit.api.slot:
#
#   grep -rlE 'import com\.trevlar\.slotkit\.api\.slot\.(CreatedSlotGroup|\*);' src \
#       | xargs sed -E -i '' -f amethyst-renames.sed
#
# ContainerKeys.MENDING is a qualified name, so its line is safe anywhere.
s/(^|[^A-Za-z0-9_.])SlotGroup([^A-Za-z0-9_]|$)/\1CreatedSlotGroup\2/g
s/(^|[^A-Za-z0-9_])ContainerKeys\.MENDING([^A-Za-z0-9_]|$)/\1BehaviorKeys.MENDING\2/g
