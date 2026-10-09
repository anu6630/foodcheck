import { existsSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { CatalogSourceFile } from "../src/domain/dataSource.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CATALOG_PATH = join(ROOT, "data/catalog-sources.json");

export function loadCatalogSources(): CatalogSourceFile[] {
  return JSON.parse(readFileSync(CATALOG_PATH, "utf8")) as CatalogSourceFile[];
}

export function catalogSourceById(id: string): CatalogSourceFile {
  const hit = loadCatalogSources().find((s) => s.id === id);
  if (!hit) throw new Error(`Unknown catalog source id: ${id}`);
  return hit;
}

export function retrievedAtFor(src: CatalogSourceFile): string {
  if (src.retrieved_at) {
    const d = new Date(src.retrieved_at);
    if (!Number.isNaN(d.getTime())) return d.toISOString();
  }
  if (src.file_path) {
    const abs = join(ROOT, src.file_path);
    if (existsSync(abs)) return statSync(abs).mtime.toISOString();
  }
  return new Date().toISOString();
}
