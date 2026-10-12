#!/usr/bin/env python3
"""Lays the seeded tree out as SlotKit and renames by one table.

Run once, from the repo root, on the seed (Containers' history plus the slot files
MenuKit 7.0.0 deleted). It git-mv's every Java file to its SlotKit path, then rewrites
packages, imports and fully qualified names across the tree from the same old-to-new
class map, and writes that map as the migration guide's sed files, so the guide's
import map is generated from the actual moves, never by hand.

Kept as a record of how the move was made; it is not part of the build.
"""
import os
import re
import subprocess
import sys

MK = "com/trevlar/menukit/"
SK = "com/trevlar/slotkit/"

# Mixins with no MK/MKC prefix get SK prepended. M7BlockDropMixin is renamed outright,
# since its M7 prefix was an internal label.
MIXIN_RENAMES = {"M7BlockDropMixin": "SKBlockDropMixin"}
# Accessors and invokers keep their names: they never show in a stack trace as the culprit.
KEEP = {"CompoundContainerAccessor", "SlotPositionAccessor", "SlotWrapperAccessor",
        "AbstractContainerMenuInvoker"}
CLASS_RENAMES = {"SlotGroup": "CreatedSlotGroup", "MenuKitContainers": "SlotKit",
                 "MenuKitContainersClient": "SlotKitClient",
                 "Presence": "Handshake", "PresenceClient": "HandshakeClient"}


def new_path(old):
    """Old repo path to new repo path (None to leave alone)."""
    m = re.match(r"menukit-containers/src/(main|client)/java/com/trevlar/menukit/containers/(.*)", old)
    if m:
        side, rest = m.groups()
        d, name = os.path.split(rest)
        stem = name[:-5] if name.endswith(".java") else name
        if d == "api/slot" and stem == "SlotGroup":
            name = "CreatedSlotGroup.java"
        elif d == "" and stem in ("MenuKitContainers", "MenuKitContainersClient"):
            name = CLASS_RENAMES[stem] + ".java"
        elif d == "network" and stem in ("Presence", "PresenceClient"):
            name = CLASS_RENAMES[stem] + ".java"
        elif d == "mixin":
            name = mixin_name(stem) + ".java"
        return f"src/{side}/java/{SK}{os.path.join(d, name)}"
    m = re.match(r"menukit/src/(main|client)/java/com/trevlar/menukit/(.*)", old)
    if m:
        side, rest = m.groups()
        d, name = os.path.split(rest)
        stem = name[:-5]
        if d == "api/slot":
            d = "api/storage" if stem in ("Storage", "VirtualStorage") else "api/group"
        elif d == "api/panel":          # SlotGroupPanelAdapter
            d = "api/group"
        elif d in ("inject", "core"):   # Slots, resolvers, ClickOutsideHelper
            d = "core"
        elif d == "mixin":
            name = mixin_name(stem) + ".java"
        # api/window and window keep their shape
        return f"src/{side}/java/{SK}{os.path.join(d, name)}"
    m = re.match(r"menukit-containers/src/main/resources/(.*)", old)
    if m:
        rest = m.group(1).replace("menukit-containers.mixins.json", "slotkit.mixins.json")
        return f"src/main/resources/{rest}"
    m = re.match(r"menukit-containers/([^/]+)$", old)
    if m:
        return m.group(1)
    return None


def mixin_name(stem):
    if stem in KEEP:
        return stem
    if stem in MIXIN_RENAMES:
        return MIXIN_RENAMES[stem]
    for prefix in ("MKC", "MK"):
        if stem.startswith(prefix) and stem[len(prefix)].isupper():
            return "SK" + stem[len(prefix):]
    return "SK" + stem


def fqn(path):
    m = re.match(r".*?/java/(.*)\.java$", path)
    return m.group(1).replace("/", ".") if m else None


def main():
    files = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout.split()
    moves = [(f, new_path(f)) for f in files]
    moves = [(a, b) for a, b in moves if b and a != b]
    class_map = {}
    for a, b in moves:
        if a.endswith(".java") and not a.endswith("package-info.java"):
            class_map[fqn(a)] = fqn(b)
    for a, b in moves:
        os.makedirs(os.path.dirname(b) or ".", exist_ok=True)
        subprocess.run(["git", "mv", a, b], check=True)

    # Whole-package moves, for wildcard imports and package lines.
    pkg_map = {}
    for old, new in class_map.items():
        po, pn = old.rsplit(".", 1)[0], new.rsplit(".", 1)[0]
        pkg_map.setdefault(po, set()).add(pn)

    # Longest name first, so a class is never half-replaced by a shorter one's rule.
    rules = sorted(class_map.items(), key=lambda kv: -len(kv[0]))
    simple_renames = {old.rsplit(".", 1)[1]: new.rsplit(".", 1)[1]
                      for old, new in class_map.items()
                      if old.rsplit(".", 1)[1] != new.rsplit(".", 1)[1]}

    for path in subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                               check=True).stdout.split():
        if not path.endswith((".java", ".json", ".gradle", ".md", ".py")):
            continue
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        orig = text
        # Wildcard imports: a Containers package is gone, so it is replaced; a MenuKit
        # package that only lost some classes stays, beside the new home of the rest.
        for po, pns in pkg_map.items():
            news = "".join(f"import {pn}.*;\n" for pn in sorted(pns))
            keep = "" if po.startswith("com.trevlar.menukit.containers") else f"import {po}.*;\n"
            text = text.replace(f"import {po}.*;\n", keep + news)
        for old, new in rules:
            text = re.sub(re.escape(old) + r"(?![A-Za-z0-9_])", new, text)
        # Package declarations follow the file's new directory.
        if path.endswith(".java"):
            pkg = fqn(path).rsplit(".", 1)[0]
            text = re.sub(r"^package [\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
        # Same-package references to renamed classes (no import names them).
        for o, n in simple_renames.items():
            text = re.sub(r"(?<![A-Za-z0-9_.$])" + re.escape(o) + r"(?![A-Za-z0-9_])", n, text)
        # Unique-member prefixes.
        text = re.sub(r"\bmkc?\$", "sk$", text)
        if text != orig:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(text)

    # The migration guide's import map: one line per moved public type.
    with open("tools/amethyst-imports.sed", "w") as fh:
        fh.write("# Generated by tools/amethyst-move.py from the actual moves. Longest name first.\n")
        for old, new in rules:
            if ".api." not in old:
                continue
            fh.write("s/" + old.replace(".", r"\.") + r"([^A-Za-z0-9_]|$)/" + new + r"\1/g" + "\n")
    print(f"moved {len(moves)} files, {len(class_map)} classes")


if __name__ == "__main__":
    sys.exit(main())
