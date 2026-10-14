import type { components } from "./generated/schema";

type Quote = components["schemas"]["QuoteResponse"];

export interface PriceDelta {
  previousMinor: number;
  nextMinor: number;
}

export interface MealPriceDelta extends PriceDelta {
  mealId: string;
  title: string;
  quantity: number;
}

/**
 * What changed between the quote a customer saw and a fresh server quote for the same basket (owner decision D13).
 * Meal unit prices are the kitchen's; the delivery fee and tax are platform/zone components and are reported separately,
 * so a message never blames the kitchen for a fee or policy change. Amounts are integer piastres from the server.
 */
export interface PriceChanges {
  /** Meals whose unit price changed (the kitchen updated the price). */
  meals: MealPriceDelta[];
  deliveryFee: PriceDelta | null;
  tax: PriceDelta | null;
  total: PriceDelta;
  /** The total changed but no meal price, fee or tax explains it, or the basket's lines differ: show a generic notice. */
  unexplained: boolean;
}

export function priceChanges(previous: Quote, next: Quote): PriceChanges {
  const before = new Map(previous.items.map((i) => [i.mealId, i]));
  const meals: MealPriceDelta[] = [];
  let sameLines = previous.items.length === next.items.length;
  for (const line of next.items) {
    const old = before.get(line.mealId);
    if (!old || old.quantity !== line.quantity) {
      sameLines = false;
      continue;
    }
    if (old.unitPrice.amountMinor !== line.unitPrice.amountMinor) {
      meals.push({ mealId: line.mealId, title: line.title, quantity: line.quantity, previousMinor: old.unitPrice.amountMinor, nextMinor: line.unitPrice.amountMinor });
    }
  }
  const delta = (a: number, b: number): PriceDelta | null => (a === b ? null : { previousMinor: a, nextMinor: b });
  const deliveryFee = delta(previous.deliveryFee.amountMinor, next.deliveryFee.amountMinor);
  // Tax follows the subtotal and fee; it is reported only when the rate itself must have changed.
  const taxChangedAlone = previous.tax.amountMinor !== next.tax.amountMinor && meals.length === 0 && deliveryFee === null;
  const tax = taxChangedAlone ? delta(previous.tax.amountMinor, next.tax.amountMinor) : null;
  const total = { previousMinor: previous.total.amountMinor, nextMinor: next.total.amountMinor };
  const explained = meals.length > 0 || deliveryFee !== null || tax !== null;
  return { meals, deliveryFee, tax, total, unexplained: !sameLines || !explained };
}
