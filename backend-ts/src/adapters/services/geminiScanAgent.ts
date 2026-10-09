import type { Product, ScanAgentResult } from "../../domain/entities.js";
import type {
  IIngredientRepository,
  IProductRepository,
  IScanAgentService,
} from "../../domain/interfaces.js";

const SYSTEM_PROMPT = `You are an expert food & cosmetic safety assistant for the FoodCheck app.
You will be shown one or two photos of a product - typically a front-of-pack/branding photo
and/or a photo of the ingredients label.

Extract whatever you can see and reply with ONLY a single JSON object (no markdown fences):
{
  "product_name": "<string or null>",
  "brand": "<string or null>",
  "country_hint": "<string or null>",
  "ingredients_text": "<string or null>",
  "ingredient_names": ["<parsed ingredient tokens if visible>"]
}`;

function sequenceRatio(a: string, b: string): number {
  if (!a && !b) return 1;
  if (!a || !b) return 0;
  const s1 = a.toLowerCase();
  const s2 = b.toLowerCase();
  if (s1 === s2) return 1;
  // Simple Dice coefficient on bigrams
  if (s1.length < 2 || s2.length < 2) return s1 === s2 ? 1 : 0;
  const bigrams = (s: string) => {
    const m = new Map<string, number>();
    for (let i = 0; i < s.length - 1; i++) {
      const bg = s.slice(i, i + 2);
      m.set(bg, (m.get(bg) ?? 0) + 1);
    }
    return m;
  };
  const b1 = bigrams(s1);
  const b2 = bigrams(s2);
  let overlap = 0;
  for (const [k, v] of b1) overlap += Math.min(v, b2.get(k) ?? 0);
  return (2 * overlap) / (s1.length - 1 + (s2.length - 1));
}

/** Gemini Developer API over fetch — avoids bundling @google/genai (ws/events) into Neon Functions. */
async function generateContentText(apiKey: string, parts: Array<Record<string, unknown>>): Promise<string> {
  const url =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-goog-api-key": apiKey,
    },
    body: JSON.stringify({
      contents: [{ role: "user", parts }],
      generationConfig: { temperature: 0 },
    }),
  });
  const body = (await res.json()) as {
    error?: { message?: string };
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };
  if (!res.ok) {
    throw new Error(body.error?.message || `Gemini HTTP ${res.status}`);
  }
  const text = (body.candidates ?? [])
    .flatMap((c) => c.content?.parts ?? [])
    .map((p) => p.text || "")
    .join("")
    .trim();
  if (!text) throw new Error("Gemini returned an empty response");
  return text;
}

function parseFinalJson(content: string): Record<string, unknown> {
  let text = (content || "").trim();
  if (text.startsWith("```")) {
    text = text.replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  }
  try {
    return JSON.parse(text) as Record<string, unknown>;
  } catch {
    const match = text.match(/\{[\s\S]*\}/);
    if (match) {
      try {
        return JSON.parse(match[0]) as Record<string, unknown>;
      } catch {
        /* ignore */
      }
    }
    return {};
  }
}

export class GeminiScanAgentService implements IScanAgentService {
  private readonly apiKey: string | undefined;
  private readonly mockMode: boolean;

  constructor(
    private readonly productRepo: IProductRepository,
    private readonly ingredientRepo: IIngredientRepository,
    apiKey?: string
  ) {
    this.apiKey = apiKey || process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
    this.mockMode =
      (process.env.MOCK_GEMINI || "").toLowerCase() === "true" || !this.apiKey;
  }

  async analyzeImages(images: Buffer[], mockType?: string | null): Promise<ScanAgentResult> {
    if (this.mockMode) {
      return this.getMockResponse(mockType);
    }
    try {
      return await this.runAgent(images);
    } catch (e) {
      console.error("Gemini scan agent failed, falling back to mock", e);
      return this.getMockResponse(mockType);
    }
  }

  private async runAgent(images: Buffer[]): Promise<ScanAgentResult> {
    const parts: Array<Record<string, unknown>> = [
      { text: `${SYSTEM_PROMPT}\n\nAnalyze the attached product image(s).` },
    ];
    for (const img of images) {
      parts.push({
        inline_data: {
          mime_type: "image/jpeg",
          data: img.toString("base64"),
        },
      });
    }

    const text = await generateContentText(this.apiKey!, parts);
    const extracted = parseFinalJson(text);
    const productName = (extracted.product_name as string | null) ?? null;
    const brand = (extracted.brand as string | null) ?? null;
    const countryHint = (extracted.country_hint as string | null) ?? null;
    const ingredientsText = (extracted.ingredients_text as string | null) ?? null;
    const ingredientNames = Array.isArray(extracted.ingredient_names)
      ? (extracted.ingredient_names as string[])
      : [];

    const collected: Array<[Product, number]> = [];

    const queryText = [brand, productName].filter(Boolean).join(" ").trim();
    if (queryText) {
      const products = await this.productRepo.searchByName(queryText);
      for (const p of products) {
        const nameMatch = sequenceRatio(
          queryText,
          `${p.brand || ""} ${p.name}`.trim()
        );
        collected.push([p, Math.round(nameMatch * 100)]);
      }
    }

    // Ingredient-overlap SKU matching is too noisy (sugar hits every demo product).
    // Label tokens are matched in ScanWithAgentUseCase instead.
    void ingredientNames;

    // Informational country lookup (does not add candidates) — kept for parity / logging
    if (countryHint) {
      void this.ingredientRepo; // silence unused if future expansion
    }

    const bestByKey = new Map<string | number, [Product, number]>();
    for (const [product, score] of collected) {
      const key = product.id ?? product.barcode;
      const prev = bestByKey.get(key);
      if (!prev || score > prev[1]) bestByKey.set(key, [product, score]);
    }

    return {
      product_name: productName,
      brand,
      country_hint: countryHint,
      ingredients_text: ingredientsText,
      candidates: [...bestByKey.values()].sort((a, b) => b[1] - a[1]),
    };
  }

  private async getMockResponse(mockType?: string | null): Promise<ScanAgentResult> {
    const mType = mockType || process.env.MOCK_SCAN_TYPE || "ingredients_only";

    if (mType === "confident_match") {
      const product = await this.productRepo.getByBarcode("016000452327");
      return {
        product_name: "White Cookie Frosting",
        brand: "Betty Crocker",
        country_hint: "US",
        ingredients_text:
          "sugar, wheat starch, titanium dioxide, sodium benzoate, artificial flavor",
        candidates: product ? [[product, 95]] : [],
      };
    }

    if (mType === "ambiguous") {
      const p1 = await this.productRepo.getByBarcode("016000452327");
      const p2 = await this.productRepo.getByBarcode("111111111111");
      const candidates: Array<[Product, number]> = [];
      if (p1) candidates.push([p1, 70]);
      if (p2) candidates.push([p2, 62]);
      return {
        product_name: "White Bread",
        brand: null,
        country_hint: null,
        ingredients_text: "wheat flour, sugar, sodium benzoate, yeast",
        candidates,
      };
    }

    if (mType === "ingredients_only") {
      return {
        product_name: null,
        brand: null,
        country_hint: "IN",
        ingredients_text: "potassium bromate, E171, tartrazine, water, wheat flour, sugar",
        candidates: [],
      };
    }

    return { candidates: [] };
  }
}
