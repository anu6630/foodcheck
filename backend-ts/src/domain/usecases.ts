import type {
  AnnotatedIngredient,
  DetectedLabel,
  ExtractedIngredientItem,
  HealthFlag,
  Ingredient,
  Product,
  ProductIngredient,
  ScanAgentResult,
  Severity,
} from "./entities.js";
import { codesLabel, normalizeCodeValue } from "./codes.js";
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

/** Default market for regulation severity on scan/search chips. */
export const DEFAULT_MARKET = "IN";

const ADDITIVE_CODE_RE = /^(?:E|INS)?[\s.-]*\d{3,4}(?:[a-z]{1,2}|\([ivx]+\))?$/i;
const SEVERITY_RANK: Record<string, number> = { red: 0, amber: 1, green: 2, permitted: 2, gray: 3 };

export function healthFlagLabel(kind: string, value?: string | null): string {
  const k = kind.toLowerCase();
  if (k === "carcinogen" || k.startsWith("iarc") || k === "cancer_precursor_concern") {
    const v = (value || "").trim();
    if (/^(1|2A|2B|3)$/i.test(v)) return `IARC ${v.toUpperCase()}`;
    if (k.includes("2b") || /2b/i.test(v)) return "IARC 2B";
    if (k.includes("2a")) return "IARC 2A";
    if (k.includes("iarc_1") || k.endsWith("_1")) return "IARC 1";
    return "Carcinogen";
  }
  if (k === "authorisation_withdrawn" || k === "not_authorised_eu") return "EU withdrawn";
  if (k === "adi") return "ADI";
  if (k === "genotox") return value?.toLowerCase() === "positive" ? "Genotox+" : "Genotox";
  if (k === "label_warning_hyperactivity") return "Hyperactivity";
  if (k === "overexposure_risk") return "Overexposure";
  if (k === "anses_of_interest") return "ANSES";
  if (k === "evaluation") return "Evaluation";
  return kind.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()).slice(0, 20);
}

function toHealthFlags(
  rows: Array<{ kind: string; value: string | null; summary: string }>
): HealthFlag[] {
  const flags: HealthFlag[] = [];
  const seen = new Set<string>();
  for (const r of rows) {
    const label = healthFlagLabel(r.kind, r.value);
    // Dedupe by display label so IARC 2B from multiple kind rows appears once.
    if (seen.has(label)) continue;
    seen.add(label);
    flags.push({ kind: r.kind, label, value: r.value });
  }
  return flags.slice(0, 6);
}

function collectAnnotatedIds(items: AnnotatedIngredient[]): string[] {
  const ids: string[] = [];
  for (const item of items) {
    if (item.id) ids.push(item.id);
    if (item.members?.length) ids.push(...collectAnnotatedIds(item.members));
  }
  return ids;
}

function applyHealthMap(
  item: AnnotatedIngredient,
  map: Record<string, Array<{ kind: string; value: string | null; summary: string }>>
): AnnotatedIngredient {
  if (item.members?.length) {
    const members = item.members.map((m) => applyHealthMap(m, map));
    const flags = members.flatMap((m) => m.health_flags ?? []);
    const seen = new Set<string>();
    const merged: HealthFlag[] = [];
    for (const f of flags) {
      const key = `${f.kind}|${f.label}`;
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(f);
    }
    return {
      ...item,
      members,
      health_flags: merged.slice(0, 6),
      has_health_watch: members.some((m) => m.has_health_watch),
    };
  }
  if (!item.id) {
    return { ...item, health_flags: [], has_health_watch: false };
  }
  const rows = map[item.id] ?? [];
  const flags = toHealthFlags(rows);
  return {
    ...item,
    health_flags: flags,
    has_health_watch: flags.length > 0,
  };
}

/** Attach batched health flags onto annotated items (and group members). */
export async function attachHealthFlags(
  ingredientRepo: IIngredientRepository,
  items: AnnotatedIngredient[]
): Promise<AnnotatedIngredient[]> {
  const map = await ingredientRepo.getHealthFlagsByIngredientIds(collectAnnotatedIds(items));
  return items.map((i) => applyHealthMap(i, map));
}

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

/**
 * Regulation severity for a single market. Health signals are attached separately
 * via [attachHealthFlags] — they never flip this color.
 */
