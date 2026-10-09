import { query } from "./db.js";
import type { DataSource, DataSourceFile } from "../domain/dataSource.js";

export type SourceFileKind = DataSourceFile["kind"];

export interface SourceFileInput {
  sourceId: string;
  snapshotId?: number | null;
  kind: SourceFileKind;
  filename: string;
  contentType: string;
  bytes: number;
  sha256: string;
  r2Key: string;
  collectedFromUrl?: string | null;
}

function num(v: unknown): number | null {
  if (v == null) return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

function mapFile(row: Record<string, unknown>): DataSourceFile {
  const kind = row.kind === "parsed" || row.kind === "historical" ? row.kind : "original";
  const ts = (v: unknown) =>
    !v ? "" : typeof v === "string" ? v : v instanceof Date ? v.toISOString() : String(v);
  return {
    id: Number(row.id),
    source_id: String(row.source_id),
    kind,
    filename: String(row.filename),
    content_type: (row.content_type as string) || null,
    bytes: num(row.bytes),
    sha256: String(row.sha256),
    collected_from_url: (row.collected_from_url as string) || null,
    archived_at: ts(row.archived_at),
  };
}

export async function listFilesBySourceIds(sourceIds: string[]): Promise<Map<string, DataSourceFile[]>> {
  const out = new Map<string, DataSourceFile[]>();
  if (!sourceIds.length) return out;
  const { rows } = await query<Record<string, unknown>>(
    `SELECT id, source_id, kind, filename, content_type, bytes, sha256, collected_from_url, archived_at
     FROM data_source_files
     WHERE source_id = ANY($1)
     ORDER BY CASE kind WHEN 'original' THEN 0 WHEN 'parsed' THEN 1 ELSE 2 END, id`,
    [sourceIds]
  );
  for (const row of rows) {
    const file = mapFile(row);
    const list = out.get(file.source_id) ?? [];
    list.push(file);
    out.set(file.source_id, list);
  }
  return out;
}

export async function attachFiles(sources: DataSource[]): Promise<DataSource[]> {
  const files = await listFilesBySourceIds(sources.map((s) => s.id));
  return sources.map((s) => ({ ...s, files: files.get(s.id) ?? [] }));
}

export function attachDownloadUrls(sources: DataSource[], origin: string): DataSource[] {
  const base = origin.replace(/\/$/, "");
  return sources.map((s) => {
    const { r2_key: _r2, ...rest } = s;
    return {
      ...rest,
      r2_key: null,
      files: (s.files ?? []).map((f) => ({
        ...f,
        download_url: `${base}/api/sources/${encodeURIComponent(s.id)}/files/${f.id}`,
      })),
    };
  });
}

export async function latestSnapshotId(sourceId: string): Promise<number | null> {
  const { rows } = await query<{ id: number }>(
    `SELECT id FROM data_source_snapshots WHERE source_id = $1 ORDER BY ingested_at DESC, id DESC LIMIT 1`,
    [sourceId]
  );
  return rows[0]?.id ?? null;
}

export async function upsertSourceFile(input: SourceFileInput): Promise<DataSourceFile> {
  const { rows } = await query<Record<string, unknown>>(
    `INSERT INTO data_source_files
       (source_id, snapshot_id, kind, filename, content_type, bytes, sha256, r2_key, collected_from_url)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
     ON CONFLICT (source_id, sha256) DO UPDATE SET
       snapshot_id = COALESCE(EXCLUDED.snapshot_id, data_source_files.snapshot_id),
       kind = EXCLUDED.kind,
       filename = EXCLUDED.filename,
       content_type = EXCLUDED.content_type,
       bytes = EXCLUDED.bytes,
       r2_key = EXCLUDED.r2_key,
       collected_from_url = EXCLUDED.collected_from_url,
       archived_at = NOW()
     RETURNING id, source_id, kind, filename, content_type, bytes, sha256, collected_from_url, archived_at`,
    [
      input.sourceId,
      input.snapshotId ?? null,
      input.kind,
      input.filename,
      input.contentType,
      input.bytes,
      input.sha256,
      input.r2Key,
      input.collectedFromUrl ?? null,
    ]
  );
  return mapFile(rows[0]!);
}

export async function setSourceArchivePointer(
  sourceId: string,
  pointer: { r2Key: string; sha256: string; bytes: number; contentType: string }
): Promise<void> {
  await query(
    `UPDATE data_sources
     SET r2_key = $2, file_sha256 = $3, file_bytes = $4, content_type = $5
     WHERE id = $1`,
    [sourceId, pointer.r2Key, pointer.sha256, pointer.bytes, pointer.contentType]
  );
  await query(
    `UPDATE data_source_snapshots SET
       r2_key = $2, file_sha256 = $3, file_bytes = $4, content_type = $5
     WHERE id = (
       SELECT id FROM data_source_snapshots WHERE source_id = $1
       ORDER BY ingested_at DESC, id DESC LIMIT 1
     )`,
    [sourceId, pointer.r2Key, pointer.sha256, pointer.bytes, pointer.contentType]
  );
}

export async function getSourceFile(
  sourceId: string,
  fileId: number
): Promise<{ file: DataSourceFile; r2Key: string } | null> {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT id, source_id, kind, filename, content_type, bytes, sha256, r2_key, collected_from_url, archived_at
     FROM data_source_files WHERE source_id = $1 AND id = $2`,
    [sourceId, fileId]
  );
  const row = rows[0];
  if (!row) return null;
  return { file: mapFile(row), r2Key: String(row.r2_key) };
}
