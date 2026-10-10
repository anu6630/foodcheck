import { Hono } from "hono";
import { randomUUID } from "node:crypto";
import { PgIngredientRepository } from "../repositories/ingredientRepository.js";
import { getR2OrNull } from "../services/r2Storage.js";
import { upsertDeviceUser } from "../../infrastructure/deviceUser.js";
import {
  LABEL_DISCLAIMER,
  addComment,
  annotateSnapshot,
  createScan,
  createShelfItem,
  createStory,
  getShelfItem,
  getStory,
  getStoryImageKey,
  listBrandNames,
  listComments,
  listCompanies,
  listShelf,
  listStories,
  parseCompanyIds,
  parseSnapshot,
  toggleLike,
} from "../../infrastructure/community.js";

export const communityRoutes = new Hono();

async function userId(c: { req: { header: (n: string) => string | undefined } }): Promise<string> {
  return upsertDeviceUser(c.req.header("x-device-key"));
}

function httpError(e: unknown): { detail: string; status: 400 | 401 | 500 } {
  const status = (e as { status?: number }).status;
  if (status === 401) return { detail: (e as Error).message, status: 401 };
  return { detail: (e as Error).message, status: 500 };
}

async function readImage(form: Record<string, unknown>): Promise<{ buf: Buffer; ext: string } | null> {
  const file = form.image;
  if (!file || typeof file === "string" || !(file instanceof File)) return null;
  const buf = Buffer.from(await file.arrayBuffer());
  if (!buf.length) return null;
  const ext = (file.name.split(".").pop() || "jpg").toLowerCase().replace(/[^a-z0-9]/g, "") || "jpg";
  return { buf, ext };
}

async function putScanImage(userId: string, file: { buf: Buffer; ext: string }): Promise<string | null> {
  const r2 = getR2OrNull();
  if (!r2) return null;
  const key = `scans/${userId}/${randomUUID()}.${file.ext}`;
  await r2.put(key, file.buf, file.ext === "png" ? "image/png" : "image/jpeg");
  return key;
}

