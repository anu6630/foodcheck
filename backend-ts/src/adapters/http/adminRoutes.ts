import { Hono } from "hono";
import { createMiddleware } from "hono/factory";
import { PgIngredientRepository } from "../repositories/ingredientRepository.js";
import { PgRequestRepository } from "../repositories/requestRepository.js";
import { getR2OrNull } from "../services/r2Storage.js";

export const adminRoutes = new Hono();

const requireAdmin = createMiddleware(async (c, next) => {
  const token = c.req.header("x-admin-token");
  const expected = process.env.ADMIN_TOKEN || "123";
  if (token !== expected) {
    return c.json({ detail: "Forbidden: invalid admin token" }, 403);
  }
  await next();
});

adminRoutes.use("*", requireAdmin);

function iso(d: Date | string | null | undefined): string | null {
  if (!d) return null;
  return typeof d === "string" ? d : d.toISOString();
}

async function keyToBase64(key: string | null): Promise<string | null> {
  if (!key) return null;
  const r2 = getR2OrNull();
  if (!r2) return null;
  const buf = await r2.get(key);
  return buf ? buf.toString("base64") : null;
}

adminRoutes.get("/requests/products", async (c) => {
  const status = c.req.query("status");
  const items = await new PgRequestRepository().listProductRequests(status);
  return c.json(
    items.map((r) => ({
      id: r.id,
      status: r.status,
      product_name: r.product_name,
      brand: r.brand,
      barcode: r.barcode,
      country_code: r.country_code,
      category: r.category,
      created_at: iso(r.created_at),
    }))
  );
});

adminRoutes.get("/requests/products/:request_id", async (c) => {
  const id = Number(c.req.param("request_id"));
  const r = await new PgRequestRepository().getProductRequest(id);
  if (!r) return c.json({ detail: "Product request not found" }, 404);
  return c.json({
    id: r.id,
    status: r.status,
    product_name: r.product_name,
    brand: r.brand,
    barcode: r.barcode,
    country_code: r.country_code,
    category: r.category,
    ingredients_text: r.ingredients_text,
    requester_notes: r.requester_notes,
    created_at: iso(r.created_at),
    image_1: await keyToBase64(r.image_1_key),
    image_2: await keyToBase64(r.image_2_key),
    image_3: await keyToBase64(r.image_3_key),
  });
});

adminRoutes.put("/requests/products/:request_id/status", async (c) => {
  const body = await c.req.json<{ status: string }>();
  const allowed = ["pending", "approved", "rejected"];
  if (!allowed.includes(body.status)) {
    return c.json({ detail: `status must be one of ${allowed.join(", ")}` }, 422);
  }
  const updated = await new PgRequestRepository().updateProductRequestStatus(
    Number(c.req.param("request_id")),
    body.status
  );
  if (!updated) return c.json({ detail: "Product request not found" }, 404);
  return c.json({ id: updated.id, status: updated.status });
});

adminRoutes.get("/requests/ingredients", async (c) => {
  const status = c.req.query("status");
  const items = await new PgRequestRepository().listIngredientRequests(status);
  return c.json(
    items.map((r) => ({
      id: r.id,
      status: r.status,
      ingredient_name: r.ingredient_name,
      e_number: r.e_number,
      category: r.category,
      created_at: iso(r.created_at),
    }))
  );
});

adminRoutes.get("/requests/ingredients/:request_id", async (c) => {
  const r = await new PgRequestRepository().getIngredientRequest(
    Number(c.req.param("request_id"))
  );
  if (!r) return c.json({ detail: "Ingredient request not found" }, 404);
  return c.json({
    id: r.id,
    status: r.status,
    ingredient_name: r.ingredient_name,
    e_number: r.e_number,
    category: r.category,
    requester_notes: r.requester_notes,
    created_at: iso(r.created_at),
  });
});

adminRoutes.put("/requests/ingredients/:request_id/status", async (c) => {
  const body = await c.req.json<{ status: string }>();
  const allowed = ["pending", "approved", "rejected"];
  if (!allowed.includes(body.status)) {
    return c.json({ detail: `status must be one of ${allowed.join(", ")}` }, 422);
  }
  const updated = await new PgRequestRepository().updateIngredientRequestStatus(
    Number(c.req.param("request_id")),
    body.status
  );
  if (!updated) return c.json({ detail: "Ingredient request not found" }, 404);
  return c.json({ id: updated.id, status: updated.status });
});

adminRoutes.put("/products/:product_id/publish", async (c) => {
  const product = await new PgRequestRepository().publishProduct(
    Number(c.req.param("product_id"))
  );
  if (!product) return c.json({ detail: "Product not found" }, 404);
  return c.json({ id: product.id, name: product.name, published: true });
});

adminRoutes.post("/products", async (c) => {
  const body = await c.req.json<{
    name: string;
    barcode: string;
    brand?: string | null;
    category?: string | null;
    source?: string;
    published?: boolean;
  }>();
  const created = await new PgRequestRepository().createProductDirect(body);
  return c.json(created, 201);
});

adminRoutes.post("/ingredients", async (c) => {
  try {
    const body = await c.req.json<{
      id: string;
      name: string;
      category?: string | null;
      e_number?: string | null;
      inci_name?: string | null;
      cas_number?: string | null;
      bans?: Array<{
        country_code: string;
        status?: string;
        condition?: string | null;
        regulation_ref?: string | null;
      }>;
    }>();
    const repo = new PgIngredientRepository();
    const existing = await repo.getById(body.id);
    if (existing) return c.json({ detail: "Ingredient already exists" }, 409);
    const saved = await repo.save({
      id: body.id,
      name: body.name,
      category: body.category ?? null,
      e_number: body.e_number ?? null,
      inci_name: body.inci_name ?? null,
      cas_number: body.cas_number ?? null,
      names: [],
      bans: (body.bans || []).map((b) => ({
        country_code: b.country_code,
        status: b.status || "restricted",
        condition: b.condition ?? null,
        regulation_ref: b.regulation_ref ?? null,
      })),
    });
    return c.json({ id: saved.id, name: saved.name }, 201);
  } catch (e) {
    return c.json({ detail: (e as Error).message }, 500);
  }
});

adminRoutes.post("/bans", async (c) => {
  const body = await c.req.json<{
    ingredient_id: string;
    country_code: string;
    status: string;
    condition?: string | null;
    regulation_ref?: string | null;
  }>();
  const ban = await new PgRequestRepository().createBanDirect(body);
  return c.json(ban, 201);
});
