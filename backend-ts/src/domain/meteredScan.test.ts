import { describe, expect, it, vi } from "vitest";
import { MeteredScanUseCase } from "./meteredScan.js";
import { ScanQuotaExceededError } from "./entitlements.js";
import type { IMeteredScanRepository } from "../infrastructure/meteredScanRepo.js";
import type { ScanWithAgentUseCase } from "./usecases.js";

function createMockRepo(overrides: Partial<IMeteredScanRepository> = {}): IMeteredScanRepository {
  return {
    getEntitlement: vi.fn().mockResolvedValue({ plan: "free", scanQuota: 5 }),
    getUsage: vi.fn().mockResolvedValue({ used: 1, topupLeft: 0 }),
    tryConsumeQuota: vi.fn().mockResolvedValue({
      success: true,
      usage: { used: 1, topupLeft: 0 },
    }),
    rollbackQuota: vi.fn().mockResolvedValue(undefined),
    getCachedScan: vi.fn().mockResolvedValue(null),
    setCachedScan: vi.fn().mockResolvedValue(undefined),
    recordAiCall: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

function createMockAgentUseCase(scanResult = { match_type: "ingredients_only", ingredients: [] }): ScanWithAgentUseCase {
  return {
    execute: vi.fn().mockResolvedValue(scanResult),
  } as unknown as ScanWithAgentUseCase;
}

describe("MeteredScanUseCase", () => {
  const dummyBuffer = Buffer.from("fake-image-bytes");
  const testUserId = "58af6340-eaf6-4dcb-bc44-6a83e52396c1";

  it("returns cached result without consuming quota on cache hit", async () => {
    const cachedData = {
      match_type: "confident",
      product: { name: "Cached Milk" },
      ingredients: [],
    };
    const repo = createMockRepo({
      getCachedScan: vi.fn().mockResolvedValue(cachedData),
      getUsage: vi.fn().mockResolvedValue({ used: 2, topupLeft: 0 }),
    });
    const agent = createMockAgentUseCase();
    const usecase = new MeteredScanUseCase(repo, agent);

    const result = await usecase.execute([dummyBuffer], testUserId);

    expect(repo.getCachedScan).toHaveBeenCalled();
    expect(repo.tryConsumeQuota).not.toHaveBeenCalled();
    expect(agent.execute).not.toHaveBeenCalled();
    expect(repo.recordAiCall).toHaveBeenCalledWith(
      expect.objectContaining({ cacheHit: true, costUsd: 0 })
    );
    expect(result.match_type).toBe("confident");
    expect(result.quota).toEqual(
      expect.objectContaining({ used: 2, limit: 5 })
    );
  });

  it("consumes quota and calls agent on cache miss", async () => {
    const repo = createMockRepo({
      getCachedScan: vi.fn().mockResolvedValue(null),
      tryConsumeQuota: vi.fn().mockResolvedValue({
        success: true,
        usage: { used: 3, topupLeft: 0 },
      }),
    });
    const agent = createMockAgentUseCase({
      match_type: "ingredients_only",
      ingredients: [{ id: "ing1" } as any],
    });
    const usecase = new MeteredScanUseCase(repo, agent);

    const result = await usecase.execute([dummyBuffer], testUserId);

    expect(repo.tryConsumeQuota).toHaveBeenCalledWith("user", testUserId, expect.any(String), 5);
    expect(agent.execute).toHaveBeenCalledWith([dummyBuffer], undefined);
    expect(repo.setCachedScan).toHaveBeenCalled();
    expect(repo.recordAiCall).toHaveBeenCalledWith(
      expect.objectContaining({ cacheHit: false, costUsd: 0.01 })
    );
    expect(result.quota.used).toBe(3);
  });

  it("throws ScanQuotaExceededError when user is over quota without running agent", async () => {
    const repo = createMockRepo({
      getCachedScan: vi.fn().mockResolvedValue(null),
      tryConsumeQuota: vi.fn().mockResolvedValue({
        success: false,
        usage: { used: 5, topupLeft: 0 },
      }),
    });
    const agent = createMockAgentUseCase();
    const usecase = new MeteredScanUseCase(repo, agent);

    await expect(usecase.execute([dummyBuffer], testUserId)).rejects.toThrow(
      ScanQuotaExceededError
    );
    expect(agent.execute).not.toHaveBeenCalled();
    expect(repo.setCachedScan).not.toHaveBeenCalled();
  });

  it("rolls back quota when agent execution fails", async () => {
    const repo = createMockRepo({
      getCachedScan: vi.fn().mockResolvedValue(null),
      tryConsumeQuota: vi.fn().mockResolvedValue({
        success: true,
        usage: { used: 1, topupLeft: 0 },
      }),
    });
    const agent = {
      execute: vi.fn().mockRejectedValue(new Error("Gemini down")),
    } as unknown as ScanWithAgentUseCase;
    const usecase = new MeteredScanUseCase(repo, agent);

    await expect(usecase.execute([dummyBuffer], testUserId)).rejects.toThrow("Gemini down");
    expect(repo.rollbackQuota).toHaveBeenCalledWith("user", testUserId, expect.any(String));
    expect(repo.setCachedScan).not.toHaveBeenCalled();
  });
});
