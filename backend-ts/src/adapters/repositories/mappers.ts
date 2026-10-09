import type {
  Ingredient,
  IngredientBan,
  IngredientCode,
  IngredientName,
  IngredientUseLimit,
  Product,
  ProductIngredient,
} from "../../domain/entities.js";
import { displayCode } from "../../domain/codes.js";
import { resolveFoodCategoryName } from "../../domain/fssaiCategories.js";

export interface IngredientRow {
  id: string;
  name: string;
  category: string | null;
  e_number: string | null;
  inci_name: string | null;
  cas_number: string | null;
}

export interface NameRow {
  ingredient_id: string;
  lang: string;
  name: string;
}

export interface CodeRow {
  ingredient_id: string;
  system: string;
  code: string;
}

export interface BanRow {
  ingredient_id: string;
  country_code: string;
  status: string;
  condition: string | null;
  regulation_ref: string | null;
  effective_date: string | Date | null;
}

export interface UseLimitRow {
  ingredient_id: string;
  country_code: string;
  food_category_code: string;
  food_category_name: string | null;
  max_level: string;
  note: string | null;
  regulation_ref: string | null;
}

export interface ProductRow {
  id: number;
  name: string;
  brand: string | null;
  barcode: string;
  category: string | null;
  source: string;
  published: boolean;
}

export interface ProductIngredientRow {
  product_id: number;
  ingredient_id: string;
  order: number;
}

function mapBan(row: BanRow): IngredientBan {
  return {
    country_code: row.country_code,
    status: row.status,
    condition: row.condition,
    regulation_ref: row.regulation_ref,
    effective_date: row.effective_date
      ? typeof row.effective_date === "string"
        ? row.effective_date
        : row.effective_date.toISOString().slice(0, 10)
      : null,
  };
}

function mapName(row: NameRow): IngredientName {
  return { lang: row.lang, name: row.name };
}

export function mapCode(row: CodeRow): IngredientCode {
  return {
    system: row.system,
    code: row.code,
    display: displayCode(row.system, row.code),
  };
}

export function mapUseLimit(row: UseLimitRow): IngredientUseLimit {
  return {
    country_code: row.country_code,
    food_category_code: row.food_category_code,
    food_category_name: resolveFoodCategoryName(
      row.food_category_code,
      row.food_category_name
    ),
    max_level: row.max_level,
    note: row.note,
    regulation_ref: row.regulation_ref,
  };
}

export function mapIngredient(
  row: IngredientRow,
  names: NameRow[] = [],
  bans: BanRow[] = [],
  codes: CodeRow[] = []
): Ingredient {
  const mappedCodes = codes.map(mapCode);
  return {
    id: row.id,
    name: row.name,
    category: row.category,
    e_number: row.e_number,
    inci_name: row.inci_name,
    cas_number: row.cas_number,
    codes: mappedCodes,
    names: names.map(mapName),
    bans: bans.map(mapBan),
  };
}

export function mapProduct(
  row: ProductRow,
  ingredients: ProductIngredient[]
): Product {
  return {
    id: row.id,
    name: row.name,
    brand: row.brand,
    barcode: row.barcode,
    category: row.category,
    source: row.source,
    ingredients,
  };
}
