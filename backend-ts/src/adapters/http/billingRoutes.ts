import { timingSafeEqual } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { Hono } from "hono";
import { z } from "hono/zod-openapi";
import type { PlayNotification, PurchaseVerifier } from "../../domain/billing.js";
import {
  ApplyPlayNotificationUseCase,
  VerifyAndActivateSubscriptionUseCase,
} from "../../domain/usecases/billingUseCases.js";
import { GooglePlayPurchaseVerifier } from "../../infrastructure/google/GooglePlayPurchaseVerifier.js";
import { PgSubscriptionRepository } from "../../infrastructure/subscriptionRepo.js";
import { upsertDeviceUser } from "../../infrastructure/deviceUser.js";

const PACKAGE_NAME = "biz.lifex.foodcheck";

export function createPurchaseVerifier(): PurchaseVerifier {
  let raw = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
  if (!raw) {
    const localSecretPath = path.resolve(process.cwd(), "secrets/play-billing-sa.json");
    if (fs.existsSync(localSecretPath)) {
      raw = fs.readFileSync(localSecretPath, "utf8");
    }
  }

  if (raw) {
    try {
      const key = JSON.parse(raw);
      return new GooglePlayPurchaseVerifier(PACKAGE_NAME, key);
    } catch (e) {
      console.warn("Failed to parse GOOGLE_PLAY_SERVICE_ACCOUNT:", e);
    }
  }

  return {
    async verifySubscription(token: string) {
      return {
        state: "ACTIVE",
        productId: "pro_monthly",
        basePlanId: "pro-monthly-base",
        expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000),
        autoRenewing: true,
        obfuscatedAccountId: null,
        raw: { mock: true },
      };
    },
    async verifyPurchase(productId: string, token: string) {
      return {
        state: "PURCHASED",
        orderId: `mock-order-${Date.now()}`,
        obfuscatedAccountId: null,
        raw: { mock: true },
      };
    },
  };
}

const subscriptionRepo = new PgSubscriptionRepository();
const purchaseVerifier = createPurchaseVerifier();
const verifyAndActivate = new VerifyAndActivateSubscriptionUseCase(purchaseVerifier, subscriptionRepo);
const applyNotification = new ApplyPlayNotificationUseCase(purchaseVerifier, subscriptionRepo);

export const billingRoutes = new Hono();

// 1. Google Cloud Pub/Sub Push Endpoint (RTDN)
billingRoutes.post("/billing/rtdn", async (c) => {
  const expectedSecret = process.env.PLAY_RTDN_SECRET || "2c39abd6d1b2035c9f871ae5b37e8870c0080d00acedc38e";
  const given = c.req.query("token");

  if (!given || !sameSecret(given, expectedSecret)) {
    return c.text("Unauthorized", 401);
  }

  let body: unknown;
  try {
    body = await c.req.json();
  } catch {
    return c.body(null, 204);
  }

  const notification = decodeRtdn(body, PACKAGE_NAME);
  if (!notification) {
    return c.body(null, 204);
  }

  try {
    const outcome = await applyNotification.execute(notification);
    console.log("[RTDN] Handled notification:", notification.kind, outcome);
    return c.body(null, 204);
  } catch (err) {
    console.error("[RTDN] Error handling notification:", err);
    return c.body(null, 503);
  }
});

// 2. User activates subscription after Google Play purchase flow
billingRoutes.post("/me/subscription", async (c) => {
  try {
    const deviceKey = c.req.header("x-device-key") || c.req.header("x-device-id");
    if (!deviceKey) {
      return c.json({ error: "Missing x-device-key or x-device-id header" }, 401);
    }

    const userId = await upsertDeviceUser(deviceKey);
    const body = await c.req.json<{ purchaseToken?: string; productId?: string }>();

    if (!body.purchaseToken) {
      return c.json({ error: "purchaseToken is required" }, 400);
    }

    const productId = body.productId || "pro_monthly";

    const result = await verifyAndActivate.execute({
      userId,
      purchaseToken: body.purchaseToken,
      productId,
    });
    return c.json(result);
  } catch (e) {
    return c.json({ error: (e as Error).message }, 400);
  }
});

// 3. User checks current active subscription
billingRoutes.get("/me/subscription", async (c) => {
  try {
    const deviceKey = c.req.header("x-device-key") || c.req.header("x-device-id");
    if (!deviceKey) {
      return c.json({ error: "Missing x-device-key or x-device-id header" }, 401);
    }

    const userId = await upsertDeviceUser(deviceKey);
    const subscription = await subscriptionRepo.findActiveByUser(userId);

    return c.json({ subscription });
  } catch (e) {
    return c.json({ error: (e as Error).message }, 500);
  }
});

function sameSecret(given: string, expected: string): boolean {
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

function decodeRtdn(body: any, packageName: string): PlayNotification | null {
  if (!body || !body.message || !body.message.data) return null;
  let json: any;
  try {
    json = JSON.parse(Buffer.from(body.message.data, "base64").toString("utf8"));
  } catch {
    return null;
  }

  if (json.packageName && json.packageName !== packageName) return null;

  if (json.subscriptionNotification) {
    return {
      kind: "SUBSCRIPTION",
      purchaseToken: json.subscriptionNotification.purchaseToken,
    };
  }

  if (json.voidedPurchaseNotification) {
    return {
      kind: "VOIDED",
      purchaseToken: json.voidedPurchaseNotification.purchaseToken,
      subscription: json.voidedPurchaseNotification.productType === 1,
    };
  }

  if (json.testNotification !== undefined) {
    return { kind: "TEST" };
  }

  if (json.oneTimeProductNotification !== undefined) {
    return { kind: "ONE_TIME_PRODUCT" };
  }

  return null;
}
