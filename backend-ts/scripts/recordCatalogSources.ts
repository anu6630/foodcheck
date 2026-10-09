/**
 * Register every captured dataset in data_sources (+ a snapshot row)
 * and stamp source_id on existing ban/limit rows.
 */
import "dotenv/config";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import { catalogSourceById, loadCatalogSources, retrievedAtFor } from "./loadCatalogSources.js";
import {
  FAI_SOURCE_BY_COUNTRY,
  FSSAI_APPENDIX_SOURCE_ID,
  FSSAI_BROMATE_SOURCE_ID,
  FSSAI_CYCLAMATE_SOURCE_ID,
} from "../src/domain/dataSource.js";

async function main() {
  await ensureSchema();
  for (const src of loadCatalogSources()) {
    await recordCapture(src, null, retrievedAtFor(src));
    console.log("recorded", src.id, src.version_date || src.version || src.retrieved_at || "");
  }

  await query(
    `UPDATE ingredient_use_limits SET source_id = $1 WHERE country_code = 'IN' AND source_id IS NULL`,
    [FSSAI_APPENDIX_SOURCE_ID]
  );
  await query(
    `UPDATE ingredient_bans SET source_id = $1
     WHERE country_code = 'IN' AND status <> 'banned' AND source_id IS NULL`,
    [FSSAI_APPENDIX_SOURCE_ID]
  );
  await query(
    `UPDATE ingredient_bans SET source_id = $1
     WHERE country_code = 'IN' AND ingredient_id = 'potassium-bromate'`,
    [FSSAI_BROMATE_SOURCE_ID]
  );
  await query(
    `UPDATE ingredient_bans SET source_id = $1
     WHERE country_code = 'IN' AND status = 'banned'
       AND ingredient_id <> 'potassium-bromate'
       AND (source_id IS NULL OR source_id = $2)`,
    [FSSAI_CYCLAMATE_SOURCE_ID, FSSAI_APPENDIX_SOURCE_ID]
  );

  for (const [cc, sourceId] of Object.entries(FAI_SOURCE_BY_COUNTRY)) {
    catalogSourceById(sourceId);
    await query(
      `UPDATE ingredient_use_limits SET source_id = $1 WHERE country_code = $2 AND source_id IS NULL`,
      [sourceId, cc]
    );
    await query(
      `UPDATE ingredient_bans SET source_id = $1 WHERE country_code = $2 AND source_id IS NULL`,
      [sourceId, cc]
    );
  }

  const srcs = await query<{ n: string }>(`SELECT COUNT(*)::text AS n FROM data_sources`);
  const snaps = await query<{ n: string }>(`SELECT COUNT(*)::text AS n FROM data_source_snapshots`);
  const linked = await query<{ bans: string; limits: string }>(
    `SELECT
       (SELECT COUNT(*)::text FROM ingredient_bans WHERE source_id IS NOT NULL) AS bans,
       (SELECT COUNT(*)::text FROM ingredient_use_limits WHERE source_id IS NOT NULL) AS limits`
  );
  console.log({ sources: srcs.rows[0]?.n, snapshots: snaps.rows[0]?.n, linked: linked.rows[0] });
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
