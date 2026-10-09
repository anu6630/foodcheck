/**
 * Ingest public additive + ingredient catalogs into Neon.
 *
 * Sources (downloaded into ./data):
 * - foodadditiveindex.com additives.csv + permissions-*.csv
 * - Open Food Facts taxonomies (additives.txt, food/ingredients.txt)
 *
 * Ban mapping:
 * - prohibited → banned
 * - permitted_with_conditions → permitted (caps stay in condition / use_limits)
 * - permitted → permitted
 * - E-numbered additives missing from EU/GB authorised lists → banned
 *   (those lists are exhaustive Union/GB authorised additives)
 */
import "dotenv/config";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "csv-parse/sync";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { normalizeCodeValue } from "../src/domain/codes.js";
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

const EXHAUSTIVE_AUTHORISED = new Set(["EU", "GB"]);

const NAME_LANGS = new Set([
  "en", "hi", "ta", "te", "kn", "ml", "mr", "pa", "gu", "bn", "ur",
  "fr", "de", "es", "it", "nl", "pt", "ar", "zh", "ja", "ko",
  "ru", "pl", "tr", "th", "vi", "id", "ms", "sv", "fi", "el", "he",
  "uk", "cs", "hu", "ro", "fa", "da", "no", "nb", "hr", "sk", "sl",
  "bg", "sr", "lt", "lv", "et", "ca", "tl", "ne", "si", "my",
]);

type Rank = 0 | 1 | 2;
const STATUS_RANK: Record<string, Rank> = {
  banned: 0,
  prohibited: 0,
  restricted: 1,
  permitted_with_conditions: 2,
  permitted: 2,
};

function mapStatus(raw: string): "banned" | "restricted" | "permitted" {
  const r = (raw || "").toLowerCase();
  if (r === "banned" || r === "prohibited") return "banned";
  // Explicit "restricted" (rare) stays amber. A legal max is still authorised.
  if (r === "restricted") return "restricted";
  return "permitted";
}

function slugify(name: string): string {
  const s = name
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[^\w\s-]/g, "")
    .trim()
    .replace(/[\s_]+/g, "-")
    .replace(/-+/g, "-")
    .slice(0, 150);
  return s || "unknown";
}

function clip(s: string | null | undefined, n: number): string | null {
  if (!s) return null;
  const t = s.trim();
  return t.length <= n ? t : t.slice(0, n - 1) + "…";
}

