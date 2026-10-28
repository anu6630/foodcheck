import { Hono } from "hono";
import { communityRoutes } from "./communityRoutes.js";
import { comparisonRoutes } from "./comparisonRoutes.js";
import { PgDataSourceRepository } from "../repositories/dataSourceRepository.js";
import { PgIngredientRepository } from "../repositories/ingredientRepository.js";
import { PgProductRepository } from "../repositories/productRepository.js";
import { PgRequestRepository } from "../repositories/requestRepository.js";
import { GeminiScanAgentService } from "../services/geminiScanAgent.js";
import { OpenFoodFactsService } from "../services/openFoodFacts.js";
import { getR2OrNull, newProductRequestImageKey } from "../services/r2Storage.js";
import { attachDownloadUrls, getSourceFile } from "../../infrastructure/sourceFiles.js";
import {
  GetIngredientDetailsUseCase,
  GetProductDetailsUseCase,
  ListDataSourcesUseCase,
  ScanWithAgentUseCase,
  SearchProductUseCase,
  UnifiedSearchUseCase,
} from "../../domain/usecases.js";
import { MeteredScanUseCase } from "../../domain/meteredScan.js";
import { ScanQuotaExceededError } from "../../domain/entitlements.js";
import { PgMeteredScanRepository } from "../../infrastructure/meteredScanRepo.js";
import { upsertDeviceUser } from "../../infrastructure/deviceUser.js";

export const publicRoutes = new Hono();
publicRoutes.route("/", communityRoutes);
publicRoutes.route("/", comparisonRoutes);

publicRoutes.post("/scan", async (c) => {
  try {
    const form = await c.req.parseBody({ all: true });
    const raw = form["images"];
    const files = (Array.isArray(raw) ? raw : raw ? [raw] : []).filter(
      (f): f is File => typeof f !== "string" && f instanceof File
    );
    if (!files.length) {
      return c.json({ detail: "At least one image is required" }, 400);
    }
    const images = await Promise.all(
      files.map(async (f) => Buffer.from(await f.arrayBuffer()))
    );
    const mockType = c.req.header("x-mock-image-type") ?? null;
    const clientSha256 = c.req.header("x-image-sha256") ?? null;

    const deviceKey = c.req.header("x-device-key") || c.req.header("x-device-id");
    let uid: string;
    try {
      uid = await upsertDeviceUser(deviceKey);
    } catch {
      return c.json({ detail: "X-Device-Key is required" }, 401);
    }

    const productRepo = new PgProductRepository();
    const ingredientRepo = new PgIngredientRepository();
    const agent = new GeminiScanAgentService(productRepo, ingredientRepo);
    const scanWithAgent = new ScanWithAgentUseCase(agent, ingredientRepo);
    const meteredRepo = new PgMeteredScanRepository();
    const meteredUsecase = new MeteredScanUseCase(meteredRepo, scanWithAgent);

    const result = await meteredUsecase.execute(images, uid, mockType, clientSha256);
    return c.json(result);
  } catch (e) {
    if (e instanceof ScanQuotaExceededError || (e as Error)?.name === "ScanQuotaExceededError") {
      const quotaErr = e as ScanQuotaExceededError;
      return c.json(
        {
          code: "SCAN_QUOTA_EXCEEDED",
          detail: "Monthly AI scan limit reached",
          quota: quotaErr.quota,
        },
        402
      );
    }
    // The scan agent already returns a user-facing reason (busy, unreadable, not configured).
    return c.json({ detail: (e as Error).message || "Image scanning failed" }, 500);
  }
});

publicRoutes.get("/search", async (c) => {
  try {
    const q = c.req.query("q") ?? "";
    const usecase = new UnifiedSearchUseCase(
      new PgProductRepository(),
      new PgIngredientRepository(),
      new OpenFoodFactsService()
    );
    return c.json(await usecase.execute(q));
  } catch (e) {
    return c.json({ detail: `Search failed: ${(e as Error).message}` }, 500);
  }
});

publicRoutes.get("/sources", async (c) => {
  const data = await new ListDataSourcesUseCase(new PgDataSourceRepository()).execute();
  return c.json({ sources: attachDownloadUrls(data.sources, new URL(c.req.url).origin) });
});

