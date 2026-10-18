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

/** Fallback line maximum when the server's published limit (API-001) is not available; the quote still enforces the policy. */
export const LINE_MAX = 20;

/** The per-line maximum: the server's published policy limit when known, otherwise LINE_MAX. */
export function lineLimit(published: number | null): number {
  return published ?? LINE_MAX;
}

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
  /** The server asked for a second factor (D18) when this was sent: it is re-sent only by the customer's explicit retry, never on focus or restart. */
  awaitingExplicitRetry?: boolean;
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

/**
 * The stored basket and pending placement belong to one signed-in person (TC-048). The owner is recorded next to them; while the
 * signed-in person is someone else, both read as empty and are never written over silently, so a storage cleanup that failed during
 * an account switch cannot hand the previous person's basket or unconfirmed order to the next one.
 */
const OWNER = "matbakhy.shoppingOwner.v1";
let activeOwner: string | null = null;

export const shoppingOwner = () => activeOwner;

/** Makes {@code subject} the person the stored shopping data is read for (null: nobody is bound). Claims unowned data for them. */
export async function bindShopping(subject: string | null) {
  activeOwner = subject;
  if (subject && (await read<string>(OWNER)) === null) {
    await write(OWNER, subject);
  }
}

async function foreign(): Promise<boolean> {
  if (!activeOwner) {
    return false;
  }
  const stored = await read<string>(OWNER);
  return stored !== null && stored !== activeOwner;
}

async function claim() {
  if (activeOwner && (await read<string>(OWNER)) !== activeOwner) {
    await write(OWNER, activeOwner);
  }
}

export const loadBasket = async () => ((await foreign()) ? null : read<Basket>(BASKET));
export const saveBasket = async (basket: Basket | null) => {
  if (basket !== null) {
    await claim();
  }
  return write(BASKET, basket);
};
export const loadPending = async () => ((await foreign()) ? null : read<PendingPlacement>(PENDING));
export const savePending = async (pending: PendingPlacement | null) => {
  if (pending !== null) {
    await claim();
  }
  return write(PENDING, pending);
};

/** Removes the basket, the pending placement and the owner record. Throws when storage refuses; callers must not continue as if it worked. */
export async function clearShopping() {
  await saveBasket(null);
  await savePending(null);
  await write(OWNER, null);
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
