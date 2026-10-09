/**
 * Fill ingredient_codes from existing e_number / CAS / INS aliases / additives.csv.
 * Does not assume E === INS: INS is stored only when we have an INS source
 * (CSV, alias, or an India Appendix A match).
 */
import "dotenv/config";
import { readFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "csv-parse/sync";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { normalizeCodeValue } from "../src/domain/codes.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

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

async function insertCodes(rows: Array<{ id: string; system: string; code: string }>) {
  const seen = new Set<string>();
  const unique = [];
  for (const r of rows) {
    const code = normalizeCodeValue(r.code);
    if (!r.id || !code) continue;
    const key = `${r.id}|${r.system}|${code}`;
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push({ id: r.id, system: r.system.toUpperCase(), code });
  }
  const chunk = 200;
  let n = 0;
  for (let i = 0; i < unique.length; i += chunk) {
    const part = unique.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 3;
      values.push(row.id, row.system, row.code);
      return `($${o + 1},$${o + 2},$${o + 3})`;
    });
    await query(
      `INSERT INTO ingredient_codes (ingredient_id, system, code)
       VALUES ${placeholders.join(",")}
       ON CONFLICT DO NOTHING`,
      values
    );
    n += part.length;
  }
  return n;
}

async function main() {
  await ensureSchema();
  const pending: Array<{ id: string; system: string; code: string }> = [];

  const { rows: ingredients } = await query<{
    id: string;
    e_number: string | null;
    cas_number: string | null;
  }>(`SELECT id, e_number, cas_number FROM ingredients`);
  const idSet = new Set(ingredients.map((r) => r.id));

  for (const r of ingredients) {
    if (r.e_number) pending.push({ id: r.id, system: "E", code: r.e_number });
    if (r.cas_number) pending.push({ id: r.id, system: "CAS", code: r.cas_number });
  }

  const csvPath = join(ROOT, "data/additives.csv");
  if (existsSync(csvPath)) {
    const rows = parse(readFileSync(csvPath, "utf8"), {
      columns: true,
      skip_empty_lines: true,
      relax_quotes: true,
    }) as Array<Record<string, string>>;
    for (const row of rows) {
      const id = slugify(row.slug || row.primary_name);
      if (!idSet.has(id)) continue;
      if (row.e_number) pending.push({ id, system: "E", code: row.e_number });
      if (row.ins_number) pending.push({ id, system: "INS", code: row.ins_number });
    }
  }

  const { rows: insNames } = await query<{ ingredient_id: string; name: string }>(
    `SELECT ingredient_id, name FROM ingredient_names WHERE LOWER(name) LIKE 'ins%'`
  );
  for (const n of insNames) {
    pending.push({ id: n.ingredient_id, system: "INS", code: n.name });
  }

  // India Appendix A matches prove we resolved this substance via INS digits.
  const { rows: inHits } = await query<{ id: string; e_number: string }>(
    `SELECT DISTINCT i.id, i.e_number
     FROM ingredients i
     JOIN ingredient_bans b ON b.ingredient_id = i.id AND b.country_code = 'IN'
     WHERE i.e_number IS NOT NULL`
  );
  for (const r of inHits) {
    pending.push({ id: r.id, system: "INS", code: r.e_number });
  }

  const queued = await insertCodes(pending);
  const { rows: counts } = await query<{ system: string; n: string }>(
    `SELECT system, COUNT(*)::text AS n FROM ingredient_codes GROUP BY system ORDER BY system`
  );
  console.log("queued", queued, "by system", counts);
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
