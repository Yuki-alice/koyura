#!/usr/bin/env python3
"""Render appendix A (all 19 themes) from assets/design-tokens.json.

    python3 -I tools/gen_appendix.py > /tmp/appendixA.md

The output is pure markdown tables. Copy it into 10-附录.md under section A,
or keep it as a separate generated file.
"""

import json
import os
import sys


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tokens = json.load(open(os.path.join(root, "assets", "design-tokens.json"), encoding="utf-8"))

    out = []
    out.append("<!-- 本节由 tools/gen_appendix.py 从 assets/design-tokens.json 生成，请勿手改 -->\n")
    for scheme in tokens["color"]["schemes"]:
        out.append(f"### {scheme['label']}（`{scheme['enum']}`）\n")
        out.append(
            f"源文件 `{scheme['sourceFile']}` · 显式定义 "
            f"**{scheme['definedRoles']['dark']}/48**（dark）· "
            f"**{scheme['definedRoles']['light']}/48**（light）\n"
        )
        roles = list(dict.fromkeys(list(scheme["dark"].keys()) + list(scheme["light"].keys())))
        out.append("| 角色 | dark | light |")
        out.append("|---|---|---|")
        for role in roles:
            dark = scheme["dark"].get(role, "—")
            light = scheme["light"].get(role, "—")
            out.append(f"| `{role}` | `{dark}` | `{light}` |")

        missing = scheme["missingRoles"]["dark"]
        if missing:
            out.append(
                f"\n> 未定义（{len(missing)}）：" + ", ".join(f"`{m}`" for m in missing)
            )
        out.append("")

    sys.stdout.write("\n".join(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
