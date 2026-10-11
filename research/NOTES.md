# Health / hazard reference data (research)

Downloaded for exploration. **Not wired into the app yet.**

These sources answer different questions:

| Question | Best sources |
|----------|----------------|
| How much is “OK” daily? | EFSA OpenFoodTox **ADI/TDI**, JECFA ADI (page-level) |
| Can it damage DNA / show genotox signals? | OpenFoodTox **Genotoxicity** |
| Is it a carcinogen (hazard ID)? | **IARC** groups 1 / 2A / 2B |
| Cancer / repro listing (US CA warning law)? | **Prop 65** (download blocked here; PDF text kept) |
| Additive of interest / overexposure risk? | **Open Food Facts** additives taxonomy (ANSES + EFSA tags) |
| Authorised vs withdrawn in food? | EU Reg 1333/2008 (**not** a toxicity DB) |
| Process contaminants? | EFSA opinions + IARC (e.g. acrylamide) |

**ADI ≠ “safe to ignore.”** Missing ADI ≠ banned. IARC hazard ≠ dietary risk (JECFA/EFSA still set intake limits separately).

---

## Layout

```
research/
  NOTES.md
  openfoodtox/          # EFSA ADI, genotox, reference points
  jecfa/                # WHO JECFA scrape (links, little ADI text)
  iarc/                 # IARC classifications CSV (+ SMILES mirror)
  ntp/                  # US NTP Report on Carcinogens (PDF list)
  prop65/               # CA Prop 65 (PDF text; official CSV bot-blocked)
  openfoodfacts/        # additives taxonomy + hazard-signal extract
  concerns/             # small curated seed (EU bans, Southampton, IARC hits)
```

---

## Sources downloaded

| Source | What it gives | Local path | Join key |
|--------|---------------|------------|----------|
| **OpenFoodTox 3.0** | ADI/TDI, NoAllocated, tox ref values | `openfoodtox/OFT3.0_export_repository.xlsx` | CAS via REF_SUB |
| **OpenFoodTox 2.0** | ADI, **genotoxicity**, NOAEL/BMDL points, EFSA opinion links | `openfoodtox/oft2_2023/*` | Substance → CAS (decode `_xNNNN_`) |
| **JECFA (Figshare)** | Names, CAS, eval year, WHO URLs (no ADI column) | `jecfa/JECFA.csv` | CAS |
| **IARC Monographs** | Carcinogen group 1 / 2A / 2B / 3 | `iarc/iarc_classifications.csv` (1128 agents) | CAS (+ name) |
| **NTP RoC** | US “known / reasonably anticipated” carcinogens | `ntp/listed_substances_508.pdf` | name (PDF) |
| **Open Food Facts additives** | E-number, EFSA eval links, **ANSES of interest**, overexposure tags | `openfoodfacts/additives*.json/txt` + `additives_hazard_signals.csv` | E-number |
| **Prop 65** | Cancer / repro toxicant list | `prop65/p65chemicalslist_extracted.txt` (official CSV/Excel blocked by Incapsula) | CAS/name |
| **Seed concerns** | Hand-picked high-signal rows for FoodCheck | `concerns/seed_health_concerns.csv` | E / CAS |

Samples already joined for ETL experiments:

- `openfoodtox/samples/oft3_adi_with_cas.csv`
- `openfoodtox/samples/oft2_adi_with_cas.csv`
- `openfoodtox/samples/oft2_genotox_with_cas.csv` (~10.8k rows; 229 Positive / 637 Ambiguous)
- `iarc/iarc_1_2a_2b_with_cas.csv` (444 rows with CAS)
- `openfoodfacts/additives_hazard_signals.csv` (764 additives; 122 ANSES-of-interest; 38 “high” overexposure)

---

## Beyond ADI — what “potential health issue” looks like in data

### 1. Genotoxicity (EFSA OpenFoodTox)

From OFT2 `Genotoxicity_KJ_2023.xlsx` after CAS join:

| Call | Count (approx) |
|------|----------------|
| Negative | 4719 |
| Not determined / No data | ~5100 |
| Ambiguous | 637 |
| Positive | 229 |

Useful as a **flag + citation** (EFSA OutputID → opinion URL), not as a consumer “danger score” alone.  
Example: acrylamide CAS `79-06-1` → Positive; aspartame mostly Negative; TiO₂ mixed No data/Negative across years.

### 2. Carcinogen hazard ID (IARC)

Official CSV (updated 2026-07-23): groups **1** (135), **2A** (98), **2B** (329), **3** (498).

Food-relevant examples with CAS:

