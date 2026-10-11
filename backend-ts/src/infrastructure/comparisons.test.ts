import { describe, expect, it } from "vitest";
import { isFlagged, parseComparisonPayload } from "./comparisons.js";

const snap = [{ id: "sugar", name: "Sugar" }];

describe("parseComparisonPayload", () => {
  it("accepts 2 products, one reused by scan_id", () => {
    const p = parseComparisonPayload({
      category: " Biscuits ",
      tags: ["kids", "Unknown"],
      decision: "picked",
      chosen_slot: 1,
      reasons: ["Price", "Price", " "],
      add_chosen_to_shelf: true,
      items: [
        { slot: 1, scan_id: 42 },
        { slot: 0, display_name: "Parle-G", snapshot: snap },
      ],
    });
    expect(p.category).toBe("Biscuits");
    expect(p.tags).toEqual(["Kids"]);
    expect(p.reasons).toEqual(["Price"]);
    expect(p.items.map((i) => i.slot)).toEqual([0, 1]);
    expect(p.items[1]!.scanId).toBe(42);
    expect(p.items[1]!.displayName).toBe("Product B");
    expect(p.addChosenToShelf).toBe(true);
  });

  it("rejects a fresh product with no ingredients", () => {
    expect(() =>
      parseComparisonPayload({
        items: [
          { slot: 0, snapshot: snap },
          { slot: 1, snapshot: [] },
        ],
      })
    ).toThrow("Product B has no ingredients");
  });

  it("rejects fewer than 2 or more than 3 products", () => {
    expect(() => parseComparisonPayload({ items: [{ slot: 0, snapshot: snap }] })).toThrow();
    expect(() =>
      parseComparisonPayload({
        items: [0, 1, 2, 3].map((slot) => ({ slot, snapshot: snap })),
      })
    ).toThrow();
  });

  it("rejects duplicate slots and a pick outside the products", () => {
    expect(() =>
      parseComparisonPayload({
        items: [
          { slot: 0, snapshot: snap },
          { slot: 0, snapshot: snap },
        ],
      })
    ).toThrow("Invalid product slot");
    expect(() =>
      parseComparisonPayload({
        decision: "picked",
        chosen_slot: 2,
        items: [
          { slot: 0, snapshot: snap },
          { slot: 1, snapshot: snap },
        ],
      })
    ).toThrow("chosen_slot");
  });

  it("defaults unknown decisions to undecided and ignores add-to-list without a pick", () => {
    const p = parseComparisonPayload({
      decision: "maybe",
      add_chosen_to_shelf: true,
      items: [
        { slot: 0, snapshot: snap },
        { slot: 1, snapshot: snap },
      ],
    });
    expect(p.decision).toBe("undecided");
    expect(p.chosenSlot).toBeNull();
    expect(p.addChosenToShelf).toBe(false);
  });
});

describe("snapshot label order", () => {
  it("keeps a positive integer order and drops invalid ones", () => {
    const p = parseComparisonPayload({
      items: [
        { slot: 0, snapshot: [{ id: "a", name: "A", order: 3 }, { id: "b", name: "B", order: "x" }] },
        { slot: 1, snapshot: [{ id: null, name: "C", order: 0 }] },
      ],
    });
    expect(p.items[0]!.snapshot).toEqual([{ id: "a", name: "A", order: 3 }, { id: "b", name: "B" }]);
    expect(p.items[1]!.snapshot).toEqual([{ id: null, name: "C" }]);
  });
});

describe("isFlagged", () => {
  const base = { id: "x", name: "X", category: null, e_number: null, codes: [], matched_bans: [], market_code: "IN" };
  it("flags restricted, banned and health-watch items only", () => {
    expect(isFlagged({ ...base, severity: "amber" } as never)).toBe(true);
    expect(isFlagged({ ...base, severity: "red" } as never)).toBe(true);
    expect(isFlagged({ ...base, severity: "green", has_health_watch: true } as never)).toBe(true);
    expect(isFlagged({ ...base, severity: "green", health_flags: [] } as never)).toBe(false);
    expect(isFlagged({ ...base, severity: "gray" } as never)).toBe(false);
  });
});