publicRoutes.get("/sources/:source_id/files/:file_id", async (c) => {
  const fileId = Number(c.req.param("file_id"));
  if (!Number.isFinite(fileId)) return c.json({ detail: "Invalid file" }, 400);
  const hit = await getSourceFile(c.req.param("source_id"), fileId);
  if (!hit) return c.json({ detail: "Archived file not found" }, 404);
  const r2 = getR2OrNull();
  if (!r2) return c.json({ detail: "Archive storage is not configured" }, 503);
  const buf = await r2.get(hit.r2Key);
  if (!buf) return c.json({ detail: "Archived file missing from storage" }, 404);
  const filename = hit.file.filename.replace(/[\r\n"]/g, "_");
  return new Response(new Uint8Array(buf), {
    status: 200,
    headers: {
      "content-type": hit.file.content_type || "application/octet-stream",
      "content-disposition": `inline; filename="${filename}"`,
      etag: `"${hit.file.sha256}"`,
      "cache-control": "public, max-age=31536000, immutable",
      "x-file-sha256": hit.file.sha256,
    },
  });
});

publicRoutes.get("/ingredients/:ingredient_id", async (c) => {
  const usecase = new GetIngredientDetailsUseCase(new PgIngredientRepository());
  const result = await usecase.execute(c.req.param("ingredient_id"));
  if (!result) return c.json({ detail: "Ingredient not found" }, 404);
  return c.json({
    ...result,
    sources: attachDownloadUrls(result.sources, new URL(c.req.url).origin),
  });
});

publicRoutes.get("/products/search", async (c) => {
  try {
    const q = c.req.query("q") ?? "";
    const usecase = new SearchProductUseCase(new PgProductRepository());
    return c.json(await usecase.execute(q));
  } catch (e) {
    return c.json({ detail: `Search failed: ${(e as Error).message}` }, 500);
  }
});

publicRoutes.get("/products/:barcode", async (c) => {
  const usecase = new GetProductDetailsUseCase(
    new PgProductRepository(),
    new OpenFoodFactsService(),
    new PgIngredientRepository()
  );
  const result = await usecase.execute(c.req.param("barcode"));
  if (!result) return c.json({ detail: "Product not found" }, 404);
  return c.json(result);
});

publicRoutes.post("/requests/product", async (c) => {
  try {
    const form = await c.req.parseBody();
    const productName = String(form.product_name || "");
    const countryCode = String(form.country_code || "");
    if (!productName || !countryCode) {
      return c.json({ detail: "product_name and country_code are required" }, 400);
    }

    const r2 = getR2OrNull();
    const upload = async (field: string): Promise<string | null> => {
      const file = form[field];
      if (!file || typeof file === "string" || !(file instanceof File)) return null;
      const buf = Buffer.from(await file.arrayBuffer());
      if (!buf.length) return null;
      if (!r2) {
        console.warn("R2 not configured; skipping image upload");
        return null;
      }
      const key = newProductRequestImageKey(
        (file.name.split(".").pop() || "jpg").toLowerCase().replace(/[^a-z0-9]/g, "") || "jpg"
      );
      await r2.put(key, buf, file.type || "image/jpeg");
      return key;
    };

    const repo = new PgRequestRepository();
    const req = await repo.createProductRequest({
      product_name: productName,
      country_code: countryCode,
      brand: form.brand ? String(form.brand) : null,
      barcode: form.barcode ? String(form.barcode) : null,
      category: form.category ? String(form.category) : null,
      ingredients_text: form.ingredients_text ? String(form.ingredients_text) : null,
      requester_notes: form.requester_notes ? String(form.requester_notes) : null,
      image_1_key: await upload("image_1"),
      image_2_key: await upload("image_2"),
      image_3_key: await upload("image_3"),
    });
    return c.json({ request_id: req.id, status: req.status }, 201);
  } catch (e) {
    return c.json({ detail: `Failed to submit product request: ${(e as Error).message}` }, 500);
  }
});

publicRoutes.post("/requests/ingredient", async (c) => {
  try {
    const body = await c.req.json<{
      ingredient_name: string;
      e_number?: string | null;
      category?: string | null;
      requester_notes?: string | null;
    }>();
    if (!body.ingredient_name) {
      return c.json({ detail: "ingredient_name is required" }, 400);
    }
    const repo = new PgRequestRepository();
    const req = await repo.createIngredientRequest(body);
    return c.json({ request_id: req.id, status: req.status }, 201);
  } catch (e) {
    return c.json(
      { detail: `Failed to submit ingredient request: ${(e as Error).message}` },
      500
    );
  }
});
