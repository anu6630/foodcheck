import { query } from "./db.js";
import type {
  Subscription,
  SubscriptionRepository,
  SubscriptionStatus,
} from "../domain/billing.js";

interface SubscriptionRow {
  id: string;
  user_id: string;
  purchase_token: string;
  product_id: string;
  base_plan_id: string | null;
  status: string;
  auto_renewing: boolean;
  starts_at: string;
  expires_at: string | null;
  updated_at: string;
}

export class PgSubscriptionRepository implements SubscriptionRepository {
  async findByPurchaseToken(purchaseToken: string): Promise<Subscription | null> {
    const res = await query<SubscriptionRow>(
      `SELECT id, user_id, purchase_token, product_id, base_plan_id, status, auto_renewing, starts_at, expires_at, updated_at
       FROM subscriptions
       WHERE purchase_token = $1`,
      [purchaseToken]
    );
    return res.rows[0] ? toDomain(res.rows[0]) : null;
  }

  async findActiveByUser(userId: string): Promise<Subscription | null> {
    const res = await query<SubscriptionRow>(
      `SELECT id, user_id, purchase_token, product_id, base_plan_id, status, auto_renewing, starts_at, expires_at, updated_at
       FROM subscriptions
       WHERE user_id = $1
         AND status IN ('ACTIVE', 'IN_GRACE_PERIOD')
         AND (expires_at IS NULL OR expires_at > NOW())
       ORDER BY expires_at DESC NULLS FIRST
       LIMIT 1`,
      [userId]
    );
    return res.rows[0] ? toDomain(res.rows[0]) : null;
  }

  async upsert(sub: {
    userId: string;
    purchaseToken: string;
    productId: string;
    basePlanId: string | null;
    status: SubscriptionStatus;
    autoRenewing: boolean;
    expiresAt: Date | null;
    rawState?: unknown;
  }): Promise<Subscription> {
    const res = await query<SubscriptionRow>(
      `INSERT INTO subscriptions (
         user_id, purchase_token, product_id, base_plan_id, status, auto_renewing, expires_at, raw_state, updated_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
       ON CONFLICT (purchase_token) DO UPDATE SET
         user_id = EXCLUDED.user_id,
         product_id = EXCLUDED.product_id,
         base_plan_id = EXCLUDED.base_plan_id,
         status = EXCLUDED.status,
         auto_renewing = EXCLUDED.auto_renewing,
         expires_at = EXCLUDED.expires_at,
         raw_state = EXCLUDED.raw_state,
         updated_at = NOW()
       RETURNING id, user_id, purchase_token, product_id, base_plan_id, status, auto_renewing, starts_at, expires_at, updated_at`,
      [
        sub.userId,
        sub.purchaseToken,
        sub.productId,
        sub.basePlanId,
        sub.status,
        sub.autoRenewing,
        sub.expiresAt ? sub.expiresAt.toISOString() : null,
        sub.rawState ? JSON.stringify(sub.rawState) : null,
      ]
    );
    return toDomain(res.rows[0]);
  }
}

function toDomain(row: SubscriptionRow): Subscription {
  return {
    id: row.id,
    userId: row.user_id,
    purchaseToken: row.purchase_token,
    productId: row.product_id,
    basePlanId: row.base_plan_id,
    status: row.status as SubscriptionStatus,
    autoRenewing: row.auto_renewing,
    startsAt: new Date(row.starts_at),
    expiresAt: row.expires_at ? new Date(row.expires_at) : null,
    updatedAt: new Date(row.updated_at),
  };
}
