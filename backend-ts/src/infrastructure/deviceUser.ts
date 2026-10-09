import { query } from "./db.js";

const KEY_RE = /^[A-Za-z0-9_-]{16,80}$/;

export async function upsertDeviceUser(deviceKey: string | null | undefined): Promise<string> {
  const key = (deviceKey || "").trim();
  if (!KEY_RE.test(key)) {
    throw Object.assign(new Error("X-Device-Key is required"), { status: 401 });
  }
  const { rows } = await query<{ id: string }>(
    `INSERT INTO app_users (device_key) VALUES ($1)
     ON CONFLICT (device_key) DO UPDATE SET device_key = EXCLUDED.device_key
     RETURNING id::text`,
    [key]
  );
  return rows[0]!.id;
}
