import * as SecureStore from "expo-secure-store";

/**
 * The basket holds one kitchen and one slot (BR-018). The unit price and portion limit seen when a meal was added are
 * kept only for the estimate and the quantity controls (CW05); they are never sent: the server quotes the price. A pending
 * placement keeps the client request id and idempotency key from before the request is sent, so a retry after the
 * app was killed or the response was lost can only find or repeat the same order (BR-023, TC-049). Both are kept in
 * the OS secure store and cleared on sign-out and account switch.
 */
export interface BasketLine {
  mealId: string;
  title: string;
  quantity: number;
  /** Integer piastres shown when added; estimate only. Absent in baskets saved before basket editing. */
  unitPriceMinor?: number;
  /** min(20, the slot's remaining portions when added); the server re-checks stock at quote and placement. */
  maxQuantity?: number;
}

/** Order line maximum, matching the meal screen. Open question Q1: the active policy limit may be lower. */
export const LINE_MAX = 20;

export interface Basket {
  kitchenId: string;
  kitchenName?: string;
  slotId: string;
  slotStartsAt: string;
  modes: string[];
  lines: BasketLine[];
}

export interface PendingPlacement {
  clientRequestId: string;
  idempotencyKey: string;
  quoteId: string;
  terms: string;
  cancellation: string;
}

const BASKET = "matbakhy.basket.v1";
const PENDING = "matbakhy.pendingPlacement.v1";
const OPTIONS: SecureStore.SecureStoreOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };

/**
 * One retry for a failed secure-storage read: a single failure must not make a basket or an unconfirmed order vanish
 * from the screen (native runs 27/28, Android cold start). The warning names the key and error type only.
 */
async function getWithRetry(key: string): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(key, OPTIONS);
  } catch (e) {
    console.warn(`secure storage read failed for ${key} (${e instanceof Error ? e.name : typeof e}); retrying`);
    await new Promise((resolve) => setTimeout(resolve, 300));
    return SecureStore.getItemAsync(key, OPTIONS);
  }
}

async function read<T>(key: string): Promise<T | null> {
  const raw = await getWithRetry(key);
  if (raw === null) {
    return null;
  }
  try {
    return JSON.parse(raw) as T;
  } catch {
    await SecureStore.deleteItemAsync(key, OPTIONS);
    return null;
  }
}

async function write(key: string, value: unknown) {
  if (value === null) {
    await SecureStore.deleteItemAsync(key, OPTIONS);
  } else {
    await SecureStore.setItemAsync(key, JSON.stringify(value), OPTIONS);
  }
}

export const loadBasket = () => read<Basket>(BASKET);
export const saveBasket = (basket: Basket | null) => write(BASKET, basket);
export const loadPending = () => read<PendingPlacement>(PENDING);
export const savePending = (pending: PendingPlacement | null) => write(PENDING, pending);

export async function clearShopping() {
  await saveBasket(null);
  await savePending(null);
}

export type AddOutcome =
  | { kind: "added"; basket: Basket }
  /** Another kitchen or slot: the customer must confirm before the basket is replaced. */
  | { kind: "confirmReplace"; replacement: Basket }
  /** An order may already exist for the current basket; it is resolved at checkout before any change. */
  | { kind: "placementPending" };

/** Pure decision for "add to basket" (TC-148): same kitchen and slot merges; anything else needs confirmation. */
export function decideAdd(current: Basket | null, pending: PendingPlacement | null, target: Omit<Basket, "lines">, line: BasketLine): AddOutcome {
  const replacement: Basket = { ...target, lines: [line] };
  if (current && current.lines.length > 0 && (current.kitchenId !== target.kitchenId || current.slotId !== target.slotId)) {
    return pending ? { kind: "placementPending" } : { kind: "confirmReplace", replacement };
  }
  const lines = (current?.lines ?? []).filter((l) => l.mealId !== line.mealId);
  lines.push(line);
  return { kind: "added", basket: { ...target, lines } };
}

/** Sum of unit price × quantity in integer piastres; {@code null} when any line has no price snapshot. */
export function estimateMinor(basket: Basket): number | null {
  let total = 0;
  for (const line of basket.lines) {
    if (line.unitPriceMinor === undefined) {
      return null;
    }
    total += line.unitPriceMinor * line.quantity;
  }
  return total;
}
