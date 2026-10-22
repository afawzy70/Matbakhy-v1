import { describe, expect, it } from "vitest";
import { priceChanges } from "../src/priceChanges";

const egp = (amountMinor: number) => ({ amountMinor, currency: "EGP" as const });
function quote(lines: [string, number, number][], fee: number, tax = 0) {
  const items = lines.map(([mealId, quantity, unit]) => ({ mealId, mealRevision: 1, title: `Meal ${mealId}`, quantity, unitPrice: egp(unit), lineTotal: egp(unit * quantity) }));
  const subtotal = items.reduce((s, i) => s + i.lineTotal.amountMinor, 0);
  return {
    quoteId: "q", expiresAt: "2026-10-13T10:00:00Z", kitchenId: "k", items, subtotal: egp(subtotal), deliveryFee: egp(fee), tax: egp(tax),
    total: egp(subtotal + fee + tax), cancellationPolicyVersion: "c", termsVersion: "t", acceptanceDeadlinePreview: "2026-10-13T10:00:00Z", warnings: [],
    payment: { method: "COD" as const, depositBps: 0, deposit: egp(0), remainder: egp(subtotal + fee + tax), recipient: null },
  };
}

describe("priceChanges (D13)", () => {
  it("reports a kitchen price increase and decrease on several lines with the exact totals", () => {
    const c = priceChanges(quote([["a", 2, 12000], ["b", 1, 8000]], 3000), quote([["a", 2, 13000], ["b", 1, 7500]], 3000));
    expect(c.meals).toEqual([
      { mealId: "a", title: "Meal a", quantity: 2, previousMinor: 12000, nextMinor: 13000 },
      { mealId: "b", title: "Meal b", quantity: 1, previousMinor: 8000, nextMinor: 7500 },
    ]);
    expect(c.deliveryFee).toBeNull();
    expect(c.total).toEqual({ previousMinor: 35000, nextMinor: 36500 });
    expect(c.unexplained).toBe(false);
  });

  it("attributes a fee-only change to the delivery fee, not to the kitchen", () => {
    const c = priceChanges(quote([["a", 1, 12000]], 3000), quote([["a", 1, 12000]], 3125));
    expect(c.meals).toEqual([]);
    expect(c.deliveryFee).toEqual({ previousMinor: 3000, nextMinor: 3125 });
    expect(c.total).toEqual({ previousMinor: 15000, nextMinor: 15125 });
  });

  it("reports a tax change only when nothing else explains it, and flags an unexplained change", () => {
    expect(priceChanges(quote([["a", 1, 10000]], 0, 0), quote([["a", 1, 10000]], 0, 1400)).tax).toEqual({ previousMinor: 0, nextMinor: 1400 });
    const same = priceChanges(quote([["a", 1, 10000]], 0), quote([["a", 1, 10000]], 0));
    expect(same.unexplained).toBe(true);
    expect(priceChanges(quote([["a", 1, 10000]], 0), quote([["a", 2, 10000]], 0)).unexplained).toBe(true);
  });
});
