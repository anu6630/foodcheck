import { query } from "./db.js";
import {
  type Entitlement,
  type ScanUsage,
  quotaFor,
} from "../domain/entitlements.js";

export interface IMeteredScanRepository {
  getEntitlement(userId: string): Promise<Entitlement>;
  getUsage(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string
  ): Promise<ScanUsage>;
  tryConsumeQuota(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string,
    limit: number
  ): Promise<{ success: boolean; usage: ScanUsage }>;
  rollbackQuota(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string
  ): Promise<void>;
  getCachedScan(key: string): Promise<unknown | null>;
  setCachedScan(key: string, kind: string, result: unknown): Promise<void>;
  recordAiCall(call: {
    userId?: string | null;
    familyId?: string | null;
    kind: string;
    model: string;
    inputTokens?: number;
    outputTokens?: number;
    costUsd?: number;
    cacheHit?: boolean;
  }): Promise<void>;
}

export class PgMeteredScanRepository implements IMeteredScanRepository {
  async getEntitlement(userId: string): Promise<Entitlement> {
    const subRes = await query<{ product_id: string; status: string; expires_at: string | null }>(
      `SELECT product_id, status, expires_at FROM subscriptions
       WHERE user_id = $1
         AND status IN ('ACTIVE', 'IN_GRACE_PERIOD')
         AND (expires_at IS NULL OR expires_at > NOW())
       ORDER BY expires_at DESC NULLS FIRST
       LIMIT 1`,
      [userId]
    );

    if (subRes.rows.length > 0) {
      const sub = subRes.rows[0];
      const plan = sub.product_id.includes("family") ? "family" : "pro";
      return {
        plan,
        scanQuota: quotaFor(plan),
        adsSuppressed: true,
      };
    }

    return {
      plan: "free",
      scanQuota: quotaFor("free"),
      adsSuppressed: false,
    };
  }

  async getUsage(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string
  ): Promise<ScanUsage> {
    const res = await query<{ used: number; topup_left: number }>(
      `SELECT used, topup_left FROM scan_usage
       WHERE subject_type = $1 AND subject_id = $2 AND period_start = $3`,
      [subjectType, subjectId, periodStart]
    );
    if (!res.rows.length) {
      return { used: 0, topupLeft: 0 };
    }
    return {
      used: Number(res.rows[0].used) || 0,
      topupLeft: Number(res.rows[0].topup_left) || 0,
    };
  }

  async tryConsumeQuota(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string,
    limit: number
  ): Promise<{ success: boolean; usage: ScanUsage }> {
    // 1. Ensure the row exists
    await query(
      `INSERT INTO scan_usage (subject_type, subject_id, period_start, used, topup_left)
       VALUES ($1, $2, $3, 0, 0)
       ON CONFLICT (subject_type, subject_id, period_start) DO NOTHING`,
      [subjectType, subjectId, periodStart]
    );

    // 2. Atomic consumption check and increment
    const res = await query<{ used: number; topup_left: number }>(
      `UPDATE scan_usage
       SET
         used = CASE WHEN scan_usage.used < $4 THEN scan_usage.used + 1 ELSE scan_usage.used END,
         topup_left = CASE WHEN scan_usage.used >= $4 AND scan_usage.topup_left > 0 THEN scan_usage.topup_left - 1 ELSE scan_usage.topup_left END
       WHERE subject_type = $1 AND subject_id = $2 AND period_start = $3
         AND (scan_usage.used < $4 OR scan_usage.topup_left > 0)
       RETURNING used, topup_left`,
      [subjectType, subjectId, periodStart, limit]
    );

    if (res.rows.length > 0) {
      return {
        success: true,
        usage: {
          used: Number(res.rows[0].used),
          topupLeft: Number(res.rows[0].topup_left),
        },
      };
    }

    // Over quota: fetch current usage for reporting
    const current = await this.getUsage(subjectType, subjectId, periodStart);
    return {
      success: false,
      usage: current,
    };
  }

  async rollbackQuota(
    subjectType: "user" | "family",
    subjectId: string,
    periodStart: string
  ): Promise<void> {
    await query(
      `UPDATE scan_usage
       SET used = GREATEST(used - 1, 0)
       WHERE subject_type = $1 AND subject_id = $2 AND period_start = $3`,
      [subjectType, subjectId, periodStart]
    );
  }

  async getCachedScan(key: string): Promise<unknown | null> {
    const res = await query<{ result: unknown }>(
      `UPDATE scan_cache
       SET hits = hits + 1, updated_at = NOW()
       WHERE key = $1
       RETURNING result`,
      [key]
    );
    if (!res.rows.length) return null;
    return res.rows[0].result;
  }

  async setCachedScan(key: string, kind: string, result: unknown): Promise<void> {
    await query(
      `INSERT INTO scan_cache (key, kind, result, hits)
       VALUES ($1, $2, $3, 1)
       ON CONFLICT (key)
       DO UPDATE SET result = EXCLUDED.result, updated_at = NOW()`,
      [key, kind, JSON.stringify(result)]
    );
  }

  async recordAiCall(call: {
    userId?: string | null;
    familyId?: string | null;
    kind: string;
    model: string;
    inputTokens?: number;
    outputTokens?: number;
    costUsd?: number;
    cacheHit?: boolean;
  }): Promise<void> {
    await query(
      `INSERT INTO ai_calls (user_id, family_id, kind, model, input_tokens, output_tokens, cost_usd, cache_hit)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [
        call.userId ?? null,
        call.familyId ?? null,
        call.kind,
        call.model,
        call.inputTokens ?? 0,
        call.outputTokens ?? 0,
        call.costUsd ?? 0,
        call.cacheHit ?? false,
      ]
    );
  }
}
