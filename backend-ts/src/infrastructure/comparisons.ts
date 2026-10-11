import { query } from "./db.js";
import type { AnnotatedIngredient } from "../domain/entities.js";
import { SHELF_TAG_OPTIONS, parseSnapshot, type SnapshotName } from "./community.js";

/** A comparison holds 2–3 products, each backed by its own `scans` row. */
export const COMPARISON_MIN_ITEMS = 2;
export const COMPARISON_MAX_ITEMS = 3;
export const COMPARISON_DECISIONS = ["picked", "none", "undecided"] as const;
export type ComparisonDecision = (typeof COMPARISON_DECISIONS)[number];

export type ComparisonItemInput = {
  slot: number;
  displayName: string;
  brand: string | null;
  /** Reuse an existing scan (picked from My list / an earlier comparison). */
  scanId: number | null;
  /** New label read: ingredients in label order. Required when scanId is null. */
  snapshot: SnapshotName[];
  ingredientsText: string | null;
};

export type ComparisonPayload = {
  category: string | null;
  tags: string[];
  decision: ComparisonDecision;
  chosenSlot: number | null;
  reasons: string[];
  note: string | null;
  addChosenToShelf: boolean;
  items: ComparisonItemInput[];
};

export class ComparisonValidationError extends Error {
  readonly status = 400;
}

const SLOT_LETTERS = ["A", "B", "C"];

function str(raw: unknown, max: number): string | null {
  if (typeof raw !== "string") return null;
  const s = raw.trim();
  return s ? s.slice(0, max) : null;
}

function strList(raw: unknown, maxItems: number, maxLen: number): string[] {
  if (!Array.isArray(raw)) return [];
  const out: string[] = [];
  for (const v of raw) {
    const s = str(v, maxLen);
    if (s && !out.includes(s)) out.push(s);
    if (out.length >= maxItems) break;
  }
  return out;
}

/** Validates the JSON `payload` part of POST /me/comparisons. Pure — unit tested. */
export function parseComparisonPayload(raw: unknown): ComparisonPayload {
  if (!raw || typeof raw !== "object") throw new ComparisonValidationError("payload is required");
  const o = raw as Record<string, unknown>;
  const itemsRaw = Array.isArray(o.items) ? o.items : [];
  if (itemsRaw.length < COMPARISON_MIN_ITEMS || itemsRaw.length > COMPARISON_MAX_ITEMS) {
    throw new ComparisonValidationError(
      `A comparison needs ${COMPARISON_MIN_ITEMS}–${COMPARISON_MAX_ITEMS} products`
    );
  }
  const seenSlots = new Set<number>();
  const items: ComparisonItemInput[] = itemsRaw.map((it) => {
    const r = (it ?? {}) as Record<string, unknown>;
    const slot = Number(r.slot);
    if (!Number.isInteger(slot) || slot < 0 || slot >= COMPARISON_MAX_ITEMS || seenSlots.has(slot)) {
      throw new ComparisonValidationError("Invalid product slot");
    }
    seenSlots.add(slot);
    const scanIdNum = Number(r.scan_id);
    const scanId = Number.isInteger(scanIdNum) && scanIdNum > 0 ? scanIdNum : null;
    const snapshot = parseSnapshot(r.snapshot);
    if (scanId == null && snapshot.length === 0) {
      throw new ComparisonValidationError(`Product ${SLOT_LETTERS[slot]} has no ingredients`);
    }
    return {
      slot,
      displayName: str(r.display_name, 255) ?? `Product ${SLOT_LETTERS[slot]}`,
      brand: str(r.brand, 255),
      scanId,
      snapshot,
      ingredientsText: str(r.ingredients_text, 8000),
    };
  });

  const decisionRaw = String(o.decision ?? "undecided").toLowerCase();
  const decision = (COMPARISON_DECISIONS as readonly string[]).includes(decisionRaw)
    ? (decisionRaw as ComparisonDecision)
    : "undecided";
  const chosenNum = Number(o.chosen_slot);
  const chosenSlot = decision === "picked" && seenSlots.has(chosenNum) ? chosenNum : null;
  if (decision === "picked" && chosenSlot == null) {
    throw new ComparisonValidationError("chosen_slot must be one of the products");
  }
  const tags = strList(o.tags, 4, 20)
    .map((t) => SHELF_TAG_OPTIONS.find((x) => x.toLowerCase() === t.toLowerCase()))
    .filter((t): t is (typeof SHELF_TAG_OPTIONS)[number] => Boolean(t));

  return {
    category: str(o.category, 60),
    tags,
    decision,
    chosenSlot,
    reasons: strList(o.reasons, 8, 40),
    note: str(o.note, 500),
    addChosenToShelf: o.add_chosen_to_shelf === true && chosenSlot != null,
    items: items.sort((a, b) => a.slot - b.slot),
  };
}

