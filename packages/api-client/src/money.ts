import type { components } from "./generated/schema";

export type Money = components["schemas"]["Money"];

const SUPPORTED_CURRENCIES = new Set<string>(["EGP"]);

/** Thrown when a value is not a well-formed {@link Money}. */
export class MalformedMoneyError extends Error {
  constructor(reason: string) {
    super(`Malformed money: ${reason}`);
    this.name = "MalformedMoneyError";
  }
}

/**
 * Validates an untrusted value as Money: exactly {amountMinor, currency}, a safe integer amount in
 * minor units and a supported ISO currency. Decimals, numeric strings and extra fields are rejected,
 * so a client can never silently round or reinterpret an amount.
 */
export function parseMoney(value: unknown): Money {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new MalformedMoneyError("not an object");
  }
  const keys = Object.keys(value).sort();
  if (keys.length !== 2 || keys[0] !== "amountMinor" || keys[1] !== "currency") {
    throw new MalformedMoneyError("expected exactly amountMinor and currency");
  }
  const { amountMinor, currency } = value as Record<string, unknown>;
  if (typeof amountMinor !== "number" || !Number.isSafeInteger(amountMinor)) {
    throw new MalformedMoneyError("amountMinor must be a safe integer in minor units");
  }
  if (typeof currency !== "string" || !SUPPORTED_CURRENCIES.has(currency)) {
    throw new MalformedMoneyError("unsupported currency");
  }
  return { amountMinor, currency: currency as Money["currency"] };
}
