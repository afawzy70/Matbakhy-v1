import { describe, expect, it } from "vitest";
import { MalformedMoneyError, parseMoney } from "../src";
import { validatorFor } from "./contract";

const malformed: [string, unknown][] = [
  ["decimal amount", { amountMinor: 270.5, currency: "EGP" }],
  ["numeric string amount", { amountMinor: "27000", currency: "EGP" }],
  ["formatted amount", { amountMinor: "270.00", currency: "EGP" }],
  ["missing currency", { amountMinor: 27000 }],
  ["missing amount", { currency: "EGP" }],
  ["unsupported currency", { amountMinor: 27000, currency: "USD" }],
  ["lower-case currency", { amountMinor: 27000, currency: "egp" }],
  ["extra field", { amountMinor: 27000, currency: "EGP", formatted: "EGP 270.00" }],
  ["unsafe integer", { amountMinor: 2 ** 60, currency: "EGP" }],
  ["null", null],
];

describe("TC-107 malformed monetary DTO", () => {
  const validateMoney = validatorFor("Money");

  it("accepts the synthetic order total", () => {
    const total = { amountMinor: 27000, currency: "EGP" };
    expect(validateMoney(total)).toBe(true);
    expect(parseMoney(total)).toEqual(total);
  });

  it.each(malformed)("contract schema rejects %s", (_, value) => {
    expect(validateMoney(value)).toBe(false);
  });

  it.each(malformed)("client parser rejects %s", (_, value) => {
    expect(() => parseMoney(value)).toThrow(MalformedMoneyError);
  });

  it("non-negative money rejects negative prices", () => {
    expect(validatorFor("NonNegativeMoney")({ amountMinor: -1, currency: "EGP" })).toBe(false);
    expect(validatorFor("NonNegativeMoney")({ amountMinor: 0, currency: "EGP" })).toBe(true);
  });
});

describe("authoritative server amounts", () => {
  it("quote request rejects client-supplied prices", () => {
    const quote = validatorFor("QuoteRequest");
    const base = { items: [{ mealId: "00000000-0000-4000-8000-000000000001", quantity: 2 }],
      slotId: "00000000-0000-4000-8000-000000000002", fulfillmentMode: "PICKUP" };
    expect(quote(base)).toBe(true);
    expect(quote({ ...base, total: { amountMinor: 1, currency: "EGP" } })).toBe(false);
    expect(quote({ ...base, items: [{ ...base.items[0], unitPrice: { amountMinor: 1, currency: "EGP" } }] })).toBe(false);
    expect(quote({ ...base, items: [{ ...base.items[0], quantity: 0 }] })).toBe(false);
  });

  it("place-order request rejects privileged fields", () => {
    const place = validatorFor("PlaceOrderRequest");
    const base = { quoteId: "00000000-0000-4000-8000-000000000101", clientRequestId: "00000000-0000-4000-8000-000000000202",
      acceptedPolicyVersions: { terms: "draft-test-1", cancellation: "draft-test-1" } };
    expect(place(base)).toBe(true);
    expect(place({ ...base, status: "COMPLETED" })).toBe(false);
    expect(place({ ...base, customerId: "00000000-0000-4000-8000-000000000999" })).toBe(false);
  });
});
