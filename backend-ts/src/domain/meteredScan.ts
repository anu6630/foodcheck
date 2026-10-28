import { createHash } from "node:crypto";
import {
  type Entitlement,
  type QuotaInfo,
  ScanQuotaExceededError,
  periodStart,
  resetsAt,
} from "./entitlements.js";
import type { IMeteredScanRepository } from "../infrastructure/meteredScanRepo.js";
import type { ScanWithAgentUseCase } from "./usecases.js";

export interface MeteredScanResult {
  match_type: string;
  product?: unknown;
  candidates?: unknown[];
  ingredients?: unknown[];
  detected?: unknown;
  quota: QuotaInfo;
}

export function computeImagesHash(images: Buffer[]): string {
  const hash = createHash("sha256");
  for (const img of images) {
    hash.update(img);
  }
  return hash.digest("hex");
}

export class MeteredScanUseCase {
  constructor(
    private readonly meteredRepo: IMeteredScanRepository,
    private readonly scanWithAgentUseCase: ScanWithAgentUseCase
  ) {}

  async execute(
    images: Buffer[],
    userId: string,
    mockType?: string | null,
    clientSha256?: string | null,
    now: Date = new Date()
  ): Promise<MeteredScanResult> {
    const computedHash = computeImagesHash(images);
    if (clientSha256 && clientSha256.toLowerCase() !== computedHash.toLowerCase()) {
      console.warn("Client sha256 mismatch with server computed hash");
    }

    const cacheKey = `image:${computedHash}`;
    const pStart = periodStart(now);
    const pResets = resetsAt(now);

    const entitlement: Entitlement = await this.meteredRepo.getEntitlement(userId);

    // 1. Check shared result cache
    const cached = await this.meteredRepo.getCachedScan(cacheKey);
    if (cached && typeof cached === "object") {
      await this.meteredRepo.recordAiCall({
        userId,
        kind: "scan",
        model: "cache",
        inputTokens: 0,
        outputTokens: 0,
        costUsd: 0,
        cacheHit: true,
      });

      const currentUsage = await this.meteredRepo.getUsage("user", userId, pStart);
      return {
        ...(cached as Record<string, unknown>),
        quota: {
          used: currentUsage.used,
          limit: entitlement.scanQuota,
          topupLeft: currentUsage.topupLeft,
          resetsAt: pResets,
        },
      } as MeteredScanResult;
    }

    // 2. Cache miss: check and atomically consume quota
    const consumption = await this.meteredRepo.tryConsumeQuota(
      "user",
      userId,
      pStart,
      entitlement.scanQuota
    );

    if (!consumption.success) {
      throw new ScanQuotaExceededError({
        used: consumption.usage.used,
        limit: entitlement.scanQuota,
        topupLeft: consumption.usage.topupLeft,
        resetsAt: pResets,
      });
    }

    // 3. Quota reserved -> execute Gemini scan agent
    let scanResult;
    try {
      scanResult = await this.scanWithAgentUseCase.execute(images, mockType);
    } catch (err) {
      // Rollback quota if agent execution fails so user isn't charged for broken scans
      await this.meteredRepo.rollbackQuota("user", userId, pStart);
      throw err;
    }

    // 4. Log AI call and populate cache
    await this.meteredRepo.recordAiCall({
      userId,
      kind: "scan",
      model: mockType ? "mock" : "gemini-2.5-flash",
      inputTokens: 0,
      outputTokens: 0,
      costUsd: 0.01, // Standard estimated per-scan cost
      cacheHit: false,
    });

    await this.meteredRepo.setCachedScan(cacheKey, "image_hash", scanResult);

    return {
      ...scanResult,
      quota: {
        used: consumption.usage.used,
        limit: entitlement.scanQuota,
        topupLeft: consumption.usage.topupLeft,
        resetsAt: pResets,
      },
    };
  }
}
