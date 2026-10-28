import { describe, expect, it } from "vitest";
import {
  canScan,
  periodStart,
  quotaFor,
  resetsAt,
} from "./entitlements.js";

describe("entitlements", () => {
  it("returns correct quota per plan", () => {
    expect(quotaFor("free")).toBe(5);
    expect(quotaFor("pro")).toBe(50);
    expect(quotaFor("family")).toBe(100);
  });

  it("calculates periodStart correctly across month boundaries", () => {
    // Normal date
    const d1 = new Date(Date.UTC(2026, 9, 28)); // Oct 28, 2026
    expect(periodStart(d1)).toBe("2026-10-01");

    // Month rollover (31st of Jan -> Jan 1st)
    const d2 = new Date(Date.UTC(2026, 0, 31)); // Jan 31, 2026
    expect(periodStart(d2)).toBe("2026-01-01");

    // Feb leap year (Feb 29, 2028)
    const d3 = new Date(Date.UTC(2028, 1, 29));
    expect(periodStart(d3)).toBe("2028-02-01");
  });

  it("calculates resetsAt as start of next month UTC", () => {
    const d1 = new Date(Date.UTC(2026, 9, 15, 12, 0, 0));
    expect(resetsAt(d1)).toBe("2026-11-01T00:00:00.000Z");

    // December rollover to January
    const d2 = new Date(Date.UTC(2026, 11, 25));
    expect(resetsAt(d2)).toBe("2027-01-01T00:00:00.000Z");
  });

  it("evaluates canScan with quota and topup", () => {
    const freeEntitlement = { plan: "free" as const, scanQuota: 5 };

    // Under quota
    expect(canScan({ used: 0 }, freeEntitlement)).toBe(true);
    expect(canScan({ used: 4 }, freeEntitlement)).toBe(true);

    // At or over quota
    expect(canScan({ used: 5 }, freeEntitlement)).toBe(false);
    expect(canScan({ used: 6 }, freeEntitlement)).toBe(false);

    // Over quota but with top-up
    expect(canScan({ used: 5, topupLeft: 1 }, freeEntitlement)).toBe(true);
    expect(canScan({ used: 5, topupLeft: 0 }, freeEntitlement)).toBe(false);
    expect(canScan({ used: 7, topupLeft: 3 }, freeEntitlement)).toBe(true);
    expect(canScan({ used: 7, topupLeft: 2 }, freeEntitlement)).toBe(false);
  });
});
