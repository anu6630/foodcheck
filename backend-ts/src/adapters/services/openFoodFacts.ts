import type { Ingredient, Product, ProductIngredient } from "../../domain/entities.js";
import type { IProductLookupService } from "../../domain/interfaces.js";
import { cleanAndSplitIngredients } from "../../domain/usecases.js";

const OFF_UA = "FoodCheckApp/1.0 (ingredient safety; local catalog ingest)";

function productFromOff(prod: Record<string, unknown>, ingredientsList: ProductIngredient[]): Product | null {
  const barcode = String(prod.code || prod._id || prod.id || "").trim();
  if (!barcode || barcode.length < 8) return null;
  let name =
    (prod.product_name as string) ||
    (prod.product_name_en as string) ||
    (prod.product_name_hi as string) ||
    "";
  name = name.trim();
  if (!name) return null;
  let brand = prod.brands as string | undefined;
  if (brand) brand = brand.split(",")[0].trim();
  let category = prod.categories as string | undefined;
  if (category) category = category.split(",")[0].trim();
  return {
    id: null,
    name,
    brand: brand ?? null,
    barcode,
    category: category ?? null,
    source: "open_food_facts",
    ingredients: ingredientsList,
  };
}

export class OpenFoodFactsService implements IProductLookupService {
  private readonly mockMode =
    (process.env.MOCK_GEMINI || "").toLowerCase() === "true" ||
    (process.env.MOCK_OFF || "").toLowerCase() === "true";

  async searchByName(query: string): Promise<Product[]> {
    const q = query.trim();
    if (this.mockMode || q.length < 2) return [];
    const url = new URL("https://world.openfoodfacts.org/cgi/search.pl");
    url.searchParams.set("search_terms", q);
    url.searchParams.set("search_simple", "1");
    url.searchParams.set("action", "process");
    url.searchParams.set("json", "1");
    url.searchParams.set("page_size", "8");
    url.searchParams.set("sort_by", "unique_scans_n");
    try {
      const response = await fetch(url, {
        headers: { "User-Agent": OFF_UA },
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) return [];
      const data = (await response.json()) as { products?: Array<Record<string, unknown>> };
      const out: Product[] = [];
      const seen = new Set<string>();
      for (const prod of data.products ?? []) {
        const mapped = productFromOff(prod, []);
        if (!mapped || seen.has(mapped.barcode)) continue;
        seen.add(mapped.barcode);
        out.push(mapped);
      }
      return out;
    } catch (e) {
      console.error("Open Food Facts search failed", e);
      return [];
    }
  }

  async lookupBarcode(barcode: string): Promise<Product | null> {
    if (this.mockMode) {
      if (barcode === "999999999999") return null;
      return {
        id: null,
        name: "Mock Product",
        brand: "Mock Brand",
        barcode,
        category: "Mock Category",
        source: "open_food_facts",
        ingredients: [],
      };
    }

    const url = `https://world.openfoodfacts.org/api/v3/product/${barcode}.json`;
    try {
      const response = await fetch(url, {
        headers: { "User-Agent": OFF_UA },
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) return null;
      const data = (await response.json()) as {
        product?: Record<string, unknown>;
      };
      if (!data.product) return null;

      const prod = data.product;

      const ingredientsList: ProductIngredient[] = [];
      const offIngredients = prod.ingredients;
      if (Array.isArray(offIngredients)) {
        for (let i = 0; i < offIngredients.length; i++) {
          const ing = offIngredients[i] as Record<string, unknown>;
          const rawId = String(ing.id || "");
          const cleanedId = rawId
            ? rawId.split(":").pop()!.trim().toLowerCase().replace(/ /g, "-")
            : "";
          const ingName =
            (ing.text as string) ||
            cleanedId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
          if (cleanedId && ingName) {
            let eNumber: string | null = null;
            if (/^e\d+$/i.test(cleanedId)) eNumber = cleanedId.toUpperCase();
            const ingredient: Ingredient = {
              id: cleanedId,
              name: ingName,
              e_number: eNumber,
              names: [],
              bans: [],
            };
            ingredientsList.push({ ingredient, order: i });
          }
        }
      } else {
        const ingredientsText =
          (prod.ingredients_text as string) ||
          (prod.ingredients_text_en as string) ||
          "";
        if (ingredientsText) {
          const parsed = cleanAndSplitIngredients(ingredientsText);
          parsed.forEach((token, i) => {
            const cleanedId = token.toLowerCase().replace(/ /g, "-").replace(/[.*]/g, "");
            ingredientsList.push({
              order: i,
              ingredient: {
                id: cleanedId,
                name: token,
                names: [],
                bans: [],
              },
            });
          });
        }
      }

      return productFromOff({ ...prod, code: barcode }, ingredientsList);
    } catch (e) {
      console.error("Open Food Facts lookup failed", e);
      return null;
    }
  }
}
