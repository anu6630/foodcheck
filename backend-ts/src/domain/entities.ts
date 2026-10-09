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

export interface ScanAgentResult {
  product_name?: string | null;
  brand?: string | null;
  country_hint?: string | null;
  ingredients_text?: string | null;
  candidates: Array<[Product, number]>;
}

export type Severity = "red" | "amber" | "green" | "gray";

export interface AnnotatedIngredient {
  id: string | null;
  name: string;
  category: string | null;
  e_number: string | null;
  codes?: IngredientCode[];
  severity: Severity;
  matched_bans: Array<{
    country_code: string;
    status: string;
    condition: string | null;
    regulation_ref: string | null;
  }>;
  order?: number;
}
