/**
 * Ingest multi-source health reports (IARC, OpenFoodTox, JECFA, EU actions, OFF tags).
 * One DB row per claim per source — never merge cancer/ADI across publishers.
 * Archives research artifacts to R2 and links via source_id.
 */
import "dotenv/config";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "csv-parse/sync";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import {
  catalogSourceObjectKey,
  getR2OrNull,
  sha256Hex,
} from "../src/adapters/services/r2Storage.js";
import {
  latestSnapshotId,
  setSourceArchivePointer,
  upsertSourceFile,
} from "../src/infrastructure/sourceFiles.js";
import {
  EU_ADDITIVES_HEALTH_SOURCE_ID,
  IARC_SOURCE_ID,
  JECFA_SOURCE_ID,
  OFF_ADDITIVES_SOURCE_ID,
  OPENFOODTOX_SOURCE_ID,
  type CatalogSourceFile,
} from "../src/domain/dataSource.js";
import { loadCatalogSources } from "./loadCatalogSources.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const RESEARCH = resolve(ROOT, "../research");

const HEALTH_SOURCE_IDS = new Set([
  IARC_SOURCE_ID,
  OPENFOODTOX_SOURCE_ID,
  JECFA_SOURCE_ID,
  EU_ADDITIVES_HEALTH_SOURCE_ID,
  OFF_ADDITIVES_SOURCE_ID,
]);

interface Claim {
  sourceId: string;
  kind: string;
  summary: string;
  value?: string | null;
  unit?: string | null;
  evaluationYear?: string | null;
  citation?: string | null;
  citationUrl?: string | null;
  /** Match via CAS and/or E/INS digits */
  cas?: string | null;
  eNumber?: string | null;
  nameHint?: string | null;
}

function normCas(c: string): string {
  return c.trim().replace(/\s+/g, "");
}

function normE(c: string): string {
  return c.trim().replace(/^e/i, "").replace(/^ins\s*/i, "").toLowerCase();
}

type IngredientMaps = {
  byCas: Map<string, string>;
  byE: Map<string, string>;
  casKeys: Set<string>;
  eKeys: Set<string>;
};

async function loadIngredientMaps(): Promise<IngredientMaps> {
  const byCas = new Map<string, string>();
  const byE = new Map<string, string>();
  const codes = await query<{ ingredient_id: string; system: string; code: string }>(
    `SELECT ingredient_id, system, code FROM ingredient_codes
     WHERE system ILIKE ANY(ARRAY['CAS','E','INS'])`
  );
  for (const r of codes.rows) {
    const sys = r.system.toUpperCase();
    if (sys === "CAS") byCas.set(normCas(r.code).toLowerCase(), r.ingredient_id);
    else byE.set(normE(r.code), r.ingredient_id);
  }
  const ings = await query<{ id: string; cas_number: string | null; e_number: string | null }>(
    `SELECT id, cas_number, e_number FROM ingredients`
  );
  for (const r of ings.rows) {
    if (r.cas_number) {
      const k = normCas(r.cas_number).toLowerCase();
      if (!byCas.has(k)) byCas.set(k, r.id);
    }
    if (r.e_number) {
      const k = normE(r.e_number);
      if (!byE.has(k)) byE.set(k, r.id);
    }
  }
  return {
    byCas,
    byE,
    casKeys: new Set(byCas.keys()),
    eKeys: new Set(byE.keys()),
  };
}

function resolveIngredientId(
  maps: IngredientMaps,
  cas?: string | null,
  eNumber?: string | null
): string | null {
  if (cas) {
    const id = maps.byCas.get(normCas(cas).toLowerCase());
    if (id) return id;
  }
  if (eNumber) {
    const id = maps.byE.get(normE(eNumber));
    if (id) return id;
  }
  return null;
}

