export interface IngredientName {
  lang: string;
  name: string;
}

/** One numbering scheme on an ingredient. `system` is open: E, INS, CAS, FDC, … */
export interface IngredientCode {
  system: string;
  code: string;
  display: string;
}

export interface IngredientBan {
  country_code: string;
  status: string;
  condition?: string | null;
  regulation_ref?: string | null;
  effective_date?: string | null;
}

/** One food-category cap (e.g. FSSAI Appendix A: INS 102 in 7.2.1 at 100 mg/kg). */
export interface IngredientUseLimit {
  country_code: string;
  food_category_code: string;
  food_category_name?: string | null;
  max_level: string;
  note?: string | null;
  regulation_ref?: string | null;
}

/**
 * One health/hazard claim from a single publisher. Same topic from two sources
 * (e.g. IARC carcinogen + Prop 65 cancer) = two rows — never dedupe across sources.
 */
export interface IngredientHealthReport {
  id: number;
  kind: string;
  summary: string;
  value?: string | null;
  unit?: string | null;
  evaluation_year?: string | null;
  citation?: string | null;
  citation_url?: string | null;
  source_id: string;
  source_title?: string | null;
  source_publisher?: string | null;
}

export interface Ingredient {
  id: string;
  name: string;
  category?: string | null;
  e_number?: string | null;
  inci_name?: string | null;
  cas_number?: string | null;
  codes?: IngredientCode[];
  names: IngredientName[];
  bans: IngredientBan[];
}

export interface ProductIngredient {
  ingredient: Ingredient;
  order: number;
}

export interface Product {
  id: number | null;
  name: string;
  brand?: string | null;
  barcode: string;
  category?: string | null;
  source: string;
  ingredients: ProductIngredient[];
}

/** Flat name, or a role+codes group Gemini (or the parser) split from the label. */
export type ExtractedIngredientItem =
  | { kind?: "item"; name: string }
  | { kind: "group"; role: string; codes: string[]; label_text?: string };

export interface ScanAgentResult {
  product_name?: string | null;
  brand?: string | null;
  country_hint?: string | null;
  ingredients_text?: string | null;
  /** Preferred structured tokens from Gemini; falls back to splitting ingredients_text. */
  ingredient_items?: ExtractedIngredientItem[];
  candidates: Array<[Product, number]>;
}

export type Severity = "red" | "amber" | "green" | "gray";

/** Compact health/hazard flag for list chips (not a full report). */
export interface HealthFlag {
  kind: string;
  label: string;
  value?: string | null;
}

export interface AnnotatedIngredient {
  id: string | null;
  name: string;
  category: string | null;
  e_number: string | null;
  codes?: IngredientCode[];
  /** Regulation severity for [market_code] only — not a safety score. */
  severity: Severity;
  matched_bans: Array<{
    country_code: string;
    status: string;
    condition: string | null;
    regulation_ref: string | null;
  }>;
  /** Market used for regulation severity (ISO-ish code, default IN). */
  market_code?: string;
  /** True when any health_reports row exists for this ingredient (or a group member). */
  has_health_watch?: boolean;
  /** Short flags for chip UI (IARC 2B, ADI, …). */
  health_flags?: HealthFlag[];
  order?: number;
  /** "group" = role chip with expandable members (e.g. Colours). */
  kind?: "item" | "group";
  role?: string | null;
  label_text?: string | null;
  members?: AnnotatedIngredient[];
}
