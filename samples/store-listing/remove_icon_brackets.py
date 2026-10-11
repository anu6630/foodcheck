#!/usr/bin/env python3
"""Remove viewfinder corner brackets + alert shield from LifeX Food logo art."""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
SAMPLES = ROOT / "samples"


def remove_brackets(src: Path, dst: Path) -> Image.Image:
    im = Image.open(src).convert("RGBA")
    a = np.array(im)
    h, w = a.shape[:2]
    r = a[:, :, 0].astype(int)
    g = a[:, :, 1].astype(int)
    b = a[:, :, 2].astype(int)

    neon = (
        (g > 60)
        & (b > 40)
        & (g + b > r + 40)
        & (g >= r - 10)
        & ((r + g + b) > 90)
    )
    labels, n = ndimage.label(neon)
    remove = np.zeros((h, w), dtype=bool)

    for lab in range(1, n + 1):
        mask = labels == lab
        ys, xs = np.where(mask)
        if len(ys) == 0:
            continue
        size = int(mask.sum())
        cy, cx = ys.mean() / h, xs.mean() / w
        if cy > 0.78:
            continue
        if size > int(0.008 * h * w):
            continue
        if size > 800 and 0.22 < cx < 0.58 and cy < 0.32:
            continue
        if size < 3000 and 0.18 < cx < 0.42 and 0.26 < cy < 0.48:
            continue
        near_corner = (
            (cx < 0.32 and cy < 0.32)
            or (cx > 0.68 and cy < 0.32)
            or (cx < 0.32 and 0.52 < cy < 0.80)
            or (cx > 0.68 and 0.52 < cy < 0.80)
        )
        if near_corner or (cx > 0.70 and 0.52 < cy < 0.80 and size < 8000):
            remove |= mask

    warm = (r > 140) & (r > g + 35) & (r > b + 35)
    labels_r, nr = ndimage.label(warm)
    for lab in range(1, nr + 1):
        mask = labels_r == lab
        ys, xs = np.where(mask)
        if len(ys) == 0:
            continue
        size = int(mask.sum())
        cy, cx = ys.mean() / h, xs.mean() / w
        if cx > 0.65 and 0.50 < cy < 0.82 and size < 20000:
            remove |= mask
        if size < 200 and cx > 0.55 and 0.50 < cy < 0.75:
            remove |= mask

    remove = ndimage.binary_dilation(remove, iterations=3)
    dark = (r + g + b) < 120
    bg_samples = a[dark & ~neon]
    bg = (
        np.median(bg_samples, axis=0).astype(np.uint8)
        if len(bg_samples) > 100
        else np.array([7, 17, 37, 255], dtype=np.uint8)
    )
    out = a.copy()
    out[remove] = bg
    Image.fromarray(out).save(dst, optimize=True)
    print(f"{src.name}: removed {int(remove.sum())} -> {dst}")
    return Image.fromarray(out)


def main() -> None:
    out_dir = SAMPLES / "icon-no-brackets"
    out_dir.mkdir(exist_ok=True)
    backup = SAMPLES / "icon-with-brackets-backup"
    backup.mkdir(exist_ok=True)

    src = SAMPLES / "lifex-food-app-icon-1024.png"
    # Prefer untouched backup if present
    backed = backup / "lifex-food-app-icon-1024.png"
    if backed.exists():
        src = backed
    elif not (backup / src.name).exists() and src.exists():
        Image.open(src).save(backup / src.name)

    cleaned = remove_brackets(src, out_dir / "lifex-food-app-icon-1024-no-brackets.png")
    cleaned.save(SAMPLES / "lifex-food-app-icon-1024.png")
    cleaned.resize((512, 512), Image.Resampling.LANCZOS).save(
        SAMPLES / "lifex-food-app-icon-512.png"
    )
    cleaned.save(SAMPLES / "app-icon-source.png")
    cleaned.convert("RGB").save(SAMPLES / "lifex-food-app-icon.jpg", quality=95)
    print("Updated sample masters.")


if __name__ == "__main__":
    main()
