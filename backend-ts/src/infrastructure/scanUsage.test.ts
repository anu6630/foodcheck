import { describe, expect, it, beforeAll } from "vitest";
import { ensureSchema } from "./schemaEnsure.js";
import { PgMeteredScanRepository } from "./meteredScanRepo.js";
import { MeteredScanUseCase, computeImagesHash } from "../domain/meteredScan.js";
import { ScanQuotaExceededError, periodStart } from "../domain/entitlements.js";
import { query } from "./db.js";
import type { ScanWithAgentUseCase } from "../domain/usecases.js";

describe("PgMeteredScanRepository Integration", () => {
  const repo = new PgMeteredScanRepository();
  const testUserId = "00000000-0000-0000-0000-000000000001";
  const user2Id = "00000000-0000-0000-0000-000000000002";
  const pStart = periodStart(new Date());

  beforeAll(async () => {
    await ensureSchema();
    const ts = Date.now();
    await query(
      `INSERT INTO app_users (id, device_key)
       VALUES ($1, $3), ($2, $4)
       ON CONFLICT (id) DO UPDATE SET device_key = EXCLUDED.device_key`,
      [testUserId, user2Id, `test_device_key_001_${ts}`, `test_device_key_002_${ts}`]
    );
    // Clean up test rows
    await query("DELETE FROM scan_usage WHERE subject_id IN ($1, $2)", [testUserId, user2Id]);
    await query("DELETE FROM scan_cache WHERE key LIKE 'image:test_%'");
    await query("DELETE FROM ai_calls WHERE user_id IN ($1, $2)", [testUserId, user2Id]);
  }, 30000);

  it(
    "handles concurrent scans atomically: exactly 5 of 10 succeed when limit is 5",
    async () => {
      const limit = 5;
      const attempts = 10;

      // Run 10 concurrent quota consumption requests
      const results = await Promise.all(
        Array.from({ length: attempts }, () =>
          repo.tryConsumeQuota("user", testUserId, pStart, limit)
        )
      );

      const succeeded = results.filter((r) => r.success);
      const failed = results.filter((r) => !r.success);

      expect(succeeded.length).toBe(5);
      expect(failed.length).toBe(5);

      // Verify database state has used = 5
      const finalUsage = await repo.getUsage("user", testUserId, pStart);
      expect(finalUsage.used).toBe(5);
    },
    30000
  );

  it(
    "caches image scan so second user hits cache and does not consume quota",
    async () => {
      const testImageBuffer = Buffer.from("distinct_test_pack_image_content_12345");
      const testHash = computeImagesHash([testImageBuffer]);
      // Reset usage and cache for this test
      await query("DELETE FROM scan_usage WHERE subject_id IN ($1, $2)", [testUserId, user2Id]);
      await query("DELETE FROM scan_cache WHERE key = $1", [`image:${testHash}`]);

      let agentCalls = 0;

      const mockAgentUseCase = {
        execute: async () => {
          agentCalls++;
          return {
            match_type: "ingredients_only",
            ingredients: [{ id: "salt", name: "Salt" }],
            detected: { product_name: "Test Salt Pack" },
          };
        },
      } as unknown as ScanWithAgentUseCase;

      const meteredUseCase = new MeteredScanUseCase(repo, mockAgentUseCase);

      // User 1 scans: cache miss, consumes quota
      const res1 = await meteredUseCase.execute([testImageBuffer], testUserId);
      expect(agentCalls).toBe(1);
      expect(res1.match_type).toBe("ingredients_only");

      // User 2 scans the exact same image: cache hit, quota for user 2 is NOT consumed!
      const res2 = await meteredUseCase.execute([testImageBuffer], user2Id);
      expect(agentCalls).toBe(1); // Still 1! Gemini was not called!
      expect(res2.match_type).toBe("ingredients_only");

      // Verify User 2 usage is 0
      const user2Usage = await repo.getUsage("user", user2Id, pStart);
      expect(user2Usage.used).toBe(0);

      // Verify ai_calls has 1 cache_hit=false and 1 cache_hit=true
      const callsRes = await query<{ cache_hit: boolean }>(
        "SELECT cache_hit FROM ai_calls WHERE user_id IN ($1, $2) ORDER BY created_at ASC",
        [testUserId, user2Id]
      );
      expect(callsRes.rows.length).toBeGreaterThanOrEqual(2);
      const hits = callsRes.rows.map((r) => r.cache_hit);
      expect(hits).toContain(false);
      expect(hits).toContain(true);

      // Cleanup
      await query("DELETE FROM scan_usage WHERE subject_id IN ($1, $2)", [testUserId, user2Id]);
      await query("DELETE FROM ai_calls WHERE user_id IN ($1, $2)", [testUserId, user2Id]);
      await query("DELETE FROM app_users WHERE id IN ($1, $2)", [testUserId, user2Id]);
    },
    30000
  );
});
