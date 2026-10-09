/**
 * One-shot UX pass on existing catalog rows:
 * - destaturate amber "restricted" that was really authorised-with-a-max
 * - rewrite junk Appendix A category names using Codex GSFA titles
 */
import "dotenv/config";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { resolveFoodCategoryName } from "../src/domain/fssaiCategories.js";

async function main() {
  await ensureSchema();

  const destat = await query<{ n: string }>(
    `WITH u AS (
       UPDATE ingredient_bans SET status = 'permitted'
       WHERE status = 'restricted'
       RETURNING 1
     ) SELECT COUNT(*)::text AS n FROM u`
  );
  console.log("restricted → permitted", destat.rows[0]?.n);

  const { rows } = await query<{ food_category_code: string; food_category_name: string | null }>(
    `SELECT DISTINCT food_category_code, food_category_name FROM ingredient_use_limits`
  );

  let updated = 0;
  for (const row of rows) {
    const next = resolveFoodCategoryName(row.food_category_code, row.food_category_name);
    if (!next || next === row.food_category_name) continue;
    const res = await query<{ n: number }>(
      `UPDATE ingredient_use_limits
       SET food_category_name = $1
       WHERE food_category_code = $2
         AND COALESCE(food_category_name, '') IS DISTINCT FROM $1
       RETURNING 1 AS n`,
      [next.slice(0, 255), row.food_category_code]
    );
    updated += res.rows.length;
  }
  console.log("use-limit names rewritten", updated);

  const bans = await query<{ status: string; n: string }>(
    `SELECT status, COUNT(*)::text AS n FROM ingredient_bans GROUP BY status ORDER BY status`
  );
  console.log("ban status counts", bans.rows);
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
