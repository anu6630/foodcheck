#!/usr/bin/env python3
"""Capture Play Store tablet screenshots on a given adb serial."""

from __future__ import annotations

import argparse
import subprocess
import time
from pathlib import Path

from PIL import Image

PKG = "biz.lifex.foodcheck"


def adb(serial: str, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", "-s", serial, *args],
        check=False,
        capture_output=True,
        text=True,
    )


def wait_boot(serial: str, timeout: int = 120) -> None:
    end = time.time() + timeout
    while time.time() < end:
        out = adb(serial, "shell", "getprop", "sys.boot_completed").stdout.strip()
        if out == "1":
            return
        time.sleep(2)
    raise RuntimeError(f"boot timeout: {serial}")


def launch(serial: str) -> None:
    adb(serial, "shell", "am", "force-stop", PKG)
    time.sleep(0.4)
    adb(
        serial,
        "shell",
        "monkey",
        "-p",
        PKG,
        "-c",
        "android.intent.category.LAUNCHER",
        "1",
    )
    time.sleep(3.5)


def size(serial: str) -> tuple[int, int]:
    out = adb(serial, "shell", "wm", "size").stdout
    for line in out.splitlines():
        if "Override size:" in line or "Physical size:" in line:
            wh = line.split(":")[-1].strip()
            w, h = map(int, wh.split("x"))
            if "Override" in line:
                return w, h
            phys = (w, h)
    # prefer last physical if no override
    return phys  # type: ignore[name-defined]


def shot(serial: str, outdir: Path, name: str) -> Path:
    remote = f"/sdcard/{name}.png"
    local = outdir / f"{name}.png"
    adb(serial, "shell", "input", "keyevent", "111")
    time.sleep(0.15)
    adb(serial, "shell", "screencap", "-p", remote)
    adb(serial, "pull", remote, str(local))
    im = Image.open(local)
    print(f"  saved {local.name} {im.size[0]}x{im.size[1]}")
    return local


def tap(serial: str, x: float, y: float) -> None:
    adb(serial, "shell", "input", "tap", str(int(x)), str(int(y)))


def type_text(serial: str, text: str) -> None:
    adb(serial, "shell", "input", "text", text.replace(" ", "%s"))


def hide_kb(serial: str) -> None:
    adb(serial, "shell", "input", "keyevent", "4")
    time.sleep(0.5)


def capture(serial: str, outdir: Path) -> None:
    outdir.mkdir(parents=True, exist_ok=True)
    wait_boot(serial)
    w, h = size(serial)
    print(f"capturing {serial} {w}x{h} -> {outdir}")

    # Install if missing
    path = adb(serial, "shell", "pm", "path", PKG).stdout
    if PKG not in path:
        apk = Path(
            "/Volumes/SSDX 1/codes/foodcheck/mobile/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
        )
        print("installing", apk)
        print(adb(serial, "install", "-r", str(apk)).stdout or adb(serial, "install", "-r", str(apk)).stderr)

    adb(serial, "shell", "ime", "disable", "com.github.uiautomator/.AdbKeyboard")

    launch(serial)
    shot(serial, outdir, "01-search-home")

    tap(serial, w * 0.50, h * 0.30)
    time.sleep(0.5)
    type_text(serial, "Ajinomoto")
    time.sleep(3.8)
    hide_kb(serial)
    shot(serial, outdir, "02-search-ajinomoto")
    tap(serial, w * 0.50, h * 0.38)
    time.sleep(2.6)
    shot(serial, outdir, "03-ingredient-msg")
    adb(serial, "shell", "input", "keyevent", "4")
    time.sleep(0.7)

    # Bournvita
    tap(serial, w * 0.85, h * 0.21)
    time.sleep(0.4)
    tap(serial, w * 0.50, h * 0.21)
    time.sleep(0.35)
    type_text(serial, "Bournvita")
    time.sleep(3.8)
    hide_kb(serial)
    shot(serial, outdir, "04-search-bournvita")
    tap(serial, w * 0.50, h * 0.40)
    time.sleep(2.6)
    shot(serial, outdir, "05-product-bournvita")

    # Aspartame
    launch(serial)
    tap(serial, w * 0.50, h * 0.30)
    time.sleep(0.45)
    type_text(serial, "Aspartame")
    time.sleep(3.8)
    hide_kb(serial)
    shot(serial, outdir, "06-search-aspartame")
    tap(serial, w * 0.50, h * 0.38)
    time.sleep(2.6)
    shot(serial, outdir, "07-ingredient-aspartame")

    # Scan / My list
    launch(serial)
    tap(serial, w * 0.50, h * 0.90)
    time.sleep(1.4)
    shot(serial, outdir, "08-scan-sheet")
    adb(serial, "shell", "input", "keyevent", "4")
    time.sleep(0.7)
    tap(serial, w * 0.78, h * 0.90)
    time.sleep(1.8)
    shot(serial, outdir, "09-my-list")

    # potassium bromate
    launch(serial)
    tap(serial, w * 0.50, h * 0.30)
    time.sleep(0.4)
    type_text(serial, "potassium%sbromate")
    time.sleep(3.8)
    hide_kb(serial)
    shot(serial, outdir, "10-search-potassium-bromate")
    tap(serial, w * 0.50, h * 0.38)
    time.sleep(2.6)
    shot(serial, outdir, "11-ingredient-potassium-bromate")
    print("done", outdir)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--outdir", required=True)
    args = ap.parse_args()
    capture(args.serial, Path(args.outdir).expanduser())


if __name__ == "__main__":
    main()
