import type {
  PlayPurchaseVerification,
  PlaySubscriptionVerification,
  PurchaseVerifier,
  SubscriptionStatus,
} from "../../domain/billing.js";
import {
  ServiceAccountTokenSource,
  type ServiceAccountKey,
} from "./ServiceAccountTokenSource.js";

const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

export class GooglePlayPurchaseVerifier implements PurchaseVerifier {
  private readonly tokens: ServiceAccountTokenSource;

  constructor(
    private readonly packageName: string,
    key: ServiceAccountKey,
    private readonly fetchFn: typeof fetch = fetch,
    now: () => number = Date.now
  ) {
    this.tokens = new ServiceAccountTokenSource(key, SCOPE, fetchFn, now);
  }

  async verifySubscription(purchaseToken: string): Promise<PlaySubscriptionVerification> {
    const res = await this.call(
      "GET",
      `${API}/${this.packageName}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`
    );

    if (res.status === 400 || res.status === 404 || res.status === 410) {
      throw new Error("Google Play does not know this subscription");
    }

    if (!res.ok) {
      throw new Error(`Google Play verify subscription error: ${res.status} ${await res.text()}`);
    }

    const body = (await res.json()) as {
      subscriptionState?: string;
      lineItems?: {
        productId?: string;
        expiryTime?: string;
        autoRenewingPlan?: { autoRenewEnabled?: boolean };
        offerDetails?: { basePlanId?: string };
      }[];
      externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
    };

    const item = [...(body.lineItems ?? [])].sort(
      (a, b) => Date.parse(b.expiryTime ?? "") - Date.parse(a.expiryTime ?? "")
    )[0];

    return {
      state: toSubscriptionStatus(body.subscriptionState),
      productId: item?.productId ?? null,
      basePlanId: item?.offerDetails?.basePlanId ?? null,
      expiresAt: item?.expiryTime ? new Date(item.expiryTime) : null,
      autoRenewing: item?.autoRenewingPlan?.autoRenewEnabled ?? false,
      obfuscatedAccountId: body.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
      raw: body,
    };
  }

  async verifyPurchase(productId: string, purchaseToken: string): Promise<PlayPurchaseVerification> {
    const res = await this.call(
      "GET",
      `${API}/${this.packageName}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`
    );

    if (res.status === 400 || res.status === 404 || res.status === 410) {
      throw new Error("Google Play does not know this purchase");
    }

    if (!res.ok) {
      throw new Error(`Google Play verify purchase error: ${res.status} ${await res.text()}`);
    }

    const body = (await res.json()) as {
      purchaseState?: number;
      orderId?: string;
      obfuscatedExternalAccountId?: string;
    };

    return {
      state: body.purchaseState === 0 ? "PURCHASED" : body.purchaseState === 2 ? "PENDING" : "CANCELED",
      orderId: body.orderId ?? null,
      obfuscatedAccountId: body.obfuscatedExternalAccountId ?? null,
      raw: body,
    };
  }

  private async call(method: "GET" | "POST", url: string): Promise<Response> {
    const token = await this.tokens.token();
    return this.fetchFn(url, {
      method,
      headers: { authorization: `Bearer ${token}` },
    });
  }
}

function toSubscriptionStatus(state?: string): SubscriptionStatus {
  switch (state) {
    case "SUBSCRIPTION_STATE_ACTIVE":
      return "ACTIVE";
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
    case "SUBSCRIPTION_STATE_ON_HOLD":
      return "IN_GRACE_PERIOD";
    case "SUBSCRIPTION_STATE_PAUSED":
      return "PAUSED";
    case "SUBSCRIPTION_STATE_CANCELED":
      return "CANCELED";
    case "SUBSCRIPTION_STATE_EXPIRED":
      return "EXPIRED";
    case "SUBSCRIPTION_STATE_PENDING":
      return "PENDING";
    default:
      return "ACTIVE";
  }
}
