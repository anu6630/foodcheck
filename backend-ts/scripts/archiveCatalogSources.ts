/**
 * Put every processed ingredient source file in R2 (content-addressed) and
 * link those objects to data_sources / existing ban+limit rows via source_id.
 */
import "dotenv/config";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool } from "../src/infrastructure/db.js";
import {
  catalogSourceObjectKey,
  getR2OrNull,
  sha256Hex,
} from "../src/adapters/services/r2Storage.js";
import {
  latestSnapshotId,
  setSourceArchivePointer,
  upsertSourceFile,
  type SourceFileKind,
} from "../src/infrastructure/sourceFiles.js";
import { loadCatalogSources } from "./loadCatalogSources.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

interface Artifact {
  sourceId: string;
  kind: SourceFileKind;
  relPath?: string;
  url?: string;
  filename?: string;
  primary?: boolean;
}

const EXTRA: Artifact[] = [
  {
    sourceId: "fssai-appendix-a",
    kind: "parsed",
    relPath: "data/fssai/permissions-in.json",
    url: "https://fssai.gov.in/upload/uploadfiles/files/Appendix%20A(2).pdf",
  },
  {
    sourceId: "fssai-appendix-a",
    kind: "historical",
    relPath: "data/fssai/appendix-a-2011-original.pdf",
  },
  {
    sourceId: "fssai-appendix-a",
    kind: "historical",
    relPath: "data/fssai/appendix-a-alt.pdf",
  },
  {
    sourceId: "off-taxonomies",
    kind: "original",
    relPath: "data/off-additives.txt",
    url: "https://static.openfoodfacts.org/data/taxonomies/additives.txt",
  },
  {
    sourceId: "fssai-gazette-bromate-2016",
    kind: "original",
    relPath: "data/fssai/Gazette_Notification_Additive_Harmonization_16_09_2016.pdf",
    url: "https://www.fssai.gov.in/upload/uploadfiles/files/Gazette_Notification_Additive_Harmonization_16_09_2016.pdf",
    primary: true,
  },
  {
    sourceId: "fssai-gazette-bromate-2016",
    kind: "historical",
    relPath: "data/fssai/Notice_Foods_Stan_Operationalize_20_06_2016.pdf",
    url: "https://www.pfndai.org/Document/Gazette/FSSAI/Notice_Foods_Stan_Operationalize_20_06_2016_1.pdf",
  },
  {
    sourceId: "fssai-cyclamates-2016",
    kind: "original",
    relPath: "data/fssai/Gazette_Notification_Additive_Harmonization_16_09_2016.pdf",
    url: "https://www.fssai.gov.in/upload/uploadfiles/files/Gazette_Notification_Additive_Harmonization_16_09_2016.pdf",
    primary: true,
  },
];

function contentTypeFor(filename: string): string {
  const lower = filename.toLowerCase();
  if (lower.endsWith(".pdf")) return "application/pdf";
  if (lower.endsWith(".csv")) return "text/csv; charset=utf-8";
  if (lower.endsWith(".json")) return "application/json";
  if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
  return "application/octet-stream";
}

function artifacts(): Artifact[] {
  const fromCatalog: Artifact[] = loadCatalogSources()
    .filter((s) => s.file_path)
    .map((s) => ({
      sourceId: s.id,
      kind: "original" as const,
      relPath: s.file_path!,
      url: s.url ?? undefined,
      primary: true,
    }));
  return [...fromCatalog, ...EXTRA];
}

async function fetchUrl(url: string): Promise<Buffer> {
  const res = await fetch(url, {
    headers: { "user-agent": "FoodCheckCatalogArchive/1.0 (ingredient source provenance)" },
    signal: AbortSignal.timeout(120_000),
  });
  if (!res.ok) throw new Error(`GET ${url} → ${res.status}`);
  return Buffer.from(await res.arrayBuffer());
}

async function loadBytes(art: Artifact): Promise<{ buf: Buffer; filename: string; fromUrl: string | null }> {
  const filename = art.filename || (art.relPath ? art.relPath.split("/").pop()! : "file");
  if (art.relPath) {
    const abs = join(ROOT, art.relPath);
    if (existsSync(abs)) {
      return { buf: readFileSync(abs), filename, fromUrl: art.url ?? null };
    }
  }
  if (!art.url) throw new Error(`Missing file for ${art.sourceId}: ${art.relPath || filename}`);
  const buf = await fetchUrl(art.url);
  if (art.relPath) {
    const abs = join(ROOT, art.relPath);
    mkdirSync(dirname(abs), { recursive: true });
    writeFileSync(abs, buf);
  }
  return { buf, filename, fromUrl: art.url };
}

async function main() {
  await ensureSchema();
  const r2 = getR2OrNull();
  if (!r2) throw new Error("R2 is not configured");

  const primaryBySource = new Map<
    string,
    { r2Key: string; sha256: string; bytes: number; contentType: string }
  >();
  let uploaded = 0;
  let reused = 0;

  for (const art of artifacts()) {
    const { buf, filename, fromUrl } = await loadBytes(art);
    const sha256 = sha256Hex(buf);
    const r2Key = catalogSourceObjectKey(sha256, filename);
    const contentType = contentTypeFor(filename);
    const existing = await r2.exists(r2Key);
    if (!existing) {
      await r2.put(r2Key, buf, contentType);
      uploaded += 1;
    } else {
      reused += 1;
    }
    const snapshotId = await latestSnapshotId(art.sourceId);
    await upsertSourceFile({
      sourceId: art.sourceId,
      snapshotId,
      kind: art.kind,
      filename,
      contentType,
      bytes: buf.length,
      sha256,
      r2Key,
      collectedFromUrl: fromUrl,
    });
    if (art.primary !== false && art.kind === "original" && !primaryBySource.has(art.sourceId)) {
      primaryBySource.set(art.sourceId, { r2Key, sha256, bytes: buf.length, contentType });
    }
    console.log(
      `${existing ? "reuse" : "put  "} ${art.sourceId} ${art.kind} ${filename} ${buf.length} ${sha256.slice(0, 12)}`
    );
  }

  for (const [sourceId, pointer] of primaryBySource) {
    await setSourceArchivePointer(sourceId, pointer);
  }

  console.log({ uploaded, reused, sources: primaryBySource.size });
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
