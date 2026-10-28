export type Plan = "free" | "pro" | "family";

export interface Entitlement {
  plan: Plan;
  scanQuota: number;
  familyId?: string;
}

export interface ScanUsage {
  used: number;
  topupLeft: number;
}

export interface QuotaInfo {
  used: number;
  limit: number;
  topupLeft: number;
  resetsAt: string;
}

export class ScanQuotaExceededError extends Error {
  readonly quota: QuotaInfo;

  constructor(quota: QuotaInfo) {
    super("Monthly AI scan limit reached");
    this.name = "ScanQuotaExceededError";
    this.quota = quota;
  }
}

/** Pure mapping from plan to monthly quota. */
export function quotaFor(plan: Plan): number {
  switch (plan) {
    case "free":
      return 5;
    case "pro":
      return 50;
    case "family":
      return 100;
  }
}

/**
 * Computes calendar month start in UTC as 'YYYY-MM-01'.
 * Example: 2026-10-28 -> '2026-10-01'.
 */
export function periodStart(now: Date = new Date()): string {
  const year = now.getUTCFullYear();
  const month = String(now.getUTCMonth() + 1).padStart(2, "0");
  return `${year}-${month}-01`;
}

/**
 * Computes the reset timestamp (00:00:00 UTC on the 1st of next month) as ISO string.
 */
export function resetsAt(now: Date = new Date()): string {
  const nextMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1, 0, 0, 0));
  return nextMonth.toISOString();
}

/**
 * Checks whether user has remaining scans (quota + topup).
 */
export function canScan(
  usage: { used: number; topupLeft?: number },
  entitlement: Entitlement
): boolean {
  const remainingInPlan = entitlement.scanQuota - usage.used;
  const totalRemaining = remainingInPlan + (usage.topupLeft || 0);
  return totalRemaining > 0;
}
