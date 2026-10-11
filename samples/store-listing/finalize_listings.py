#!/usr/bin/env python3
"""Merge cache + remaining locales and emit Play Console import files."""

from __future__ import annotations

import csv
import json
import shutil
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font

ROOT = Path(__file__).resolve().parent
DL = Path.home() / "Downloads"

TITLE = "LifeX: Food Ingredients Check"
SHORT = (
    "Scan food labels to spot banned & restricted ingredients—know what’s in a pack."
)
FULL = """LifeX Food helps you understand what’s in packaged food—before you buy or serve it.

Snap a photo of the ingredients label. We read the pack, match substances, and show regulation signals for your market (starting with India)—including banned and restricted additives—not vague “health scores.”

What you can do
• Scan ingredient labels with your camera or photo library
• See clear substance tags with watch signals and regulation context
• Search products and ingredients in the catalog
• Save packs to My list on this device with your stance (Avoid, Occasional, OK, Unsure)
• Optionally tag items for Kids, Adults, Elderly, or Home

Important
LifeX Food is an information tool. It does not provide medical advice. Always follow local regulations and your own judgment for dietary needs.

Know what’s banned, anywhere."""

ORDER = [
    "en-US",
    "hi-IN",
    "bn-BD",
    "te-IN",
    "mr-IN",
    "ta-IN",
    "gu-IN",
    "kn-IN",
    "ml-IN",
    "pa-IN",
    "or-IN",
    "as-IN",
    "ur",
    "de-DE",
    "fr-FR",
    "es-ES",
    "es-419",
    "pt-BR",
    "it-IT",
    "nl-NL",
    "pl-PL",
    "ru-RU",
    "tr-TR",
    "ar",
    "zh-CN",
    "zh-TW",
    "ja-JP",
    "ko-KR",
    "th",
    "vi",
    "id",
    "ms-MY",
]

