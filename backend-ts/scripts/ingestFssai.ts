/**
 * Map parsed FSSAI Appendix A JSON onto ingredients and upsert IN ban rows.
 *
 * Presence in Appendix A → permitted. Numeric mg/kg caps live in ingredient_use_limits,
 * not as an amber "restricted" country chip — a legal max is still authorised.
 * Gazette overlays (bromate, cyclamates) → banned and always win.
 * Not listed is NOT treated as a nationwide ban.
 */
import "dotenv/config";
import { readFileSync, existsSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { pool, query } from "../src/infrastructure/db.js";
import { resolveFoodCategoryName } from "../src/domain/fssaiCategories.js";
import { recordCapture } from "../src/infrastructure/dataSources.js";
import { catalogSourceById, retrievedAtFor } from "./loadCatalogSources.js";
import {
  FSSAI_APPENDIX_SOURCE_ID,
  FSSAI_BROMATE_SOURCE_ID,
  FSSAI_CYCLAMATE_SOURCE_ID,
} from "../src/domain/dataSource.js";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const JSON_PATH = join(ROOT, "data/fssai/permissions-in.json");
const UNMATCHED_DOC = join(ROOT, "data/fssai-unmatched-ins.json");
const PARSER = join(ROOT, "scripts/parseFssaiAppendix.py");

const STATUS_RANK: Record<string, number> = { banned: 0, restricted: 1, permitted: 2 };

type Provision = {
  category?: string | null;
  category_name?: string | null;
  additive?: string | null;
  ins: string;
  limit?: string | null;
  note?: string | null;
  group?: boolean;
};

type Payload = {
  source: string;
  regulation_ref: string;
  provisions: Provision[];
  gmp: Array<{ ins: string; additive: string }>;
};

function clip(s: string | null | undefined, n: number): string | null {
  if (!s) return null;
  const t = s.trim();
  return t.length <= n ? t : t.slice(0, n - 1) + "…";
}

function insCandidates(raw: string): string[] {
  const core = raw.trim().replace(/^INS\s*/i, "").replace(/^E/i, "").replace(/\s+/g, "");
  const upper = core.toUpperCase();
  const noParen = upper.replace(/[()]/g, "");
  const out = new Set<string>([
    `E${upper}`,
    `E${noParen}`,
    upper,
    `INS ${core}`,
    `INS${core}`,
    `INS ${upper}`,
  ]);
  return [...out];
}

function isNumericLimit(limit: string | null | undefined): boolean {
  return Boolean(limit && /mg\/|ppm|g\/kg/i.test(limit) && !/^GMP$/i.test(limit));
}

async function loadPayload(): Promise<Payload> {
  if (!existsSync(JSON_PATH)) {
    console.log("Parsed JSON missing; running parser…");
    execFileSync("python3", [PARSER], { stdio: "inherit" });
  }
  return JSON.parse(readFileSync(JSON_PATH, "utf8")) as Payload;
}

async function mapInsToIds(): Promise<Map<string, string[]>> {
  const { rows } = await query<{ id: string; e_number: string | null }>(
    `SELECT id, e_number FROM ingredients`
  );
  const { rows: names } = await query<{ ingredient_id: string; name: string }>(
    `SELECT ingredient_id, name FROM ingredient_names
     WHERE LOWER(name) LIKE 'ins%' OR LOWER(name) LIKE 'e%'`
  );
  const { rows: codes } = await query<{ ingredient_id: string; system: string; code: string }>(
    `SELECT ingredient_id, system, code FROM ingredient_codes`
  );

  const map = new Map<string, Set<string>>();
  const add = (key: string, id: string) => {
    const k = key.toUpperCase().replace(/\s+/g, "");
    if (!map.has(k)) map.set(k, new Set());
    map.get(k)!.add(id);
  };

  for (const r of rows) {
    if (r.e_number) {
      add(r.e_number, r.id);
      add(r.e_number.replace(/^E/i, ""), r.id);
    }
  }
  for (const n of names) {
    const m = n.name.match(/^(?:INS|E)[\s-]*(\d{3,4}.*)$/i);
    if (m) {
      add(m[1], n.ingredient_id);
      add(`E${m[1]}`, n.ingredient_id);
    }
  }
  for (const c of codes) {
    add(c.code, c.ingredient_id);
    add(c.code.replace(/^E/i, ""), c.ingredient_id);
    const sys = c.system.toUpperCase();
    if (sys === "INS" || sys === "E") add(`${sys}${c.code}`, c.ingredient_id);
  }

  const out = new Map<string, string[]>();
  for (const [k, v] of map) out.set(k, [...v]);
  return out;
}

function lookupIds(ins: string, index: Map<string, string[]>): string[] {
  const seen = new Set<string>();
  for (const c of insCandidates(ins)) {
    const hit = index.get(c.toUpperCase().replace(/\s+/g, ""));
    if (hit) for (const id of hit) seen.add(id);
  }
  return [...seen];
}

type UnmatchedMiss = {
  ins: string;
  kind: string;
  rows: number;
  pdf_name: string | null;
  what?: string;
  impact?: string;
};

function writeUnmatchedDoc(args: {
  version: string | null;
  versionDate: string | null;
  provisions: number;
  gmp: number;
  misses: UnmatchedMiss[];
}): UnmatchedMiss[] {
  let prior: {
    summary?: string;
    status?: string;
    fixable?: boolean;
    deferred?: unknown;
    how_to_fix?: unknown;
    misses?: UnmatchedMiss[];
  } = {};
  if (existsSync(UNMATCHED_DOC)) {
    try {
      prior = JSON.parse(readFileSync(UNMATCHED_DOC, "utf8")) as typeof prior;
    } catch {
      prior = {};
    }
  }
  const notes = new Map((prior.misses || []).map((m) => [m.ins.toLowerCase(), m]));
  const misses = args.misses.map((m) => {
    const old = notes.get(m.ins.toLowerCase());
    return {
      ...m,
      kind: old?.kind || "unclassified",
      what: old?.what,
      impact: old?.impact,
    };
  });
  const rows = misses.reduce((n, m) => n + m.rows, 0);
  writeFileSync(
    UNMATCHED_DOC,
    JSON.stringify(
      {
        source_id: FSSAI_APPENDIX_SOURCE_ID,
        appendix_version: args.version,
        appendix_date: args.versionDate,
        documented_at: new Date().toISOString().slice(0, 10),
        status: prior.status || "deferred",
        fixable: prior.fixable !== false,
        summary:
          prior.summary ||
          "Appendix A provisions are keyed by INS. A row is stored only when that INS maps to an ingredient already in the catalog. Unmatched strings are listed below.",
        deferred: prior.deferred ?? null,
        how_to_fix: prior.how_to_fix ?? null,
        totals: {
          provisions: args.provisions,
          gmp: args.gmp,
          unmatched_unique_ins: misses.length,
          unmatched_rows: rows,
        },
        misses,
      },
      null,
      2
    ) + "\n"
  );
  return misses;
}

async function upsertBans(
  rows: Array<{
    ingredient_id: string;
    status: "banned" | "restricted" | "permitted";
    condition: string | null;
    regulation_ref: string | null;
  }>
) {
  const unique = new Map<string, (typeof rows)[0]>();
  for (const r of rows) {
    const prev = unique.get(r.ingredient_id);
    if (!prev || (STATUS_RANK[r.status] ?? 2) < (STATUS_RANK[prev.status] ?? 2)) {
      unique.set(r.ingredient_id, r);
    } else if (prev && r.condition && prev.condition) {
      // keep the more detailed condition when status ties
      if (r.condition.length > prev.condition.length) unique.set(r.ingredient_id, { ...prev, condition: r.condition });
    }
  }
  const list = [...unique.values()];
  const chunk = 80;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 6;
      values.push(
        row.ingredient_id,
        "IN",
        row.status,
        row.condition,
        row.regulation_ref,
        FSSAI_APPENDIX_SOURCE_ID
      );
      return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6})`;
    });
    await query(
      `INSERT INTO ingredient_bans
         (ingredient_id, country_code, status, condition, regulation_ref, source_id)
       VALUES ${placeholders.join(",")}
       ON CONFLICT (ingredient_id, country_code) DO UPDATE SET
         status = CASE
           WHEN ingredient_bans.status = 'banned' OR EXCLUDED.status = 'banned' THEN 'banned'
           ELSE EXCLUDED.status
         END,
         condition = EXCLUDED.condition,
         regulation_ref = COALESCE(EXCLUDED.regulation_ref, ingredient_bans.regulation_ref),
         source_id = CASE
           WHEN ingredient_bans.status = 'banned' AND EXCLUDED.status <> 'banned'
             THEN ingredient_bans.source_id
           ELSE EXCLUDED.source_id
         END`,
      values
    );
  }
  return list.length;
}

type LimitRow = {
  ingredient_id: string;
  food_category_code: string;
  food_category_name: string | null;
  max_level: string;
  note: string | null;
  regulation_ref: string | null;
};

async function upsertLimits(rows: LimitRow[]) {
  await query(`DELETE FROM ingredient_use_limits WHERE country_code = 'IN'`);
  const unique = new Map<string, LimitRow>();
  for (const r of rows) {
    unique.set(`${r.ingredient_id}|${r.food_category_code}|${r.max_level}`, r);
  }
  const list = [...unique.values()];
  const chunk = 50;
  for (let i = 0; i < list.length; i += chunk) {
    const part = list.slice(i, i + chunk);
    const values: unknown[] = [];
    const placeholders = part.map((row, idx) => {
      const o = idx * 8;
      values.push(
        row.ingredient_id,
        "IN",
        row.food_category_code,
        row.food_category_name,
        row.max_level,
        row.note,
        row.regulation_ref,
        FSSAI_APPENDIX_SOURCE_ID
      );
      return `($${o + 1},$${o + 2},$${o + 3},$${o + 4},$${o + 5},$${o + 6},$${o + 7},$${o + 8})`;
    });
    await query(
      `INSERT INTO ingredient_use_limits
         (ingredient_id, country_code, food_category_code, food_category_name,
          max_level, note, regulation_ref, source_id)
       VALUES ${placeholders.join(",")}
       ON CONFLICT (ingredient_id, country_code, food_category_code, max_level) DO NOTHING`,
      values
    );
  }
  return list.length;
}

function provisionLimit(
  rec: Provision | { ins: string; additive?: string },
  fromGmp: boolean
): Omit<LimitRow, "ingredient_id" | "regulation_ref"> {
  if (fromGmp) {
    return {
      food_category_code: "GMP",
      food_category_name: resolveFoodCategoryName("GMP"),
      max_level: "GMP",
      note: null,
    };
  }
  const p = rec as Provision;
  const code = clip(p.category, 64) || "";
  return {
    food_category_code: code,
    food_category_name: clip(resolveFoodCategoryName(code, p.category_name), 255),
    max_level: clip(p.limit, 80) || "authorised",
    note: clip(p.note, 500),
  };
}

async function main() {
  console.log("Ingesting FSSAI Appendix A into IN rows…");
  await ensureSchema();
  const appendix = catalogSourceById(FSSAI_APPENDIX_SOURCE_ID);
  await recordCapture(appendix, null, retrievedAtFor(appendix));
  const payload = await loadPayload();
  const index = await mapInsToIds();

  type Agg = {
    status: "permitted";
    examples: string[];
    n: number;
    gmpOnly: boolean;
    hasNumeric: boolean;
  };
  const byId = new Map<string, Agg>();
  const limitRows: LimitRow[] = [];

  const bump = (id: string, rec: Provision | { ins: string; additive?: string; limit?: string | null; category?: string | null; category_name?: string | null }, fromGmp: boolean) => {
    const limit = "limit" in rec ? rec.limit : "GMP";
    const numeric = isNumericLimit(limit ?? null);
    const status: Agg["status"] = "permitted";
    const code = "category" in rec && rec.category ? rec.category : fromGmp ? "GMP" : "";
    const catName = resolveFoodCategoryName(code, "category_name" in rec ? rec.category_name : null);
    const example = [catName, limit || ""].filter(Boolean).join(" · ");
    const prev = byId.get(id);
    if (!prev) {
      byId.set(id, {
        status,
        examples: example ? [example] : [],
        n: 1,
        gmpOnly: fromGmp && !numeric,
        hasNumeric: numeric,
      });
      return;
    }
    prev.n += 1;
    if (numeric) prev.hasNumeric = true;
    if (fromGmp) prev.gmpOnly = prev.gmpOnly || !numeric;
    if (example && prev.examples.length < 8 && !prev.examples.includes(example)) {
      prev.examples.push(example);
    }
  };

  let matchedProvisions = 0;
  const unmatched = new Map<string, UnmatchedMiss>();
  const noteUnmatched = (ins: string, additive: string | null | undefined) => {
    const key = ins.toLowerCase();
    const prev = unmatched.get(key);
    if (!prev) {
      unmatched.set(key, {
        ins: key,
        kind: "unclassified",
        rows: 1,
        pdf_name: additive?.trim() || null,
      });
      return;
    }
    prev.rows += 1;
    if (!prev.pdf_name && additive?.trim()) prev.pdf_name = additive.trim();
  };
  for (const rec of payload.provisions) {
    const ids = lookupIds(rec.ins, index);
    if (!ids.length) {
      noteUnmatched(rec.ins, rec.additive);
      continue;
    }
    matchedProvisions += 1;
    for (const id of ids) {
      bump(id, rec, false);
      const lim = provisionLimit(rec, false);
      limitRows.push({ ingredient_id: id, regulation_ref: payload.regulation_ref, ...lim });
    }
  }

  let matchedGmp = 0;
  for (const rec of payload.gmp) {
    const ids = lookupIds(rec.ins, index);
    if (!ids.length) {
      noteUnmatched(rec.ins, rec.additive);
      continue;
    }
    matchedGmp += 1;
    for (const id of ids) {
      bump(id, rec, true);
      const lim = provisionLimit(rec, true);
      limitRows.push({ ingredient_id: id, regulation_ref: payload.regulation_ref, ...lim });
    }
  }

  const ref = payload.regulation_ref;
  const rows = [...byId.entries()].map(([ingredient_id, agg]) => ({
    ingredient_id,
    status: agg.status,
    condition: clip(
      [
        agg.hasNumeric
          ? `Authorised in India. Caps for ${agg.n} food categor${agg.n === 1 ? "y" : "ies"} are listed below.`
          : agg.gmpOnly
            ? "GMP in most food categories (Appendix A GMP table)"
            : `Authorised in ${agg.n} Appendix A provision(s)`,
        agg.examples.length ? `e.g. ${agg.examples.slice(0, 5).join("; ")}` : "",
      ]
        .filter(Boolean)
        .join(" — "),
      2000
    ),
    regulation_ref: clip(ref, 255),
  }));

  const upserted = await upsertBans(rows);
  const limitsWritten = await upsertLimits(limitRows);

  const gazetteBanned = [
    "potassium-bromate",
    "cyclamate-and-its-derivatives",
    "cyclamic-acid-and-its-na-and-ca-salts",
    "sodium-cyclamate",
    "calcium-cyclamate",
  ];
  const bromateSrc = catalogSourceById(FSSAI_BROMATE_SOURCE_ID);
  const cyclamateSrc = catalogSourceById(FSSAI_CYCLAMATE_SOURCE_ID);
  await recordCapture(bromateSrc, { ingredients: ["potassium-bromate"] }, retrievedAtFor(bromateSrc));
  await recordCapture(
    cyclamateSrc,
    { ingredients: gazetteBanned.filter((id) => id !== "potassium-bromate") },
    retrievedAtFor(cyclamateSrc)
  );
  for (const id of gazetteBanned) {
    const exists = await query<{ id: string }>(`SELECT id FROM ingredients WHERE id = $1`, [id]);
    if (!exists.rows[0]) continue;
    const sourceId = id.includes("bromate")
      ? FSSAI_BROMATE_SOURCE_ID
      : FSSAI_CYCLAMATE_SOURCE_ID;
    await query(
      `INSERT INTO ingredient_bans (ingredient_id, country_code, status, condition, regulation_ref, source_id)
       VALUES ($1,'IN','banned',$2,$3,$4)
       ON CONFLICT (ingredient_id, country_code) DO UPDATE SET
         status = 'banned',
         condition = EXCLUDED.condition,
         regulation_ref = EXCLUDED.regulation_ref,
         source_id = EXCLUDED.source_id`,
      [
        id,
        id.includes("bromate")
          ? "Not allowed as a food additive in any food (FSSAI Gazette / 2016 operationalisation)"
          : "Cyclamates removed from the authorised additive list",
        id.includes("bromate")
          ? "FSSAI Gazette operationalisation 20 June 2016; FSSAI clarification Feb 2018"
          : "FSSAI additive list 2016 (cyclamates not authorised)",
        sourceId,
      ]
    );
  }

  const counts = await query<{ n: string; banned: string; restricted: string; permitted: string }>(
    `SELECT COUNT(*)::text AS n,
            COUNT(*) FILTER (WHERE status='banned')::text AS banned,
            COUNT(*) FILTER (WHERE status='restricted')::text AS restricted,
            COUNT(*) FILTER (WHERE status='permitted')::text AS permitted
     FROM ingredient_bans WHERE country_code = 'IN'`
  );

  const limitCount = await query<{ n: string }>(
    `SELECT COUNT(*)::text AS n FROM ingredient_use_limits WHERE country_code = 'IN'`
  );

  console.log({
    matchedProvisions,
    matchedGmp,
    unmatchedIns: unmatched.size,
    unmatchedRows: [...unmatched.values()].reduce((n, m) => n + m.rows, 0),
    ingredientRows: upserted,
    inBans: counts.rows[0],
    useLimitsAttempted: limitsWritten,
    useLimitsStored: limitCount.rows[0]?.n,
  });
  const missList = writeUnmatchedDoc({
    version: appendix.version ?? null,
    versionDate: appendix.version_date ?? null,
    provisions: payload.provisions.length,
    gmp: payload.gmp.length,
    misses: [...unmatched.values()].sort((a, b) => b.rows - a.rows || a.ins.localeCompare(b.ins)),
  });
  await recordCapture(
    appendix,
    {
      matchedProvisions,
      matchedGmp,
      unmatchedIns: unmatched.size,
      unmatchedRows: missList.reduce((n, m) => n + m.rows, 0),
      unmatched: missList.map((m) => ({ ins: m.ins, rows: m.rows, kind: m.kind, pdf_name: m.pdf_name })),
      ingredientRows: upserted,
      useLimits: limitsWritten,
    },
    retrievedAtFor(appendix)
  );
  if (missList.length && missList.length < 40) {
    console.log("unmatched INS", missList.map((m) => m.ins).join(", "));
  }
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
