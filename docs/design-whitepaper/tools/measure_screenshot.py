#!/usr/bin/env python3
"""Measure layout boundaries in a Komikku screenshot.

    python3 -I tools/measure_screenshot.py assets/tablet/10_library_grid.jpg
    python3 -I tools/measure_screenshot.py assets/phone/10_library_grid.jpg --density 2.0

Prints, in pixels and in dp:

  * the left navigation rail width (tablet) or bottom bar height (phone)
  * card edges along a horizontal scan line
  * the inferred dp size of the screen

Requires Pillow. The script never writes to disk.
"""

import argparse
import sys

try:
    from PIL import Image
except ImportError:
    print("error: Pillow is required (pip install Pillow)", file=sys.stderr)
    raise SystemExit(2)


def runs(mask, start, stop):
    """Contiguous [start, end] ranges where mask[i] is truthy."""
    out = []
    i = start
    while i < stop:
        if mask[i]:
            begin = i
            while i < stop and mask[i]:
                i += 1
            out.append((begin, i - 1))
        else:
            i += 1
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument(
        "--density",
        type=float,
        default=None,
        help="px per dp; inferred from width when omitted",
    )
    ap.add_argument(
        "--tolerance",
        type=int,
        default=12,
        help="per-channel tolerance for treating a pixel as background",
    )
    args = ap.parse_args()

    im = Image.open(args.image).convert("RGB")
    w, h = im.size
    px = im.load()

    # Background = the most common colour in the top-left 20x20 block.
    samples = [px[x, y] for x in range(0, 20) for y in range(0, 20)]
    bg = max(set(samples), key=samples.count)

    def is_bg(c):
        return all(abs(a - b) <= args.tolerance for a, b in zip(c, bg))

    # Navigation rail: a solid vertical strip whose dominant colour is constant.
    # Icons and labels interrupt it, so compare each column's *modal* colour
    # over the middle half of the height, and start from the far-left column.
    y_from, y_to = h // 4, (h * 3) // 4
    step = max(1, (y_to - y_from) // 60)

    def modal_colour(x):
        samples = [px[x, y] for y in range(y_from, y_to, step)]
        return max(set(samples), key=samples.count)

    def near(c1, c2, tol=6):
        return all(abs(a - b) <= tol for a, b in zip(c1, c2))

    rail_colour = modal_colour(2)
    rail_width = 0
    while rail_width < w and near(modal_colour(rail_width), rail_colour):
        rail_width += 1

    # Only call it a rail if the band is much narrower than the screen and the
    # colour actually differs from the content area behind the grid.
    content_colour = modal_colour(w - 8)
    if rail_width > w // 2 or near(rail_colour, content_colour, tol=3):
        rail_width = 0

    # Bottom bar: walk upward from the last row while each row's modal colour
    # matches the bottom row's modal colour. This survives icon/label pixels.
    def row_modal(y):
        samples = [px[x, y] for x in range(0, w, max(1, w // 80))]
        return max(set(samples), key=samples.count)

    bar_colour = row_modal(h - 1)
    bar_top = h - 1
    while bar_top > 0 and near(row_modal(bar_top - 1), bar_colour, tol=4):
        bar_top -= 1
    bottom_band_start = bar_top if (h - bar_top) > 4 else None

    density = args.density
    if density is None:
        # Prefer the 720dp and 360dp anchors, whichever matches better.
        candidates = [(w / 1280, "1280dp wide"), (w / 360, "360dp wide")]
        density, note = min(candidates, key=lambda c: abs(c[0] - round(c[0], 2)))
        print(f"inferred density: {density:.3f} px/dp ({note})")
    else:
        print(f"density: {density:.3f} px/dp")

    print(f"image: {w}x{h}px -> {w / density:.0f}x{h / density:.0f}dp")

    if rail_width > 4:
        dp = rail_width / density
        if dp <= 96:
            print(
                f"left rail: {rail_width}px = {dp:.1f}dp "
                f"(code: min 80dp, NavigationRail.kt:47)"
            )
        else:
            print(
                f"left panel: {rail_width}px = {dp:.1f}dp "
                f"(TwoPanelBox start pane; formula min(w/2, 450dp), TwoPanelBox.kt:31)"
            )
    else:
        print("no left rail detected (phone layout or full-width content)")
        if bottom_band_start is not None:
            bar = h - bottom_band_start
            print(
                f"bottom bar: {bar}px = {bar / density:.1f}dp "
                f"(code: 80dp content height + system insets, NavigationBar.kt:43)"
            )

    # Card edges along the vertical middle of the content area.
    scan_y = h // 2
    row = [px[x, scan_y] for x in range(w)]
    mask = [not is_bg(c) for c in row]
    bands = [r for r in runs(mask, 0, w) if r[1] - r[0] > 8]
    if bands:
        print(f"content bands at y={scan_y}px (px -> dp):")
        for a, b in bands:
            print(
                f"  {a:4d}..{b:4d}  width {b - a + 1:4d}px "
                f"= {(b - a + 1) / density:6.1f}dp"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
