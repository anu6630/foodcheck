import { describe, expect, it } from "vitest";
import {
  annotateIngredient,
  healthFlagLabel,
  splitRoleAndCodes,
  worstSeverity,
} from "./usecases.js";
import type { AnnotatedIngredient, Ingredient } from "./entities.js";

describe("annotateIngredient market scope", () => {
  const base: Ingredient = {
    id: "asp",
    name: "Aspartame",
    names: [],
    bans: [
      { country_code: "IN", status: "permitted" },
      { country_code: "EU", status: "banned" },
    ],
  };

  it("uses only the selected market for severity", () => {
    expect(annotateIngredient(base, "IN").severity).toBe("green");
    expect(annotateIngredient(base, "EU").severity).toBe("red");
  });

  it("stays gray when market has no ban row (ignores other countries)", () => {
    expect(annotateIngredient(base, "US").severity).toBe("gray");
  });
});

describe("healthFlagLabel", () => {
  it("formats common kinds", () => {
    expect(healthFlagLabel("iarc_2b", "2B")).toBe("IARC 2B");
    expect(healthFlagLabel("iarc_2b", "iarc_2b")).toBe("IARC 2B");
    expect(healthFlagLabel("authorisation_withdrawn")).toBe("EU withdrawn");
    expect(healthFlagLabel("genotox", "Positive")).toBe("Genotox+");
  });
});

describe("splitRoleAndCodes", () => {
  it("splits Colours (171, 102, …)", () => {
    const g = splitRoleAndCodes("Colours (171, 102, 133, 124, 127, 122, 132, 110)");
    expect(g?.role).toBe("Colours");
    expect(g?.codes).toEqual(["171", "102", "133", "124", "127", "122", "132", "110"]);
  });

  it("keeps 500(ii) inside Raising Agent", () => {
    const g = splitRoleAndCodes("Raising Agent (500(ii))");
    expect(g?.role).toBe("Raising Agent");
    expect(g?.codes).toEqual(["500(ii)"]);
  });

  it("splits Emulsifiers (322, 471)", () => {
    const g = splitRoleAndCodes("Emulsifiers (322, 471)");
    expect(g?.codes).toEqual(["322", "471"]);
  });

  it("rejects percentage amounts", () => {
    expect(splitRoleAndCodes("Cocoa Solids (8%)")).toBeNull();
    expect(splitRoleAndCodes("Cereal Extract 51%* (Barley, Wheat)")).toBeNull();
  });
});

describe("worstSeverity", () => {
  const item = (severity: AnnotatedIngredient["severity"]): AnnotatedIngredient => ({
    id: null,
    name: "x",
    category: null,
    e_number: null,
    severity,
    matched_bans: [],
  });

  it("parent is red if any child is banned", () => {
    expect(worstSeverity([item("green"), item("red"), item("gray")])).toBe("red");
  });

  it("falls back through amber then green then gray", () => {
    expect(worstSeverity([item("gray"), item("green")])).toBe("green");
    expect(worstSeverity([item("gray")])).toBe("gray");
  });
});
