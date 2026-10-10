import { query } from "./db.js";
import type { AnnotatedIngredient } from "../domain/entities.js";
import type { IIngredientRepository } from "../domain/interfaces.js";
import { annotateIngredient, attachHealthFlags, DEFAULT_MARKET } from "../domain/usecases.js";

export const LABEL_DISCLAIMER =
  "Status is for the substances on this label, not a certificate for the brand.";

export type SnapshotName = { id: string | null; name: string };

export type CatalogCompany = { id: number; name: string };
export type CatalogBrand = {
  id: number;
  name: string;
  company_id: number | null;
  company_name: string | null;
};

export async function createScan(input: {
  userId: string;
  ingredientsText: string | null;
  snapshot: SnapshotName[];
  imageKey: string | null;
}): Promise<number> {
  const { rows } = await query<{ id: number }>(
    `INSERT INTO scans (user_id, ingredients_text, snapshot, image_key)
     VALUES ($1,$2,$3::jsonb,$4) RETURNING id`,
    [input.userId, input.ingredientsText, JSON.stringify(input.snapshot), input.imageKey]
  );
  return Number(rows[0]!.id);
}

export async function createShelfItem(input: {
  userId: string;
  scanId: number;
  displayName: string;
  brand: string | null;
  brandId: number | null;
  barcode: string | null;
  companyIds: number[];
}): Promise<number> {
  const { rows } = await query<{ id: number }>(
    `INSERT INTO shelf_items (user_id, scan_id, display_name, brand, brand_id, barcode)
     VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,
    [
      input.userId,
      input.scanId,
      input.displayName,
      input.brand,
      input.brandId,
      input.barcode,
    ]
  );
  const id = Number(rows[0]!.id);
  const uniqueCompanyIds = [...new Set(input.companyIds.filter((n) => Number.isFinite(n) && n > 0))];
  for (const companyId of uniqueCompanyIds) {
    await query(
      `INSERT INTO shelf_item_companies (shelf_item_id, company_id)
       SELECT $1, $2
       WHERE EXISTS (SELECT 1 FROM companies WHERE id = $2)
       ON CONFLICT DO NOTHING`,
      [id, companyId]
    );
  }
  return id;
}

export async function listShelf(userId: string, q: string) {
  const like = q.trim() ? `%${q.trim()}%` : null;
  const { rows } = await query<Record<string, unknown>>(
    `SELECT s.id, s.display_name, s.brand, s.brand_id, s.barcode, s.created_at,
            sc.snapshot, sc.ingredients_text, sc.image_key,
            COALESCE((
              SELECT json_agg(json_build_object('id', c.id, 'name', c.name) ORDER BY c.name)
              FROM shelf_item_companies sic
              JOIN companies c ON c.id = sic.company_id
              WHERE sic.shelf_item_id = s.id
            ), '[]'::json) AS companies
     FROM shelf_items s
     JOIN scans sc ON sc.id = s.scan_id
     WHERE s.user_id = $1
       AND (
         $2::text IS NULL
         OR s.display_name ILIKE $2
         OR COALESCE(s.brand,'') ILIKE $2
         OR EXISTS (
           SELECT 1 FROM shelf_item_companies sic
           JOIN companies c ON c.id = sic.company_id
           WHERE sic.shelf_item_id = s.id AND c.name ILIKE $2
         )
       )
     ORDER BY s.created_at DESC`,
    [userId, like]
  );
  return rows;
}

export async function getShelfItem(userId: string, id: number) {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT s.id, s.display_name, s.brand, s.brand_id, s.barcode, s.created_at,
            sc.snapshot, sc.ingredients_text, sc.image_key
     FROM shelf_items s
     JOIN scans sc ON sc.id = s.scan_id
     WHERE s.user_id = $1 AND s.id = $2`,
    [userId, id]
  );
  return rows[0] ?? null;
}

export async function listBrandNames(q: string, limit = 20): Promise<CatalogBrand[]> {
  const like = q.trim() ? `%${q.trim()}%` : null;
  const safeLimit = Math.min(Math.max(limit, 1), 100);
  const { rows } = await query<CatalogBrand>(
    `SELECT b.id, b.name, b.company_id,
            c.name AS company_name
     FROM brand_names b
     LEFT JOIN companies c ON c.id = b.company_id
     WHERE ($1::text IS NULL OR b.name ILIKE $1)
     ORDER BY b.name ASC
     LIMIT $2`,
    [like, safeLimit]
  );
  return rows.map((r) => ({
    id: Number(r.id),
    name: r.name,
    company_id: r.company_id == null ? null : Number(r.company_id),
    company_name: r.company_name ?? null,
  }));
}

export async function listCompanies(q: string, limit = 20): Promise<CatalogCompany[]> {
  const like = q.trim() ? `%${q.trim()}%` : null;
  const safeLimit = Math.min(Math.max(limit, 1), 100);
  const { rows } = await query<CatalogCompany>(
    `SELECT id, name FROM companies
     WHERE ($1::text IS NULL OR name ILIKE $1)
     ORDER BY name ASC
     LIMIT $2`,
    [like, safeLimit]
  );
  return rows.map((r) => ({ id: Number(r.id), name: r.name }));
}

