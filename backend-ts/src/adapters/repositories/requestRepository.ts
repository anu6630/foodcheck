import { query } from "../../infrastructure/db.js";

export interface ProductRequestRow {
  id: number;
  created_at: Date | string;
  status: string;
  product_name: string;
  brand: string | null;
  barcode: string | null;
  category: string | null;
  country_code: string;
  ingredients_text: string | null;
  requester_notes: string | null;
  image_1_key: string | null;
  image_2_key: string | null;
  image_3_key: string | null;
}

export interface IngredientRequestRow {
  id: number;
  created_at: Date | string;
  status: string;
  ingredient_name: string;
  e_number: string | null;
  category: string | null;
  requester_notes: string | null;
}

export class PgRequestRepository {
  async createProductRequest(input: {
    product_name: string;
    country_code: string;
    brand?: string | null;
    barcode?: string | null;
    category?: string | null;
    ingredients_text?: string | null;
    requester_notes?: string | null;
    image_1_key?: string | null;
    image_2_key?: string | null;
    image_3_key?: string | null;
  }): Promise<ProductRequestRow> {
    const { rows } = await query<ProductRequestRow>(
      `INSERT INTO product_requests
         (product_name, country_code, brand, barcode, category, ingredients_text,
          requester_notes, image_1_key, image_2_key, image_3_key, status)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'pending')
       RETURNING *`,
      [
        input.product_name,
        input.country_code,
        input.brand ?? null,
        input.barcode ?? null,
        input.category ?? null,
        input.ingredients_text ?? null,
        input.requester_notes ?? null,
        input.image_1_key ?? null,
        input.image_2_key ?? null,
        input.image_3_key ?? null,
      ]
    );
    return rows[0];
  }

  async listProductRequests(status?: string | null): Promise<ProductRequestRow[]> {
    if (status) {
      const { rows } = await query<ProductRequestRow>(
        `SELECT * FROM product_requests WHERE status = $1 ORDER BY created_at DESC`,
        [status]
      );
      return rows;
    }
    const { rows } = await query<ProductRequestRow>(
      `SELECT * FROM product_requests ORDER BY created_at DESC`
    );
    return rows;
  }

  async getProductRequest(id: number): Promise<ProductRequestRow | null> {
    const { rows } = await query<ProductRequestRow>(
      `SELECT * FROM product_requests WHERE id = $1`,
      [id]
    );
    return rows[0] ?? null;
  }

  async updateProductRequestStatus(id: number, status: string): Promise<ProductRequestRow | null> {
    const { rows } = await query<ProductRequestRow>(
      `UPDATE product_requests SET status = $1 WHERE id = $2 RETURNING *`,
      [status, id]
    );
    return rows[0] ?? null;
  }

  async createIngredientRequest(input: {
    ingredient_name: string;
    e_number?: string | null;
    category?: string | null;
    requester_notes?: string | null;
  }): Promise<IngredientRequestRow> {
    const { rows } = await query<IngredientRequestRow>(
      `INSERT INTO ingredient_requests
         (ingredient_name, e_number, category, requester_notes, status)
       VALUES ($1,$2,$3,$4,'pending')
       RETURNING *`,
      [
        input.ingredient_name,
        input.e_number ?? null,
        input.category ?? null,
        input.requester_notes ?? null,
      ]
    );
    return rows[0];
  }

  async listIngredientRequests(status?: string | null): Promise<IngredientRequestRow[]> {
    if (status) {
      const { rows } = await query<IngredientRequestRow>(
        `SELECT * FROM ingredient_requests WHERE status = $1 ORDER BY created_at DESC`,
        [status]
      );
      return rows;
    }
    const { rows } = await query<IngredientRequestRow>(
      `SELECT * FROM ingredient_requests ORDER BY created_at DESC`
    );
    return rows;
  }

  async getIngredientRequest(id: number): Promise<IngredientRequestRow | null> {
    const { rows } = await query<IngredientRequestRow>(
      `SELECT * FROM ingredient_requests WHERE id = $1`,
      [id]
    );
    return rows[0] ?? null;
  }

  async updateIngredientRequestStatus(
    id: number,
    status: string
  ): Promise<IngredientRequestRow | null> {
    const { rows } = await query<IngredientRequestRow>(
      `UPDATE ingredient_requests SET status = $1 WHERE id = $2 RETURNING *`,
      [status, id]
    );
    return rows[0] ?? null;
  }

  async publishProduct(productId: number) {
    const { rows } = await query<{ id: number; name: string; published: boolean }>(
      `UPDATE products SET published = TRUE WHERE id = $1 RETURNING id, name, published`,
      [productId]
    );
    return rows[0] ?? null;
  }

  async createProductDirect(input: {
    name: string;
    barcode: string;
    brand?: string | null;
    category?: string | null;
    source?: string;
    published?: boolean;
  }) {
    const { rows } = await query<{ id: number; name: string; barcode: string; published: boolean }>(
      `INSERT INTO products (name, barcode, brand, category, source, published)
       VALUES ($1,$2,$3,$4,$5,$6)
       RETURNING id, name, barcode, published`,
      [
        input.name,
        input.barcode,
        input.brand ?? null,
        input.category ?? null,
        input.source ?? "local",
        input.published ?? true,
      ]
    );
    return rows[0];
  }

  async createBanDirect(input: {
    ingredient_id: string;
    country_code: string;
    status: string;
    condition?: string | null;
    regulation_ref?: string | null;
  }) {
    const { rows } = await query(
      `INSERT INTO ingredient_bans
         (ingredient_id, country_code, status, condition, regulation_ref)
       VALUES ($1,$2,$3,$4,$5)
       RETURNING id, ingredient_id, country_code, status, condition, regulation_ref`,
      [
        input.ingredient_id,
        input.country_code,
        input.status,
        input.condition ?? null,
        input.regulation_ref ?? null,
      ]
    );
    return rows[0];
  }
}
