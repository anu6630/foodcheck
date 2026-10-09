import type { Product, ProductIngredient } from "../../domain/entities.js";
import type { IProductRepository } from "../../domain/interfaces.js";
import { query } from "../../infrastructure/db.js";
import {
  mapIngredient,
  mapProduct,
  type BanRow,
  type CodeRow,
  type IngredientRow,
  type NameRow,
  type ProductIngredientRow,
  type ProductRow,
} from "./mappers.js";

export class PgProductRepository implements IProductRepository {
  async getByBarcode(barcode: string): Promise<Product | null> {
    const { rows } = await query<ProductRow>(
      `SELECT id, name, brand, barcode, category, source, published
       FROM products WHERE barcode = $1`,
      [barcode]
    );
    if (!rows[0]) return null;
    return this.hydrate(rows[0]);
  }

  async searchByName(searchQuery: string): Promise<Product[]> {
    let sql = `SELECT id, name, brand, barcode, category, source, published FROM products`;
    const params: unknown[] = [];
    if (searchQuery) {
      sql += ` WHERE name ILIKE $1 OR brand ILIKE $1`;
      params.push(`%${searchQuery}%`);
    }
    sql += ` ORDER BY id`;
    const { rows } = await query<ProductRow>(sql, params);
    const products: Product[] = [];
    for (const row of rows) {
      products.push(await this.hydrate(row));
    }
    return products;
  }

  async searchByIngredientNames(names: string[]): Promise<Array<[Product, number]>> {
    const cleaned = names.map((n) => n.trim()).filter(Boolean);
    if (!cleaned.length) return [];

    const ingredientIds = new Set<string>();
    for (const n of cleaned) {
      const like = `%${n}%`;
      const direct = await query<{ id: string }>(
        `SELECT id FROM ingredients WHERE name ILIKE $1`,
        [like]
      );
      for (const r of direct.rows) ingredientIds.add(r.id);
      const syn = await query<{ ingredient_id: string }>(
        `SELECT ingredient_id FROM ingredient_names WHERE name ILIKE $1`,
        [like]
      );
      for (const r of syn.rows) ingredientIds.add(r.ingredient_id);
    }

    if (!ingredientIds.size) return [];

    const all = await this.searchByName("");
    const results: Array<[Product, number]> = [];
    for (const p of all) {
      const pIds = new Set(p.ingredients.map((pi) => pi.ingredient.id));
      if (!pIds.size) continue;
      let intersect = 0;
      for (const id of pIds) if (ingredientIds.has(id)) intersect++;
      if (intersect > 0) {
        results.push([p, intersect / pIds.size]);
      }
    }
    results.sort((a, b) => b[1] - a[1]);
    return results;
  }

  async save(product: Product): Promise<Product> {
    const existing = await query<{ id: number }>(
      `SELECT id FROM products WHERE barcode = $1`,
      [product.barcode]
    );

    let productId: number;
    if (!existing.rows[0]) {
      const inserted = await query<{ id: number }>(
        `INSERT INTO products (name, brand, barcode, category, source, published)
         VALUES ($1, $2, $3, $4, $5, TRUE)
         RETURNING id`,
        [
          product.name,
          product.brand ?? null,
          product.barcode,
          product.category ?? null,
          product.source,
        ]
      );
      productId = inserted.rows[0].id;
    } else {
      productId = existing.rows[0].id;
      await query(
        `UPDATE products SET name = $1, brand = $2, category = $3, source = $4 WHERE id = $5`,
        [
          product.name,
          product.brand ?? null,
          product.category ?? null,
          product.source,
          productId,
        ]
      );
      await query(`DELETE FROM product_ingredients WHERE product_id = $1`, [productId]);
    }

    for (const pi of product.ingredients) {
      if (!pi.ingredient.id) continue;
      const known = await query<{ id: string }>(
        `SELECT id FROM ingredients WHERE id = $1`,
        [pi.ingredient.id]
      );
      if (!known.rows[0]) continue;
      await query(
        `INSERT INTO product_ingredients (product_id, ingredient_id, "order")
         VALUES ($1, $2, $3)`,
        [productId, pi.ingredient.id, pi.order]
      );
    }

    return (await this.getByBarcode(product.barcode))!;
  }

  private async hydrate(row: ProductRow): Promise<Product> {
    const links = await query<ProductIngredientRow>(
      `SELECT product_id, ingredient_id, "order"
       FROM product_ingredients WHERE product_id = $1 ORDER BY "order"`,
      [row.id]
    );
    const ingredientIds = links.rows.map((l) => l.ingredient_id);
    if (!ingredientIds.length) return mapProduct(row, []);

    const { rows: ingRows } = await query<IngredientRow>(
      `SELECT id, name, category, e_number, inci_name, cas_number
       FROM ingredients WHERE id = ANY($1)`,
      [ingredientIds]
    );
    const { rows: nameRows } = await query<NameRow>(
      `SELECT ingredient_id, lang, name FROM ingredient_names WHERE ingredient_id = ANY($1)`,
      [ingredientIds]
    );
    const { rows: banRows } = await query<BanRow>(
      `SELECT ingredient_id, country_code, status, condition, regulation_ref, effective_date
       FROM ingredient_bans WHERE ingredient_id = ANY($1)`,
      [ingredientIds]
    );
    const { rows: codeRows } = await query<CodeRow>(
      `SELECT ingredient_id, system, code FROM ingredient_codes WHERE ingredient_id = ANY($1)`,
      [ingredientIds]
    );

    const byId = new Map(ingRows.map((i) => [i.id, i]));
    const ingredients: ProductIngredient[] = [];
    for (const link of links.rows) {
      const ing = byId.get(link.ingredient_id);
      if (!ing) continue;
      ingredients.push({
        order: link.order,
        ingredient: mapIngredient(
          ing,
          nameRows.filter((n) => n.ingredient_id === ing.id),
          banRows.filter((b) => b.ingredient_id === ing.id),
          codeRows.filter((c) => c.ingredient_id === ing.id)
        ),
      });
    }
    return mapProduct(row, ingredients);
  }
}