communityRoutes.get("/brands", async (c) => {
  try {
    const brands = await listBrandNames(c.req.query("q") ?? "", Number(c.req.query("limit") || 20));
    return c.json({ brands });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.get("/companies", async (c) => {
  try {
    const companies = await listCompanies(c.req.query("q") ?? "", Number(c.req.query("limit") || 20));
    return c.json({ companies });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.post("/me/shelf", async (c) => {
  try {
    const uid = await userId(c);
    const form = await c.req.parseBody();
    const displayName = String(form.display_name || "").trim();
    if (!displayName) return c.json({ detail: "display_name is required" }, 400);
    const snapshot = parseSnapshot(
      typeof form.snapshot === "string" ? JSON.parse(form.snapshot || "[]") : []
    );
    const brandRaw = form.brand ? String(form.brand).trim() : "";
    const brandIdRaw = form.brand_id ? Number(form.brand_id) : NaN;
    const brandId = Number.isFinite(brandIdRaw) && brandIdRaw > 0 ? brandIdRaw : null;
    const companyIds = parseCompanyIds(form.company_ids);
    const img = await readImage(form as Record<string, unknown>);
    const imageKey = img ? await putScanImage(uid, img) : null;
    const scanId = await createScan({
      userId: uid,
      ingredientsText: form.ingredients_text ? String(form.ingredients_text) : null,
      snapshot,
      imageKey,
    });
    const id = await createShelfItem({
      userId: uid,
      scanId,
      displayName,
      brand: brandRaw || null,
      brandId,
      barcode: form.barcode ? String(form.barcode) : null,
      companyIds,
    });
    return c.json({ id, disclaimer: LABEL_DISCLAIMER }, 201);
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.get("/me/shelf", async (c) => {
  try {
    const uid = await userId(c);
    const repo = new PgIngredientRepository();
    const rows = await listShelf(uid, c.req.query("q") ?? "");
    const items = [];
    for (const row of rows) {
      let companies = row.companies;
      if (typeof companies === "string") {
        try {
          companies = JSON.parse(companies);
        } catch {
          companies = [];
        }
      }
      if (!Array.isArray(companies)) companies = [];
      items.push({
        id: Number(row.id),
        display_name: row.display_name,
        brand: row.brand,
        brand_id: row.brand_id == null ? null : Number(row.brand_id),
        barcode: row.barcode,
        created_at: row.created_at,
        has_photo: Boolean(row.image_key),
        companies,
        ingredients: await annotateSnapshot(repo, parseSnapshot(row.snapshot)),
        disclaimer: LABEL_DISCLAIMER,
      });
    }
    return c.json({ items });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.get("/me/shelf/:id/photo", async (c) => {
  try {
    const uid = await userId(c);
    const row = await getShelfItem(uid, Number(c.req.param("id")));
    const key = row?.image_key as string | undefined;
    if (!key) return c.json({ detail: "Photo not found" }, 404);
    const r2 = getR2OrNull();
    if (!r2) return c.json({ detail: "Storage not configured" }, 503);
    const buf = await r2.get(key);
    if (!buf) return c.json({ detail: "Photo missing" }, 404);
    return new Response(new Uint8Array(buf), { headers: { "content-type": "image/jpeg" } });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.post("/stories", async (c) => {
  try {
    const uid = await userId(c);
    const form = await c.req.parseBody();
    const snapshot = parseSnapshot(
      typeof form.snapshot === "string" ? JSON.parse(form.snapshot || "[]") : []
    );
    const img = await readImage(form as Record<string, unknown>);
    const imageKey = img ? await putScanImage(uid, img) : null;
    const scanId = await createScan({
      userId: uid,
      ingredientsText: form.ingredients_text ? String(form.ingredients_text) : null,
      snapshot,
      imageKey,
    });
    const id = await createStory({
      userId: uid,
      scanId,
      caption: form.caption ? String(form.caption).slice(0, 500) : null,
      snapshot,
    });
    return c.json({ id, disclaimer: LABEL_DISCLAIMER }, 201);
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.get("/stories", async (c) => {
  const repo = new PgIngredientRepository();
  const limit = Math.min(Math.max(Number(c.req.query("limit") || 10), 1), 30);
  const offset = Math.max(Number(c.req.query("offset") || 0), 0);
  const rows = await listStories(limit + 1, offset);
  const hasMore = rows.length > limit;
  const page = hasMore ? rows.slice(0, limit) : rows;
  const stories = [];
  for (const row of page) {
    stories.push({
      id: Number(row.id),
      caption: row.caption,
      published_at: row.published_at,
      comment_count: Number(row.comment_count || 0),
      like_count: Number(row.like_count || 0),
      has_photo: Boolean(row.image_key),
      ingredients: await annotateSnapshot(repo, parseSnapshot(row.snapshot)),
      disclaimer: LABEL_DISCLAIMER,
    });
  }
  return c.json({ stories, has_more: hasMore });
});

communityRoutes.get("/stories/:id", async (c) => {
  const row = await getStory(Number(c.req.param("id")));
  if (!row) return c.json({ detail: "Story not found" }, 404);
  const repo = new PgIngredientRepository();
  return c.json({
    id: Number(row.id),
    caption: row.caption,
    published_at: row.published_at,
    comment_count: Number(row.comment_count || 0),
    like_count: Number(row.like_count || 0),
    has_photo: Boolean(row.image_key),
    ingredients: await annotateSnapshot(repo, parseSnapshot(row.snapshot)),
    comments: await listComments(Number(row.id)),
    disclaimer: LABEL_DISCLAIMER,
  });
});

communityRoutes.get("/stories/:id/photo", async (c) => {
  const key = await getStoryImageKey(Number(c.req.param("id")));
  if (!key) return c.json({ detail: "Photo not found" }, 404);
  const r2 = getR2OrNull();
  if (!r2) return c.json({ detail: "Storage not configured" }, 503);
  const buf = await r2.get(key);
  if (!buf) return c.json({ detail: "Photo missing" }, 404);
  return new Response(new Uint8Array(buf), { headers: { "content-type": "image/jpeg" } });
});

communityRoutes.get("/stories/:id/comments", async (c) => {
  return c.json({ comments: await listComments(Number(c.req.param("id"))) });
});

communityRoutes.post("/stories/:id/comments", async (c) => {
  try {
    const uid = await userId(c);
    const body = await c.req.json<{ body?: string }>();
    const text = (body.body || "").trim();
    if (!text) return c.json({ detail: "body is required" }, 400);
    const story = await getStory(Number(c.req.param("id")));
    if (!story) return c.json({ detail: "Story not found" }, 404);
    const id = await addComment(Number(c.req.param("id")), uid, text.slice(0, 1000));
    return c.json({ id }, 201);
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});

communityRoutes.post("/stories/:id/like", async (c) => {
  try {
    const uid = await userId(c);
    const story = await getStory(Number(c.req.param("id")));
    if (!story) return c.json({ detail: "Story not found" }, 404);
    const liked = await toggleLike(Number(c.req.param("id")), uid);
    return c.json({ liked });
  } catch (e) {
    const err = httpError(e);
    return c.json({ detail: err.detail }, err.status);
  }
});
