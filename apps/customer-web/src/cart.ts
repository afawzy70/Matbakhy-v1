/**
 * The basket is a per-browser convenience (one kitchen and one slot). Prices are never stored: the server quotes them.
 * A pending placement keeps the client request id and idempotency key from before the request was sent, so a retry
 * after a lost response can only find or repeat the same order.
 */
export interface CartLine {
  mealId: string;
  title: string;
  quantity: number;
}

export interface Cart {
  kitchenId: string;
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
