/**
 * Popular India-sold SKUs from Open Food Facts, linked only to catalog ingredients.
 * Unknown OFF slugs are skipped so the ingredient table stays a regulation catalog.
 */
import "dotenv/config";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { cleanAndSplitIngredients } from "../src/domain/usecases.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import { catalogSourceById, retrievedAtFor } from "./loadCatalogSources.js";

const OFF_UA = "FoodCheckApp/1.0 (ingredient safety; India catalog ingest)";
const PAGE_SIZE = 50;
const PAGES = Math.max(1, Number(process.env.OFF_INDIA_PAGES || 4));

const idCache = new Map<string, string | null>();

async function resolveIngredientId(token: string): Promise<string | null> {
  const cleaned = token.trim().toLowerCase();
  if (cleaned.length < 2) return null;
  if (idCache.has(cleaned)) return idCache.get(cleaned) ?? null;
  const eMatch = cleaned.match(/\be[- ]?(\d{3,4}[a-z]?)\b/i);
  const insMatch = cleaned.match(/\bins[- ]?(\d{3,4}[a-z]?)\b/i);
  const code = (eMatch?.[1] || insMatch?.[1] || "").toUpperCase();
  const eNumber = code ? `E${code}` : null;

  const direct = await query<{ id: string }>(
    `SELECT id FROM ingredients
     WHERE id = $1 OR LOWER(name) = $1 OR ($2::text IS NOT NULL AND e_number = $2)
     LIMIT 1`,
    [cleaned.replace(/\s+/g, "-"), eNumber]
  );
  if (direct.rows[0]) {
    idCache.set(cleaned, direct.rows[0].id);
    return direct.rows[0].id;
  }

  const syn = await query<{ ingredient_id: string }>(
    `SELECT ingredient_id FROM ingredient_names WHERE LOWER(name) = $1 LIMIT 1`,
    [cleaned]
  );
  const id = syn.rows[0]?.ingredient_id ?? null;
  idCache.set(cleaned, id);
  return id;
}

async function fetchPage(page: number): Promise<Array<Record<string, unknown>>> {
  const url = new URL("https://world.openfoodfacts.org/cgi/search.pl");
  url.searchParams.set("action", "process");
  url.searchParams.set("tagtype_0", "countries");
  url.searchParams.set("tag_contains_0", "contains");
  url.searchParams.set("tag_0", "india");
  url.searchParams.set("sort_by", "unique_scans_n");
  url.searchParams.set("page_size", String(PAGE_SIZE));
  url.searchParams.set("page", String(page));
  url.searchParams.set("json", "1");
  let lastErr: Error | null = null;
  for (let attempt = 1; attempt <= 5; attempt++) {
    try {
      const res = await fetch(url, {
        headers: { "User-Agent": OFF_UA },
        signal: AbortSignal.timeout(25_000),
      });
      if (res.status === 429 || res.status >= 500) {
        lastErr = new Error(`OFF HTTP ${res.status}`);
        await new Promise((r) => setTimeout(r, 2000 * attempt));
        continue;
      }
      if (!res.ok) throw new Error(`OFF HTTP ${res.status}`);
      const data = (await res.json()) as { products?: Array<Record<string, unknown>> };
      return data.products ?? [];
    } catch (e) {
      lastErr = e as Error;
      await new Promise((r) => setTimeout(r, 2000 * attempt));
    }
  }
  throw lastErr || new Error("OFF fetch failed");
}

async function upsertProduct(args: {
  name: string;
  brand: string | null;
  barcode: string;
  category: string | null;
  ingredientIds: string[];
}) {
  const existing = await query<{ id: number }>(
    `SELECT id FROM products WHERE barcode = $1`,
    [args.barcode]
  );
  let id: number;
  if (existing.rows[0]) {
    id = existing.rows[0].id;
    await query(
      `UPDATE products SET name=$1, brand=$2, category=$3, source='open_food_facts', published=TRUE WHERE id=$4`,
      [args.name, args.brand, args.category, id]
    );
    await query(`DELETE FROM product_ingredients WHERE product_id = $1`, [id]);
  } else {
    const inserted = await query<{ id: number }>(
      `INSERT INTO products (name, brand, barcode, category, source, published)
       VALUES ($1,$2,$3,$4,'open_food_facts',TRUE) RETURNING id`,
      [args.name, args.brand, args.barcode, args.category]
    );
    id = inserted.rows[0].id;
  }
  for (let i = 0; i < args.ingredientIds.length; i++) {
    await query(
      `INSERT INTO product_ingredients (product_id, ingredient_id, "order")
       VALUES ($1,$2,$3)`,
      [id, args.ingredientIds[i], i]
    );
  }
}

async function main() {
  console.log(`Ingesting Open Food Facts India products (${PAGES} pages)…`);
  await ensureSchema();
  let saved = 0;
  let skipped = 0;
  for (let page = 1; page <= PAGES; page++) {
    const products = await fetchPage(page);
    console.log(`fetched page ${page}: ${products.length} products`);
    for (const prod of products) {
      const barcode = String(prod.code || "").trim();
      const name = String(prod.product_name || prod.product_name_en || prod.product_name_hi || "").trim();
      if (!barcode || barcode.length < 8 || !name) {
        skipped += 1;
        continue;
      }
      const tokens: string[] = [];
      if (Array.isArray(prod.ingredients)) {
        for (const ing of prod.ingredients as Array<Record<string, unknown>>) {
          const text = String(ing.text || ing.id || "").replace(/^.*:/, "").trim();
          if (text) tokens.push(text);
        }
      }
      if (!tokens.length) {
        const text = String(prod.ingredients_text || prod.ingredients_text_en || "");
        tokens.push(...cleanAndSplitIngredients(text));
      }
      const ids: string[] = [];
      const seen = new Set<string>();
      for (const token of tokens) {
        const id = await resolveIngredientId(token);
        if (!id || seen.has(id)) continue;
        seen.add(id);
        ids.push(id);
      }
      let brand = prod.brands ? String(prod.brands).split(",")[0].trim() : "";
      let category = prod.categories ? String(prod.categories).split(",")[0].trim() : "";
      await upsertProduct({
        name: name.slice(0, 255),
        brand: brand ? brand.slice(0, 255) : null,
        barcode: barcode.slice(0, 50),
        category: category ? category.slice(0, 100) : null,
        ingredientIds: ids,
      });
      saved += 1;
    }
    console.log(`page ${page}/${PAGES}: saved ${saved}, skipped ${skipped}`);
    await new Promise((r) => setTimeout(r, 800));
  }
  const count = await query<{ n: string }>(`SELECT COUNT(*)::text AS n FROM products`);
  console.log({ saved, skipped, products: count.rows[0]?.n });
  const src = catalogSourceById("off-india-products");
  await recordCapture(
    src,
    { saved, skipped, products: count.rows[0]?.n, pages: PAGES },
    retrievedAtFor(src)
  );
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