async function upsertIngredients(
  rows: Array<{
    id: string;
    name: string;
    category?: string | null;
    e_number?: string | null;
    inci_name?: string | null;
    cas_number?: string | null;
  }>
) {
  const unique = new Map<string, (typeof rows)[0]>();
  for (const r of rows) {
    if (!r.id || !r.name) continue;
    if (!unique.has(r.id)) unique.set(r.id, r);
  }
  const list = [...unique.values()];
  const chunk = 150;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 6;
      values.push(
        row.id,
        clip(row.name, 255),
        clip(row.category, 100),
        clip(row.e_number, 20),
        clip(row.inci_name, 255),
        clip(row.cas_number, 50)
      );
      return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6})`;
    });
    await query(
      `INSERT INTO ingredients (id, name, category, e_number, inci_name, cas_number)
       VALUES ${placeholders.join(",")}
       ON CONFLICT (id) DO UPDATE SET
         name = COALESCE(EXCLUDED.name, ingredients.name),
         category = COALESCE(EXCLUDED.category, ingredients.category),
         e_number = COALESCE(EXCLUDED.e_number, ingredients.e_number),
         inci_name = COALESCE(EXCLUDED.inci_name, ingredients.inci_name),
         cas_number = COALESCE(EXCLUDED.cas_number, ingredients.cas_number)`,
      values
    );
  }
  return list.length;
}

async function upsertNames(rows: Array<{ ingredient_id: string; lang: string; name: string }>) {
  const unique = new Map<string, (typeof rows)[0]>();
  for (const r of rows) {
    const name = r.name.trim();
    if (!r.ingredient_id || !name || name.length < 2) continue;
    const key = `${r.ingredient_id}|${r.lang}|${name.toLowerCase()}`;
    if (!unique.has(key)) unique.set(key, { ...r, name: clip(name, 255)! });
  }
  const list = [...unique.values()];
  const chunk = 200;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 3;
      values.push(row.ingredient_id, row.lang.slice(0, 5), row.name);
      return `($${o + 1},$${o + 2},$${o + 3})`;
    });
    await query(
      `INSERT INTO ingredient_names (ingredient_id, lang, name)
       VALUES ${placeholders.join(",")}
       ON CONFLICT DO NOTHING`,
      values
    );
  }
  return list.length;
}

async function upsertCodes(rows: Array<{ ingredient_id: string; system: string; code: string }>) {
  const unique = new Map<string, (typeof rows)[0]>();
  for (const r of rows) {
    const code = normalizeCodeValue(r.code);
    if (!r.ingredient_id || !code) continue;
    const key = `${r.ingredient_id}|${r.system}|${code}`;
    if (!unique.has(key)) unique.set(key, { ...r, code, system: r.system.toUpperCase() });
  }
  const list = [...unique.values()];
  const chunk = 200;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 3;
      values.push(row.ingredient_id, row.system, row.code);
      return `($${o + 1},$${o + 2},$${o + 3})`;
    });
    await query(
      `INSERT INTO ingredient_codes (ingredient_id, system, code)
       VALUES ${placeholders.join(",")}
       ON CONFLICT DO NOTHING`,
      values
    );
  }
}

type BanAgg = {
  ingredient_id: string;
  country_code: string;
  status: "banned" | "restricted" | "permitted";
  condition: string | null;
  regulation_ref: string | null;
  source_id: string | null;
};

async function upsertBans(rows: BanAgg[]) {
  const unique = new Map<string, BanAgg>();
  for (const r of rows) {
    const key = `${r.ingredient_id}|${r.country_code}`;
    const prev = unique.get(key);
    if (!prev) {
      unique.set(key, r);
      continue;
    }
    const prevRank = STATUS_RANK[prev.status] ?? 2;
    const nextRank = STATUS_RANK[r.status] ?? 2;
    if (nextRank < prevRank) unique.set(key, r);
  }
  const list = [...unique.values()];
  const chunk = 150;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 6;
      values.push(
        row.ingredient_id,
        row.country_code,
        row.status,
        row.condition,
        clip(row.regulation_ref, 255),
        row.source_id
      );
      return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6})`;
    });
    await query(
      `INSERT INTO ingredient_bans
         (ingredient_id, country_code, status, condition, regulation_ref, source_id)
       VALUES ${placeholders.join(",")}
       ON CONFLICT (ingredient_id, country_code) DO UPDATE SET
         status = EXCLUDED.status,
         condition = EXCLUDED.condition,
         regulation_ref = COALESCE(EXCLUDED.regulation_ref, ingredient_bans.regulation_ref),
         source_id = COALESCE(EXCLUDED.source_id, ingredient_bans.source_id)`,
      values
    );
  }
  return list.length;
}

type LimitAgg = {
  ingredient_id: string;
  country_code: string;
  food_category_code: string;
  food_category_name: string | null;
  max_level: string;
  note: string | null;
  regulation_ref: string | null;
  source_id: string | null;
};

async function upsertUseLimits(rows: LimitAgg[]) {
  const byCountry = new Map<string, LimitAgg[]>();
  for (const r of rows) {
    if (!r.ingredient_id || !r.country_code) continue;
    const list = byCountry.get(r.country_code) ?? [];
    list.push(r);
    byCountry.set(r.country_code, list);
  }
  let written = 0;
  for (const [country, list] of byCountry) {
    await query(`DELETE FROM ingredient_use_limits WHERE country_code = $1`, [country]);
    const unique = new Map<string, LimitAgg>();
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
    written += vals.length;
    console.log(`Use limits ${country}: ${vals.length}`);
  }
  return written;
}

