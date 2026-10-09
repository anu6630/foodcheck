#!/usr/bin/env python3
"""Parse FSSAI Appendix A PDF into structured JSON for ingestFssai.ts."""
from __future__ import annotations

import json
import re
from pathlib import Path

import pdfplumber

ROOT = Path(__file__).resolve().parent.parent
PDF = ROOT / "data" / "fssai" / "appendix-a-current.pdf"
OUT = ROOT / "data" / "fssai" / "permissions-in.json"

CAT_RE = re.compile(r"^\d+(?:\.\d+){1,4}$")
INS_RE = re.compile(r"^\d{3,4}(?:[a-z]{1,2}|\([ivx]+\))?$", re.I)
LIMIT_UNIT = re.compile(r"^(mg/kg|mg/l|ppm|g/kg)$", re.I)
FOOTER = re.compile(r"versi\s*on|appendix a|table \d+\s*$", re.I)
HEADER_JOIN = re.compile(
    r"food additive|ins no|recommen|maximum level|categor y system|food category",
    re.I,
)

# Codex / FSSAI group names (ALL CAPS in tables) → member INS numbers.
GROUP_INS: dict[str, list[str]] = {
    "PHOSPHATES": [
        "338", "339(i)", "339(ii)", "339(iii)", "340(i)", "340(ii)", "340(iii)",
        "341(i)", "341(ii)", "341(iii)", "342(i)", "342(ii)", "343(i)", "343(ii)",
        "343(iii)", "450(i)", "450(ii)", "450(iii)", "450(v)", "450(vi)", "450(vii)",
        "450(ix)", "451(i)", "451(ii)", "452(i)", "452(ii)", "452(iii)", "452(iv)",
        "452(v)", "542",
    ],
    "RIBOFLAVINS": ["101(i)", "101(ii)", "101(iii)"],
    "ASCORBYL ESTERS": ["304", "305"],
    "CAROTENOIDS": ["160a(i)", "160a(iii)", "160e", "160f"],
    "POLYSORBATES": ["432", "433", "434", "435", "436"],
    "TOCOPHEROLS": ["306", "307", "307a", "307b", "307c"],
    "SODIUM ALUMINIUM PHOSPHATES": ["541(i)", "541(ii)"],
    "SORBATES": ["200", "201", "202", "203"],
    "BENZOATES": ["210", "211", "212", "213"],
    "SULFITES": ["220", "221", "222", "223", "224", "225", "226", "227", "228", "539"],
    "NITRITES": ["249", "250"],
    "NITRATES": ["251", "252"],
    "PARABENS": ["214", "218"],
    "STEARYL CITRATE": ["484"],
    "TANNINS": ["181"],
}


def cluster_rows(words: list[dict], ytol: float = 6.0) -> list[list[dict]]:
    words = sorted(words, key=lambda w: (w["top"], w["x0"]))
    rows: list[list[dict]] = []
    for w in words:
        if rows and abs(w["top"] - rows[-1][0]["top"]) <= ytol:
            rows[-1].append(w)
        else:
            rows.append([w])
    return rows


def parse_row(words: list[dict]) -> dict | None:
    items = [(w["x0"], w["text"].strip()) for w in sorted(words, key=lambda w: w["x0"])]
    items = [(x, t) for x, t in items if t]
    if not items:
        return None
    joined = " ".join(t for _, t in items)
    if HEADER_JOIN.search(joined) or FOOTER.search(joined):
        return None
    if joined.lower().startswith("table ") and len(items) <= 4:
        return {"_table": joined}

    limit = None
    limit_i = None
    for i, (x, t) in enumerate(items):
        if t.upper() == "GMP":
            limit, limit_i = "GMP", i
            break
        if LIMIT_UNIT.match(t) and i > 0 and re.match(r"^[\d,]+$", items[i - 1][1]):
            limit, limit_i = f"{items[i - 1][1]} {t}", i - 1
            break
        m = re.match(r"^([\d,]+)\s*(mg/kg|mg/l|ppm)$", t, re.I)
        if m:
            limit, limit_i = f"{m.group(1)} {m.group(2)}", i
            break

    ins = None
    ins_i = None
    search_upto = limit_i if limit_i is not None else len(items)
    for i in range(search_upto - 1, -1, -1):
        x, t = items[i]
        if "." in t:
            continue
        if INS_RE.match(t) and x >= 200:
            ins, ins_i = t, i
            break

    left = items[: ins_i if ins_i is not None else (limit_i if limit_i is not None else 0)]
    cat = None
    cat_name_parts: list[str] = []
    additive_parts: list[str] = []
    for x, t in left:
        if cat is None and CAT_RE.match(t) and x < 140:
            cat = t
            continue
        if x < 215:
            cat_name_parts.append(t)
        else:
            additive_parts.append(t)

    notes = []
    start_notes = (limit_i + (2 if limit and limit != "GMP" else 1)) if limit_i is not None else len(items)
    for _, t in items[start_notes:]:
        if re.fullmatch(r"\d{1,3}", t) or re.fullmatch(r"\d{1,3}(?:,\s*\d{1,3})+", t):
            notes.append(t)

    additive = " ".join(additive_parts).strip()
    cat_name = " ".join(cat_name_parts).strip()
    group = bool(additive and additive.upper() == additive and re.search(r"[A-Z]{5,}", additive))

    if not ins and not additive and not cat:
        return None
    return {
        "category": cat,
        "category_name": cat_name or None,
        "additive": additive or None,
        "ins": ins,
        "limit": limit,
        "note": ", ".join(notes) if notes else None,
        "group": group,
    }


