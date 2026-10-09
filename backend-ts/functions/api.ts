import { Hono } from "hono";
import { cors } from "hono/cors";
import { adminRoutes } from "../src/adapters/http/adminRoutes.js";
import { publicRoutes } from "../src/adapters/http/publicRoutes.js";
import { query } from "../src/infrastructure/db.js";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";

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

app.route("/api", publicRoutes);
app.route("/api/admin", adminRoutes);

export default app;