export async function createStory(input: {
  userId: string;
  scanId: number;
  caption: string | null;
  snapshot: SnapshotName[];
}): Promise<number> {
  const { rows } = await query<{ id: number }>(
    `INSERT INTO stories (user_id, scan_id, caption, snapshot)
     VALUES ($1,$2,$3,$4::jsonb) RETURNING id`,
    [input.userId, input.scanId, input.caption, JSON.stringify(input.snapshot)]
  );
  return Number(rows[0]!.id);
}

export async function listStories(limit = 10, offset = 0) {
  const safeLimit = Math.min(Math.max(limit, 1), 30);
  const safeOffset = Math.max(offset, 0);
  const { rows } = await query<Record<string, unknown>>(
    `SELECT st.id, st.caption, st.snapshot, st.published_at, sc.image_key,
            (SELECT COUNT(*)::int FROM story_comments c WHERE c.story_id = st.id) AS comment_count,
            (SELECT COUNT(*)::int FROM story_likes l WHERE l.story_id = st.id) AS like_count
     FROM stories st
     JOIN scans sc ON sc.id = st.scan_id
     ORDER BY st.published_at DESC
     LIMIT $1 OFFSET $2`,
    [safeLimit, safeOffset]
  );
  return rows;
}

export async function getStory(id: number) {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT st.id, st.caption, st.snapshot, st.published_at, sc.image_key,
            (SELECT COUNT(*)::int FROM story_comments c WHERE c.story_id = st.id) AS comment_count,
            (SELECT COUNT(*)::int FROM story_likes l WHERE l.story_id = st.id) AS like_count
     FROM stories st
     JOIN scans sc ON sc.id = st.scan_id
     WHERE st.id = $1`,
    [id]
  );
  return rows[0] ?? null;
}

export async function getStoryImageKey(id: number): Promise<string | null> {
  const { rows } = await query<{ image_key: string | null }>(
    `SELECT sc.image_key FROM stories st JOIN scans sc ON sc.id = st.scan_id WHERE st.id = $1`,
    [id]
  );
  return rows[0]?.image_key ?? null;
}

export async function listComments(storyId: number) {
  const { rows } = await query<Record<string, unknown>>(
    `SELECT id, body, created_at FROM story_comments WHERE story_id = $1 ORDER BY created_at ASC`,
    [storyId]
  );
  return rows;
}

export async function addComment(storyId: number, userId: string, body: string) {
  const { rows } = await query<{ id: number }>(
    `INSERT INTO story_comments (story_id, user_id, body) VALUES ($1,$2,$3) RETURNING id`,
    [storyId, userId, body]
  );
  return Number(rows[0]!.id);
}

export async function toggleLike(storyId: number, userId: string): Promise<boolean> {
  const existing = await query<{ story_id: number }>(
    `SELECT story_id FROM story_likes WHERE story_id = $1 AND user_id = $2`,
    [storyId, userId]
  );
  if (existing.rows[0]) {
    await query(`DELETE FROM story_likes WHERE story_id = $1 AND user_id = $2`, [storyId, userId]);
    return false;
  }
  await query(`INSERT INTO story_likes (story_id, user_id) VALUES ($1,$2)`, [storyId, userId]);
  return true;
}

export function parseSnapshot(raw: unknown): SnapshotName[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((x) => {
      const o = x as { id?: unknown; name?: unknown };
      const name = typeof o.name === "string" ? o.name.trim() : "";
      if (!name) return null;
      return { id: typeof o.id === "string" ? o.id : null, name };
    })
    .filter((x): x is SnapshotName => x !== null);
}

export function parseCompanyIds(raw: unknown): number[] {
  if (Array.isArray(raw)) {
    return raw.map((x) => Number(x)).filter((n) => Number.isFinite(n) && n > 0);
  }
  if (typeof raw === "string" && raw.trim()) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.map((x) => Number(x)).filter((n) => Number.isFinite(n) && n > 0);
      }
    } catch {
      return raw
        .split(",")
        .map((x) => Number(x.trim()))
        .filter((n) => Number.isFinite(n) && n > 0);
    }
  }
  return [];
}

export async function annotateSnapshot(
  repo: IIngredientRepository,
  snap: SnapshotName[]
): Promise<AnnotatedIngredient[]> {
  const out: AnnotatedIngredient[] = [];
  for (const item of snap) {
    const hit = item.id
      ? await repo.getById(item.id)
      : await repo.findByNameOrSynonym(item.name);
    if (hit) out.push(annotateIngredient(hit));
    else {
      out.push({
        id: null,
        name: item.name,
        category: null,
        e_number: null,
        codes: [],
        severity: "gray",
        matched_bans: [],
        market_code: DEFAULT_MARKET,
        has_health_watch: false,
        health_flags: [],
      });
    }
  }
  return attachHealthFlags(repo, out);
}
