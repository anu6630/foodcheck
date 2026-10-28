import 'dotenv/config';
import { query } from '../src/infrastructure/db.ts';

async function run() {
  const userId = '58af6340-eaf6-4dcb-bc44-6a83e52396c1';
  const existing = await query(
    'SELECT id FROM shelf_items WHERE user_id = $1 AND display_name = $2',
    [userId, 'Maggi 2-Minute Noodles']
  );
  if (existing.rows.length === 0) {
    const scan = await query(
      `INSERT INTO scans (user_id, ingredients_text, snapshot)
       VALUES ($1, $2, $3::jsonb) RETURNING id`,
      [
        userId,
        'Wheat flour, Palm oil, Salt, Monosodium L-glutamate, Spices',
        JSON.stringify([
          { id: 'wheat-flour', name: 'Wheat flour', severity: 'green' },
          { id: 'palm-oil', name: 'Palm oil', severity: 'amber' },
          { id: 'salt', name: 'Salt', severity: 'green' },
          { id: 'monosodium-l-glutamate', name: 'Monosodium L-glutamate', e_number: 'E621', severity: 'green' },
        ]),
      ]
    );
    await query(
      `INSERT INTO shelf_items (user_id, scan_id, display_name, brand, barcode, stance, tags)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb)`,
      [
        userId,
        scan.rows[0].id,
        'Maggi 2-Minute Noodles',
        'Nestle',
        '8901058862492',
        'avoid',
        JSON.stringify(['noodles', 'instant']),
      ]
    );
    console.log('Inserted Maggi 2-Minute Noodles into user shelf');
  } else {
    console.log('Already exists on user shelf');
  }
  process.exit(0);
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
