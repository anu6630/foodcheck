import { query } from "./db.js";
import { upsertDeviceUser } from "./deviceUser.js";

export interface LinkAuthPayload {
  firebaseUid: string;
  email?: string | null;
  displayName?: string | null;
  photoUrl?: string | null;
  phone?: string | null;
}

export interface UserProfileDto {
  id: string;
  is_anonymous: boolean;
  isAnonymous?: boolean;
  firebase_uid: string | null;
  firebaseUid?: string | null;
  email: string | null;
  display_name: string | null;
  displayName?: string | null;
  photo_url: string | null;
  photoUrl?: string | null;
  phone: string | null;
  device_count: number;
  deviceCount?: number;
  shelf_count: number;
  shelfCount?: number;
}

export interface AuthLinkResult {
  status: "linked" | "merged";
  user_id: string;
  userId?: string;
  merged_shelf_items: number;
  mergedShelfItems?: number;
  merged_scans: number;
  mergedScans?: number;
  user: UserProfileDto;
}

/**
 * Links a Firebase identity to the current device user.
 * - If no account exists with this firebase_uid, attaches identity to current device user.
 * - If an existing account already exists with this firebase_uid, merges current device data
 *   (scans, shelf items, comparisons, stories) into the existing account and re-parents the device.
 */
export async function linkFirebaseAccount(
  deviceKey: string,
  payload: LinkAuthPayload
): Promise<AuthLinkResult> {
  const currentUserId = await upsertDeviceUser(deviceKey);
  const fbUid = (payload.firebaseUid || "").trim();
  if (!fbUid) {
    throw Object.assign(new Error("firebaseUid is required"), { status: 400 });
  }

  // Check if an account already exists with this firebase_uid
  const existingAcc = await query<{
    id: string;
    email: string | null;
    display_name: string | null;
  }>(`SELECT id::text, email, display_name FROM app_users WHERE firebase_uid = $1`, [
    fbUid,
  ]);

  let targetUserId = currentUserId;
  let mergedShelf = 0;
  let mergedScans = 0;
  let status: "linked" | "merged" = "linked";

  if (!existingAcc.rows.length) {
    // Case 1: Brand new cloud account -> link to current user row
    await query(
      `UPDATE app_users
          SET firebase_uid = $1,
              email = COALESCE($2, email),
              display_name = COALESCE($3, display_name),
              photo_url = COALESCE($4, photo_url),
              phone = COALESCE($5, phone)
        WHERE id = $6`,
      [
        fbUid,
        payload.email || null,
        payload.displayName || null,
        payload.photoUrl || null,
        payload.phone || null,
        currentUserId,
      ]
    );

    await query(
      `INSERT INTO user_devices (device_key, user_id, last_seen_at)
       VALUES ($1, $2, NOW())
       ON CONFLICT (device_key) DO UPDATE SET user_id = EXCLUDED.user_id, last_seen_at = NOW()`,
      [deviceKey, currentUserId]
    );
  } else {
    // Case 2: Existing cloud account -> merge current anonymous data into it
    const cloudUserId = existingAcc.rows[0].id;

    if (cloudUserId !== currentUserId) {
      status = "merged";
      targetUserId = cloudUserId;

      // 1. Count items to be merged for reporting
      const shelfToMerge = await query<{ count: string }>(
        `SELECT COUNT(*) as count FROM shelf_items WHERE user_id = $1`,
        [currentUserId]
      );
      mergedShelf = Number(shelfToMerge.rows[0].count);

      const scansToMerge = await query<{ count: string }>(
        `SELECT COUNT(*) as count FROM scans WHERE user_id = $1`,
        [currentUserId]
      );
      mergedScans = Number(scansToMerge.rows[0].count);

      // 2. Re-parent scans, comparisons, stories
      await query(`UPDATE scans SET user_id = $1 WHERE user_id = $2`, [
        cloudUserId,
        currentUserId,
      ]);
      await query(`UPDATE comparisons SET user_id = $1 WHERE user_id = $2`, [
        cloudUserId,
        currentUserId,
      ]);
      await query(`UPDATE stories SET user_id = $1 WHERE user_id = $2`, [
        cloudUserId,
        currentUserId,
      ]);
      await query(`UPDATE story_comments SET user_id = $1 WHERE user_id = $2`, [
        cloudUserId,
        currentUserId,
      ]);

      // 3. Merge story likes
      await query(
        `INSERT INTO story_likes (user_id, story_id, created_at)
         SELECT $1, story_id, created_at FROM story_likes WHERE user_id = $2
         ON CONFLICT (user_id, story_id) DO NOTHING`,
        [cloudUserId, currentUserId]
      );
      await query(`DELETE FROM story_likes WHERE user_id = $1`, [currentUserId]);

      // 4. Merge shelf items (keep cloud stance if duplicate barcode)
      await query(
        `UPDATE shelf_items
            SET user_id = $1
          WHERE user_id = $2
            AND (barcode IS NULL OR barcode NOT IN (
              SELECT barcode FROM shelf_items WHERE user_id = $1 AND barcode IS NOT NULL
            ))`,
        [cloudUserId, currentUserId]
      );
      await query(`DELETE FROM shelf_items WHERE user_id = $1`, [currentUserId]);

      // 5. Update user_devices mapping for this device
      await query(
        `INSERT INTO user_devices (device_key, user_id, last_seen_at)
         VALUES ($1, $2, NOW())
         ON CONFLICT (device_key) DO UPDATE SET user_id = EXCLUDED.user_id, last_seen_at = NOW()`,
        [deviceKey, cloudUserId]
      );

      // 6. Delete temporary anonymous user row if it had no firebase_uid
      await query(
        `DELETE FROM app_users WHERE id = $1 AND firebase_uid IS NULL`,
        [currentUserId]
      );

      // 7. Update profile metadata
      await query(
        `UPDATE app_users
            SET email = COALESCE($2, email),
                display_name = COALESCE($3, display_name),
                photo_url = COALESCE($4, photo_url),
                phone = COALESCE($5, phone)
          WHERE id = $1`,
        [
          cloudUserId,
          payload.email || null,
          payload.displayName || null,
          payload.photoUrl || null,
          payload.phone || null,
        ]
      );
    }
  }

  const profile = await getUserProfileById(targetUserId);
  return {
    status,
    user_id: targetUserId,
    userId: targetUserId,
    merged_shelf_items: mergedShelf,
    mergedShelfItems: mergedShelf,
    merged_scans: mergedScans,
    mergedScans: mergedScans,
    user: profile,
  };
}