/** Same rule the app uses for the "Flagged" section: regulation or health watch signal. */
export function isFlagged(ing: AnnotatedIngredient): boolean {
  const s = (ing.severity || "").toLowerCase();
  return s === "red" || s === "amber" || Boolean(ing.has_health_watch) || (ing.health_flags?.length ?? 0) > 0;
}

/** Scans the caller may reuse in a comparison: only their own. */
export async function ownedScanSnapshots(
  userId: string,
  scanIds: number[]
): Promise<Map<number, SnapshotName[]>> {
  if (!scanIds.length) return new Map();
  const { rows } = await query<{ id: number; snapshot: unknown }>(
    `SELECT id, snapshot FROM scans WHERE user_id = $1 AND id = ANY($2::int[])`,
    [userId, scanIds]
  );
  return new Map(rows.map((r) => [Number(r.id), parseSnapshot(r.snapshot)]));
}

export type ComparisonItemRow = {
  slot: number;
  scanId: number;
  displayName: string;
  brand: string | null;
  frontImageKey: string | null;
  flaggedCount: number;
  ingredientCount: number;
};

/** Inserts the comparison and its items in one statement so a failure leaves no half-saved row. */
export async function insertComparison(
  userId: string,
  p: ComparisonPayload,
  items: ComparisonItemRow[]
): Promise<number> {
  const { rows } = await query<{ id: number }>(
    `WITH c AS (
       INSERT INTO comparisons (user_id, category, tags, decision, chosen_slot, reasons, note)
       VALUES ($1,$2,$3::jsonb,$4,$5,$6::jsonb,$7)
       RETURNING id
     ), i AS (
       INSERT INTO comparison_items
         (comparison_id, slot, scan_id, display_name, brand, front_image_key, flagged_count, ingredient_count)
       SELECT c.id, t.slot, t.scan_id, t.display_name, t.brand, t.front_key, t.flagged, t.total
       FROM c, unnest($8::int[], $9::int[], $10::text[], $11::text[], $12::text[], $13::int[], $14::int[])
         AS t(slot, scan_id, display_name, brand, front_key, flagged, total)
     )
     SELECT id FROM c`,
    [
      userId,
      p.category,
      JSON.stringify(p.tags),
      p.decision,
      p.chosenSlot,
      JSON.stringify(p.reasons),
      p.note,
      items.map((i) => i.slot),
      items.map((i) => i.scanId),
      items.map((i) => i.displayName),
      items.map((i) => i.brand),
      items.map((i) => i.frontImageKey),
      items.map((i) => i.flaggedCount),
      items.map((i) => i.ingredientCount),
    ]
  );
  return Number(rows[0]!.id);
}

/** Adds the picked product to My list unless that scan is already there. */
export async function addScanToShelfIfMissing(input: {
  userId: string;
  scanId: number;
  displayName: string;
  brand: string | null;
  tags: string[];
}): Promise<void> {
  await query(
    `INSERT INTO shelf_items (user_id, scan_id, display_name, brand, stance, tags)
     SELECT $1, $2, $3, $4, 'ok', $5::jsonb
     WHERE NOT EXISTS (SELECT 1 FROM shelf_items WHERE user_id = $1 AND scan_id = $2)`,
    [input.userId, input.scanId, input.displayName, input.brand, JSON.stringify(input.tags)]
  );
}

const ITEMS_JSON = `COALESCE((
  SELECT json_agg(json_build_object(
    'slot', ci.slot,
    'scan_id', ci.scan_id,
    'display_name', ci.display_name,
    'brand', ci.brand,
    'has_front', ci.front_image_key IS NOT NULL,
    'has_label', sc.image_key IS NOT NULL,
    'label_date', sc.created_at,
    'flagged_count', ci.flagged_count,
    'ingredient_count', ci.ingredient_count
  ) ORDER BY ci.slot)
  FROM comparison_items ci JOIN scans sc ON sc.id = ci.scan_id
  WHERE ci.comparison_id = cm.id
), '[]'::json)`;