| Agent | CAS | Group |
|-------|-----|-------|
| Aspartame | 22839-47-0 | **2B** |
| Titanium dioxide | 13463-67-7 | **2B** |
| Potassium bromate | 7758-01-2 | **2B** |
| Acrylamide | 79-06-1 | **2A** |
| Formaldehyde | 50-00-0 | **1** |
| Sunset Yellow FCF | 2783-94-0 | 3 |
| Nitrate/nitrite (endogenous nitrosation) | — | **2A** (exposure circumstance) |
| Processed meat | — | **1** (not a CAS) |

Overlap IARC∩OFT3-ADI CAS ≈ **36**; IARC∩genotox± ≈ **78**. Many food concerns are **mixtures / processes**, not single CAS.

IARC = **hazard identification**. Risk (how much you eat) is still EFSA/JECFA. Aspartame is the textbook case: IARC 2B + JECFA ADI kept.

### 3. Open Food Facts additive tags (crowd + agency pointers)

From taxonomy (not primary science, but great UX signals):

- `anses_additives_of_interest` — **122** additives (French ANSES “of interest” list)
- `efsa_evaluation_overexposure_risk` — high (38) / moderate (14) / no (13)
- `efsa_evaluation_exposure_95th_greater_than_adi` — population overexposure vs ADI
- EFSA evaluation title + URL + date on ~207 entries

Join on **E-number** to our `ingredient_codes`.

### 4. Regulatory withdrawal / warnings (not tox DB, still “health issue” for users)

See `concerns/seed_health_concerns.csv`:

- **E171 TiO₂** — EU food authorisation withdrawn (2022)
- **Southampton six colours** — EU/UK hyperactivity warning label
- **Potassium bromate** — not authorised in EU; IARC 2B
- Nitrites — IARC 2A under nitrosation conditions

### 5. Prop 65 / NTP

- **Prop 65**: cancer & reproductive toxicants; good for US warning-style UI. Official download blocked here → re-fetch Excel manually from [OEHHA](https://oehha.ca.gov/proposition-65/proposition-65-list).
- **NTP RoC**: US federal carcinogen list (PDF). Complements IARC; fewer food additives.

### 6. JECFA / WHO

Still valuable for **authoritative evaluation links** and history; bulk CSV lacks ADI/hazard text. Pair with IARC + OpenFoodTox for numbers/flags.

---

## Suggested FoodCheck health layer (multi-signal)

```
ingredient_codes (CAS, E, INS)
        │
        ├─► OpenFoodTox: ADI / NoAllocated / genotox call + opinion URL
        ├─► IARC: group 1|2A|2B (hazard badge) + monograph citation
        ├─► OFF taxonomy: ANSES interest, overexposure risk, EFSA link
        ├─► Reg layer: EU withdrawn / not authorised / warning label
        └─► JECFA: WHO chemical URL (optional)
```

**UI principles**

1. Show **separate chips**: “ADI”, “IARC 2B”, “EU withdrawn”, “genotox signal” — don’t collapse into one fake score.  
2. Prefer **citations** (EFSA DOI, IARC volume, EUR-Lex).  
3. Process contaminants (acrylamide) attach via product category / process, not only label OCR.  
4. Filter OpenFoodTox to food-additive-relevant substances where possible (lots of pesticides in the dump).

---

## OpenFoodTox ADI join (short)

**OFT3:** `ToxRefValues.Parent UUID` → `SUB` → `REF_SUB.Inventory.CASNumber`  
→ ~1007 numeric ADIs, ~533 unique CAS (`samples/oft3_adi_with_cas.csv`).

**OFT2:** decode Excel `_x0020_` escapes; `ReferenceValues` ADI ⋈ `SubstanceCharacterisation` (`has == as such`).

TiO₂: CAS present; **no ADI** in OFT (and IARC **2B** + EU withdrawn).

---

## JECFA (short)

6552-row Figshare scrape: identifiers + document links, **no ADI column**. Use for deep links; scrape WHO pages if numeric ADI needed.

---

## Gaps / next downloads (optional)

| Source | Why | Status |
|--------|-----|--------|
| Prop 65 Excel (manual) | Clean CAS + toxicity type | Bot-blocked |
| ECHA SVHC / EU ED list | Endocrine / SVHC flags | Portal 403 from CLI |
| EPA CompTox / ToxValDB | Broad POD/RfD (needs API key) | Not fetched |
| EU Annex II full parse | Authorised E-list | Use EUR-Lex / OFF |
| CTD chemical–disease | Mechanistic associations | Large; optional |

---

## Caveats

- Community scrapes (JECFA Figshare, OFF taxonomy) need provenance labels in-app.  
- IARC group ≠ “banned” and ≠ exceedance of ADI.  
- Multiple ADI years → keep latest + history.  
- CAS typos across DBs (aspartame OFT `22839-47-0` vs some JECFA scrape variants).
