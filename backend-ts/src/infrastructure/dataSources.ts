import { query } from "./db.js";
import type { CatalogSourceFile, DataSource } from "../domain/dataSource.js";
import { attachFiles } from "./sourceFiles.js";

export async function recordCapture(
  src: CatalogSourceFile,
  stats?: Record<string, unknown> | null,
  retrievedAt?: string
): Promise<void> {
  const retrieved = retrievedAt || src.retrieved_at || new Date().toISOString();
  const statsJson = stats ? JSON.stringify(stats) : null;
  await query(
    `INSERT INTO data_sources
       (id, publisher, country_code, title, version, version_date, citation, citation_url, url, urls,
        file_path, retrieved_at, ingested_at, notes, stats)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,NOW(),$13,$14::jsonb)
     ON CONFLICT (id) DO UPDATE SET
       publisher = EXCLUDED.publisher,
       country_code = EXCLUDED.country_code,
       title = EXCLUDED.title,
       version = EXCLUDED.version,
       version_date = EXCLUDED.version_date,
       citation = EXCLUDED.citation,
       citation_url = EXCLUDED.citation_url,
       url = EXCLUDED.url,
       urls = EXCLUDED.urls,
       file_path = EXCLUDED.file_path,
       retrieved_at = EXCLUDED.retrieved_at,
       ingested_at = NOW(),
       notes = EXCLUDED.notes,
       stats = EXCLUDED.stats`,
    [
      src.id,
      src.publisher,
      src.country_code ?? null,
      src.title,
      src.version ?? null,
      src.version_date ?? null,
      src.citation ?? null,
      src.citation_url ?? null,
      src.url ?? null,
      JSON.stringify(src.urls?.length ? src.urls : src.url ? [src.url] : []),
      src.file_path ?? null,
      retrieved,
      src.notes ?? null,
      statsJson,
    ]
  );
  await query(
    `INSERT INTO data_source_snapshots
       (source_id, publisher, country_code, title, version, version_date, citation, citation_url, url, urls,
        file_path, retrieved_at, notes, stats)
     SELECT id, publisher, country_code, title, version, version_date, citation, citation_url, url, urls,
            file_path, retrieved_at, notes, stats
     FROM data_sources WHERE id = $1`,
    [src.id]
  );
}

function parseUrlList(v: unknown): string[] {
  if (Array.isArray(v)) return v.filter((u): u is string => typeof u === "string" && u.length > 0);
  if (typeof v === "string" && v.trim().startsWith("[")) {
    try {
      const parsed = JSON.parse(v) as unknown;
      return Array.isArray(parsed) ? parsed.filter((u): u is string => typeof u === "string") : [];
    } catch {
      return [];
    }
  }
  return [];
}

function mapSource(row: Record<string, unknown>): DataSource {
  const ts = (v: unknown) =>
    !v ? null : typeof v === "string" ? v : v instanceof Date ? v.toISOString() : String(v);
  const day = (v: unknown) => {
    if (!v) return null;
    if (typeof v === "string") return v.slice(0, 10);
    if (v instanceof Date) return v.toISOString().slice(0, 10);
    return String(v).slice(0, 10);
  };
  return {
    id: String(row.id),
    publisher: String(row.publisher),
    country_code: (row.country_code as string) || null,
    title: String(row.title),
    version: (row.version as string) || null,
    version_date: day(row.version_date),
    citation: (row.citation as string) || null,
    citation_url: (row.citation_url as string) || null,
    url: (row.url as string) || null,
    urls: parseUrlList(row.urls),
    file_path: (row.file_path as string) || null,
    r2_key: (row.r2_key as string) || null,
    file_sha256: (row.file_sha256 as string) || null,
    file_bytes: row.file_bytes == null ? null : Number(row.file_bytes),
    content_type: (row.content_type as string) || null,
    files: [],
    retrieved_at: ts(row.retrieved_at) || "",
    ingested_at: ts(row.ingested_at) || "",
    notes: (row.notes as string) || null,
    stats: (row.stats as Record<string, unknown>) || null,
  };
}

export async function listDataSources(): Promise<DataSource[]> {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT id, publisher, country_code, title, version, version_date, citation, citation_url, url, urls,
            file_path, r2_key, file_sha256, file_bytes, content_type,
            retrieved_at, ingested_at, notes, stats
     FROM data_sources
     ORDER BY country_code NULLS LAST, id`
  );
  return attachFiles(rows.map(mapSource));
}

export async function listSourcesForIngredient(ingredientId: string): Promise<DataSource[]> {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT s.id, s.publisher, s.country_code, s.title, s.version, s.version_date, s.citation,
            s.citation_url, s.url, s.urls, s.file_path, s.r2_key, s.file_sha256, s.file_bytes, s.content_type,
            s.retrieved_at, s.ingested_at, s.notes, s.stats
     FROM data_sources s
     WHERE s.id IN (
       SELECT source_id FROM ingredient_bans
         WHERE ingredient_id = $1 AND source_id IS NOT NULL
       UNION
       SELECT source_id FROM ingredient_use_limits
         WHERE ingredient_id = $1 AND source_id IS NOT NULL
       UNION
       SELECT source_id FROM ingredient_health_reports
         WHERE ingredient_id = $1 AND source_id IS NOT NULL
     )
     ORDER BY CASE s.country_code WHEN 'IN' THEN 0 ELSE 1 END, s.id`,
    [ingredientId]
  );
  return attachFiles(rows.map(mapSource));
}