function parseOffTaxonomy(text: string): Array<{
  enNames: string[];
  namesByLang: Record<string, string[]>;
  eNumber: string | null;
  category: string | null;
}> {
  const out: ReturnType<typeof parseOffTaxonomy> = [];
  const blocks = text.split("\n\n");
  for (const raw of blocks) {
    const block = raw.trim();
    if (!block || block.startsWith("synonyms") || block.startsWith("stopwords")) {
      continue;
    }
    const lines = block
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l.length > 0 && !l.startsWith("#"));
    if (!lines.length) continue;
    const namesByLang: Record<string, string[]> = {};
    let category: string | null = null;
    for (const line of lines) {
      if (line.startsWith("<") || line.startsWith("wikidata:") || line.startsWith("carbon_footprint")) continue;
      if (line.startsWith("additives_classes:en:")) {
        const cls = line.split(":").pop()?.replace(/^en:/, "").trim();
        if (cls) category = cls.replace(/-/g, " ");
        continue;
      }
      const m = line.match(/^([a-z]{2,3}):\s*(.+)$/i);
      if (!m) continue;
      const lang = m[1].toLowerCase();
      if (!NAME_LANGS.has(lang)) continue;
      const names = m[2].split(",").map((s) => s.trim()).filter((s) => s.length > 1);
      if (!names.length) continue;
      namesByLang[lang] = [...(namesByLang[lang] || []), ...names];
    }
    const enNames = namesByLang.en?.length
      ? namesByLang.en
      : (() => {
          for (const names of Object.values(namesByLang)) {
            const latin = names.find((n) => /[a-z]/i.test(n));
            if (latin) return [latin];
          }
          const first = Object.values(namesByLang)[0];
          return first?.length ? [first[0]] : [];
        })();
    if (!enNames.length) continue;
    let eNumber: string | null = null;
    for (const n of enNames) {
      const em = n.match(/^E\s?(\d{3,4}[a-z]?)$/i);
      if (em) {
        eNumber = `E${em[1].toUpperCase()}`;
        break;
      }
    }
    out.push({ enNames, namesByLang, eNumber, category });
  }
  return out;
}

