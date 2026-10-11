import { Hono } from "hono";
import { PgIngredientRepository } from "../repositories/ingredientRepository.js";
import { getR2OrNull } from "../services/r2Storage.js";
import { LABEL_DISCLAIMER, annotateSnapshot, createScan } from "../../infrastructure/community.js";
import {
  COMPARISON_DECISIONS,
  ComparisonValidationError,
  addScanToShelfIfMissing,
  deleteComparison,
  getComparison,
  getComparisonPhotoKey,
  getComparisonSnapshots,
  insertComparison,
  isFlagged,
  listComparisons,
  ownedScanSnapshots,
  parseComparisonPayload,
  updateComparisonDecision,
  type ComparisonDecision,
  type ComparisonItemRow,
} from "../../infrastructure/comparisons.js";
import { httpError, photoResponse, putScanImage, readImage, userId } from "./deviceRequest.js";

export const comparisonRoutes = new Hono();

function jsonArray(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw;
  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

function toDto(row: Record<string, unknown>) {
  return {
    id: Number(row.id),
    category: row.category ?? null,
    tags: jsonArray(row.tags),
    decision: row.decision,
    chosen_slot: row.chosen_slot == null ? null : Number(row.chosen_slot),
    reasons: jsonArray(row.reasons),
    note: row.note ?? null,
    created_at: row.created_at,
    items: jsonArray(row.items),
  };
}

/**
 * Multipart: `payload` (JSON, see parseComparisonPayload) plus optional `label_<slot>` /
 * `front_<slot>` photos for products read fresh in this session.
 */
comparisonRoutes.post("/me/comparisons", async (c) => {
  try {
    const uid = await userId(c);
    const form = (await c.req.parseBody()) as Record<string, unknown>;
    let payloadRaw: unknown;
    try {
      payloadRaw = JSON.parse(String(form.payload ?? ""));
    } catch {
      throw new ComparisonValidationError("payload must be JSON");
    }
    const payload = parseComparisonPayload(payloadRaw);

    const reused = payload.items.filter((i) => i.scanId != null).map((i) => i.scanId!);
    const owned = await ownedScanSnapshots(uid, reused);
    for (const id of reused) {
      if (!owned.has(id)) throw new ComparisonValidationError("A reused product was not found");
    }

    const repo = new PgIngredientRepository();
    const rows: ComparisonItemRow[] = [];
    for (const item of payload.items) {
      let scanId = item.scanId;
      const snapshot = scanId != null ? owned.get(scanId)! : item.snapshot;
      if (scanId == null) {
        const label = await readImage(form, `label_${item.slot}`);
        scanId = await createScan({
          userId: uid,
          ingredientsText: item.ingredientsText,
          snapshot,
          imageKey: label ? await putScanImage(uid, label) : null,
        });
      }
      const front = await readImage(form, `front_${item.slot}`);
      const annotated = await annotateSnapshot(repo, snapshot);
      rows.push({
        slot: item.slot,
        scanId,
        displayName: item.displayName,
        brand: item.brand,
        frontImageKey: front ? await putScanImage(uid, front) : null,
        flaggedCount: annotated.filter(isFlagged).length,
        ingredientCount: annotated.length,
      });
    }

    const id = await insertComparison(uid, payload, rows);
    if (payload.addChosenToShelf) {
      const chosen = rows.find((r) => r.slot === payload.chosenSlot)!;
      await addScanToShelfIfMissing({
        userId: uid,
        scanId: chosen.scanId,
        displayName: chosen.displayName,
        brand: chosen.brand,
        tags: payload.tags,
      });
    }
    return c.json({ id, disclaimer: LABEL_DISCLAIMER }, 201);
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

/** Cheap list: stored per-product counts only, no ingredient annotation. */
comparisonRoutes.get("/me/comparisons", async (c) => {
  try {
    const uid = await userId(c);
    const rows = await listComparisons(uid);
    return c.json({ comparisons: rows.map(toDto) });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

/** Detail re-checks every saved label against the current rules. */
comparisonRoutes.get("/me/comparisons/:id", async (c) => {
  try {
    const uid = await userId(c);
    const id = Number(c.req.param("id"));
    const row = await getComparison(uid, id);
    if (!row) return c.json({ detail: "Comparison not found" }, 404);
    const snapshots = await getComparisonSnapshots(uid, id);
    const repo = new PgIngredientRepository();
    const dto = toDto(row);
    const items = [];
    for (const raw of dto.items as Array<Record<string, unknown>>) {
      const snap = snapshots.get(Number(raw.slot)) ?? [];
      items.push({ ...raw, ingredients: await annotateSnapshot(repo, snap) });
    }
    return c.json({ ...dto, items, disclaimer: LABEL_DISCLAIMER });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

comparisonRoutes.patch("/me/comparisons/:id", async (c) => {
  try {
    const uid = await userId(c);
    const id = Number(c.req.param("id"));
    const body = await c.req
      .json<Record<string, unknown>>()
      .catch(() => ({}) as Record<string, unknown>);
    const decisionRaw = String(body.decision ?? "").toLowerCase();
    if (!(COMPARISON_DECISIONS as readonly string[]).includes(decisionRaw)) {
      return c.json({ detail: "decision must be picked, none or undecided" }, 400);
    }
    const decision = decisionRaw as ComparisonDecision;
    const slot = Number(body.chosen_slot);
    const chosenSlot = decision === "picked" && Number.isInteger(slot) ? slot : null;
    if (decision === "picked" && chosenSlot == null) {
      return c.json({ detail: "chosen_slot is required" }, 400);
    }
    const reasons = jsonArray(body.reasons)
      .map((r) => String(r ?? "").trim().slice(0, 40))
      .filter(Boolean)
      .slice(0, 8);
    const note = typeof body.note === "string" && body.note.trim() ? body.note.trim().slice(0, 500) : null;
    const ok = await updateComparisonDecision(uid, id, { decision, chosenSlot, reasons, note });
    if (!ok) return c.json({ detail: "Comparison or picked product not found" }, 404);
    return c.json({ id, decision, chosen_slot: chosenSlot, reasons, note });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

comparisonRoutes.delete("/me/comparisons/:id", async (c) => {
  try {
    const uid = await userId(c);
    const keys = await deleteComparison(uid, Number(c.req.param("id")));
    if (keys == null) return c.json({ detail: "Comparison not found" }, 404);
    const r2 = getR2OrNull();
    if (r2) await Promise.all(keys.map((k) => r2.delete(k)));
    return c.json({ deleted: true });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

comparisonRoutes.get("/me/comparisons/:id/items/:slot/photo", async (c) => {
  try {
    const uid = await userId(c);
    const kind = c.req.query("kind") === "front" ? "front" : "label";
    const key = await getComparisonPhotoKey(
      uid,
      Number(c.req.param("id")),
      Number(c.req.param("slot")),
      kind
    );
    return photoResponse(key);
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});
