import { randomUUID } from "node:crypto";
import { getR2OrNull } from "../services/r2Storage.js";
import { upsertDeviceUser } from "../../infrastructure/deviceUser.js";

/** Shared by device-scoped routes (My list, stories, comparisons). */
export async function userId(c: { req: { header: (n: string) => string | undefined } }): Promise<string> {
  return upsertDeviceUser(c.req.header("x-device-key"));
}

export function httpError(e: unknown): { detail: string; status: 400 | 401 | 500 } {
  const status = (e as { status?: number }).status;
  if (status === 401) return { detail: (e as Error).message, status: 401 };
  if (status === 400) return { detail: (e as Error).message, status: 400 };
  return { detail: (e as Error).message, status: 500 };
}

export async function readImage(
  form: Record<string, unknown>,
  field = "image"
): Promise<{ buf: Buffer; ext: string } | null> {
  const file = form[field];
  if (!file || typeof file === "string" || !(file instanceof File)) return null;
  const buf = Buffer.from(await file.arrayBuffer());
  if (!buf.length) return null;
  const ext = (file.name.split(".").pop() || "jpg").toLowerCase().replace(/[^a-z0-9]/g, "") || "jpg";
  return { buf, ext };
}

export async function putScanImage(userId: string, file: { buf: Buffer; ext: string }): Promise<string | null> {
  const r2 = getR2OrNull();
  if (!r2) return null;
  const key = `scans/${userId}/${randomUUID()}.${file.ext}`;
  await r2.put(key, file.buf, file.ext === "png" ? "image/png" : "image/jpeg");
  return key;
}

export async function photoResponse(key: string | null | undefined): Promise<Response> {
  const json = (detail: string, status: number) =>
    new Response(JSON.stringify({ detail }), { status, headers: { "content-type": "application/json" } });
  if (!key) return json("Photo not found", 404);
  const r2 = getR2OrNull();
  if (!r2) return json("Storage not configured", 503);
  const buf = await r2.get(key);
  if (!buf) return json("Photo missing", 404);
  return new Response(new Uint8Array(buf), { headers: { "content-type": "image/jpeg" } });
}