async function main() {
  console.log("Ingesting ingredient catalog…");
  await ensureSchema();

  const additivesPath = join(DATA, "additives.csv");
  const additivesCsv = readFileSync(additivesPath, "utf8");
  const additiveRows = parse(additivesCsv, {
    columns: true,
    skip_empty_lines: true,
    relax_quotes: true,
  }) as Array<Record<string, string>>;

  const additiveIds = new Set<string>();
  const csvAdditiveIds = new Set<string>();
  const eNumberToId = new Map<string, string>();
  const ingredients = additiveRows.map((row) => {
    const id = slugify(row.slug || row.primary_name);
    additiveIds.add(id);
    csvAdditiveIds.add(id);
    const e = (row.e_number || "").trim() || null;
    if (e) eNumberToId.set(e.toUpperCase().replace(/\s+/g, ""), id);
    return {
      id,
      name: row.primary_name || row.slug,
      category: clip(row.functional_class, 100) || "additive",
      e_number: e,
      cas_number: clip(row.cas_number, 50),
      ins_number: (row.ins_number || "").trim() || null,
    };
  });
  console.log(`Additives CSV: ${ingredients.length}`);
  await upsertIngredients(ingredients);
  await upsertCodes(
    ingredients.flatMap((row) => {
      const out: Array<{ ingredient_id: string; system: string; code: string }> = [];
      if (row.e_number) out.push({ ingredient_id: row.id, system: "E", code: row.e_number });
      if (row.ins_number) out.push({ ingredient_id: row.id, system: "INS", code: row.ins_number });
      if (row.cas_number) out.push({ ingredient_id: row.id, system: "CAS", code: row.cas_number });
      return out;
    })
  );

  const additiveNames: Array<{ ingredient_id: string; lang: string; name: string }> = [];
  for (const row of ingredients) {
    additiveNames.push({ ingredient_id: row.id, lang: "en", name: row.name });
    if (row.e_number) additiveNames.push({ ingredient_id: row.id, lang: "en", name: row.e_number });
    if (row.ins_number) {
      const ins = row.ins_number.replace(/^INS\s*/i, "");
      additiveNames.push({ ingredient_id: row.id, lang: "en", name: `INS ${ins}` });
      additiveNames.push({ ingredient_id: row.id, lang: "en", name: `INS${ins}` });
      if (!row.e_number) {
        additiveNames.push({ ingredient_id: row.id, lang: "en", name: `E${ins}` });
      }
    }
  }

  const offAdditives = parseOffTaxonomy(readFileSync(join(DATA, "off-additives.txt"), "utf8"));
  const extraOffAdditives: typeof ingredients = [];
  for (const block of offAdditives) {
    let id = block.eNumber ? eNumberToId.get(block.eNumber) : undefined;
    if (!id) {
      const primary = block.enNames.find((n) => !/^E\s?\d/i.test(n)) || block.enNames[0];
      id = slugify(primary);
      if (block.eNumber && !additiveIds.has(id)) {
        extraOffAdditives.push({
          id,
          name: primary,
          category: block.category || "additive",
          e_number: block.eNumber,
          cas_number: null,
          ins_number: block.eNumber.replace(/^E/i, ""),
        });
        additiveIds.add(id);
        eNumberToId.set(block.eNumber, id);
        additiveNames.push({ ingredient_id: id, lang: "en", name: `INS ${block.eNumber.replace(/^E/i, "")}` });
      }
    }
    if (!id || !additiveIds.has(id)) continue;
    for (const [lang, names] of Object.entries(block.namesByLang)) {
      for (const n of names) additiveNames.push({ ingredient_id: id, lang, name: n });
    }
  }
  if (extraOffAdditives.length) {
    console.log(`OFF additives not in CSV: ${extraOffAdditives.length}`);
    await upsertIngredients(extraOffAdditives);
  }
  console.log(`Additive synonyms queued: ${additiveNames.length}`);
  await upsertNames(additiveNames);

  const foodIng = parseOffTaxonomy(readFileSync(join(DATA, "off-ingredients.txt"), "utf8"));
  const foodRows: typeof ingredients = [];
  const foodNames: typeof additiveNames = [];
  for (const block of foodIng) {
    const primary = block.enNames.find((n) => !/^E\s?\d/i.test(n)) || block.enNames[0];
    const id = block.eNumber && eNumberToId.has(block.eNumber)
      ? eNumberToId.get(block.eNumber)!
      : slugify(primary);
    if (!additiveIds.has(id)) {
      foodRows.push({
        id,
        name: primary,
        category: block.category || "ingredient",
        e_number: block.eNumber,
        cas_number: null,
        ins_number: null,
      });
    }
    for (const [lang, names] of Object.entries(block.namesByLang)) {
      for (const n of names) foodNames.push({ ingredient_id: id, lang, name: n });
    }
  }
  console.log(`OFF food ingredients: ${foodRows.length}`);
  await upsertIngredients(foodRows);
  await upsertNames(foodNames);

  const { rows: existing } = await query<{ id: string; e_number: string | null }>(
    `SELECT id, e_number FROM ingredients`
  );
  const idSet = new Set(existing.map((r) => r.id));
  const eToId = new Map<string, string>();
  for (const r of existing) {
    if (r.e_number) eToId.set(r.e_number.toUpperCase(), r.id);
  }

  const bans: BanAgg[] = [];
  const limits: LimitAgg[] = [];
  const authorised = new Map<string, Set<string>>(); // country -> ingredient ids seen

  for (const [fileKey, countries] of Object.entries(JURISDICTION_TO_COUNTRY)) {
    const path = join(DATA, `permissions-${fileKey}.csv`);
    const rows = parse(readFileSync(path, "utf8"), {
      columns: true,
      skip_empty_lines: true,
      relax_quotes: true,
    }) as Array<Record<string, string>>;

    for (const country of countries) {
      const seen = authorised.get(country) ?? new Set<string>();
      for (const row of rows) {
        const slug = slugify(row.additive_slug || "");
        if (!slug || !idSet.has(slug)) continue;
        seen.add(slug);
        const condParts = [
          row.food_category_name,
          row.max_level ? `max ${row.max_level}${row.max_level_unit ? " " + row.max_level_unit : ""}` : "",
          row.conditions,
        ]
          .map((s) => (s || "").trim())
          .filter(Boolean);
        const sourceId = FAI_SOURCE_BY_COUNTRY[country] ?? null;
        bans.push({
          ingredient_id: slug,
          country_code: country,
          status: mapStatus(row.status),
          condition: clip(condParts.join(" — "), 2000),
          regulation_ref: clip(row.citation_ref, 255),
          source_id: sourceId,
        });
        const catCode = clip(row.food_category_code, 64) || "";
        const maxLevel =
          [row.max_level, row.max_level_unit].filter(Boolean).join(" ").trim() ||
          (/quantum satis/i.test(row.conditions || "") ? "quantum satis" : "authorised");
        limits.push({
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
          source_id: sourceId,
        });
      }
      authorised.set(country, seen);
      console.log(`Permissions ${fileKey} → ${country}: ${rows.length} rows, ${seen.size} matched additives`);
    }
  }

  for (const country of EXHAUSTIVE_AUTHORISED) {
    const seen = authorised.get(country) ?? new Set();
    let missing = 0;
    for (const row of existing) {
      if (!row.e_number) continue;
      if (seen.has(row.id)) continue;
      if (!csvAdditiveIds.has(row.id)) continue;
      bans.push({
        ingredient_id: row.id,
        country_code: country,
        status: "banned",
        condition: `Not authorised as a food additive (${country} authorised list is exhaustive)`,
        regulation_ref:
          country === "EU"
            ? "Regulation (EC) No 1333/2008 Annex II"
            : "GB assimilated Regulation 1333/2008 Annex II",
        source_id: FAI_SOURCE_BY_COUNTRY[country] ?? null,
      });
      missing++;
    }
    console.log(`${country} E-numbers not on authorised list → banned: ${missing}`);
  }

  const banCount = await upsertBans(bans);
  console.log(`Ban rows upserted: ${banCount}`);
  const limitCount = await upsertUseLimits(limits);
  console.log(`Use-limit rows upserted: ${limitCount}`);

  // Exhaustive-list bans apply only to Food Additive Index additives, not OFF-only E-numbers.
  const csvIds = [...csvAdditiveIds];
  if (csvIds.length) {
    await query(
      `DELETE FROM ingredient_bans
       WHERE country_code = ANY($1)
         AND regulation_ref ILIKE '%1333/2008%'
         AND NOT (ingredient_id = ANY($2))`,
      [["EU", "GB"], csvIds]
    );
  }

  if (!idSet.has("vanaspati")) {
    await upsertIngredients([
      { id: "vanaspati", name: "vanaspati", category: "fat" },
    ]);
    idSet.add("vanaspati");
  }

  const extraAliases = [
    { ingredient_id: "monosodium-l-glutamate", lang: "en", name: "MSG" },
    { ingredient_id: "monosodium-l-glutamate", lang: "en", name: "Ajinomoto" },
    { ingredient_id: "fd-and-c-yellow-no-5", lang: "en", name: "tartrazine" },
    { ingredient_id: "palm-oil", lang: "en", name: "palm oil" },
    { ingredient_id: "palm-oil-and-fat", lang: "en", name: "palm oil" },
    { ingredient_id: "palm-olein", lang: "en", name: "palmolein" },
    { ingredient_id: "chickpea-flour", lang: "en", name: "besan" },
    { ingredient_id: "chickpea-flour", lang: "en", name: "gram flour" },
    { ingredient_id: "vanaspati", lang: "en", name: "vanaspati" },
    { ingredient_id: "vanaspati", lang: "en", name: "hydrogenated vegetable oil" },
  ].filter((row) => idSet.has(row.ingredient_id));
  await upsertNames(extraAliases);

  // Seed leftover shares E102 with the catalog additive; keep the richer record.
  const { rows: e102 } = await query<{ id: string }>(
    `SELECT id FROM ingredients WHERE e_number = 'E102' ORDER BY id`
  );
  if (e102.some((r) => r.id === "tartrazine") && e102.some((r) => r.id === "fd-and-c-yellow-no-5")) {
    await query(
      `UPDATE product_ingredients SET ingredient_id = 'fd-and-c-yellow-no-5'
       WHERE ingredient_id = 'tartrazine'`
    );
    await query(`DELETE FROM ingredients WHERE id = 'tartrazine'`);
    console.log("Merged seed tartrazine into fd-and-c-yellow-no-5");
  }

  const counts = await query<{
    ingredients: string;
    names: string;
    bans: string;
    products: string;
  }>(
    `SELECT
       (SELECT COUNT(*)::text FROM ingredients) AS ingredients,
       (SELECT COUNT(*)::text FROM ingredient_names) AS names,
       (SELECT COUNT(*)::text FROM ingredient_bans) AS bans,
       (SELECT COUNT(*)::text FROM products) AS products`
  );
  console.log("Final counts", counts.rows[0]);
  const seenSources = new Set(Object.values(FAI_SOURCE_BY_COUNTRY));
  seenSources.add("off-taxonomies");
  seenSources.add("fai-additives");
  for (const id of seenSources) {
    const src = catalogSourceById(id);
    await recordCapture(src, counts.rows[0] as unknown as Record<string, unknown>, retrievedAtFor(src));
  }
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
