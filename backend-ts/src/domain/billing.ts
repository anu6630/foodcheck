export type SubscriptionStatus =
  | 'ACTIVE'
  | 'CANCELED'
  | 'EXPIRED'
  | 'IN_GRACE_PERIOD'
  | 'PAUSED'
  | 'PENDING';

export interface Subscription {
  id: string;
  userId: string;
  purchaseToken: string;
  productId: string;
  basePlanId: string | null;
  status: SubscriptionStatus;
  autoRenewing: boolean;
  startsAt: Date;
  expiresAt: Date | null;
  updatedAt: Date;
}

export interface PlaySubscriptionVerification {
  state: SubscriptionStatus;
  productId: string | null;
  basePlanId: string | null;
  expiresAt: Date | null;
  autoRenewing: boolean;
  obfuscatedAccountId: string | null;
  raw: unknown;
}

export interface PlayPurchaseVerification {
  state: 'PURCHASED' | 'CANCELED' | 'PENDING';
  orderId: string | null;
  obfuscatedAccountId: string | null;
  raw: unknown;
}

export type PlayNotification =
  | { kind: 'SUBSCRIPTION'; purchaseToken: string }
  | { kind: 'VOIDED'; purchaseToken: string; subscription: boolean }
  | { kind: 'TEST' }
  | { kind: 'ONE_TIME_PRODUCT' };

export interface PurchaseVerifier {
  verifySubscription(purchaseToken: string): Promise<PlaySubscriptionVerification>;
  verifyPurchase(productId: string, purchaseToken: string): Promise<PlayPurchaseVerification>;
}

export interface SubscriptionRepository {
  findByPurchaseToken(purchaseToken: string): Promise<Subscription | null>;
  findActiveByUser(userId: string): Promise<Subscription | null>;
  upsert(sub: {
    userId: string;
    purchaseToken: string;
    productId: string;
    basePlanId: string | null;
    status: SubscriptionStatus;
    autoRenewing: boolean;
    expiresAt: Date | null;
    rawState?: unknown;
  }): Promise<Subscription>;
}
