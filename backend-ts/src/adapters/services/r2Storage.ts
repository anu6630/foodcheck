import { createHash, randomUUID } from "node:crypto";
import { AwsClient } from "aws4fetch";
import type { IObjectStorage } from "../../domain/interfaces.js";

export interface R2Config {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  bucket: string;
}

const TIMEOUT_MS = 120_000;

export class R2Store implements IObjectStorage {
  private readonly aws: AwsClient;
  private readonly base: string;
  readonly bucket: string;

  constructor(
    config: R2Config,
    private readonly fetchFn: typeof fetch = fetch
  ) {
    this.bucket = config.bucket;
    this.aws = new AwsClient({
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      service: "s3",
      region: "auto",
    });
    this.base = `https://${config.accountId}.r2.cloudflarestorage.com/${config.bucket}`;
  }

  async put(key: string, body: Buffer, contentType: string): Promise<void> {
    await this.send(`${this.base}/${encodeKey(key)}`, {
      method: "PUT",
      body: new Uint8Array(body),
      headers: { "content-type": contentType },
    });
  }

  async get(key: string): Promise<Buffer | null> {
    const res = await this.send(`${this.base}/${encodeKey(key)}`, { method: "GET" }, [404]);
    return res.status === 404 ? null : Buffer.from(await res.arrayBuffer());
  }

  async exists(key: string): Promise<boolean> {
    const res = await this.send(`${this.base}/${encodeKey(key)}`, { method: "HEAD" }, [404]);
    return res.status !== 404;
  }

  async delete(key: string): Promise<void> {
    await this.send(`${this.base}/${encodeKey(key)}`, { method: "DELETE" }, [404]);
  }

  private async send(url: string, init: RequestInit, okStatuses: number[] = []): Promise<Response> {
    const signed = await this.aws.sign(url, init);
    const res = await this.fetchFn(signed, { signal: AbortSignal.timeout(TIMEOUT_MS) });
    if (!res.ok && !okStatuses.includes(res.status)) {
      const body = await res.text().catch(() => "");
      throw new Error(`R2 ${init.method} ${url} → ${res.status} ${body.slice(0, 200)}`);
    }
    return res;
  }
}

export function r2ConfigFromEnv(env: NodeJS.ProcessEnv = process.env): R2Config {
  const config = {
    accountId: env.R2_ACCOUNT_ID,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    bucket: env.R2_FOODCHECK_BUCKET || env.R2_BUCKET,
  };
  const missing = Object.entries({
    R2_ACCOUNT_ID: config.accountId,
    R2_ACCESS_KEY_ID: config.accessKeyId,
    R2_SECRET_ACCESS_KEY: config.secretAccessKey,
    R2_FOODCHECK_BUCKET: config.bucket,
  })
    .filter(([, v]) => !v)
    .map(([k]) => k);
  if (missing.length > 0) {
    throw new Error(`R2 is not configured: ${missing.join(", ")}`);
  }
  return config as R2Config;
}

export function sha256Hex(buf: Buffer): string {
  return createHash("sha256").update(buf).digest("hex");
}

export function newProductRequestImageKey(ext = "jpg"): string {
  return `product-requests/${randomUUID()}.${ext}`;
}

export function catalogSourceObjectKey(sha256: string, filename: string): string {
  const safe = filename.replace(/[^A-Za-z0-9._-]+/g, "-");
  return `catalog-sources/${sha256}/${safe}`;
}

function encodeKey(key: string): string {
  return key.split("/").map(encodeURIComponent).join("/");
}

let cached: R2Store | null = null;

export function getR2OrNull(): R2Store | null {
  if (cached) return cached;
  try {
    cached = new R2Store(r2ConfigFromEnv());
    return cached;
  } catch {
    return null;
  }
}
