import { describe, expect, it, vi } from "vitest";
import { UnifiedSearchUseCase } from "./usecases.js";
import type { IIngredientRepository, IProductRepository } from "./usecases.js";
import type { Ingredient } from "./entities.js";

describe("UnifiedSearchUseCase legal compliance & deprecation", () => {
  it("never returns public commercial products from the catalog to avoid brand defamation/legal risk", async () => {
    const mockProductRepo: IProductRepository = {
      getByBarcode: vi.fn(),
      searchByName: vi.fn().mockResolvedValue([
        {
          barcode: "8901058862492",
          name: "Maggi 2-Minute Noodles",
          brand: "Nestle",
          ingredients_text: "Monosodium glutamate",
        },
      ]),
      save: vi.fn(),
    };

    const mockIngredient: Ingredient = {
      id: "monosodium-l-glutamate",
      name: "Monosodium L-glutamate",
      e_number: "E621",
      names: ["Ajinomoto", "MSG", "Agino motto"],
      bans: [{ country_code: "IN", status: "permitted" }],
    };

    const mockIngredientRepo: IIngredientRepository = {
      getById: vi.fn(),
      searchIngredients: vi.fn().mockResolvedValue([mockIngredient]),
      getUseLimits: vi.fn().mockResolvedValue([]),
      getUseLimitCounts: vi.fn().mockResolvedValue({}),
      getHealthReports: vi.fn().mockResolvedValue([]),
      getDataSources: vi.fn().mockResolvedValue([]),
      getHealthFlagsByIngredientIds: vi.fn().mockResolvedValue(new Map()),
      listAll: vi.fn(),
    };

    const usecase = new UnifiedSearchUseCase(mockProductRepo, mockIngredientRepo);
    const result = await usecase.execute("ajinomoto");

    // 1. Products MUST be empty array (public commercial products deprecated)
    expect(result.products).toEqual([]);

    // 2. Ingredients are returned with annotations
    expect(result.ingredients).toHaveLength(1);
    expect(result.ingredients[0].id).toBe("monosodium-l-glutamate");
    expect(result.ingredients[0].name).toBe("Monosodium L-glutamate");
    expect(result.ingredients[0].severity).toBe("green");
  });
});