export async function listComparisons(userId: string) {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT cm.id, cm.category, cm.tags, cm.decision, cm.chosen_slot, cm.reasons, cm.note,
            cm.created_at, ${ITEMS_JSON} AS items
     FROM comparisons cm
     WHERE cm.user_id = $1
     ORDER BY cm.created_at DESC
     LIMIT 200`,
    [userId]
  );
  return rows;
}

export async function getComparison(userId: string, id: number) {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT cm.id, cm.category, cm.tags, cm.decision, cm.chosen_slot, cm.reasons, cm.note,
            cm.created_at, ${ITEMS_JSON} AS items
     FROM comparisons cm
     WHERE cm.user_id = $1 AND cm.id = $2`,
    [userId, id]
  );
  return rows[0] ?? null;
}

export async function getComparisonSnapshots(
  userId: string,
  id: number
): Promise<Map<number, SnapshotName[]>> {
  const { rows } = await query<{ slot: number; snapshot: unknown }>(
    `SELECT ci.slot, sc.snapshot
     FROM comparison_items ci
     JOIN comparisons cm ON cm.id = ci.comparison_id
     JOIN scans sc ON sc.id = ci.scan_id
     WHERE cm.user_id = $1 AND cm.id = $2`,
    [userId, id]
  );
  return new Map(rows.map((r) => [Number(r.slot), parseSnapshot(r.snapshot)]));
}

export async function updateComparisonDecision(
  userId: string,
  id: number,
  input: { decision: ComparisonDecision; chosenSlot: number | null; reasons: string[]; note: string | null }
): Promise<boolean> {
  const { rows } = await query<{ id: number }>(
    `UPDATE comparisons cm
     SET decision = $3, chosen_slot = $4, reasons = $5::jsonb, note = $6
     WHERE cm.user_id = $1 AND cm.id = $2
       AND ($4::int IS NULL OR EXISTS (
         SELECT 1 FROM comparison_items ci WHERE ci.comparison_id = cm.id AND ci.slot = $4
       ))
     RETURNING cm.id`,
    [userId, id, input.decision, input.chosenSlot, JSON.stringify(input.reasons), input.note]
  );
  return Boolean(rows[0]);
}

export async function getComparisonPhotoKey(
  userId: string,
  id: number,
  slot: number,
  kind: "front" | "label"
): Promise<string | null> {
  const { rows } = await query<{ key: string | null }>(
    `SELECT CASE WHEN $4 = 'front' THEN ci.front_image_key ELSE sc.image_key END AS key
     FROM comparison_items ci
     JOIN comparisons cm ON cm.id = ci.comparison_id
     JOIN scans sc ON sc.id = ci.scan_id
     WHERE cm.user_id = $1 AND cm.id = $2 AND ci.slot = $3`,
    [userId, id, slot, kind]
  );
  return rows[0]?.key ?? null;
}

/**
 * Deletes the comparison, then any of its scans nothing else references (scans reused from
 * My list stay). Returns the photo keys that are now unreferenced so the caller can drop them.
 */
export async function deleteComparison(userId: string, id: number): Promise<string[] | null> {
  const { rows } = await query<{ scan_id: number; front_image_key: string | null }>(
    `DELETE FROM comparison_items ci
     USING comparisons cm
     WHERE cm.id = ci.comparison_id AND cm.user_id = $1 AND cm.id = $2
     RETURNING ci.scan_id, ci.front_image_key`,
    [userId, id]
  );
  const deleted = await query<{ id: number }>(
    `DELETE FROM comparisons WHERE user_id = $1 AND id = $2 RETURNING id`,
    [userId, id]
  );
  if (!deleted.rows[0]) return null;
  const scanIds = rows.map((r) => Number(r.scan_id));
  const orphans = await query<{ image_key: string | null }>(
    `DELETE FROM scans sc
     WHERE sc.user_id = $1 AND sc.id = ANY($2::int[])
       AND NOT EXISTS (SELECT 1 FROM shelf_items s WHERE s.scan_id = sc.id)
       AND NOT EXISTS (SELECT 1 FROM stories st WHERE st.scan_id = sc.id)
       AND NOT EXISTS (SELECT 1 FROM comparison_items ci WHERE ci.scan_id = sc.id)
     RETURNING sc.image_key`,
    [userId, scanIds]
  );
  return [
    ...rows.map((r) => r.front_image_key),
    ...orphans.rows.map((r) => r.image_key),
  ].filter((k): k is string => Boolean(k));
}
