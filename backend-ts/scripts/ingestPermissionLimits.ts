/**
 * Per-food caps from Food Additive Index permission CSVs (EU, GB, US, AU/NZ, CA, KR, JP).
 * India Appendix A is owned by ingestFssai.ts and is not overwritten here.
 */
import "dotenv/config";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "csv-parse/sync";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { resolveFoodCategoryName } from "../src/domain/fssaiCategories.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import { catalogSourceById, retrievedAtFor } from "./loadCatalogSources.js";
import { FAI_SOURCE_BY_COUNTRY } from "../src/domain/dataSource.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const DATA = join(ROOT, "data");

const JURISDICTION_TO_COUNTRY: Record<string, string[]> = {
  eu: ["EU"],
  gb: ["GB"],
  anz: ["AU", "NZ"],
  ca: ["CA"],
  us: ["US"],
  kr: ["KR"],
  jp: ["JP"],
};

function clip(s: string | null | undefined, n: number): string | null {
  if (!s) return null;
  const t = s.trim();
  return t.length <= n ? t : t.slice(0, n - 1) + "…";
}

function slugify(name: string): string {
  return (
    name
      .toLowerCase()
      .normalize("NFKD")
      .replace(/[^\w\s-]/g, "")
      .trim()
      .replace(/[\s_]+/g, "-")
      .replace(/-+/g, "-")
      .slice(0, 150) || "unknown"
  );
}

type LimitRow = {
  ingredient_id: string;
  country_code: string;
  food_category_code: string;
  food_category_name: string | null;
  max_level: string;
  note: string | null;
  regulation_ref: string | null;
  source_id: string;
};

async function main() {
  console.log("Ingesting per-category limits for EU/GB/US/AU/NZ/CA/KR/JP…");
  await ensureSchema();
  const { rows: existing } = await query<{ id: string }>(`SELECT id FROM ingredients`);
  const idSet = new Set(existing.map((r) => r.id));

  const byCountry = new Map<string, LimitRow[]>();

  for (const [fileKey, countries] of Object.entries(JURISDICTION_TO_COUNTRY)) {
    const path = join(DATA, `permissions-${fileKey}.csv`);
    const rows = parse(readFileSync(path, "utf8"), {
      columns: true,
      skip_empty_lines: true,
      relax_quotes: true,
    }) as Array<Record<string, string>>;

    for (const country of countries) {
      const list = byCountry.get(country) ?? [];
      for (const row of rows) {
        const slug = slugify(row.additive_slug || "");
        if (!slug || !idSet.has(slug)) continue;
        const catCode = clip(row.food_category_code, 64) || "";
        const maxLevel =
          [row.max_level, row.max_level_unit].filter(Boolean).join(" ").trim() ||
          (/quantum satis/i.test(row.conditions || "") ? "quantum satis" : "authorised");
        list.push({
          ingredient_id: slug,
          country_code: country,
          food_category_code: catCode,
          food_category_name: clip(
            resolveFoodCategoryName(catCode, row.food_category_name),
            255
          ),
          max_level: clip(maxLevel, 80) || "authorised",
          note: clip(row.conditions, 500),
          regulation_ref: clip(row.citation_ref, 255),
          source_id: FAI_SOURCE_BY_COUNTRY[country] || `fai-${country.toLowerCase()}`,
        });
      }
      byCountry.set(country, list);
    }
  }

  for (const [country, list] of byCountry) {
    await query(`DELETE FROM ingredient_use_limits WHERE country_code = $1`, [country]);
    const unique = new Map<string, LimitRow>();
    for (const r of list) {
      unique.set(`${r.ingredient_id}|${r.food_category_code}|${r.max_level}`, r);
    }
    const vals = [...unique.values()];
    const chunk = 50;
    for (let i = 0; i < vals.length; i += chunk) {
      const part = vals.slice(i, i + chunk);
      const values: unknown[] = [];
      const placeholders = part.map((row, idx) => {
        const o = idx * 8;
        values.push(
          row.ingredient_id,
          row.country_code,
          row.food_category_code,
          row.food_category_name,
          row.max_level,
          row.note,
          row.regulation_ref,
          row.source_id
        );
        return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6},$${o + 7},$${o + 8})`;
      });
      await query(
        `INSERT INTO ingredient_use_limits
           (ingredient_id, country_code, food_category_code, food_category_name,
            max_level, note, regulation_ref, source_id)
         VALUES ${placeholders.join(",")}
         ON CONFLICT (ingredient_id, country_code, food_category_code, max_level) DO NOTHING`,
        values
      );
    }
    console.log(`${country}: ${vals.length} use-limit rows`);
    const sourceId = FAI_SOURCE_BY_COUNTRY[country];
    if (sourceId) {
      const src = catalogSourceById(sourceId);
      await recordCapture(src, { country, rows: vals.length }, retrievedAtFor(src));
    }
  }

  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
