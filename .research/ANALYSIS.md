# Open Food Facts Server — Research Analysis
_Analyzed: 2026-07-01_

---

## What This Repo Is

`openfoodfacts-server` is the **backend server + database layer** (written in Perl) that powers:
- `world.openfoodfacts.org` (food)
- `world.openbeautyfacts.org` (cosmetics)
- The public REST API (v2 deprecated, **v3 current**)

It does **NOT** contain product photos — those live on a CDN and AWS S3.

---

## Does It Have Photos?

**NO photos in this repo.** But the ecosystem provides full image access:

| Source | URL Pattern | Notes |
|---|---|---|
| CDN (live) | `https://images.openfoodfacts.org/images/products/{barcode_path}/{img}.jpg` | Per-product, real-time |
| AWS S3 (bulk) | `https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/{barcode_path}/{img}.jpg` | Monthly sync, bulk download |
| API response | `selected_images` field | Returns URLs for front/ingredients/nutrition/packaging images |

### Image Types Per Product (via API)
- `front_{lang}` — front of product
- `ingredients_{lang}` — ingredients label
- `nutrition_{lang}` — nutrition facts
- `packaging_{lang}` — packaging logos

### Image URL Computation
Barcode `3435660768163` → path `343/566/076/8163`
- Full: `https://images.openfoodfacts.org/images/products/343/566/076/8163/1.jpg`
- 400px: `...343/566/076/8163/1.400.jpg`

---

## Indian Food Support

### Strong Support ✅

| What | Evidence |
|---|---|
| **IFCT 2017 dataset** | `external-data/ifct/IndianFoodCompositionTables2017.csv` — 541 Indian foods with local names in Hindi (H.), Tamil (Tam.), Telugu (Tel.), Kannada (Kan.), Malayalam (Mal.), Marathi (Mar.), Punjabi (P.), Gujarati (G.), Assamese (A.), Oriya (O.), Kashmiri (Kash.) |
| **Hindi in taxonomy** | 235 Hindi (`hi:`) ingredient entries in `taxonomies/food/ingredients.txt` |
| **Hindi categories** | 49 Hindi (`hi:`) entries in `taxonomies/food/categories.txt` |
| **India as country** | `countries.txt` has `en: India, Bharat, Hindustan, IN, IND` |
| **Indian-specific items** | Ghee, Basmati rice, Amla (Indian Gooseberry), buffalo milk, flat beans (gavar phali), moth bean, lotus seeds, etc. all explicitly described |

### IFCT 2017 CSV Structure
```
Food Code, Food Name, Scientific Name, Local Name (all Indian languages), Food Group, 
Nutritional fields: Energy, Moisture, Ash, Vitamins, Fat, Cholesterol, Fiber, 
Carbs, Protein, Amino acids, Carotenoids, Polyphenols, Minerals, Fatty acids...
```
541 rows of Indian food items — good seed data for Indian food composition.

---

## What the Repo Contains (Key for Our App)

### Taxonomies (`/taxonomies/`)
Structured text files used for ingredient parsing, matching, and classification:

| File | Purpose |
|---|---|
| `food/ingredients.txt` | Master ingredient list (multilingual, including hi:) |
| `food/categories.txt` | Product categories (multilingual) |
| `additives.txt` | Food additives with EU/Codex regulatory references |
| `allergens.txt` | Standard allergens |
| `ingredients_analysis.txt` | Vegan/vegetarian/palm-oil analysis rules |
| `labels.txt` | Certifications (organic, halal, kosher, etc.) |
| `countries.txt` | Countries list (includes India/Bharat) |
| `nutrients.txt` | Nutrient definitions |

### External Data (`/external-data/`)
| Directory | Contents |
|---|---|
| `ifct/` | **Indian Food Composition Tables 2017** — 541 Indian foods, full nutrition data |
| `ciqual/` | French food composition data (ANSES) |
| `environmental_score/` | Eco-score data |

### API (v3 — Current)
- **Rate limits:** 15 req/min for product reads, 10 req/min for search (per IP)
- **Barcode lookup:** `GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json`
- Returns: name, ingredients list, additives, allergens, images URLs, nutrition, categories, labels
- **Image field:** `selected_images` in response has front/ingredients/nutrition image URLs per language

### AI / OCR (Robotoff)
- `run_cloud_vision_ocr.pl` and `run_ocr.py` scripts — uses Google Cloud Vision
- `docs/api/intro-robotoff.md` — **Robotoff** is their separate ML service that auto-extracts ingredients from photos
- Robotoff: `https://github.com/openfoodfacts/robotoff` (separate repo)

---

## Relevance to Our FoodCheck App

### What We CAN Use Directly
1. **Open Food Facts API** — barcode → product name + ingredients + images. Free, no key required for read.
2. **IFCT 2017 CSV** — seed our DB with 541 Indian foods and their compositions.
3. **Additives taxonomy** — has EU regulatory references baked in, good for banned ingredient detection.
4. **Ingredients taxonomy** — multilingual ingredient matching including Hindi.

### What We Need Separately
1. **Banned ingredients by country** — NOT in this repo. Additives taxonomy has EU references but not a clean banned-list. Need EU CosIng Annex II CSV + FSSAI (India) data.
2. **Indian packaged food ingredient data** — Open Food Facts coverage of Indian packaged products is sparse. Consider supplementing with FSSAI product database.
3. **Gemini Vision** — for photo → product identification, we'll use Gemini directly (not Robotoff).

---

## Recommended Architecture for FoodCheck

```
User Photo
    ↓
Gemini Vision API
    → Extract: product name / barcode / label text
    ↓
Open Food Facts API (barcode or name search)
    → Returns: ingredients list, images
    ↓
Ingredient Matching
    → Against our DB seeded from:
       - IFCT 2017 (Indian foods)
       - OFF Ingredients taxonomy
    ↓
Banned Ingredient Check
    → Against banned-by-country table seeded from:
       - EU CosIng Annex II (cosmetics)
       - EU Food Additives Regulation 1333/2008
       - FSSAI banned list (India)
    ↓
Result: Safe / Banned in [Country List]
```

---

## Next Steps

- [ ] Download EU CosIng Annex II as CSV for banned ingredients seeding
- [ ] Check FSSAI (India) for banned food additives/ingredients list
- [ ] Evaluate Open Beauty Facts API for cosmetics coverage
- [ ] Prototype: Gemini Vision → barcode extraction → OFF API lookup
- [ ] Design DB schema: products, ingredients, ingredient_bans (ingredient_id, country, regulation_ref)
