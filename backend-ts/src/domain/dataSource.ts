export interface DataSource {
  id: string;
  publisher: string;
  country_code: string | null;
  title: string;
  version: string | null;
  version_date: string | null;
  citation: string | null;
  citation_url: string | null;
  url: string | null;
  urls: string[];
  file_path: string | null;
  r2_key: string | null;
  file_sha256: string | null;
  file_bytes: number | null;
  content_type: string | null;
  files: DataSourceFile[];
  retrieved_at: string;
  ingested_at: string;
  notes: string | null;
  stats?: Record<string, unknown> | null;
}

/** One captured artifact stored in R2, hashed so a later PDF cannot pretend to be this one. */
export interface DataSourceFile {
  id: number;
  source_id: string;
  kind: "original" | "parsed" | "historical";
  filename: string;
  content_type: string | null;
  bytes: number | null;
  sha256: string;
  collected_from_url: string | null;
  archived_at: string;
  download_url?: string | null;
}

export interface DataSourceSnapshot extends DataSource {
  snapshot_id: number;
  source_id: string;
}

/** Disk record for a captured dataset. `retrieved_at` may be filled at ingest from file mtime. */
export interface CatalogSourceFile {
  id: string;
  publisher: string;
  country_code?: string | null;
  title: string;
  version?: string | null;
  version_date?: string | null;
  citation?: string | null;
  citation_url?: string | null;
  url?: string | null;
  urls?: string[] | null;
  file_path?: string | null;
  retrieved_at?: string | null;
  notes?: string | null;
}

export const FAI_SOURCE_BY_COUNTRY: Record<string, string> = {
  EU: "fai-eu",
  GB: "fai-gb",
  AU: "fai-anz",
  NZ: "fai-anz",
  CA: "fai-ca",
  US: "fai-us",
  KR: "fai-kr",
  JP: "fai-jp",
};

export const FSSAI_APPENDIX_SOURCE_ID = "fssai-appendix-a";
export const FSSAI_BROMATE_SOURCE_ID = "fssai-gazette-bromate-2016";
export const FSSAI_CYCLAMATE_SOURCE_ID = "fssai-cyclamates-2016";
