# Play Store listing translations

## Files (also in ~/Downloads)
- `play-store-listing-translations.csv` / `.xlsx` — **32** locales
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
