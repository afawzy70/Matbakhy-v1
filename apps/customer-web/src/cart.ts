/**
 * The basket is a per-browser convenience (one kitchen and one slot). The unit price and portion limit seen when a
 * meal was added are kept only to show an estimate and bound the quantity controls (CW05); they are never sent and
 * never treated as the price: the server quotes the authoritative total (BR-019).
 * A pending placement keeps the client request id and idempotency key from before the request was sent, so a retry
 * after a lost response can only find or repeat the same order.
 */
export interface CartLine {
  mealId: string;
  title: string;
  quantity: number;
  /** Integer piastres shown when added; estimate only. Absent in baskets saved before CW05 editing. */
  unitPriceMinor?: number;
  /** min(20, the slot's remaining portions when added); the server re-checks stock at quote and placement. */
  maxQuantity?: number;
}

/** Order line maximum, matching the meal page. Open question Q1: the active policy limit may be lower. */
export const LINE_MAX = 20;

export interface Cart {
  kitchenId: string;
  kitchenName?: string;
  slotId: string;
  slotStartsAt: string;
  modes: string[];
  lines: CartLine[];
}

export interface PendingPlacement {
  clientRequestId: string;
  idempotencyKey: string;
  quoteId: string;
  terms: string;
  cancellation: string;
}

const CART = "matbakhy.cart";
const PENDING = "matbakhy.pendingPlacement";

function read<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === null) {
      window.localStorage.removeItem(key);
    } else {
      window.localStorage.setItem(key, JSON.stringify(value));
    }
  } catch {
    // Storage may be unavailable (private mode); the flow still works within the page.
  }
}

export const loadCart = () => read<Cart>(CART);
export const saveCart = (cart: Cart | null) => write(CART, cart);
export const loadPending = () => read<PendingPlacement>(PENDING);
export const savePending = (p: PendingPlacement | null) => write(PENDING, p);

/** Sum of unit price × quantity in integer piastres; {@code null} when any line has no price snapshot. */
export function estimateMinor(cart: Cart): number | null {
  let total = 0;
  for (const line of cart.lines) {
    if (line.unitPriceMinor === undefined) {
      return null;
    }
    total += line.unitPriceMinor * line.quantity;
  }
  return total;
}
