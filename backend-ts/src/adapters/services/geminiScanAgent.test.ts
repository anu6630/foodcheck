import { describe, expect, it } from "vitest";
import { parseLabelFacts } from "./geminiScanAgent.js";

describe("parseLabelFacts", () => {
  it("keeps one valid role per image and normalises category", () => {
    expect(
      parseLabelFacts(
        { image_roles: ["Front", "ingredients"], category: "biscuits", ingredients_complete: true },
        2
      )
    ).toEqual({ image_roles: ["front", "ingredients"], category: "Biscuits", ingredients_complete: true });
  });

  it("maps unknown roles to other and drops unknown categories", () => {
    expect(parseLabelFacts({ image_roles: ["back"], category: "Candy" }, 1)).toEqual({
      image_roles: ["other"],
      category: null,
      ingredients_complete: null,
    });
  });

  it("returns no roles when the model gave fewer roles than images", () => {
    expect(parseLabelFacts({ image_roles: ["both"] }, 2).image_roles).toEqual([]);
    expect(parseLabelFacts({}, 1).image_roles).toEqual([]);
  });

  it("ignores extra roles beyond the uploaded images", () => {
    expect(parseLabelFacts({ image_roles: ["both", "front"] }, 1).image_roles).toEqual(["both"]);
  });
});
