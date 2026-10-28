import type { Context, MiddlewareHandler } from "hono";

export interface RateLimitOptions {
  windowMs: number;
  max: number;
  message?: string;
  keyPrefix?: string;
  useDeviceKey?: boolean;
  skip?: (c: Context) => boolean;
}

interface RateLimitRecord {
  count: number;
  resetAt: number;
}

// In-memory store per function isolate
const store = new Map<string, RateLimitRecord>();

// Periodic garbage collection for expired entries (every 60s)
let lastCleanup = Date.now();
function cleanupExpired() {
  const now = Date.now();
  if (now - lastCleanup < 60_000) return;
  lastCleanup = now;
  for (const [key, record] of store.entries()) {
    if (record.resetAt <= now) {
      store.delete(key);
    }
  }
}

/** Reset in-memory rate limit store (useful for tests). */
export function clearRateLimits(): void {
  store.clear();
}

/** Extract client IP address safely from headers. */
export function getClientIp(c: Context): string {
  const forwarded = c.req.header("x-forwarded-for");
  if (forwarded) {
    const first = forwarded.split(",")[0].trim();
    if (first) return first;
  }
  return (
    c.req.header("cf-connecting-ip") ||
    c.req.header("x-real-ip") ||
    "127.0.0.1"
  );
}

/** Extract device ID/key from headers or query param. */
export function getDeviceId(c: Context): string | undefined {
  const headerId =
    c.req.header("x-device-key") ||
    c.req.header("x-device-id") ||
    c.req.header("X-Device-Key") ||
    c.req.header("X-Device-Id");
  if (headerId && headerId.trim()) return headerId.trim();
  const queryId = c.req.query("deviceKey") || c.req.query("deviceId");
  if (queryId && queryId.trim()) return queryId.trim();
  return undefined;
}

/**
 * Creates a rate limiting middleware.
 */
export function createRateLimiter(options: RateLimitOptions): MiddlewareHandler {
  const {
    windowMs,
    max,
    message = "Too many requests. Please try again later.",
    keyPrefix = "rl",
    useDeviceKey = true,
    skip,
  } = options;

  return async (c, next) => {
    // If request matches skip condition (e.g. health checks), bypass
    if (skip && skip(c)) {
      return next();
    }

    cleanupExpired();

    const ip = getClientIp(c);
    const deviceId = useDeviceKey ? getDeviceId(c) : undefined;

    // Dual-key preference: use device ID when available, fallback to IP
    const identifier = deviceId ? `dev:${deviceId}` : `ip:${ip}`;
    const key = `${keyPrefix}:${identifier}`;

    const now = Date.now();
    let record = store.get(key);

    if (!record || record.resetAt <= now) {
      record = { count: 1, resetAt: now + windowMs };
      store.set(key, record);
    } else {
      record.count += 1;
    }

    const remaining = Math.max(0, max - record.count);
    const resetSeconds = Math.ceil(record.resetAt / 1000);
    const retryAfter = Math.max(1, Math.ceil((record.resetAt - now) / 1000));

    c.header("X-RateLimit-Limit", String(max));
    c.header("X-RateLimit-Remaining", String(remaining));
    c.header("X-RateLimit-Reset", String(resetSeconds));

    if (record.count > max) {
      c.header("Retry-After", String(retryAfter));
      return c.json(
        {
          detail: message,
          code: "RATE_LIMIT_EXCEEDED",
          retryAfter,
        },
        429
      );
    }

    await next();
  };
}

/**
 * Tier 1: AI Scan Limiter
 * 10 operations per minute per device (or IP) to protect Gemini API quota and compute.
 */
export const aiScanRateLimiter = createRateLimiter({
  keyPrefix: "ai-scan",
  windowMs: 60 * 1000,
  max: 10,
  useDeviceKey: true,
  message: "Scan limit reached. Please wait a minute before scanning another product label.",
});

/**
 * Tier 2: Product & Ingredient Requests Limiter
 * 15 requests per 10 minutes per device/client to prevent R2 image flooding and queue spam.
 */
export const uploadRateLimiter = createRateLimiter({
  keyPrefix: "upload-req",
  windowMs: 10 * 60 * 1000,
  max: 15,
  useDeviceKey: true,
  message: "Submission limit reached. Please wait before submitting more product requests.",
});

/**
 * Tier 3: User Actions (Shelf, Comparisons, Stories, Likes, Comments)
 * 60 operations per minute per device (or IP fallback).
 */
export const actionRateLimiter = createRateLimiter({
  keyPrefix: "action",
  windowMs: 60 * 1000,
  max: 60,
  useDeviceKey: true,
  message: "You have made too many requests. Please wait a moment before trying again.",
});

/**
 * Tier 4: Admin Limiter
 * 15 requests per minute per IP to prevent token brute-forcing.
 */
export const adminRateLimiter = createRateLimiter({
  keyPrefix: "admin",
  windowMs: 60 * 1000,
  max: 15,
  useDeviceKey: false,
  message: "Too many admin requests. Please wait before retrying.",
});

/**
 * Tier 5: General Public & Read Limiter
 * 180 requests per minute per client for search, product info, ingredients, and sources.
 * Health check `/health` and root `/` are bypassed.
 */
export const generalRateLimiter = createRateLimiter({
  keyPrefix: "general",
  windowMs: 60 * 1000,
  max: 180,
  useDeviceKey: true,
  skip: (c) => {
    const path = c.req.path;
    return path === "/" || path === "/health";
  },
  message: "High traffic detected. Please slow down your requests.",
});
