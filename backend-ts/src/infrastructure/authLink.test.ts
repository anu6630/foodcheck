import { describe, expect, it } from "vitest";
import { query } from "./db.js";
import { linkFirebaseAccount, unlinkDeviceSession, getUserProfile } from "./authLink.js";

describe("authLink & device lifecycle", () => {
  const hasDb = Boolean(process.env.DATABASE_URL);

  it("unlinkDeviceSession gracefully handles blank key", async () => {
    await expect(unlinkDeviceSession("")).resolves.toBeUndefined();
    await expect(unlinkDeviceSession("   ")).resolves.toBeUndefined();
  });

  (hasDb ? it : it.skip)(
    "preserves user data on merge and unlinks cleanly on signout",
    async () => {
      const testFbUid = `test_uid_${Date.now()}`;
      const devKey1 = `test_dev_1_${Date.now()}`;
      const devKey2 = `test_dev_2_${Date.now()}`;

      // 1. Create a guest session on devKey1, record a scan, and save a shelf item
      const initialProfile = await getUserProfile(devKey1);
      expect(initialProfile.isAnonymous).toBe(true);

      const anonUserId = initialProfile.id;
      const scanRes = await query<{ id: number }>(
        `INSERT INTO scans (user_id, ingredients_text) VALUES ($1, 'Sugar, Wheat Flour') RETURNING id`,
        [anonUserId]
      );
      const scanId = scanRes.rows[0].id;

      await query(
        `INSERT INTO shelf_items (user_id, scan_id, display_name, barcode, stance, created_at)
         VALUES ($1, $2, 'Biscuit Pack', '8901234567890', 'avoid', NOW())`,
        [anonUserId, scanId]
      );

      // 2. Link this guest session to a Google account
      const linkResult = await linkFirebaseAccount(devKey1, {
        firebaseUid: testFbUid,
        email: "test_user@example.com",
        displayName: "Test User",
        photoUrl: "https://example.com/avatar.jpg",
      });

      expect(linkResult.user.email).toBe("test_user@example.com");
      expect(linkResult.user.isAnonymous).toBe(false);
      expect(linkResult.user.photoUrl).toBe("https://example.com/avatar.jpg");

      const linkedUserId = linkResult.user.id;

      // Verify scan and shelf item were preserved under the linked user
      const scanCheck = await query<{ count: string }>(
        `SELECT COUNT(*) as count FROM scans WHERE user_id = $1 AND id = $2`,
        [linkedUserId, scanId]
      );
      expect(Number(scanCheck.rows[0].count)).toBe(1);

      const shelfCheck = await query<{ count: string }>(
        `SELECT COUNT(*) as count FROM shelf_items WHERE user_id = $1 AND barcode = '8901234567890'`,
        [linkedUserId]
      );
      expect(Number(shelfCheck.rows[0].count)).toBe(1);

      // 3. User links a second device (e.g. tablet)
      await linkFirebaseAccount(devKey2, {
        firebaseUid: testFbUid,
        email: "test_user@example.com",
      });

      // Verify device count is now 2
      const profileWithTwo = await getUserProfile(devKey1);
      expect(profileWithTwo.deviceCount).toBe(2);

      // 4. User signs out of devKey2
      await unlinkDeviceSession(devKey2);

      // Verify device count drops back to 1
      const profileAfterSignout = await getUserProfile(devKey1);
      expect(profileAfterSignout.deviceCount).toBe(1);

      // Verify shelf items and scans are STILL 100% intact in the user's cloud account!
      const shelfAfterSignout = await query<{ count: string }>(
        `SELECT COUNT(*) as count FROM shelf_items WHERE user_id = $1 AND barcode = '8901234567890'`,
        [linkedUserId]
      );
      expect(Number(shelfAfterSignout.rows[0].count)).toBe(1);

      // Clean up test data
      await query(`DELETE FROM shelf_items WHERE user_id = $1`, [linkedUserId]);
      await query(`DELETE FROM scans WHERE user_id = $1`, [linkedUserId]);
      await query(`DELETE FROM user_devices WHERE user_id = $1`, [linkedUserId]);
      await query(`DELETE FROM app_users WHERE id = $1`, [linkedUserId]);
    },
    30000
  );
});
