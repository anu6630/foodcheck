import { Hono } from "hono";
import { cors } from "hono/cors";
import { adminRoutes } from "../src/adapters/http/adminRoutes.js";
import { publicRoutes } from "../src/adapters/http/publicRoutes.js";
import { query } from "../src/infrastructure/db.js";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";

import {
  actionRateLimiter,
  adminRateLimiter,
  aiScanRateLimiter,
  generalRateLimiter,
  uploadRateLimiter,
} from "../src/adapters/http/rateLimiter.js";

const app = new Hono();

app.use(
  "*",
  cors({
    origin: "*",
    allowMethods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowHeaders: ["*"],
  })
);

app.use("*", async (_c, next) => {
  await ensureSchema();
  await next();
});

// Rate limiting middleware
app.use("/api/*", generalRateLimiter);
app.use("/api/scan", aiScanRateLimiter);
app.use("/api/requests/*", uploadRateLimiter);
app.use("/api/me/*", actionRateLimiter);
app.use("/api/stories/*", actionRateLimiter);
app.use("/api/admin/*", adminRateLimiter);

app.get("/", (c) =>
  c.json({
    message: "FoodCheck API",
    docs_url: "/docs",
    status: "healthy",
    catalog: "ux-sources-v3",
    gemini: "rest",
    gemini_configured: Boolean(process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY),
  })
);

app.get("/health", async (c) => {
  try {
    await query("SELECT 1");
    return c.json({ status: "healthy", database: "connected" });
  } catch (e) {
    return c.json(
      { status: "unhealthy", database: "error", detail: (e as Error).message },
      500
    );
  }
});

import { billingRoutes } from "../src/adapters/http/billingRoutes.js";

app.route("/api", publicRoutes);
app.route("/api", billingRoutes);
app.route("/api/admin", adminRoutes);

export default app;
