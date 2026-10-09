/**
 * Pack-label names used in India (Hindi/Tamil/… + Latin transliteration) so OCR and search hit.
 */
import "dotenv/config";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import { catalogSourceById, retrievedAtFor } from "./loadCatalogSources.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

type Alias = { lang: string; name: string; match: string[] };

async function resolveId(match: string[]): Promise<string | null> {
  for (const raw of match) {
    const m = raw.trim();
    if (!m) continue;
    const byId = await query<{ id: string }>(`SELECT id FROM ingredients WHERE id = $1`, [m]);
    if (byId.rows[0]) return byId.rows[0].id;
    const byName = await query<{ id: string }>(
      `SELECT id FROM ingredients WHERE LOWER(name) = LOWER($1) LIMIT 1`,
      [m]
    );
    if (byName.rows[0]) return byName.rows[0].id;
    const byAlias = await query<{ ingredient_id: string }>(
      `SELECT ingredient_id FROM ingredient_names WHERE LOWER(name) = LOWER($1) LIMIT 1`,
      [m]
    );
    if (byAlias.rows[0]) return byAlias.rows[0].ingredient_id;
  }
  return null;
}

async function main() {
  await ensureSchema();
  const aliases = JSON.parse(
    readFileSync(join(ROOT, "data/indic-aliases.json"), "utf8")
  ) as Alias[];

  let inserted = 0;
  let unmatched = 0;
  const missing = new Set<string>();
  for (const alias of aliases) {
    const name = alias.name.trim();
    if (!name) continue;
    const id = await resolveId(alias.match);
    if (!id) {
      unmatched += 1;
      missing.add(alias.match.join("|"));
      continue;
    }
    const res = await query<{ ingredient_id: string }>(
      `INSERT INTO ingredient_names (ingredient_id, lang, name)
       VALUES ($1, $2, $3)
       ON CONFLICT DO NOTHING
       RETURNING ingredient_id`,
      [id, (alias.lang || "en").slice(0, 5), name.slice(0, 255)]
    );
    inserted += res.rows.length;
  }
  console.log({ attempted: aliases.length, inserted, unmatched, missing: [...missing] });
  const src = catalogSourceById("indic-aliases");
  await recordCapture(src, { attempted: aliases.length, inserted, unmatched }, retrievedAtFor(src));
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
