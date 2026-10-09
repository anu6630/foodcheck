import type {
  AnnotatedIngredient,
  Ingredient,
  Product,
  ProductIngredient,
  ScanAgentResult,
  Severity,
} from "./entities.js";
import { codesLabel } from "./codes.js";
import { TRACKED_COUNTRIES, countryLabel } from "./countries.js";
import { resolveFoodCategoryName } from "./fssaiCategories.js";
import type {
  IDataSourceRepository,
  IIngredientRepository,
  IProductLookupService,
  IProductRepository,
  IScanAgentService,
} from "./interfaces.js";

export { TRACKED_COUNTRIES, countryLabel } from "./countries.js";

/** Product suggestions need a real name/brand hit — ingredient overlap with a tiny catalog is noise. */
const SUGGEST_SCORE_MIN = 45;

export function scoreCandidate(nameMatch: number, ingredientOverlap: number): number {
  const n = Math.max(0, Math.min(1, nameMatch));
  const o = Math.max(0, Math.min(1, ingredientOverlap));
  return Math.round((n * 0.5 + o * 0.5) * 100);
}

export function cleanAndSplitIngredients(text: string): string[] {
  let t = text.replace(
    /^(ingredients|composition|contains|contains less than|inci|ingredients list)\s*:\s*/i,
    ""
  );
  const tokens: string[] = [];
  let current: string[] = [];
  let parenDepth = 0;
  for (const char of t) {
    if (char === "(") {
      parenDepth++;
      current.push(char);
    } else if (char === ")") {
      parenDepth--;
      current.push(char);
    } else if ((char === "," || char === ";" || char === "\n") && parenDepth === 0) {
      const token = current.join("").trim();
      if (token) tokens.push(token);
      current = [];
    } else {
      current.push(char);
    }
  }
  if (current.length) {
    const token = current.join("").trim();
    if (token) tokens.push(token);
  }

  const cleaned: string[] = [];
  for (let token of tokens) {
    token = token.replace(/\s*\(\s*\d+(\.\d+)?\s*%\s*\)/g, "");
    token = token.replace(/\s*\d+(\.\d+)?\s*%/g, "");
    token = token.replace(/^[ .*,]+|[ .*,]+$/g, "");
    if (token && token.length > 1) cleaned.push(token);
  }
  return cleaned;
}

export function annotateIngredient(ingredient: Ingredient): AnnotatedIngredient {
  const matchedBans: AnnotatedIngredient["matched_bans"] = [];
  // No country row means we have no regulation data — not "verified safe".
  let severity: Severity = "gray";

  for (const ban of ingredient.bans) {
    if (ban.status === "banned") {
      severity = "red";
      matchedBans.push({
        country_code: ban.country_code,
        status: ban.status,
        condition: ban.condition ?? null,
        regulation_ref: ban.regulation_ref ?? null,
      });
    } else if (ban.status === "restricted") {
      if (severity !== "red") severity = "amber";
      matchedBans.push({
        country_code: ban.country_code,
        status: ban.status,
        condition: ban.condition ?? null,
        regulation_ref: ban.regulation_ref ?? null,
      });
    } else if (ban.status === "permitted") {
      if (severity === "gray") severity = "green";
    }
  }

  return {
    id: ingredient.id,
    name: ingredient.name,
    category: ingredient.category ?? null,
    e_number: ingredient.e_number ?? null,
    codes: ingredient.codes ?? [],
    severity,
    matched_bans: matchedBans,
  };
}

export function annotateIngredients(productIngredients: ProductIngredient[]): AnnotatedIngredient[] {
  const annotated = productIngredients.map((pi) => {
    const ann = annotateIngredient(pi.ingredient);
    ann.order = pi.order;
    return ann;
  });
  annotated.sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  return annotated;
}

export function determineProductSeverity(annotated: AnnotatedIngredient[]): Severity {
  const severities = annotated.map((i) => i.severity);
  if (severities.includes("red")) return "red";
  if (severities.includes("amber")) return "amber";
  if (severities.includes("green")) return "green";
  return "gray";
}

function buildAnnotatedProduct(product: Product) {
  const ingredients = annotateIngredients(product.ingredients);
  return {
    product,
    severity: determineProductSeverity(ingredients),
    ingredients,
  };
}

export async function matchIngredientsFromText(
  ingredientRepo: IIngredientRepository,
  rawText: string
): Promise<AnnotatedIngredient[]> {
  const parsed = cleanAndSplitIngredients(rawText);
  const matched: AnnotatedIngredient[] = [];

  for (const token of parsed) {
    const ingredient = await ingredientRepo.findByNameOrSynonym(token);
    if (ingredient) {
      matched.push(annotateIngredient(ingredient));
    } else {
      matched.push({
        id: null,
        name: token,
        category: null,
        e_number: null,
        codes: [],
        severity: "gray",
        matched_bans: [],
      });
    }
  }

  const rank: Record<string, number> = { red: 0, amber: 1, green: 2, permitted: 2, gray: 3 };
  matched.sort((a, b) => (rank[a.severity] ?? 3) - (rank[b.severity] ?? 3));
  return matched;
}

export class SearchProductUseCase {
  constructor(private readonly productRepo: IProductRepository) {}

