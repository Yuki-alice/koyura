#!/usr/bin/env python3
"""Compare freshly parsed ColorScheme values with assets/design-tokens.json.

    python3 -I tools/parse_themes.py > /tmp/themes.json
    python3 -I tools/compare_tokens.py /tmp/themes.json assets/design-tokens.json

Exits 0 when every colour in the JSON matches the source, 1 otherwise.
Missing roles are reported separately: a theme that does not define a role in
Kotlin will not have it in the JSON either, and that is expected.
"""

import argparse
import json
import sys

# Kotlin file basename -> (JSON scheme id, human name)
FILES = {
    "TachiyomiColorScheme.kt": ("Tachiyomi", "Default"),
    "CatppuccinColorScheme.kt": ("Catppuccin", "Catppuccin"),
    "GreenAppleColorScheme.kt": ("GreenApple", "Green Apple"),
    "LavenderColorScheme.kt": ("Lavender", "Lavender"),
    "MidnightDuskColorScheme.kt": ("MidnightDusk", "Midnight Dusk"),
    "MonochromeColorScheme.kt": ("Monochrome", "Monochrome"),
    "NordColorScheme.kt": ("Nord", "Nord"),
    "StrawberryColorScheme.kt": ("Strawberry", "Strawberry Daiquiri"),
    "TakoColorScheme.kt": ("Tako", "Tako"),
    "TealTurqoiseColorScheme.kt": ("TealTurqoise", "Teal & Turquoise"),
    "TidalWaveColorScheme.kt": ("TidalWave", "Tidal Wave"),
    "YinYangColorScheme.kt": ("YinYang", "Yin & Yang"),
    "YotsubaColorScheme.kt": ("Yotsuba", "Yotsuba"),
    "CloudflareColorScheme.kt": ("Cloudflare", "Cloudflare"),
    "CottoncandyColorScheme.kt": ("Cottoncandy", "Cotton Candy"),
    "DoomColorScheme.kt": ("Doom", "Doom"),
    "MatrixColorScheme.kt": ("Matrix", "Matrix"),
    "MochaColorScheme.kt": ("Mocha", "Mocha"),
    "SapphireColorScheme.kt": ("Sapphire", "Sapphire"),
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("parsed")
    ap.add_argument("tokens")
    args = ap.parse_args()

    parsed = json.load(open(args.parsed, encoding="utf-8"))
    tokens = json.load(open(args.tokens, encoding="utf-8"))

    by_id = {s["id"]: s for s in tokens["color"]["schemes"]}

    diffs = []
    missing_schemes = []
    for filename, (scheme_id, label) in FILES.items():
        if filename not in parsed:
            missing_schemes.append(filename)
            continue
        if scheme_id not in by_id:
            missing_schemes.append(scheme_id)
            continue
        for variant in ("dark", "light"):
            src = parsed[filename].get(variant, {})
            doc = by_id[scheme_id].get(variant, {})
            for role, value in src.items():
                if role not in doc:
                    diffs.append(f"{label}/{variant}/{role}: missing in JSON")
                elif doc[role].upper() != value.upper():
                    diffs.append(
                        f"{label}/{variant}/{role}: json={doc[role]} source={value}"
                    )
            for role in doc:
                if role not in src:
                    diffs.append(f"{label}/{variant}/{role}: extra in JSON")

    for item in diffs:
        print(item)
    if missing_schemes:
        print("missing schemes:", ", ".join(missing_schemes))

    print(f"\n{len(diffs)} differences, {len(missing_schemes)} missing schemes")
    return 0 if not diffs and not missing_schemes else 1


if __name__ == "__main__":
    raise SystemExit(main())
