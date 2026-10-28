import 'dotenv/config';
import { query } from '../src/infrastructure/db.ts';

const COMMON_ALIASES: Array<{ ingredient_id: string; names: string[] }> = [
  {
    ingredient_id: 'monosodium-l-glutamate',
    names: [
      'Agino motto',
      'Aginomotto',
      'Ajino moto',
      'Ajinomotto',
      'Ajinamoto',
      'Aginamoto',
      'Chinese salt',
      'Tasting powder',
    ],
  },
];

async function main() {
  for (const item of COMMON_ALIASES) {
    for (const name of item.names) {
      const exists = await query(
        'SELECT 1 FROM ingredient_names WHERE ingredient_id = $1 AND name ILIKE $2',
        [item.ingredient_id, name]
      );
      if (exists.rows.length === 0) {
        await query(
          'INSERT INTO ingredient_names (ingredient_id, name, lang) VALUES ($1, $2, $3)',
          [item.ingredient_id, name, 'en']
        );
        console.log(`Inserted alias: ${name} -> ${item.ingredient_id}`);
      }
    }
  }
  console.log('Aliases backfilled successfully.');
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
