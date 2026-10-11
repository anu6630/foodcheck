import {
  IMAGE_ROLES,
  SCAN_CATEGORIES,
  type ExtractedIngredientItem,
  type ImageRole,
  type Product,
  type ScanAgentResult,
} from "../../domain/entities.js";
import type {
  IIngredientRepository,
  IProductRepository,
  IScanAgentService,
} from "../../domain/interfaces.js";

const SYSTEM_PROMPT = `You are an expert food & cosmetic safety assistant for the FoodCheck app.
You will be shown one or two numbered photos (Image 1, Image 2) of ONE product, in any order:
a front-of-pack/branding photo and/or a photo of the ingredients label. One photo may show both.

Extract whatever you can see and reply with ONLY a single JSON object (no markdown fences):
{
  "image_roles": ["<role of Image 1>", "<role of Image 2, if any>"],
  "product_name": "<product name as printed, or null>",
  "brand": "<brand as printed, or null>",
  "category": "<one of: ${SCAN_CATEGORIES.join(" | ")} — or null>",
  "ingredients_complete": <true | false | null>,
  "country_hint": "<string or null>",
  "ingredients_text": "<full ingredients paragraph as printed, or null>",
  "ingredient_items": [
    { "kind": "item", "name": "<single ingredient name as on pack>" },
    {
      "kind": "group",
      "role": "<word before brackets, e.g. Colours or Emulsifiers or Raising Agent>",
      "codes": ["171", "102", "500(ii)"],
      "label_text": "<exact pack phrase, e.g. Colours (171, 102, 133)>"
    }
  ]
}

Rules for image_roles (one entry per image, same order):
- "front" = branding/name side without an ingredients list
- "ingredients" = ingredients list without the product name
- "both" = shows the product name and the ingredients list
- "other" = neither (nutrition table only, barcode, unrelated photo)

Rules for product_name / brand / category:
- Only what is printed or obvious on the pack; use null when unsure. Never guess a brand.
- category must be exactly one of the listed values, or null.

ingredients_complete: false if the ingredients list is cut off, blurred or partly hidden;
true if it is fully readable; null if there is no ingredients list.

Rules for ingredient_items:
- Prefer splitting additive groups like "Colours (171, 102, 133)" or "Emulsifiers (322, 471)"
  into kind=group with one code string per member (keep suffixes like 150c or 500(ii)).
- Plain names (sugar, milk solids) are kind=item.
- Do NOT invent ingredients that are not on the label.
- Do NOT put category headers ("Panned Confectionery", "Allergen Information") as items.`;

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
    // Mock data only when explicitly requested (local dev/tests) — never as a silent
    // stand-in for a failed read, or users would see invented ingredients and flags.
    this.mockMode = (process.env.MOCK_GEMINI || "").toLowerCase() === "true";
  }

  async analyzeImages(images: Buffer[], mockType?: string | null): Promise<ScanAgentResult> {
    if (this.mockMode) {
      return this.getMockResponse(mockType);
    }
    if (!this.apiKey) throw new Error("Label reading is not configured");
    try {
      return await this.runAgent(images);
    } catch (e) {
      console.error("Gemini scan agent failed", e);
      const msg = (e as Error).message || "";
      if (/quota|rate.?limit|429/i.test(msg)) {
        throw new Error("Label reading is busy right now. Please try again later.");
      }
      throw new Error("Could not read the label. Please try again.");
    }
  }

  private async runAgent(images: Buffer[]): Promise<ScanAgentResult> {
    const parts: Array<Record<string, unknown>> = [
      { text: `${SYSTEM_PROMPT}\n\nAnalyze the attached product image(s).` },
    ];
    images.forEach((img, i) => {
      parts.push({ text: `Image ${i + 1}:` });
      parts.push({
        inline_data: {
          mime_type: "image/jpeg",
          data: img.toString("base64"),
        },
      });
    });

    const text = await generateContentText(this.apiKey!, parts);
    const extracted = parseFinalJson(text);
    const productName = (extracted.product_name as string | null) ?? null;
    const brand = (extracted.brand as string | null) ?? null;
    const countryHint = (extracted.country_hint as string | null) ?? null;
    const ingredientsText = (extracted.ingredients_text as string | null) ?? null;
    const ingredientItems = parseIngredientItems(extracted.ingredient_items);
    const labelFacts = parseLabelFacts(extracted, images.length);

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
      ingredient_items: ingredientItems,
      ...labelFacts,
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
        ingredient_items: [
          { kind: "item", name: "potassium bromate" },
          { kind: "item", name: "E171" },
          { kind: "item", name: "tartrazine" },
          { kind: "item", name: "water" },
          { kind: "item", name: "wheat flour" },
          { kind: "item", name: "sugar" },
        ],
        candidates: [],
      };
    }

    if (mType === "colours_group") {
      return {
        product_name: "Gems",
        brand: "Cadbury",
        country_hint: "IN",
        ingredients_text:
          "Sugar, Colours (171, 102, 133, 124, 127, 122, 132, 110), Emulsifiers (414, 442, 476)",
        ingredient_items: [
          { kind: "item", name: "Sugar" },
          {
            kind: "group",
            role: "Colours",
            codes: ["171", "102", "133", "124", "127", "122", "132", "110"],
            label_text: "Colours (171, 102, 133, 124, 127, 122, 132, 110)",
          },
          {
            kind: "group",
            role: "Emulsifiers",
            codes: ["414", "442", "476"],
            label_text: "Emulsifiers (414, 442, 476)",
          },
        ],
        candidates: [],
      };
    }

    return { candidates: [] };
  }
}

/** Validates the model's photo roles / category / completeness. Pure — unit tested. */
export function parseLabelFacts(
  extracted: Record<string, unknown>,
  imageCount: number
): { image_roles: ImageRole[]; category: string | null; ingredients_complete: boolean | null } {
  const rawRoles = Array.isArray(extracted.image_roles) ? extracted.image_roles : [];
  const roles = rawRoles.slice(0, imageCount).map((r) => {
    const v = String(r ?? "").trim().toLowerCase();
    return (IMAGE_ROLES as readonly string[]).includes(v) ? (v as ImageRole) : "other";
  });
  const catRaw = typeof extracted.category === "string" ? extracted.category.trim().toLowerCase() : "";
  const category = SCAN_CATEGORIES.find((c) => c.toLowerCase() === catRaw) ?? null;
  const complete = extracted.ingredients_complete;
  return {
    // Partial/missing roles are worse than none: the app then falls back to upload order.
    image_roles: roles.length === imageCount ? roles : [],
    category,
    ingredients_complete: typeof complete === "boolean" ? complete : null,
  };
}

function parseIngredientItems(raw: unknown): ExtractedIngredientItem[] {
  if (!Array.isArray(raw)) return [];
  const out: ExtractedIngredientItem[] = [];
  for (const entry of raw) {
    if (typeof entry === "string" && entry.trim()) {
      out.push({ kind: "item", name: entry.trim() });
      continue;
    }
    if (!entry || typeof entry !== "object") continue;
    const o = entry as Record<string, unknown>;
    if (o.kind === "group" && typeof o.role === "string" && Array.isArray(o.codes)) {
      const codes = o.codes.map((c) => String(c).trim()).filter(Boolean);
      if (!codes.length) continue;
      out.push({
        kind: "group",
        role: o.role.trim(),
        codes,
        label_text: typeof o.label_text === "string" ? o.label_text : undefined,
      });
      continue;
    }
    const name = typeof o.name === "string" ? o.name.trim() : "";
    if (name) out.push({ kind: "item", name });
  }
  return out;
}
