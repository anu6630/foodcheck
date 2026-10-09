import "dotenv/config";
import { pool, query } from "../src/infrastructure/db.js";
import { GetIngredientDetailsUseCase } from "../src/domain/usecases.js";
import { PgIngredientRepository } from "../src/adapters/repositories/ingredientRepository.js";

async function main() {
  const repo = new PgIngredientRepository();
  const details = new GetIngredientDetailsUseCase(repo);
  const tar = await details.execute("fd-and-c-yellow-no-5");
  const sugar = await details.execute("sugar");
  const cake = tar?.use_limits.find((l) => l.food_category_code === "7.2.1");
  const cheeni = await query<{ id: string; name: string }>(
    `SELECT i.id, i.name FROM ingredients i
     JOIN ingredient_names n ON n.ingredient_id = i.id
     WHERE n.name = $1 LIMIT 3`,
    ["चीनी"]
  );
  const bans = await query<{ status: string; n: string }>(
    `SELECT status, COUNT(*)::text n FROM ingredient_bans GROUP BY status ORDER BY 1`
  );
  const countries = await query<{ country_code: string; n: string }>(
    `SELECT country_code, COUNT(*)::text n FROM ingredient_use_limits GROUP BY 1 ORDER BY 1`
  );
  const products = await query<{ n: string }>(`SELECT COUNT(*)::text n FROM products`);
  console.log(
    JSON.stringify(
      {
        tartrazine: {
          bans: tar?.bans.map((b) => `${b.country_code}:${b.status}`),
          cake: cake && { name: cake.food_category_name, max: cake.max_level },
          limitRowsReturned: tar?.use_limits.length,
          limitCounts: tar?.use_limit_counts,
        },
        sugar: { bans: sugar?.bans.length ?? null, name: sugar?.name ?? null },
        cheeni: cheeni.rows,
        banStatus: bans.rows,
        limitsByCountry: countries.rows,
        products: products.rows[0],
      },
      null,
      2
    )
  );
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
