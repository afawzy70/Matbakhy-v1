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
 * The stored basket and pending placement belong to one signed-in person (TC-048). Each person has their own storage keys
 * ("<key>.<subject>"); signed out, the keys have no suffix (a guest's basket). Reads and writes go through a {@link Shopping}
 * handle that is bound to its owner when it is taken, so an answer that arrives after the person changed can only touch the
 * previous person's keys, never the next person's, and a cleanup that fails leaves data nobody else can read.
 */
export interface Shopping {
  readonly owner: string | null;
  loadBasket(): Promise<Basket | null>;
  saveBasket(basket: Basket | null): Promise<void>;
  loadPending(): Promise<PendingPlacement | null>;
  savePending(pending: PendingPlacement | null): Promise<void>;
  /** Removes this owner's basket and pending placement; throws when storage refuses. */
  clear(): Promise<void>;
}

/** The guest-to-person move of a basket (see bindShopping); every storage operation waits for it, so a cleanup cannot overtake it. */
let migration: Promise<void> = Promise.resolve();

function shoppingFor(owner: string | null): Shopping {
  const key = (base: string) => (owner ? `${base}.${owner}` : base);
  return {
    owner,
    loadBasket: async () => (await migration, read<Basket>(key(BASKET))),
    saveBasket: async (basket) => (await migration, write(key(BASKET), basket)),
    loadPending: async () => (await migration, read<PendingPlacement>(key(PENDING))),
    savePending: async (pending) => (await migration, write(key(PENDING), pending)),
    async clear() {
      await migration;
      await write(key(BASKET), null);
      await write(key(PENDING), null);
    },
  };
}

let current: Shopping = shoppingFor(null);

/** The handle for whoever is signed in now. Long operations take it once at their start and use it to the end. */
export const currentShopping = (): Shopping => current;

/**
 * Switches the storage to {@code subject} (null: signed out). A basket built as a guest follows the person who signs in, unless
 * they already have one of their own; nothing of another signed-in person is ever adopted.
 */
export async function bindShopping(subject: string | null): Promise<void> {
  if (current.owner === subject) {
    return;
  }
  const previous = current;
  current = shoppingFor(subject);
  if (previous.owner === null && subject !== null) {
    const move = (async () => {
      try {
        const [guestBasket, guestPending, ownBasket, ownPending] = await Promise.all([read<Basket>(BASKET), read<PendingPlacement>(PENDING), read<Basket>(`${BASKET}.${subject}`), read<PendingPlacement>(`${PENDING}.${subject}`)]);
        if (guestBasket || guestPending) {
          // The guest's keys are removed FIRST, so the same basket can never end up with two people; only then is it written for this
          // person, and only when they have nothing of their own (their own basket wins; the guest basket is dropped).
          await write(BASKET, null);
          await write(PENDING, null);
          if (!ownBasket && !ownPending) {
            if (guestBasket) {
              await write(`${BASKET}.${subject}`, guestBasket);
            }
            if (guestPending) {
              await write(`${PENDING}.${subject}`, guestPending);
            }
          }
        }
      } catch {
        // Nothing was copied before the guest keys were removed, so a failure can lose a guest basket but never share it.
      }
    })();
    migration = move;
    await move;
  }
}

export const loadBasket = () => current.loadBasket();
export const saveBasket = (basket: Basket | null) => current.saveBasket(basket);
export const loadPending = () => current.loadPending();
export const savePending = (pending: PendingPlacement | null) => current.savePending(pending);

/** Removes the current owner's basket and pending placement. Throws when storage refuses. */
export const clearShopping = () => current.clear();

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