export async function getUserProfile(deviceKey: string): Promise<UserProfileDto> {
  const uid = await upsertDeviceUser(deviceKey);
  return getUserProfileById(uid);
}

async function getUserProfileById(userId: string): Promise<UserProfileDto> {
  const userRow = await query<{
    id: string;
    firebase_uid: string | null;
    email: string | null;
    display_name: string | null;
    photo_url: string | null;
    phone: string | null;
  }>(
    `SELECT id::text, firebase_uid, email, display_name, photo_url, phone
       FROM app_users
      WHERE id = $1`,
    [userId]
  );

  if (!userRow.rows.length) {
    throw Object.assign(new Error("User not found"), { status: 404 });
  }

  const u = userRow.rows[0];

  const devCountRes = await query<{ count: string }>(
    `SELECT COUNT(*) as count FROM user_devices 
      WHERE user_id = $1 
        AND last_seen_at >= NOW() - INTERVAL '30 days'`,
    [userId]
  );
  const shelfCountRes = await query<{ count: string }>(
    `SELECT COUNT(*) as count FROM shelf_items WHERE user_id = $1`,
    [userId]
  );

  const rawDevCount = Number(devCountRes.rows[0]?.count || 0);
  const devCount = Math.max(1, rawDevCount);
  const shelfCount = Number(shelfCountRes.rows[0]?.count || 0);

  return {
    id: u.id,
    is_anonymous: u.firebase_uid == null,
    isAnonymous: u.firebase_uid == null,
    firebase_uid: u.firebase_uid,
    firebaseUid: u.firebase_uid,
    email: u.email,
    display_name: u.display_name,
    displayName: u.display_name,
    photo_url: u.photo_url,
    photoUrl: u.photo_url,
    phone: u.phone,
    device_count: devCount,
    deviceCount: devCount,
    shelf_count: shelfCount,
    shelfCount: shelfCount,
  };
}

/**
 * Unlinks a device session from user_devices upon sign-out.
 * The user's cloud data (scans, shelf items) remains completely intact.
 */
export async function unlinkDeviceSession(deviceKey: string): Promise<void> {
  const key = (deviceKey || "").trim();
  if (!key) return;
  await query(`DELETE FROM user_devices WHERE device_key = $1`, [key]);
}

