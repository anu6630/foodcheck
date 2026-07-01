# FoodCheck — Product Requirements
_Created: 2026-07-01_

---

## Overview

A mobile app that lets users identify food/cosmetic products and check whether their ingredients are banned or restricted in any country. Primary market is India; global products also supported.

---

## Core User Flows

### Entry Points
Users can enter the app via three paths:
1. **Name search** — type a product name to find it
2. **Product photo** — take/upload a photo of a product label or packaging
3. **Ingredients list photo** — take/upload a photo of an ingredients panel

### AI Intent Detection (single photo, no user decision needed)
When a photo is uploaded, Gemini Vision automatically classifies intent:
- **Product photo detected** → match product in DB → show product detail + ingredient flags
- **Ingredients list detected** → parse ingredients directly → show ingredient analysis as primary view

No extra step or prompt is shown to the user — intent is inferred from the photo.

---

## Path A — Product Photo / Name Search

### Screen 1: Search Results
- Shows matched products with confidence/match score
- Sources: local DB (seeded from Open Food Facts + IFCT 2017) + OFF API fallback
- Ordered by match confidence (e.g. 96%, 88%, 72%)
- Tap a product to go to Product Detail

### Screen 2: Product Detail
- Product name, brand, weight, barcode
- Full ingredients list
- Each ingredient tagged:
  - **Red** = banned in ≥1 country
  - **Amber** = restricted/limited in ≥1 country
  - **Gray** = no known bans
- Tap any ingredient → Ingredient Detail

### Screen 3: Ingredient Detail
- Ingredient name, E-number (if applicable), category (additive, preservative, etc.)
- Per-country ban/restriction status:
  - Banned (outright) — country + regulation reference
  - Restricted (limit/condition) — country + condition + regulation reference
  - Permitted — country + regulation reference
- Regulation references shown (e.g. EU 1333/2008 Annex II, FSSAI FSS Act 2006, FSANZ Standard 2.9.2)

---

## Path B — Ingredients List Photo (Primary Experience)

### Screen 1: OCR + Parse
- Gemini Vision extracts raw ingredient text from photo
- Ingredients normalized against ingredient taxonomy (multilingual, including Hindi)
- Flagged ingredients highlighted inline in extracted text

### Screen 2: Ingredient Analysis (HERO SCREEN for Path B)
- Primary view: list of all detected ingredients
- Each ingredient shown with:
  - Severity badge: red (banned), amber (restricted), green (permitted)
  - Countries where banned/restricted as pills
- Ordered by severity (most concerning first)

### Screen 3: Matching Products (Secondary, shown below ingredient analysis)
- Products from DB that contain the same set of ingredients
- Ordered by number of matching ingredients
- Tap a product → goes to Path A Product Detail screen

---

## Country Filter
- Available on all result screens (Search Results, Product Detail, Ingredient Analysis)
- Default: India
- User can add multiple countries (EU, USA, Australia, etc.)
- Filter narrows displayed bans to selected countries only
- Persists as user preference

---

## Data Sources

### Product + Ingredient Data
| Source | Coverage | Format |
|---|---|---|
| Open Food Facts API | 4M+ global products, barcode lookup | REST API (free, no key) |
| Open Beauty Facts API | 100K+ cosmetic products | REST API (free, no key) |
| IFCT 2017 | 541 Indian foods, full nutritional data | CSV (in OFF server repo) |
| OFF Ingredients Taxonomy | Multilingual ingredient names incl. Hindi (235 entries) | Text taxonomy file |

### Banned / Restricted Ingredients Data
| Source | Coverage | Format |
|---|---|---|
| EU CosIng Annex II | 1,700+ banned cosmetic substances | CSV (data.europa.eu) |
| EU Food Additives Regulation 1333/2008 | Food additives banned/restricted in EU | CSV / manual |
| FSSAI (India) | Indian food additive permissions/bans | FSS Act 2006 |
| FSANZ (Australia/NZ) | Food Standards Australia New Zealand | Manual / scrape |

---

## Technical Stack (Proposed)

- **AI / Vision:** Gemini Vision API (photo → product name / barcode / ingredient text)
- **Product lookup:** Open Food Facts API v3 (barcode or name search)
- **Local DB:** Seeded from OFF IFCT 2017 CSV + banned ingredients CSVs
- **Ingredient matching:** Normalized against OFF ingredients taxonomy (multilingual)

### DB Schema (High Level)
```
products          (id, name, brand, barcode, category, source)
product_ingredients (product_id, ingredient_id, order)
ingredients       (id, name, inci_name, e_number, cas_number, category)
ingredient_names  (ingredient_id, lang, name)   -- multilingual
ingredient_bans   (ingredient_id, country_code, status[banned|restricted|permitted],
                   condition, regulation_ref, effective_date)
countries         (code, name)
```

---

## Design Principles

- **Single photo is enough** — never ask user to clarify intent, infer it
- **India-first** — default country filter is India; Hindi ingredient names supported
- **Severity-first ordering** — most concerning ingredients shown at top
- **Regulation transparency** — always show the regulation reference, not just "banned"
- **Offline-friendly** — core banned ingredient DB is local; OFF API is a fallback/enrichment layer

---

## Out of Scope (v1)

- User accounts / saved history
- Barcode scanner (Gemini Vision handles product recognition from photo)
- Nutrition scoring (Nutri-Score, NOVA) — can be added in v2
- Cosmetics-specific banned list UI (same flow, different data source)