async function replaceClaims(
  rows: Array<{ ingredientId: string; claim: Claim }>
): Promise<number> {
  const sourceIds = [...HEALTH_SOURCE_IDS];
  await query(`DELETE FROM ingredient_health_reports WHERE source_id = ANY($1::text[])`, [
    sourceIds,
  ]);
  // Deduplicate identical claim keys within this run (keep first) — still keeps
  // different sources as separate rows.
  const seen = new Set<string>();
  const unique = rows.filter(({ ingredientId, claim }) => {
    const key = [
      ingredientId,
      claim.sourceId,
      claim.kind,
      claim.value ?? "",
      claim.citationUrl ?? "",
    ].join("|");
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  const chunk = 80;
  for (let i = 0; i < unique.length; i += chunk) {
    const slice = unique.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = slice.map((row, idx) => {
      const o = idx * 9;
      values.push(
        row.ingredientId,
        row.claim.sourceId,
        row.claim.kind,
        row.claim.summary,
        row.claim.value ?? null,
        row.claim.unit ?? null,
        row.claim.evaluationYear ?? null,
        row.claim.citation ?? null,
        row.claim.citationUrl ?? null
      );
      return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6},$${o + 7},$${o + 8},$${o + 9})`;
    });
    await query(
      `INSERT INTO ingredient_health_reports
         (ingredient_id, source_id, kind, summary, value, unit, evaluation_year, citation, citation_url)
       VALUES ${placeholders.join(",")}`,
      values
    );
  }
  return unique.length;
}

function seedClaims(): Claim[] {
  return [
    {
      sourceId: IARC_SOURCE_ID,
      kind: "carcinogen",
      summary: "Possibly carcinogenic to humans (IARC Group 2B)",
      value: "2B",
      cas: "22839-47-0",
      eNumber: "951",
      evaluationYear: "2023",
      citation: "IARC Monographs",
      citationUrl: "https://monographs.iarc.who.int/list-of-classifications/",
    },
    {
      sourceId: OPENFOODTOX_SOURCE_ID,
      kind: "adi",
      summary: "Acceptable daily intake",
      value: "40",
      unit: "mg/kg bw/day",
      cas: "22839-47-0",
      eNumber: "951",
      evaluationYear: "2013",
      citation: "EFSA OpenFoodTox",
      citationUrl: "https://www.efsa.europa.eu/en/data-report/chemical-hazards-database-openfoodtox",
    },
    {
      sourceId: JECFA_SOURCE_ID,
      kind: "evaluation",
      summary: "JECFA evaluation (see WHO chemical page for ADI text)",
      value: null,
      cas: "22839-47-0",
      eNumber: "951",
      evaluationYear: "2023",
      citation: "JECFA / WHO",
      citationUrl: "https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/62",
    },
    {
      sourceId: IARC_SOURCE_ID,
      kind: "carcinogen",
      summary: "Possibly carcinogenic to humans (IARC Group 2B)",
      value: "2B",
      cas: "13463-67-7",
      eNumber: "171",
      evaluationYear: "2010",
      citation: "IARC Monographs",
      citationUrl: "https://monographs.iarc.who.int/list-of-classifications/",
    },
    {
      sourceId: OPENFOODTOX_SOURCE_ID,
      kind: "adi",
      summary: "ADI not established / no allocated ADI in OpenFoodTox",
      value: "not_allocated",
      cas: "13463-67-7",
      eNumber: "171",
      citation: "EFSA OpenFoodTox",
      citationUrl: "https://www.efsa.europa.eu/en/data-report/chemical-hazards-database-openfoodtox",
    },
    {
      sourceId: EU_ADDITIVES_HEALTH_SOURCE_ID,
      kind: "authorisation_withdrawn",
      summary: "Food use as additive withdrawn in the EU (Reg 2022/63)",
      value: "withdrawn",
      cas: "13463-67-7",
      eNumber: "171",
      evaluationYear: "2022",
      citation: "Commission Regulation (EU) 2022/63",
      citationUrl: "https://eur-lex.europa.eu/eli/reg/2022/63/oj",
    },
    {
      sourceId: IARC_SOURCE_ID,
      kind: "carcinogen",
      summary: "Possibly carcinogenic to humans (IARC Group 2B)",
      value: "2B",
      cas: "7758-01-2",
      eNumber: "924a",
      citation: "IARC Monographs — potassium bromate",
      citationUrl: "https://monographs.iarc.who.int/list-of-classifications/",
    },
    {
      sourceId: OPENFOODTOX_SOURCE_ID,
      kind: "genotox",
      summary: "Genotoxicity assessment recorded as Positive",
      value: "Positive",
      cas: "79-06-1",
      nameHint: "Acrylamide",
      evaluationYear: "2005",
      citation: "EFSA OpenFoodTox genotoxicity",
      citationUrl: "https://www.efsa.europa.eu/en/data-report/chemical-hazards-database-openfoodtox",
    },
    {
      sourceId: IARC_SOURCE_ID,
      kind: "carcinogen",
      summary: "Probably carcinogenic to humans (IARC Group 2A)",
      value: "2A",
      cas: "79-06-1",
      citation: "IARC Monographs — acrylamide",
      citationUrl: "https://monographs.iarc.who.int/list-of-classifications/",
    },
  ];
}

function loadIarcClaimsForKnownCas(knownCas: Set<string>): Claim[] {
  const path = join(RESEARCH, "iarc/iarc_classifications.csv");
  if (!existsSync(path)) return [];
  const rows = parse(readFileSync(path, "utf8"), {
    columns: true,
    skip_empty_lines: true,
    bom: true,
  }) as Array<Record<string, string>>;
  const out: Claim[] = [];
  for (const r of rows) {
    const casRaw = (r["CAS No."] || "").trim();
    const group = (r.Group || "").trim();
    if (!casRaw || !["1", "2A", "2B"].includes(group)) continue;
    for (const cas of casRaw.split(/[,;]/).map(normCas).filter(Boolean)) {
      if (!knownCas.has(cas.toLowerCase())) continue;
      const label =
        group === "1"
          ? "Carcinogenic to humans (IARC Group 1)"
          : group === "2A"
            ? "Probably carcinogenic to humans (IARC Group 2A)"
            : "Possibly carcinogenic to humans (IARC Group 2B)";
      out.push({
        sourceId: IARC_SOURCE_ID,
        kind: "carcinogen",
        summary: `${r.Agent}: ${label}`,
        value: group,
        cas,
        evaluationYear: r["Evaluation year"] || null,
        citation: `IARC Vol ${r.Volume || "?"}`,
        citationUrl: "https://monographs.iarc.who.int/list-of-classifications/",
      });
    }
  }
  return out;
}

function loadOftAdiClaims(knownCas: Set<string>): Claim[] {
  const path = join(RESEARCH, "openfoodtox/samples/oft3_adi_with_cas.csv");
  if (!existsSync(path)) return [];
  const rows = parse(readFileSync(path, "utf8"), {
    columns: true,
    skip_empty_lines: true,
    bom: true,
  }) as Array<Record<string, string>>;
  // Keep latest numeric ADI per CAS among known ingredients (cap for ingest size)
  const best = new Map<string, Claim>();
  for (const r of rows) {
    const cas = normCas(r.cas || "");
    if (!cas || !knownCas.has(cas.toLowerCase())) continue;
    const val = (r["HumanHealthHazardCharacteristics.AcceptableDailyIntake.Adi.lowerValue"] || "").trim();
    if (!val) continue;
    const unit = (r["HumanHealthHazardCharacteristics.AcceptableDailyIntake.Adi.Unit"] || "").trim() || null;
    const name = (r.ReferenceSubstanceName || r.ChemicalName || cas).trim();
    best.set(cas.toLowerCase(), {
      sourceId: OPENFOODTOX_SOURCE_ID,
      kind: "adi",
      summary: `Acceptable daily intake (${name})`,
      value: val,
      unit,
      cas,
      citation: "EFSA OpenFoodTox 3.0",
      citationUrl: "https://www.efsa.europa.eu/en/data-report/chemical-hazards-database-openfoodtox",
    });
  }
  return [...best.values()];
}

function loadEuSeedClaims(): Claim[] {
  const path = join(RESEARCH, "concerns/seed_health_concerns.csv");
  if (!existsSync(path)) return [];
  const rows = parse(readFileSync(path, "utf8"), {
    columns: true,
    skip_empty_lines: true,
    bom: true,
  }) as Array<Record<string, string>>;
  return rows.map((r) => ({
    sourceId: EU_ADDITIVES_HEALTH_SOURCE_ID,
    kind: (r.concern || "concern").trim(),
    summary: (r.note || r.name || "Health concern").trim(),
    value: (r.concern || "").trim() || null,
    cas: r.cas?.trim() || null,
    eNumber: r.e_number?.trim() || null,
    citation: `EU / ${r.jurisdiction || "regulatory"}`,
    citationUrl: r.source?.trim() || null,
  }));
}

function loadOffOverexposure(knownE: Set<string>): Claim[] {
  const path = join(RESEARCH, "openfoodfacts/additives_hazard_signals.csv");
  if (!existsSync(path)) return [];
  const rows = parse(readFileSync(path, "utf8"), {
    columns: true,
    skip_empty_lines: true,
    bom: true,
  }) as Array<Record<string, string>>;
  const out: Claim[] = [];
  for (const r of rows) {
    const e = normE(r.e_number || "");
    if (!e || !knownE.has(e)) continue;
    const risk = (r.efsa_evaluation_overexposure_risk || "").replace(/^en:/, "");
    if (risk === "high" || risk === "moderate") {
      out.push({
        sourceId: OFF_ADDITIVES_SOURCE_ID,
        kind: "overexposure_risk",
        summary: `EFSA overexposure risk tagged ${risk} (via Open Food Facts)`,
        value: risk,
        eNumber: e,
        citation: r.efsa_evaluation || "Open Food Facts additives taxonomy",
        citationUrl: r.efsa_evaluation_url || "https://world.openfoodfacts.org",
      });
    }
    const anses = (r.anses_additives_of_interest || "").toLowerCase();
    if (anses.includes("yes")) {
      out.push({
        sourceId: OFF_ADDITIVES_SOURCE_ID,
        kind: "anses_of_interest",
        summary: "Listed by ANSES as an additive of interest (via Open Food Facts)",
        value: "yes",
        eNumber: e,
        citation: "ANSES additives of interest / Open Food Facts",
        citationUrl: "https://world.openfoodfacts.org",
      });
    }
  }
  return out;
}

async function archiveHealthSources(sources: CatalogSourceFile[]): Promise<void> {
  const r2 = getR2OrNull();
  if (!r2) {
    console.warn("R2 not configured — skipping archive (claims still ingested)");
    return;
  }
  for (const src of sources) {
    if (!src.file_path) continue;
    const abs = resolve(ROOT, src.file_path);
    if (!existsSync(abs)) {
      console.warn(`missing file for ${src.id}: ${abs}`);
      continue;
    }
    const buf = readFileSync(abs);
    const filename = abs.split("/").pop()!;
    const sha256 = sha256Hex(buf);
    const r2Key = catalogSourceObjectKey(sha256, filename);
    const contentType =
      filename.endsWith(".csv")
        ? "text/csv; charset=utf-8"
        : filename.endsWith(".xlsx")
          ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          : filename.endsWith(".json")
            ? "application/json"
            : "application/octet-stream";
    const existing = await r2.exists(r2Key);
    if (!existing) await r2.put(r2Key, buf, contentType);
    const snapshotId = await latestSnapshotId(src.id);
    await upsertSourceFile({
      sourceId: src.id,
      snapshotId,
      kind: "original",
      filename,
      contentType,
      bytes: buf.length,
      sha256,
      r2Key,
      collectedFromUrl: src.url ?? null,
    });
    await setSourceArchivePointer(src.id, { r2Key, sha256, bytes: buf.length, contentType });
    console.log(`${existing ? "reuse" : "put  "} ${src.id} ${filename} ${sha256.slice(0, 12)}`);
  }
}

async function main() {
  await ensureSchema();
  const catalog = loadCatalogSources().filter((s) => HEALTH_SOURCE_IDS.has(s.id));
  for (const src of catalog) {
    await recordCapture(src, { kind: "health" }, src.retrieved_at ?? new Date().toISOString());
  }
  await archiveHealthSources(catalog);

  const maps = await loadIngredientMaps();
  console.log(`ingredient maps: cas=${maps.casKeys.size} e=${maps.eKeys.size}`);
  const claims: Claim[] = [
    ...seedClaims(),
    ...loadIarcClaimsForKnownCas(maps.casKeys),
    ...loadOftAdiClaims(maps.casKeys),
    ...loadEuSeedClaims(),
    ...loadOffOverexposure(maps.eKeys),
  ];

  let skipped = 0;
  const linkedRows: Array<{ ingredientId: string; claim: Claim }> = [];
  const byIngredient = new Map<string, number>();
  for (const claim of claims) {
    const id = resolveIngredientId(maps, claim.cas, claim.eNumber);
    if (!id) {
      skipped += 1;
      continue;
    }
    linkedRows.push({ ingredientId: id, claim });
    byIngredient.set(id, (byIngredient.get(id) ?? 0) + 1);
  }
  const inserted = await replaceClaims(linkedRows);

  console.log({
    sources: catalog.length,
    claims: claims.length,
    inserted,
    skipped,
    ingredientsWithReports: byIngredient.size,
    top: [...byIngredient.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
  });
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