# Malayalam full description via unicode_escape (ASCII-only source file).
# Titles/short descriptions for all locales live in listing-overrides.json.
ML_FULL = "\n".join(
    [
        "LifeX Food "
        "\u0d2a\u0d3e\u0d2f\u0d4d\u0d15\u0d4d\u0d15\u0d4d \u0d1a\u0d46\u0d2f\u0d4d\u0d24\u0d4d "
        "\u0d2d\u0d15\u0d4d\u0d37\u0d23\u0d24\u0d4d\u0d24\u0d3f\u0d32\u0d4d "
        "\u0d0e\u0d28\u0d4d\u0d24\u0d3e\u0d23\u0d46\u0d28\u0d4d\u0d28\u0d4d "
        "\u0d2e\u0d28\u0d38\u0d4d\u0d38\u0d3f\u0d32\u0d3e\u0d15\u0d4d\u0d15\u0d3e\u0d7b "
        "\u0d38\u0d39\u0d3e\u0d2f\u0d3f\u0d15\u0d4d\u0d15\u0d41\u0d28\u0d4d\u0d28\u0d41"
        "\u2014\u0d35\u0d3e\u0d19\u0d4d\u0d19\u0d41\u0d28\u0d4d\u0d28\u0d24\u0d3f\u0d28\u0d4b "
        "\u0d35\u0d3f\u0d33\u0d2e\u0d4d\u0d2a\u0d41\u0d28\u0d4d\u0d28\u0d24\u0d3f\u0d28\u0d4b "
        "\u0d2e\u0d41\u0d2e\u0d4d\u0d2a\u0d4d.",
        "",
        "\u0d18\u0d1f\u0d15 \u0d32\u0d47\u0d2c\u0d32\u0d3f\u0d28\u0d4d\u0d31\u0d46 "
        "\u0d2b\u0d4b\u0d1f\u0d4d\u0d1f\u0d4b \u0d0e\u0d1f\u0d41\u0d15\u0d4d\u0d15\u0d41\u0d15. "
        "\u0d1e\u0d19\u0d4d\u0d19\u0d7e \u0d2a\u0d3e\u0d2f\u0d4d\u0d15\u0d4d\u0d15\u0d4d "
        "\u0d35\u0d3e\u0d2f\u0d3f\u0d15\u0d4d\u0d15\u0d41\u0d15\u0d2f\u0d41\u0d02 "
        "\u0d2a\u0d26\u0d3e\u0d30\u0d4d\u0d24\u0d4d\u0d25\u0d19\u0d4d\u0d19\u0d7e "
        "\u0d2a\u0d4a\u0d30\u0d41\u0d24\u0d4d\u0d24\u0d2a\u0d4d\u0d2a\u0d46\u0d1f\u0d41\u0d24\u0d4d\u0d24\u0d41\u0d15\u0d2f\u0d41\u0d02 "
        "\u0d28\u0d3f\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d1f\u0d46 "
        "\u0d35\u0d3f\u0d2a\u0d23\u0d3f\u0d15\u0d4d\u0d15\u0d41\u0d33\u0d4d\u0d33 "
        "(\u0d07\u0d28\u0d4d\u0d24\u0d4d\u0d2f\u0d2f\u0d3f\u0d32\u0d4d "
        "\u0d28\u0d3f\u0d28\u0d4d\u0d28\u0d4d \u0d06\u0d30\u0d02\u0d2d\u0d3f\u0d1a\u0d4d\u0d1a\u0d4d) "
        "\u0d28\u0d3f\u0d2f\u0d28\u0d4d\u0d24\u0d4d\u0d30\u0d23 "
        "\u0d38\u0d42\u0d1a\u0d28\u0d15\u0d7e "
        "\u0d15\u0d3e\u0d23\u0d3f\u0d15\u0d4d\u0d15\u0d41\u0d15\u0d2f\u0d41\u0d02 "
        "\u0d1a\u0d46\u0d2f\u0d4d\u0d2f\u0d41\u0d28\u0d4d\u0d28\u0d41"
        "\u2014\u0d28\u0d3f\u0d30\u0d4b\u0d27\u0d3f\u0d24\u0d35\u0d41\u0d02 "
        "\u0d28\u0d3f\u0d2f\u0d28\u0d4d\u0d24\u0d4d\u0d30\u0d3f\u0d24\u0d35\u0d41\u0d2e\u0d3e\u0d2f "
        "\u0d05\u0d21\u0d3f\u0d31\u0d4d\u0d1f\u0d40\u0d35\u0d41\u0d15\u0d7e "
        "\u0d09\u0d7e\u0d2a\u0d4d\u0d2a\u0d46\u0d1f\u0d46"
        "\u2014\u0d05\u0d35\u0d4d\u0d2f\u0d15\u0d4d\u0d24\u0d2e\u0d3e\u0d2f "
        "\u201c\u0d06\u0d30\u0d4b\u0d17\u0d4d\u0d2f "
        "\u0d38\u0d4d\u0d15\u0d4b\u0d31\u0d41\u0d15\u0d7e\u201d \u0d05\u0d32\u0d4d\u0d32.",
        "",
        "\u0d28\u0d3f\u0d19\u0d4d\u0d19\u0d7e\u0d15\u0d4d\u0d15\u0d4d "
        "\u0d1a\u0d46\u0d2f\u0d4d\u0d2f\u0d3e\u0d35\u0d41\u0d28\u0d4d\u0d28\u0d24\u0d4d",
        "\u2022 \u0d15\u0d4d\u0d2f\u0d3e\u0d2e\u0d31\u0d2f\u0d4b "
        "\u0d2b\u0d4b\u0d1f\u0d4d\u0d1f\u0d4b \u0d32\u0d48\u0d2c\u0d4d\u0d30\u0d31\u0d3f\u0d2f\u0d4b "
        "\u0d09\u0d2a\u0d2f\u0d4b\u0d17\u0d3f\u0d1a\u0d4d\u0d1a\u0d4d "
        "\u0d18\u0d1f\u0d15 \u0d32\u0d47\u0d2c\u0d32\u0d41\u0d15\u0d7e "
        "\u0d38\u0d4d\u0d15\u0d3e\u0d7b \u0d1a\u0d46\u0d2f\u0d4d\u0d2f\u0d41\u0d15",
        "\u2022 \u0d1c\u0d3e\u0d17\u0d4d\u0d30\u0d24\u0d3e "
        "\u0d38\u0d42\u0d1a\u0d28\u0d15\u0d33\u0d41\u0d02 "
        "\u0d28\u0d3f\u0d2f\u0d28\u0d4d\u0d24\u0d4d\u0d30\u0d23 "
        "\u0d2a\u0d36\u0d4d\u0d1a\u0d3e\u0d24\u0d4d\u0d24\u0d32\u0d35\u0d41\u0d2e\u0d41\u0d33\u0d4d\u0d33 "
        "\u0d35\u0d4d\u0d2f\u0d15\u0d4d\u0d24\u0d2e\u0d3e\u0d2f "
        "\u0d2a\u0d26\u0d3e\u0d30\u0d4d\u0d24\u0d4d\u0d25 "
        "\u0d1f\u0d3e\u0d17\u0d41\u0d15\u0d7e \u0d15\u0d3e\u0d23\u0d41\u0d15",
        "\u2022 \u0d15\u0d3e\u0d31\u0d4d\u0d31\u0d32\u0d4b\u0d17\u0d3f\u0d32\u0d4d "
        "\u0d09\u0d7d\u0d2a\u0d4d\u0d2a\u0d28\u0d4d\u0d28\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d02 "
        "\u0d18\u0d1f\u0d15\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d02 "
        "\u0d24\u0d3f\u0d30\u0d2f\u0d41\u0d15",
        "\u2022 \u0d08 \u0d09\u0d2a\u0d15\u0d30\u0d23\u0d24\u0d4d\u0d24\u0d3f\u0d32\u0d46 "
        "\u0d0e\u0d28\u0d4d\u0d31\u0d46 \u0d32\u0d3f\u0d38\u0d4d\u0d31\u0d4d\u0d31\u0d3f\u0d32\u0d47\u0d15\u0d4d\u0d15\u0d4d "
        "\u0d28\u0d3f\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d1f\u0d46 "
        "\u0d28\u0d3f\u0d32\u0d2a\u0d3e\u0d1f\u0d4b\u0d1f\u0d46 "
        "(\u0d12\u0d34\u0d3f\u0d35\u0d3e\u0d15\u0d4d\u0d15\u0d41\u0d15, "
        "\u0d07\u0d1f\u0d2f\u0d4d\u0d15\u0d4d\u0d15\u0d3f\u0d1f\u0d46, "
        "\u0d36\u0d30\u0d3f, \u0d09\u0d31\u0d2a\u0d4d\u0d2a\u0d3f\u0d32\u0d4d\u0d32) "
        "\u0d2a\u0d3e\u0d2f\u0d4d\u0d15\u0d4d\u0d15\u0d41\u0d15\u0d7e "
        "\u0d38\u0d47\u0d35\u0d4d \u0d1a\u0d46\u0d2f\u0d4d\u0d2f\u0d41\u0d15",
        "\u2022 \u0d13\u0d2a\u0d4d\u0d37\u0d23\u0d32\u0d3e\u0d2f\u0d3f "
        "\u0d15\u0d41\u0d1f\u0d4d\u0d1f\u0d3f\u0d15\u0d7e, "
        "\u0d2e\u0d41\u0d24\u0d3f\u0d30\u0d4d\u0d28\u0d4d\u0d28\u0d35\u0d30\u0d4d, "
        "\u0d2a\u0d4d\u0d30\u0d3e\u0d2f\u0d2e\u0d3e\u0d2f\u0d35\u0d30\u0d4d "
        "\u0d05\u0d32\u0d4d\u0d32\u0d46\u0d19\u0d4d\u0d15\u0d3f\u0d7d "
        "\u0d35\u0d40\u0d1f\u0d4d "
        "\u0d0e\u0d28\u0d4d\u0d28\u0d3f\u0d35\u0d2f\u0d4d\u0d15\u0d4d\u0d15\u0d3e\u0d2f\u0d3f "
        "\u0d1f\u0d3e\u0d17\u0d4d \u0d1a\u0d46\u0d2f\u0d4d\u0d2f\u0d41\u0d15",
        "",
        "\u0d2a\u0d4d\u0d30\u0d27\u0d3e\u0d28\u0d02",
        "LifeX Food \u0d12\u0d30\u0d41 "
        "\u0d35\u0d3f\u0d35\u0d30 "
        "\u0d09\u0d2a\u0d15\u0d30\u0d23\u0d2e\u0d3e\u0d23\u0d4d. "
        "\u0d07\u0d24\u0d4d "
        "\u0d35\u0d48\u0d26\u0d4d\u0d2f\u0d4b\u0d2a\u0d26\u0d47\u0d36\u0d02 "
        "\u0d28\u0d7d\u0d15\u0d41\u0d28\u0d4d\u0d28\u0d3f\u0d32\u0d4d\u0d32. "
        "\u0d2d\u0d15\u0d4d\u0d37\u0d23 "
        "\u0d06\u0d35\u0d36\u0d4d\u0d2f\u0d19\u0d4d\u0d19\u0d7e\u0d15\u0d4d\u0d15\u0d4d "
        "\u0d0e\u0d32\u0d4d\u0d32\u0d3e\u0d2f\u0d4d\u0d2a\u0d4d\u0d2a\u0d4b\u0d34\u0d41\u0d02 "
        "\u0d2a\u0d4d\u0d30\u0d3e\u0d26\u0d47\u0d36\u0d3f\u0d15 "
        "\u0d28\u0d3f\u0d2f\u0d2e\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d02 "
        "\u0d28\u0d3f\u0d19\u0d4d\u0d19\u0d33\u0d41\u0d1f\u0d46 "
        "\u0d38\u0d4d\u0d35\u0d28\u0d4d\u0d24\u0d02 "
        "\u0d35\u0d3f\u0d35\u0d47\u0d1a\u0d28\u0d35\u0d41\u0d02 "
        "\u0d2a\u0d3f\u0d28\u0d4d\u0d24\u0d41\u0d1f\u0d41\u0d15\u0d41\u0d15.",
        "",
        "\u0d0e\u0d35\u0d3f\u0d1f\u0d46\u0d2f\u0d41\u0d02 "
        "\u0d0e\u0d28\u0d4d\u0d24\u0d3e\u0d23\u0d4d "
        "\u0d28\u0d3f\u0d30\u0d4b\u0d27\u0d3f\u0d1a\u0d4d\u0d1a\u0d3f\u0d30\u0d3f\u0d15\u0d4d\u0d15\u0d41\u0d28\u0d4d\u0d28\u0d24\u0d46\u0d28\u0d4d\u0d28\u0d4d "
        "\u0d05\u0d31\u0d3f\u0d2f\u0d41\u0d15.",
    ]
)


