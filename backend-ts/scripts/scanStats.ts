import 'dotenv/config';
import { query } from '../src/infrastructure/db.ts';

/** Read-only report: scan volume per week and per user over the last 6 weeks. */
async function main() {
  const weekly = await query(
    `SELECT date_trunc('week', created_at)::date AS week,
            COUNT(*) AS scans,
            COUNT(DISTINCT user_id) AS users,
            COUNT(*) FILTER (WHERE image_key IS NOT NULL) AS with_image
       FROM scans
      WHERE created_at > now() - interval '42 days'
      GROUP BY 1 ORDER BY 1`
  );
  console.log('=== Weekly ===');
  console.table(weekly.rows);

  const perUser = await query(
    `SELECT s.user_id, u.email,
            COUNT(*) AS scans,
            MIN(s.created_at)::date AS first_scan,
            MAX(s.created_at)::date AS last_scan,
            (SELECT COUNT(*) FROM shelf_items si WHERE si.user_id = s.user_id) AS saved
       FROM scans s LEFT JOIN app_users u ON u.id = s.user_id
      WHERE s.created_at > now() - interval '42 days'
      GROUP BY s.user_id, u.email
      ORDER BY scans DESC`
  );
  console.log('=== Per user (last 6 weeks) ===');
  console.table(perUser.rows);

  const dist = await query(
    `SELECT bucket, COUNT(*) AS users FROM (
       SELECT CASE WHEN c = 1 THEN '1' WHEN c <= 3 THEN '2-3'
                   WHEN c <= 10 THEN '4-10' ELSE '11+' END AS bucket
         FROM (SELECT user_id, COUNT(*) c FROM scans
                WHERE created_at > now() - interval '42 days' GROUP BY user_id) t
     ) b GROUP BY bucket ORDER BY bucket`
  );
  console.log('=== Distribution ===');
  console.table(dist.rows);

  const total = await query(`SELECT COUNT(*) AS all_time, MIN(created_at)::date AS since FROM scans`);
  console.log('All time:', total.rows[0]);

  try {
    const aiStats = await query(
      `SELECT date_trunc('day', created_at)::date AS day,
              COUNT(*) AS total_calls,
              COUNT(*) FILTER (WHERE cache_hit = true) AS cache_hits,
              ROUND(COUNT(*) FILTER (WHERE cache_hit = true)::numeric / GREATEST(COUNT(*), 1) * 100, 1) AS hit_rate_pct,
              ROUND(SUM(cost_usd)::numeric, 4) AS total_cost_usd
         FROM ai_calls
        GROUP BY 1 ORDER BY 1 DESC LIMIT 14`
    );
    console.log('=== AI Calls (Last 14 days) ===');
    console.table(aiStats.rows);
  } catch {
    // ai_calls table might be empty or not yet deployed
  }
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
