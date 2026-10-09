import { describe, expect, it } from "vitest";
import { resolveFoodCategoryName } from "./fssaiCategories.js";

describe("resolveFoodCategoryName", () => {
  it("uses Codex titles instead of scraped additive names", () => {
    expect(resolveFoodCategoryName("7.2.1", "Tartarazine")).toMatch(/cakes/i);
    expect(resolveFoodCategoryName("1.7", "Dairy based")).toMatch(/dessert/i);
    expect(resolveFoodCategoryName("14.1.4.2", "Water-based")).toMatch(/flavoured drink/i);
  });

  it("falls back to the parent Codex title", () => {
    expect(resolveFoodCategoryName("7.2.1.9", "junk")).toMatch(/cakes/i);
  });

  it("keeps GMP readable", () => {
    expect(resolveFoodCategoryName("GMP", "Tartarazine")).toMatch(/GMP/i);
  });
});
