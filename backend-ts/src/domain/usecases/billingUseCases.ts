import type {
  PlayNotification,
  PurchaseVerifier,
  Subscription,
  SubscriptionRepository,
} from "../billing.js";
import { type Entitlement, quotaFor } from "../entitlements.js";

export class VerifyAndActivateSubscriptionUseCase {
  constructor(
    private readonly verifier: PurchaseVerifier,
    private readonly repo: SubscriptionRepository
  ) {}

  async execute(input: {
    userId: string;
    purchaseToken: string;
    productId: string;
  }): Promise<{ subscription: Subscription; entitlement: Entitlement }> {
    const verified = await this.verifier.verifySubscription(input.purchaseToken);

    const subscription = await this.repo.upsert({
      userId: input.userId,
      purchaseToken: input.purchaseToken,
      productId: verified.productId || input.productId,
      basePlanId: verified.basePlanId,
      status: verified.state,
      autoRenewing: verified.autoRenewing,
      expiresAt: verified.expiresAt,
      rawState: verified.raw,
    });

    const isPro =
      (subscription.status === "ACTIVE" || subscription.status === "IN_GRACE_PERIOD") &&
      (!subscription.expiresAt || subscription.expiresAt.getTime() > Date.now());

    const plan = isPro
      ? subscription.productId.includes("family")
        ? "family"
        : "pro"
      : "free";

    return {
      subscription,
      entitlement: {
        plan,
        scanQuota: quotaFor(plan),
        adsSuppressed: plan !== "free",
      },
    };
  }
}

export class ApplyPlayNotificationUseCase {
  constructor(
    private readonly verifier: PurchaseVerifier,
    private readonly repo: SubscriptionRepository
  ) {}

  async execute(notification: PlayNotification): Promise<{ handled: boolean; detail?: string }> {
    if (notification.kind === "TEST") {
      return { handled: true, detail: "TEST_NOTIFICATION" };
    }

    if (notification.kind === "ONE_TIME_PRODUCT") {
      return { handled: true, detail: "ONE_TIME_PRODUCT_IGNORED" };
    }

    if (notification.kind === "SUBSCRIPTION" || notification.kind === "VOIDED") {
      const existing = await this.repo.findByPurchaseToken(notification.purchaseToken);
      if (!existing) {
        return { handled: false, detail: "TOKEN_NOT_FOUND" };
      }

      if (notification.kind === "VOIDED") {
        await this.repo.upsert({
          userId: existing.userId,
          purchaseToken: existing.purchaseToken,
          productId: existing.productId,
          basePlanId: existing.basePlanId,
          status: "EXPIRED",
          autoRenewing: false,
          expiresAt: new Date(),
        });
        return { handled: true, detail: "SUBSCRIPTION_VOIDED" };
      }

      const verified = await this.verifier.verifySubscription(notification.purchaseToken);
      await this.repo.upsert({
        userId: existing.userId,
        purchaseToken: existing.purchaseToken,
        productId: verified.productId || existing.productId,
        basePlanId: verified.basePlanId,
        status: verified.state,
        autoRenewing: verified.autoRenewing,
        expiresAt: verified.expiresAt,
        rawState: verified.raw,
      });

      return { handled: true, detail: `SUBSCRIPTION_UPDATED_${verified.state}` };
    }

    return { handled: false };
  }
}
