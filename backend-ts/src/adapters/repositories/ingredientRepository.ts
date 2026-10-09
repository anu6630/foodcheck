import type { DataSource } from "../../domain/dataSource.js";
import type { Ingredient, IngredientUseLimit } from "../../domain/entities.js";
import type { IIngredientRepository } from "../../domain/interfaces.js";
import { listSourcesForIngredient } from "../../infrastructure/dataSources.js";
import { query } from "../../infrastructure/db.js";
import { normalizeCodeValue } from "../../domain/codes.js";
import {
  mapIngredient,
  mapUseLimit,
  type BanRow,
  type CodeRow,
  type IngredientRow,
  type NameRow,
  type UseLimitRow,
} from "./mappers.js";

export class PgIngredientRepository implements IIngredientRepository {
  async getById(ingredientId: string): Promise<Ingredient | null> {
    const { rows } = await query<IngredientRow>(
      `SELECT id, name, category, e_number, inci_name, cas_number
       FROM ingredients WHERE id = $1`,
      [ingredientId]
    );
    if (!rows[0]) return null;
    const [names, bans, codes] = await Promise.all([
      this.loadNames([ingredientId]),
      this.loadBans([ingredientId]),
      this.loadCodes([ingredientId]),
    ]);
    return mapIngredient(rows[0], names, bans, codes);
  }

  async findByNameOrSynonym(name: string): Promise<Ingredient | null> {
    if (!name) return null;
    const cleaned = name.trim().toLowerCase();
    const eMatch = cleaned.match(/\be[- ]?(\d{3,4}(?:[a-z]{1,2}|\([ivx]+\))?)\b/i);
    const insMatch = cleaned.match(/\bins[- ]?(\d{3,4}(?:[a-z]{1,2}|\([ivx]+\))?)\b/i);
    const code = (eMatch?.[1] || insMatch?.[1] || "").toUpperCase();
    const eNumber = code ? `E${code}` : null;
    const wanted = code ? normalizeCodeValue(code) : "";

    const { rows } = await query<IngredientRow>(
      `SELECT id, name, category, e_number, inci_name, cas_number
       FROM ingredients
       WHERE id = $1
          OR LOWER(name) = $1
          OR ($2::text IS NOT NULL AND e_number = $2)
       LIMIT 1`,
      [cleaned, eNumber]
    );
    if (rows[0]) return this.getById(rows[0].id);

    if (wanted) {
      const byCode = await query<{ ingredient_id: string }>(
        `SELECT ingredient_id FROM ingredient_codes
         WHERE UPPER(REPLACE(code, ' ', '')) = $1
         LIMIT 1`,
        [wanted]
      );
      if (byCode.rows[0]) return this.getById(byCode.rows[0].ingredient_id);
    }

    const synExact = await query<{ ingredient_id: string }>(
      `SELECT ingredient_id FROM ingredient_names WHERE LOWER(name) = $1 LIMIT 1`,
      [cleaned]
    );
    if (synExact.rows[0]) return this.getById(synExact.rows[0].ingredient_id);

    if (cleaned.length >= 5) {
      const synPartial = await query<{ ingredient_id: string }>(
        `SELECT ingredient_id FROM ingredient_names WHERE name ILIKE $1 LIMIT 1`,
        [`%${cleaned}%`]
      );
      if (synPartial.rows[0]) return this.getById(synPartial.rows[0].ingredient_id);
    }

    return null;
  }

  async searchIngredients(searchQuery: string, limit = 10): Promise<Ingredient[]> {
    const cleaned = (searchQuery || "").trim();
    if (!cleaned) return [];
    const cleanedLower = cleaned.toLowerCase();
    const like = `%${cleaned}%`;

    const rankFor = (text: string | null | undefined): number => {
      if (!text) return 3;
      const t = text.toLowerCase();
      if (t === cleanedLower) return 0;
      if (t.startsWith(cleanedLower)) return 1;
      if (t.includes(cleanedLower)) return 2;
      return 3;
    };

    const candidateRanks = new Map<string, number>();

    const direct = await query<{ id: string; name: string; e_number: string | null }>(
      `SELECT id, name, e_number FROM ingredients
       WHERE name ILIKE $1 OR e_number ILIKE $1`,
      [like]
    );
    for (const row of direct.rows) {
      const rank = Math.min(rankFor(row.name), rankFor(row.e_number));
      candidateRanks.set(row.id, Math.min(candidateRanks.get(row.id) ?? 3, rank));
    }

    const syn = await query<{ ingredient_id: string; name: string }>(
      `SELECT ingredient_id, name FROM ingredient_names
       WHERE name ILIKE $1
       ORDER BY
         CASE
           WHEN LOWER(name) = $2 THEN 0
           WHEN LOWER(name) LIKE $3 THEN 1
           ELSE 2
         END
       LIMIT 300`,
      [like, cleanedLower, `${cleanedLower}%`]
    );
    for (const row of syn.rows) {
      const rank = rankFor(row.name);
      candidateRanks.set(
        row.ingredient_id,
        Math.min(candidateRanks.get(row.ingredient_id) ?? 3, rank)
      );
    }

    if (!candidateRanks.size) return [];

    const rankedIds = [...candidateRanks.entries()]
      .sort((a, b) => a[1] - b[1])
      .slice(0, limit)
      .map(([id]) => id);

    const results: Ingredient[] = [];
    for (const id of rankedIds) {
      const ing = await this.getById(id);
      if (ing) results.push(ing);
    }
    return results;
  }

