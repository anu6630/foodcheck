import { describe, it, expect, beforeEach } from "vitest";
import { Hono } from "hono";
import {
  createRateLimiter,
  clearRateLimits,
  aiScanRateLimiter,
  uploadRateLimiter,
  actionRateLimiter,
  generalRateLimiter,
  adminRateLimiter,
} from "./rateLimiter.js";

describe("Rate Limiter Middleware", () => {
  beforeEach(() => {
    clearRateLimits();
  });

  it("allows requests under the limit and sets X-RateLimit headers", async () => {
    const app = new Hono();
    const limiter = createRateLimiter({
      windowMs: 60_000,
      max: 3,
      keyPrefix: "test",
    });
    app.get("/test", limiter, (c) => c.json({ ok: true }));

    const res1 = await app.request("/test", {
      headers: { "x-device-key": "device-1" },
    });
    expect(res1.status).toBe(200);
    expect(res1.headers.get("X-RateLimit-Limit")).toBe("3");
    expect(res1.headers.get("X-RateLimit-Remaining")).toBe("2");

    const res2 = await app.request("/test", {
      headers: { "x-device-key": "device-1" },
    });
    expect(res2.status).toBe(200);
    expect(res2.headers.get("X-RateLimit-Remaining")).toBe("1");

    const res3 = await app.request("/test", {
      headers: { "x-device-key": "device-1" },
    });
    expect(res3.status).toBe(200);
    expect(res3.headers.get("X-RateLimit-Remaining")).toBe("0");
  });

  it("blocks requests over the limit with 429 and Retry-After header", async () => {
    const app = new Hono();
    const limiter = createRateLimiter({
      windowMs: 60_000,
      max: 2,
      keyPrefix: "test-limit",
      message: "Rate limit reached.",
    });
    app.get("/test", limiter, (c) => c.json({ ok: true }));

    // Request 1 & 2 succeed
    await app.request("/test", { headers: { "x-device-key": "dev-a" } });
    await app.request("/test", { headers: { "x-device-key": "dev-a" } });

    // Request 3 is throttled
    const res = await app.request("/test", {
      headers: { "x-device-key": "dev-a" },
    });
    expect(res.status).toBe(429);
    expect(res.headers.get("Retry-After")).toBeDefined();
    const body = await res.json();
    expect(body.detail).toBe("Rate limit reached.");
    expect(body.code).toBe("RATE_LIMIT_EXCEEDED");
    expect(body.retryAfter).toBeGreaterThan(0);
  });

  it("isolates counters by device key", async () => {
    const app = new Hono();
    const limiter = createRateLimiter({
      windowMs: 60_000,
      max: 1,
      keyPrefix: "test-isolate",
    });
    app.get("/test", limiter, (c) => c.json({ ok: true }));

    // Dev 1 uses its quota
    const res1 = await app.request("/test", {
      headers: { "x-device-key": "user-alpha" },
    });
    expect(res1.status).toBe(200);

    const res1Again = await app.request("/test", {
      headers: { "x-device-key": "user-alpha" },
    });
    expect(res1Again.status).toBe(429);

    // Dev 2 still has quota
    const res2 = await app.request("/test", {
      headers: { "x-device-key": "user-beta" },
    });
    expect(res2.status).toBe(200);
  });

  it("falls back to IP when device key is not provided", async () => {
    const app = new Hono();
    const limiter = createRateLimiter({
      windowMs: 60_000,
      max: 1,
      keyPrefix: "test-ip",
    });
    app.get("/test", limiter, (c) => c.json({ ok: true }));

    const res1 = await app.request("/test", {
      headers: { "cf-connecting-ip": "203.0.113.195" },
    });
    expect(res1.status).toBe(200);

    const res2 = await app.request("/test", {
      headers: { "cf-connecting-ip": "203.0.113.195" },
    });
    expect(res2.status).toBe(429);

    // Different IP works
    const res3 = await app.request("/test", {
      headers: { "cf-connecting-ip": "198.51.100.42" },
    });
    expect(res3.status).toBe(200);
  });

  it("skips rate limiting when skip option evaluates to true", async () => {
    const app = new Hono();
    const limiter = createRateLimiter({
      windowMs: 60_000,
      max: 1,
      keyPrefix: "test-skip",
      skip: (c) => c.req.path === "/health",
    });
    app.get("/health", limiter, (c) => c.json({ status: "healthy" }));

    // Multiple health checks bypass rate limiter
    for (let i = 0; i < 5; i++) {
      const res = await app.request("/health");
      expect(res.status).toBe(200);
    }
  });

  it("exports correctly configured presets", () => {
    expect(aiScanRateLimiter).toBeDefined();
    expect(uploadRateLimiter).toBeDefined();
    expect(actionRateLimiter).toBeDefined();
    expect(adminRateLimiter).toBeDefined();
    expect(generalRateLimiter).toBeDefined();
  });
});