export function annotateIngredient(
  ingredient: Ingredient,
  market: string = DEFAULT_MARKET
): AnnotatedIngredient {
  const marketCode = (market || DEFAULT_MARKET).toUpperCase();
  const marketBans = ingredient.bans.filter(
    (b) => (b.country_code || "").toUpperCase() === marketCode
  );
  const matchedBans: AnnotatedIngredient["matched_bans"] = [];
  // No row for this market = unknown — not "safe" and not "permitted elsewhere".
  let severity: Severity = "gray";

  for (const ban of marketBans) {
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
      matchedBans.push({
        country_code: ban.country_code,
        status: ban.status,
        condition: ban.condition ?? null,
        regulation_ref: ban.regulation_ref ?? null,
      });
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
    market_code: marketCode,
    has_health_watch: false,
    health_flags: [],
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

/** Split "Colours (171, 102, 500(ii))" into role + code list. Rejects % amounts like "Cocoa (8%)". */
export function splitRoleAndCodes(token: string): { role: string; codes: string[]; label_text: string } | null {
  const t = token.trim();
  const open = t.indexOf("(");
  if (open <= 0 || !t.endsWith(")")) return null;
  let depth = 0;
  let close = -1;
  for (let i = open; i < t.length; i++) {
    if (t[i] === "(") depth++;
    else if (t[i] === ")") {
      depth--;
      if (depth === 0) {
        close = i;
        break;
      }
    }
  }
  if (close !== t.length - 1) return null;
  const role = t.slice(0, open).trim();
  const inner = t.slice(open + 1, close).trim();
  if (!role || !inner) return null;
  const codes = inner
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (!codes.length || !codes.every((c) => ADDITIVE_CODE_RE.test(c))) return null;
  return { role, codes, label_text: t };
}

export function worstSeverity(items: AnnotatedIngredient[]): Severity {
  let best = 3;
  for (const i of items) {
    const idx = SEVERITY_RANK[i.severity] ?? 3;
    if (idx < best) best = idx;
  }
  return (["red", "amber", "green", "gray"] as const)[best]!;
}

function unmatchedToken(name: string): AnnotatedIngredient {
  return {
    kind: "item",
    id: null,
    name,
    category: null,
    e_number: null,
    codes: [],
    severity: "gray",
    matched_bans: [],
    market_code: DEFAULT_MARKET,
    has_health_watch: false,
    health_flags: [],
  };
}

async function annotateCodeOrName(
  ingredientRepo: IIngredientRepository,
  raw: string
): Promise<AnnotatedIngredient> {
  const token = raw.trim();
  if (!token) return unmatchedToken(raw);
  const ingredient = await ingredientRepo.findByNameOrSynonym(token);
  if (ingredient) {
    const ann = annotateIngredient(ingredient);
    ann.kind = "item";
    return ann;
  }
  // Prefer showing INS/E style for bare additive numbers on the chip.
  if (ADDITIVE_CODE_RE.test(token) && !/^(e|ins)/i.test(token)) {
    const code = normalizeCodeValue(token);
    return {
      kind: "item",
      id: null,
      name: `INS ${code}`,
      category: null,
      e_number: `E${code}`,
      codes: [{ system: "INS", code, display: `INS ${code}` }],
      severity: "gray",
      matched_bans: [],
      market_code: DEFAULT_MARKET,
      has_health_watch: false,
      health_flags: [],
    };
  }
  return unmatchedToken(token);
}

async function annotateGroup(
  ingredientRepo: IIngredientRepository,
  role: string,
  codes: string[],
  label_text: string
): Promise<AnnotatedIngredient> {
  const members: AnnotatedIngredient[] = [];
  for (const code of codes) {
    members.push(await annotateCodeOrName(ingredientRepo, code));
  }
  const severity = worstSeverity(members);
  const banned = members.flatMap((m) => m.matched_bans).filter((b) => b.status === "banned");
  return {
    kind: "group",
    id: null,
    name: role,
    role,
    label_text,
    category: null,
    e_number: null,
    codes: [],
    severity,
    matched_bans: banned,
    market_code: DEFAULT_MARKET,
    has_health_watch: members.some((m) => m.has_health_watch),
    health_flags: [],
    members,
  };
}

export async function matchExtractedItems(
  ingredientRepo: IIngredientRepository,
  items: ExtractedIngredientItem[]
): Promise<AnnotatedIngredient[]> {
  const matched: AnnotatedIngredient[] = [];
  for (const item of items) {
    if (item.kind === "group" && item.codes?.length) {
      const label =
        item.label_text?.trim() ||
        `${item.role} (${item.codes.join(", ")})`;
      matched.push(await annotateGroup(ingredientRepo, item.role.trim(), item.codes, label));
      continue;
    }
    const name = "name" in item ? item.name : "";
    if (!name.trim()) continue;
    const asGroup = splitRoleAndCodes(name);
    if (asGroup) {
      matched.push(
        await annotateGroup(ingredientRepo, asGroup.role, asGroup.codes, asGroup.label_text)
      );
    } else {
      matched.push(await annotateCodeOrName(ingredientRepo, name));
    }
  }
  // Record label position before the display sort (flagged first) so clients can still
  // compare by label order. A group is one label entry: its members share its position.
  matched.forEach((ing, i) => {
    ing.order = i + 1;
    ing.members?.forEach((m) => (m.order = i + 1));
  });
  matched.sort((a, b) => (SEVERITY_RANK[a.severity] ?? 3) - (SEVERITY_RANK[b.severity] ?? 3));
  return attachHealthFlags(ingredientRepo, matched);
}

export async function matchIngredientsFromText(
  ingredientRepo: IIngredientRepository,
  rawText: string
): Promise<AnnotatedIngredient[]> {
  const parsed = cleanAndSplitIngredients(rawText);
  return matchExtractedItems(
    ingredientRepo,
    parsed.map((name) => ({ kind: "item" as const, name }))
  );
}

export class SearchProductUseCase {
  constructor(
    private readonly productRepo: IProductRepository,
    private readonly ingredientRepo?: IIngredientRepository
  ) {}

  async execute(query: string) {
    const products = await this.productRepo.searchByName(query);
    const annotated = products.map(buildAnnotatedProduct);
    if (!this.ingredientRepo) return annotated;
    return Promise.all(
      annotated.map(async (p) => ({
        ...p,
        ingredients: await attachHealthFlags(this.ingredientRepo!, p.ingredients),
      }))
    );
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
    const built = buildAnnotatedProduct(product);
    return {
      ...built,
      ingredients: await attachHealthFlags(this.ingredientRepo, built.ingredients),
    };
  }
}

async function withProductHealth(
  ingredientRepo: IIngredientRepository,
  built: ReturnType<typeof buildAnnotatedProduct>
) {
  return {
    ...built,
    ingredients: await attachHealthFlags(ingredientRepo, built.ingredients),
  };
}

export class ScanWithAgentUseCase {
  constructor(
    private readonly agentService: IScanAgentService,
    private readonly ingredientRepo: IIngredientRepository
  ) {}

  async execute(images: Buffer[], mockType?: string | null) {
    const result: ScanAgentResult = await this.agentService.analyzeImages(images, mockType);
    const ingredients =
      result.ingredient_items && result.ingredient_items.length
        ? await matchExtractedItems(this.ingredientRepo, result.ingredient_items)
        : result.ingredients_text
          ? await matchIngredientsFromText(this.ingredientRepo, result.ingredients_text)
          : [];
    // Label facts travel with every outcome; catalog matching is optional on top.
    const detected: DetectedLabel = {
      product_name: result.product_name?.trim() || null,
      brand: result.brand?.trim() || null,
      category: result.category ?? null,
      image_roles: result.image_roles ?? [],
      ingredients_complete: result.ingredients_complete ?? null,
    };
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
          product: await withProductHealth(this.ingredientRepo, buildAnnotatedProduct(topProduct)),
          ingredients,
          detected,
        };
      }

      return {
        match_type: "suggestions" as const,
        candidates: await Promise.all(
          candidates.slice(0, 5).map(async ([product, score]) => ({
            product: await withProductHealth(this.ingredientRepo, buildAnnotatedProduct(product)),
            match_score: score,
          }))
        ),
        ingredients,
        detected,
      };
    }

    return { match_type: "ingredients_only" as const, ingredients, detected };
  }
}