LIMITS = {"title": 30, "short_description": 80, "full_description": 4000}


def trim(s: str, n: int) -> str:
    s = s.strip()
    if len(s) <= n:
        return s
    cut = s[: n - 1].rsplit(" ", 1)[0]
    return (cut if cut else s[: n - 1]) + "\u2026"


def main() -> None:
    cache_path = ROOT / "translations-cache.json"
    rem_path = ROOT / "remaining-translations.json"
    cache: dict = json.loads(cache_path.read_text(encoding="utf-8"))
    rem: dict = json.loads(rem_path.read_text(encoding="utf-8"))

    # Overwrite corrupted Malayalam
    rem.setdefault("ml-IN", {})["full_description"] = ML_FULL
    rem_path.write_text(
        json.dumps(rem, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    cache.update(rem)

    # Locale policy: India -> English-keyword title + native copy; elsewhere native
    # titles using locally searched terms. Also replaces model output that only
    # fit the limits by being cut mid-sentence.
    overrides = json.loads((ROOT / "listing-overrides.json").read_text(encoding="utf-8"))
    for code, fields in overrides["locales"].items():
        for key, text in fields.items():
            limit = LIMITS[key]
            if len(text) > limit:
                raise SystemExit(f"override {code}.{key} is {len(text)} > {limit}")
            cache[code][key] = text
    lead = overrides["india_full_lead"]
    for code in overrides["india_locales"]:
        full = cache[code]["full_description"].strip()
        if not full.startswith(lead):
            cache[code]["full_description"] = f"{lead}\n\n{full}"

    cache_path.write_text(
        json.dumps(cache, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    rows: list[tuple[str, str, str, str]] = [("en-US", TITLE, SHORT, FULL)]
    issues: list[str] = []
    for code in ORDER[1:]:
        if code not in cache:
            issues.append(f"missing {code}")
            continue
        t = trim(cache[code]["title"], 30)
        s = trim(cache[code]["short_description"], 80)
        for key, text in (("title", t), ("short_description", s)):
            if text != cache[code][key].strip():
                issues.append(f"{code} {key} truncated; add an override")
        f = cache[code]["full_description"].strip()
        if "\ufffd" in (t + s + f):
            issues.append(f"replacement char in {code}")
        if len(f) > 4000:
            f = f[:3999] + "\u2026"
        rows.append((code, t, s, f))

    src = ROOT / "play-store-listing-source-for-ai.csv"
    with src.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_ALL)
        w.writerow(["key", "text"])
        w.writerow(["title", TITLE])
        w.writerow(["short_description", SHORT])
        w.writerow(["full_description", FULL])

    csv_path = ROOT / "play-store-listing-translations.csv"
    with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_ALL)
        w.writerow(["language", "title", "short_description", "full_description"])
        w.writerows(rows)

    wb = Workbook()
    ws = wb.active
    ws.title = "Store listings"
    ws.append(
        [
            "language",
            "title",
            "short_description",
            "full_description",
            "title_len",
            "short_len",
        ]
    )
    for c in ws[1]:
        c.font = Font(name="Arial", bold=True, size=11)
    for lang, t, s, f in rows:
        ws.append([lang, t, s, f, len(t), len(s)])
        for cell in ws[ws.max_row]:
            cell.font = Font(name="Arial", size=10)
            cell.alignment = Alignment(wrap_text=True, vertical="top")
    ws.column_dimensions["A"].width = 12
    ws.column_dimensions["B"].width = 32
    ws.column_dimensions["C"].width = 52
    ws.column_dimensions["D"].width = 80
    xlsx = ROOT / "play-store-listing-translations.xlsx"
    wb.save(xlsx)

    (ROOT / "README.md").write_text(
        f"""# Play Store listing translations

## Files (also in ~/Downloads)
- `play-store-listing-translations.csv` / `.xlsx` — **{len(rows)}** locales
- `play-store-listing-source-for-ai.csv` — English source for Play **Import translations with AI**

## How to use
1. Play Console → Grow → Store presence → Main store listing
2. **Import translations with AI** → **Import a file**
3. Upload the CSV or XLSX
4. Spot-check titles (≤30) and short descriptions (≤80)

## Locales
Indian: Hindi, Bengali, Telugu, Marathi, Tamil, Gujarati, Kannada, Malayalam, Punjabi, Odia, Assamese, Urdu  
Also: German, French, Spanish (ES + LatAm), Portuguese (Brazil), Italian, Dutch, Polish, Russian, Turkish, Arabic, Simplified/Traditional Chinese, Japanese, Korean, Thai, Vietnamese, Indonesian, Malay

## Title / keyword policy (edit `listing-overrides.json`, then rerun this script)
- **Indian locales + Urdu + Bengali**: `LifeX Food Scanner: <native ingredients word>`. Keeps the English
  search terms (food, scanner) in Latin script and adds the word printed on ingredient panels. Short and full
  descriptions stay native; the full description starts with one English sentence.
- **JP / CN / TW / KR / TH / VI / AR / RU**: native-script title (people search in their own script).
- **European / LatAm / ID / MS**: local-language title using the term people search for
  (e.g. *scanner alimentaire*, *escáner de alimentos*, *Zutaten-Scanner*).
- Overrides must fit the limits (title ≤30, short ≤80) or the script stops. If machine copy gets cut, it is
  listed under `issues`.
""",
        encoding="utf-8",
    )

    for p in [csv_path, xlsx, src, ROOT / "README.md"]:
        shutil.copy2(p, DL / p.name)
        print("copied", DL / p.name)

    print("rows", len(rows))
    print("issues", issues or "none")
    for lang, t, s, f in rows:
        print(f"{lang:8} t={len(t):2} s={len(s):2} | {t}")


if __name__ == "__main__":
    main()