  async execute(query: string) {
    const products = await this.productRepo.searchByName(query);
    return products.map(buildAnnotatedProduct);
  }
}

export class GetProductDetailsUseCase {
  constructor(
    private readonly productRepo: IProductRepository,
    private readonly lookupService: IProductLookupService,
    private readonly ingredientRepo: IIngredientRepository
  ) {}

  async execute(barcode: string) {
    let product = await this.productRepo.getByBarcode(barcode);
    if (!product) {
      product = await this.lookupService.lookupBarcode(barcode);
      if (product) {
        product = {
          ...product,
          ingredients: await Promise.all(
            product.ingredients.map(async (pi) => {
              const hit =
                (await this.ingredientRepo.findByNameOrSynonym(pi.ingredient.name)) ||
                (pi.ingredient.e_number
                  ? await this.ingredientRepo.findByNameOrSynonym(pi.ingredient.e_number)
                  : null) ||
                (pi.ingredient.id
                  ? await this.ingredientRepo.getById(pi.ingredient.id)
                  : null);
              return hit ? { ...pi, ingredient: hit } : pi;
            })
          ),
        };
        await this.productRepo.save(product);
      }
    }
    if (!product) return null;
    return buildAnnotatedProduct(product);
  }
}

export class ScanWithAgentUseCase {
  constructor(
    private readonly agentService: IScanAgentService,
    private readonly ingredientRepo: IIngredientRepository
  ) {}

  async execute(images: Buffer[], mockType?: string | null) {
    const result: ScanAgentResult = await this.agentService.analyzeImages(images, mockType);
    const ingredients = result.ingredients_text
      ? await matchIngredientsFromText(this.ingredientRepo, result.ingredients_text)
      : [];
    const candidates = [...result.candidates]
      .filter(([, score]) => score >= SUGGEST_SCORE_MIN)
      .sort((a, b) => b[1] - a[1]);

    if (candidates.length) {
      const [topProduct, topScore] = candidates[0];
      const isConfident =
        topScore >= 85 && (candidates.length === 1 || topScore - (candidates[1]?.[1] ?? 0) >= 20);

      if (isConfident) {
        return {
          match_type: "confident" as const,
          product: buildAnnotatedProduct(topProduct),
          ingredients,
        };
      }

      return {
        match_type: "suggestions" as const,
        candidates: candidates.slice(0, 5).map(([product, score]) => ({
          product: buildAnnotatedProduct(product),
          match_score: score,
        })),
        ingredients,
      };
    }

    return { match_type: "ingredients_only" as const, ingredients };
  }
}

export class UnifiedSearchUseCase {
  constructor(
    private readonly productRepo: IProductRepository,
    private readonly ingredientRepo: IIngredientRepository,
    private readonly lookup?: IProductLookupService
  ) {}

  async execute(query: string) {
    const products = await new SearchProductUseCase(this.productRepo).execute(query);
    const ingredients = await this.ingredientRepo.searchIngredients(query);
    let extra: Product[] = [];
    if (products.length < 5 && this.lookup?.searchByName) {
      extra = await this.lookup.searchByName(query);
    }
    const seen = new Set(products.map((p) => p.product.barcode));
    const merged = [...products];
    for (const p of extra) {
      if (!p.barcode || seen.has(p.barcode)) continue;
      seen.add(p.barcode);
      merged.push(buildAnnotatedProduct(p));
    }
    return {
      products: merged.slice(0, 12),
      ingredients: ingredients.map(annotateIngredient),
    };
  }
}

const BAN_STATUS_RANK: Record<string, number> = { banned: 0, restricted: 1, permitted: 2 };

export class GetIngredientDetailsUseCase {
  constructor(private readonly ingredientRepo: IIngredientRepository) {}

  async execute(ingredientId: string) {
    const ingredient = await this.ingredientRepo.getById(ingredientId);
    if (!ingredient) return null;
    const [useLimits, useLimitCounts, sources] = await Promise.all([
      this.ingredientRepo.getUseLimits(ingredientId, 16),
      this.ingredientRepo.getUseLimitCounts(ingredientId),
      this.ingredientRepo.listSources(ingredientId),
    ]);
    const bans = [...ingredient.bans].sort(
      (a, b) => (BAN_STATUS_RANK[a.status] ?? 9) - (BAN_STATUS_RANK[b.status] ?? 9)
    );
    return {
      id: ingredient.id,
      name: ingredient.name,
      category: ingredient.category ?? null,
      e_number: ingredient.e_number ?? null,
      inci_name: ingredient.inci_name ?? null,
      cas_number: ingredient.cas_number ?? null,
      codes: ingredient.codes ?? [],
      codes_label: codesLabel(ingredient.codes ?? []),
      names: ingredient.names,
      bans: bans.map((b) => ({ ...b, country_label: countryLabel(b.country_code) })),
      use_limits: useLimits.map((l) => ({
        ...l,
        food_category_name: resolveFoodCategoryName(l.food_category_code, l.food_category_name),
        country_label: countryLabel(l.country_code),
      })),
      use_limit_counts: useLimitCounts,
      sources,
      tracked_countries: [...TRACKED_COUNTRIES],
    };
  }
}

export class ListDataSourcesUseCase {
  constructor(private readonly sources: IDataSourceRepository) {}
  async execute() {
    return { sources: await this.sources.listAll() };
  }
}