def is_name_wrap(rec: dict) -> bool:
    return bool(rec.get("additive") and not rec.get("ins") and not rec.get("category") and not rec.get("limit"))


def parse_main_tables(pdf) -> list[dict]:
    records: list[dict] = []
    cur_cat = None
    cur_cat_name = None
    pending: dict | None = None

    def flush():
        nonlocal pending
        if not pending:
            return
        if pending.get("ins") or pending.get("group"):
            pending["category"] = pending.get("category") or cur_cat
            pending["category_name"] = pending.get("category_name") or cur_cat_name
            records.append(pending)
        pending = None

    for page in pdf.pages:
        text = page.extract_text() or ""
        if "GMP Table Provisions" in text:
            break
        words = page.extract_words(keep_blank_chars=False) or []
        for row_words in cluster_rows(words):
            rec = parse_row(row_words)
            if not rec or rec.get("_table"):
                continue
            if rec.get("category"):
                cur_cat = rec["category"]
            if rec.get("category_name"):
                if rec.get("category") or not cur_cat_name:
                    cur_cat_name = rec["category_name"]
                elif pending is None:
                    cur_cat_name = (cur_cat_name + " " + rec["category_name"]).strip()
            if rec.get("ins") or (rec.get("group") and rec.get("limit")):
                flush()
                rec["category"] = rec.get("category") or cur_cat
                rec["category_name"] = rec.get("category_name") or cur_cat_name
                pending = rec
            elif pending and is_name_wrap(rec):
                pending["additive"] = ((pending.get("additive") or "") + " " + rec["additive"]).strip()
            elif rec.get("group") and rec.get("limit"):
                flush()
                rec["category"] = rec.get("category") or cur_cat
                pending = rec
                flush()
    flush()
    return records


def parse_gmp_table(pdf) -> list[dict]:
    out: list[dict] = []
    started = False
    pending_name = None
    for page in pdf.pages:
        text = page.extract_text() or ""
        if "GMP Table Provisions" in text:
            started = True
        if not started:
            continue
        if started and re.search(r"Annex to GMP", text, re.I) and out and "INS No" not in text:
            break
        words = page.extract_words() or []
        for row_words in cluster_rows(words):
            items = [(w["x0"], w["text"].strip()) for w in sorted(row_words, key=lambda w: w["x0"])]
            if not items:
                continue
            joined = " ".join(t for _, t in items)
            if HEADER_JOIN.search(joined) or FOOTER.search(joined):
                continue
            if "GMP Table" in joined or joined.startswith("The following"):
                continue
            ins_item = next(((x, t) for x, t in items if INS_RE.match(t) and x < 120), None)
            if ins_item:
                if pending_name and out:
                    out[-1]["additive"] = (out[-1]["additive"] + " " + pending_name).strip()
                    pending_name = None
                name = " ".join(t for x, t in items if x >= 120)
                if FOOTER.search(name):
                    continue
                out.append({"ins": ins_item[1], "additive": name.strip()})
            elif started and out and all(x >= 120 for x, _ in items):
                extra = " ".join(t for _, t in items)
                if extra and not FOOTER.search(extra):
                    out[-1]["additive"] = (out[-1]["additive"] + " " + extra).strip()
    return out


def expand_groups(records: list[dict]) -> list[dict]:
    expanded = []
    unknown_groups = 0
    for rec in records:
        if rec.get("ins"):
            expanded.append(rec)
            continue
        name = (rec.get("additive") or "").upper()
        name = re.sub(r"\s+", " ", name).strip(" -")
        members = None
        for g, ins_list in GROUP_INS.items():
            if g in name or name in g:
                members = ins_list
                break
        if not members:
            unknown_groups += 1
            continue
        for ins in members:
            expanded.append({**rec, "ins": ins, "group": True, "group_name": name})
    return expanded, unknown_groups


def main():
    if not PDF.exists():
        raise SystemExit(f"Missing {PDF}")
    print(f"Parsing {PDF.name} …")
    with pdfplumber.open(str(PDF)) as pdf:
        print(f"pages {len(pdf.pages)}")
        raw = parse_main_tables(pdf)
        gmp = parse_gmp_table(pdf)
    expanded, unknown_groups = expand_groups(raw)
    with_ins = [r for r in expanded if r.get("ins")]
    uniq = {r["ins"].lower() for r in with_ins}
    payload = {
        "source": "FSSAI Appendix A (Food Products Standards and Food Additives) Version 2 (04.11.2024)",
        "source_id": "fssai-appendix-a",
        "version": "2",
        "version_date": "2024-11-04",
        "file": PDF.name,
        "url": "https://fssai.gov.in/food-law/regulations/compendium/food-products-standards",
        "citation_url": "https://fssai.gov.in/upload/uploadfiles/files/Appendix%20A(2).pdf",
        "regulation_ref": "FSS (Food Products Standards and Food Additives) Regulations, 2011, Appendix A",
        "provisions": with_ins,
        "gmp": gmp,
        "stats": {
            "raw_rows": len(raw),
            "expanded_rows": len(with_ins),
            "unique_ins": len(uniq),
            "gmp_rows": len(gmp),
            "unknown_groups_skipped": unknown_groups,
        },
    }
    OUT.write_text(json.dumps(payload, indent=2))
    print("stats", payload["stats"])
    print("wrote", OUT, "bytes", OUT.stat().st_size)


if __name__ == "__main__":
    main()
