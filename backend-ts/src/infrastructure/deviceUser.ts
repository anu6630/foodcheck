import { query } from "./db.js";

const KEY_RE = /^[A-Za-z0-9_-]{16,80}$/;

export async function upsertDeviceUser(deviceKey: string | null | undefined): Promise<string> {
  const key = (deviceKey || "").trim();
  if (!KEY_RE.test(key)) {
    throw Object.assign(new Error("X-Device-Key is required"), { status: 401 });
  }

  // 1. Check if device is mapped in user_devices (linked to a registered account)
  try {
    const dev = await query<{ user_id: string }>(
      `SELECT user_id::text FROM user_devices WHERE device_key = $1`,
      [key]
    );
    if (dev.rows.length) {
      await query(
        `UPDATE user_devices SET last_seen_at = NOW() WHERE device_key = $1`,
        [key]
      );
      return dev.rows[0].user_id;
    }
  } catch {
    // user_devices table might not be initialized in older tests
  }

  // 2. Upsert into app_users
  const { rows } = await query<{ id: string }>(
    `INSERT INTO app_users (device_key) VALUES ($1)
     ON CONFLICT (device_key) DO UPDATE SET device_key = EXCLUDED.device_key
     RETURNING id::text`,
    [key]
  );
  const uid = rows[0]!.id;

  // 3. Keep user_devices in sync
  try {
    await query(
      `INSERT INTO user_devices (device_key, user_id) VALUES ($1, $2)
       ON CONFLICT (device_key) DO NOTHING`,
      [key, uid]
    );
  } catch {
    // Ignore if table not yet migrated
  }

  return uid;
}
