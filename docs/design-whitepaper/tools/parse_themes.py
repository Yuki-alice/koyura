#!/usr/bin/env python3
"""Re-parse every Komikku ColorScheme source file into JSON.

Reads only; prints JSON to stdout. Run with -I to avoid picking up local
modules from the current directory:

    python3 -I tools/parse_themes.py > /tmp/themes.json

Default source root is the sibling reference checkout. Override with
--source-root.
"""

import argparse
import glob
import json
import os
import re
import sys

COLOR_RE = re.compile(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)")
SCHEME_RE = re.compile(
    r"override val (dark|light)Scheme\s*=\s*(?:dark|light)ColorScheme\((.*?)\n\s*\)",
    re.S,
)


def parse_file(path: str) -> dict:
    src = open(path, encoding="utf-8").read()
    out = {}
    for m in SCHEME_RE.finditer(src):
        variant, body = m.group(1), m.group(2)
        out[variant] = {
            name: "#" + hex_value[2:].upper()
            for name, hex_value in COLOR_RE.findall(body)
        }
    return out


COLORSCHEME_REL = os.path.join(
    "app",
    "src",
    "main",
    "java",
    "eu",
    "kanade",
    "presentation",
    "theme",
    "colorscheme",
)


def find_default_root() -> str | None:
    """Locate reference/komikku/... by walking up from this script.

    The whitepaper lives either at <workspace>/reference/<whitepaper> or at
    <workspace>/docs/<whitepaper>, so the depth differs; search instead of
    hardcoding.
    """
    current = os.path.dirname(os.path.abspath(__file__))
    for _ in range(6):
        candidate = os.path.join(current, "reference", "komikku", COLORSCHEME_REL)
        if os.path.isdir(candidate):
            return candidate
        parent = os.path.dirname(current)
        if parent == current:
            break
        current = parent
    return None


def main() -> int:
    default_root = find_default_root()
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-root", default=default_root)
    args = ap.parse_args()

    if args.source_root is None:
        print(
            "error: cannot find reference/komikku; pass --source-root explicitly",
            file=sys.stderr,
        )
        return 2
    if not os.path.isdir(args.source_root):
        print(f"error: not a directory: {args.source_root}", file=sys.stderr)
        return 2

    result = {}
    for path in sorted(glob.glob(os.path.join(args.source_root, "*.kt"))):
        scheme = parse_file(path)
        if scheme:
            result[os.path.basename(path)] = scheme

    json.dump(result, sys.stdout, indent=1, ensure_ascii=False)
    sys.stdout.write("\n")
    print(f"{len(result)} scheme files parsed", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