  async getUseLimits(ingredientId: string, perCountry = 16): Promise<IngredientUseLimit[]> {
    const { rows } = await query<UseLimitRow>(
      `SELECT ingredient_id, country_code, food_category_code, food_category_name,
              max_level, note, regulation_ref
       FROM (
         SELECT ingredient_id, country_code, food_category_code, food_category_name,
                max_level, note, regulation_ref,
                ROW_NUMBER() OVER (
                  PARTITION BY country_code
                  ORDER BY CASE WHEN food_category_code = 'GMP' THEN 1 ELSE 0 END,
                           food_category_code, max_level
                ) AS rn
         FROM ingredient_use_limits
         WHERE ingredient_id = $1
       ) ranked
       WHERE rn <= $2
       ORDER BY CASE country_code WHEN 'IN' THEN 0 ELSE 1 END,
                country_code, rn`,
      [ingredientId, perCountry]
    );
    return rows.map(mapUseLimit);
  }

  async getUseLimitCounts(ingredientId: string): Promise<Record<string, number>> {
    const { rows } = await query<{ country_code: string; n: string }>(
      `SELECT country_code, COUNT(*)::text AS n
       FROM ingredient_use_limits WHERE ingredient_id = $1
       GROUP BY country_code`,
      [ingredientId]
    );
    const out: Record<string, number> = {};
    for (const r of rows) out[r.country_code] = Number(r.n);
    return out;
  }

  async listSources(ingredientId: string): Promise<DataSource[]> {
    return listSourcesForIngredient(ingredientId);
  }

  async save(ingredient: Ingredient): Promise<Ingredient> {
    await query(
      `INSERT INTO ingredients (id, name, category, e_number, inci_name, cas_number)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (id) DO UPDATE SET
         name = EXCLUDED.name,
         category = EXCLUDED.category,
         e_number = EXCLUDED.e_number,
         inci_name = EXCLUDED.inci_name,
         cas_number = EXCLUDED.cas_number`,
      [
        ingredient.id,
        ingredient.name,
        ingredient.category ?? null,
        ingredient.e_number ?? null,
        ingredient.inci_name ?? null,
        ingredient.cas_number ?? null,
      ]
    );

    await query(`DELETE FROM ingredient_names WHERE ingredient_id = $1`, [ingredient.id]);
    await query(`DELETE FROM ingredient_bans WHERE ingredient_id = $1`, [ingredient.id]);
    if (ingredient.codes?.length) {
      await query(`DELETE FROM ingredient_codes WHERE ingredient_id = $1`, [ingredient.id]);
    }

    for (const n of ingredient.names) {
      await query(
        `INSERT INTO ingredient_names (ingredient_id, lang, name) VALUES ($1, $2, $3)`,
        [ingredient.id, n.lang, n.name]
      );
    }
    for (const c of ingredient.codes || []) {
      await query(
        `INSERT INTO ingredient_codes (ingredient_id, system, code)
         VALUES ($1, $2, $3)
         ON CONFLICT DO NOTHING`,
        [ingredient.id, c.system.toUpperCase(), normalizeCodeValue(c.code)]
      );
    }
    for (const b of ingredient.bans) {
      await query(
        `INSERT INTO ingredient_bans
           (ingredient_id, country_code, status, condition, regulation_ref, effective_date)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [
          ingredient.id,
          b.country_code,
          b.status,
          b.condition ?? null,
          b.regulation_ref ?? null,
          b.effective_date ?? null,
        ]
      );
    }

    return (await this.getById(ingredient.id))!;
  }

  private async loadNames(ids: string[]): Promise<NameRow[]> {
    if (!ids.length) return [];
    const { rows } = await query<NameRow>(
      `SELECT ingredient_id, lang, name FROM ingredient_names WHERE ingredient_id = ANY($1)`,
      [ids]
    );
    return rows;
  }

  private async loadBans(ids: string[]): Promise<BanRow[]> {
    if (!ids.length) return [];
    const { rows } = await query<BanRow>(
      `SELECT ingredient_id, country_code, status, condition, regulation_ref, effective_date
       FROM ingredient_bans WHERE ingredient_id = ANY($1)`,
      [ids]
    );
    return rows;
  }

  private async loadCodes(ids: string[]): Promise<CodeRow[]> {
    if (!ids.length) return [];
    const { rows } = await query<CodeRow>(
      `SELECT ingredient_id, system, code FROM ingredient_codes WHERE ingredient_id = ANY($1)`,
      [ids]
    );
    return rows;
  }
}
