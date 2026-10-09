import type { DataSource } from "./dataSource.js";
import type { Ingredient, IngredientUseLimit, Product, ScanAgentResult } from "./entities.js";

export interface IProductRepository {
  getByBarcode(barcode: string): Promise<Product | null>;
  searchByName(query: string): Promise<Product[]>;
  searchByIngredientNames(names: string[]): Promise<Array<[Product, number]>>;
  save(product: Product): Promise<Product>;
}

export interface IIngredientRepository {
  getById(ingredientId: string): Promise<Ingredient | null>;
  findByNameOrSynonym(name: string): Promise<Ingredient | null>;
  searchIngredients(query: string, limit?: number): Promise<Ingredient[]>;
  getUseLimits(ingredientId: string, perCountry?: number): Promise<IngredientUseLimit[]>;
  getUseLimitCounts(ingredientId: string): Promise<Record<string, number>>;
  listSources(ingredientId: string): Promise<DataSource[]>;
  save(ingredient: Ingredient): Promise<Ingredient>;
}

export interface IDataSourceRepository {
  listAll(): Promise<DataSource[]>;
}

export interface IProductLookupService {
  lookupBarcode(barcode: string): Promise<Product | null>;
  searchByName?(query: string): Promise<Product[]>;
}

export interface IScanAgentService {
  analyzeImages(images: Buffer[], mockType?: string | null): Promise<ScanAgentResult>;
}

export interface IObjectStorage {
  put(key: string, body: Buffer, contentType: string): Promise<void>;
  get(key: string): Promise<Buffer | null>;
}
