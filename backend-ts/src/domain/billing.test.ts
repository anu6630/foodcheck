import { describe, expect, it } from "vitest";
import type {
  PlayPurchaseVerification,
  PlaySubscriptionVerification,
  PurchaseVerifier,
  Subscription,
  SubscriptionRepository,
} from "./billing.js";
import {
  ApplyPlayNotificationUseCase,
  VerifyAndActivateSubscriptionUseCase,
} from "./usecases/billingUseCases.js";

class InMemorySubscriptionRepo implements SubscriptionRepository {
  private subs = new Map<string, Subscription>();

  async findByPurchaseToken(purchaseToken: string): Promise<Subscription | null> {
    return this.subs.get(purchaseToken) ?? null;
  }

  async findActiveByUser(userId: string): Promise<Subscription | null> {
    for (const sub of this.subs.values()) {
      if (
        sub.userId === userId &&
        (sub.status === "ACTIVE" || sub.status === "IN_GRACE_PERIOD") &&
        (!sub.expiresAt || sub.expiresAt.getTime() > Date.now())
      ) {
        return sub;
      }
    }
    return null;
  }

  async upsert(sub: {
    userId: string;
    purchaseToken: string;
    productId: string;
    basePlanId: string | null;
    status: Subscription["status"];
    autoRenewing: boolean;
    expiresAt: Date | null;
    rawState?: unknown;
  }): Promise<Subscription> {
    const existing = this.subs.get(sub.purchaseToken);
    const item: Subscription = {
      id: existing?.id ?? `sub-${Date.now()}`,
      userId: sub.userId,
      purchaseToken: sub.purchaseToken,
      productId: sub.productId,
      basePlanId: sub.basePlanId,
      status: sub.status,
      autoRenewing: sub.autoRenewing,
      startsAt: existing?.startsAt ?? new Date(),
      expiresAt: sub.expiresAt,
      updatedAt: new Date(),
    };
    this.subs.set(sub.purchaseToken, item);
    return item;
  }
}

class FakePurchaseVerifier implements PurchaseVerifier {
  constructor(
    private subVerification: PlaySubscriptionVerification = {
      state: "ACTIVE",
      productId: "pro_monthly",
      basePlanId: "pro-base",
      expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000),
      autoRenewing: true,
      obfuscatedAccountId: null,
      raw: {},
    }
  ) {}

  setVerification(v: PlaySubscriptionVerification) {
    this.subVerification = v;
  }

  async verifySubscription(purchaseToken: string): Promise<PlaySubscriptionVerification> {
    return this.subVerification;
  }

  async verifyPurchase(productId: string, purchaseToken: string): Promise<PlayPurchaseVerification> {
    return {
      state: "PURCHASED",
      orderId: "order-123",
      obfuscatedAccountId: null,
      raw: {},
    };
  }
}

describe("Billing Use Cases", () => {
  it("activates Pro plan on valid purchaseToken", async () => {
    const repo = new InMemorySubscriptionRepo();
    const verifier = new FakePurchaseVerifier();
    const useCase = new VerifyAndActivateSubscriptionUseCase(verifier, repo);

    const result = await useCase.execute({
      userId: "user-1",
      purchaseToken: "token-pro-123",
      productId: "pro_monthly",
    });

    expect(result.subscription.status).toBe("ACTIVE");
    expect(result.entitlement.plan).toBe("pro");
    expect(result.entitlement.scanQuota).toBe(50);
    expect(result.entitlement.adsSuppressed).toBe(true);
  });

  it("handles Family subscription plan", async () => {
    const repo = new InMemorySubscriptionRepo();
    const verifier = new FakePurchaseVerifier({
      state: "ACTIVE",
      productId: "family_monthly",
      basePlanId: "family-base",
      expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000),
      autoRenewing: true,
      obfuscatedAccountId: null,
      raw: {},
    });
    const useCase = new VerifyAndActivateSubscriptionUseCase(verifier, repo);

    const result = await useCase.execute({
      userId: "user-family-1",
      purchaseToken: "token-family-123",
      productId: "family_monthly",
    });

    expect(result.entitlement.plan).toBe("family");
    expect(result.entitlement.scanQuota).toBe(100);
    expect(result.entitlement.adsSuppressed).toBe(true);
  });

  it("downgrades to Free if subscription is expired", async () => {
    const repo = new InMemorySubscriptionRepo();
    const verifier = new FakePurchaseVerifier({
      state: "EXPIRED",
      productId: "pro_monthly",
      basePlanId: "pro-base",
      expiresAt: new Date(Date.now() - 1000),
      autoRenewing: false,
      obfuscatedAccountId: null,
      raw: {},
    });
    const useCase = new VerifyAndActivateSubscriptionUseCase(verifier, repo);

    const result = await useCase.execute({
      userId: "user-1",
      purchaseToken: "token-expired",
      productId: "pro_monthly",
    });

    expect(result.subscription.status).toBe("EXPIRED");
    expect(result.entitlement.plan).toBe("free");
    expect(result.entitlement.scanQuota).toBe(5);
    expect(result.entitlement.adsSuppressed).toBe(false);
  });

  it("handles Google Play RTDN test and renewal notifications", async () => {
    const repo = new InMemorySubscriptionRepo();
    const verifier = new FakePurchaseVerifier();
    const activate = new VerifyAndActivateSubscriptionUseCase(verifier, repo);
    const rtdn = new ApplyPlayNotificationUseCase(verifier, repo);

    // Initial activation
    await activate.execute({
      userId: "user-1",
      purchaseToken: "tok-1",
      productId: "pro_monthly",
    });

    // Test notification
    const testRes = await rtdn.execute({ kind: "TEST" });
    expect(testRes.handled).toBe(true);
    expect(testRes.detail).toBe("TEST_NOTIFICATION");

    // Subscription renewal / update notification
    const subRes = await rtdn.execute({ kind: "SUBSCRIPTION", purchaseToken: "tok-1" });
    expect(subRes.handled).toBe(true);
    expect(subRes.detail).toContain("SUBSCRIPTION_UPDATED_ACTIVE");

    // Voided notification
    const voidRes = await rtdn.execute({ kind: "VOIDED", purchaseToken: "tok-1", subscription: true });
    expect(voidRes.handled).toBe(true);
    expect(voidRes.detail).toBe("SUBSCRIPTION_VOIDED");

    const updated = await repo.findByPurchaseToken("tok-1");
    expect(updated?.status).toBe("EXPIRED");
  });
});
