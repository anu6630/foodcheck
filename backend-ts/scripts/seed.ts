import "dotenv/config";
import { ensureSchema } from "../src/infrastructure/schemaEnsure.js";
import { query, pool } from "../src/infrastructure/db.js";

async function upsertIngredient(args: {
  id: string;
  name: string;
  category?: string;
  e_number?: string;
  bans?: Array<{ country_code: string; status: string; condition?: string; regulation_ref?: string }>;
}) {
  await query(
    `INSERT INTO ingredients (id, name, category, e_number)
     VALUES ($1,$2,$3,$4)
     ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, category = EXCLUDED.category, e_number = EXCLUDED.e_number`,
    [args.id, args.name, args.category ?? null, args.e_number ?? null]
  );
  for (const b of args.bans || []) {
    await query(
      `INSERT INTO ingredient_bans (ingredient_id, country_code, status, condition, regulation_ref)
       VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (ingredient_id, country_code) DO NOTHING`,
      [args.id, b.country_code, b.status, b.condition ?? null, b.regulation_ref ?? null]
    );
  }
}

async function upsertProduct(args: {
  name: string;
  brand: string;
  barcode: string;
  category: string;
  ingredientIds: string[];
}) {
  const existing = await query<{ id: number }>(
    `SELECT id FROM products WHERE barcode = $1`,
    [args.barcode]
  );
  let id: number;
  if (existing.rows[0]) {
    id = existing.rows[0].id;
    await query(
      `UPDATE products SET name=$1, brand=$2, category=$3, source='local', published=TRUE WHERE id=$4`,
      [args.name, args.brand, args.category, id]
    );
    await query(`DELETE FROM product_ingredients WHERE product_id = $1`, [id]);
  } else {
    const inserted = await query<{ id: number }>(
      `INSERT INTO products (name, brand, barcode, category, source, published)
       VALUES ($1,$2,$3,$4,'local',TRUE) RETURNING id`,
      [args.name, args.brand, args.barcode, args.category]
    );
    id = inserted.rows[0].id;
  }
  for (let i = 0; i < args.ingredientIds.length; i++) {
    await query(
      `INSERT INTO product_ingredients (product_id, ingredient_id, "order") VALUES ($1,$2,$3)`,
      [id, args.ingredientIds[i], i]
    );
  }
}

async function main() {
  console.log("Seeding FoodCheck Neon DB...");
  await ensureSchema();

  await upsertIngredient({
    id: "sugar",
    name: "Sugar",
    category: "sweetener",
  });
  await upsertIngredient({
    id: "wheat-flour",
    name: "Wheat flour",
    category: "grain",
  });
  await upsertIngredient({
    id: "sodium-benzoate",
    name: "Sodium benzoate",
    category: "preservative",
    e_number: "E211",
    bans: [
      {
        country_code: "IN",
        status: "restricted",
        condition: "Limited use in certain categories",
        regulation_ref: "FSSAI",
      },
    ],
  });
  await upsertIngredient({
    id: "titanium-dioxide",
    name: "Titanium dioxide",
    category: "color",
    e_number: "E171",
    bans: [
      {
        country_code: "EU",
        status: "banned",
        condition: "Not authorised as a food additive",
        regulation_ref: "EU 2022/63",
      },
    ],
  });
  await upsertIngredient({
    id: "potassium-bromate",
    name: "Potassium bromate",
    category: "improver",
    bans: [
      { country_code: "IN", status: "banned", regulation_ref: "FSSAI" },
      { country_code: "EU", status: "banned" },
    ],
  });
  await upsertIngredient({
    id: "tartrazine",
    name: "Tartrazine",
    category: "color",
    e_number: "E102",
    bans: [
      {
        country_code: "EU",
        status: "restricted",
        condition: "Requires warning label",
      },
    ],
  });
  await upsertIngredient({
    id: "water",
    name: "Water",
    category: "base",
  });
  await upsertIngredient({
    id: "yeast",
    name: "Yeast",
    category: "leavening",
  });
  await upsertIngredient({
    id: "wheat-starch",
    name: "Wheat starch",
    category: "starch",
  });
  await upsertIngredient({
    id: "artificial-flavor",
    name: "Artificial flavor",
    category: "flavor",
  });
  await upsertIngredient({
    id: "maltodextrin",
    name: "Maltodextrin",
    category: "carbohydrate",
  });
  await upsertIngredient({
    id: "cocoa",
    name: "Cocoa",
    category: "flavor",
  });

  await upsertProduct({
    name: "White Cookie Frosting",
    brand: "Betty Crocker",
    barcode: "016000452327",
    category: "baking",
    ingredientIds: [
      "sugar",
      "wheat-starch",
      "titanium-dioxide",
      "sodium-benzoate",
      "artificial-flavor",
    ],
  });

  await upsertProduct({
    name: "White Bread",
    brand: "Generic",
    barcode: "111111111111",
    category: "bakery",
    ingredientIds: ["wheat-flour", "sugar", "sodium-benzoate", "yeast"],
  });

  await upsertProduct({
    name: "Bournvita",
    brand: "Cadbury",
    barcode: "8901058001234",
    category: "beverage",
    ingredientIds: ["maltodextrin", "sugar", "cocoa", "sodium-benzoate"],
  });

  console.log("Seed complete.");
  await pool.end();
}

main().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