export class UnifiedSearchUseCase {
  constructor(
    private readonly productRepo: IProductRepository,
    private readonly ingredientRepo: IIngredientRepository,
    private readonly lookup?: IProductLookupService
  ) {}

  async execute(query: string) {
    const products = await new SearchProductUseCase(this.productRepo, this.ingredientRepo).execute(
      query
    );
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
      merged.push(
        await withProductHealth(this.ingredientRepo, buildAnnotatedProduct(p))
      );
    }
    return {
      products: merged.slice(0, 12),
      ingredients: await attachHealthFlags(
        this.ingredientRepo,
        ingredients.map((i) => annotateIngredient(i))
      ),
    };
  }
}

const BAN_STATUS_RANK: Record<string, number> = { banned: 0, restricted: 1, permitted: 2 };

export class GetIngredientDetailsUseCase {
  constructor(private readonly ingredientRepo: IIngredientRepository) {}

  async execute(ingredientId: string) {
    const ingredient = await this.ingredientRepo.getById(ingredientId);
    if (!ingredient) return null;
    const [useLimits, useLimitCounts, healthReports, sources] = await Promise.all([
      this.ingredientRepo.getUseLimits(ingredientId, 16),
      this.ingredientRepo.getUseLimitCounts(ingredientId),
      this.ingredientRepo.getHealthReports(ingredientId),
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
      health_reports: healthReports,
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
