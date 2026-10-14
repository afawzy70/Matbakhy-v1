import { cleanup, fireEvent, render, screen, waitFor } from "../../../../apps/customer-web/node_modules/@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "../../../../apps/customer-web/node_modules/vitest";
import { App } from "../../../../apps/customer-web/src/App";

beforeEach(() => {
  window.localStorage.clear();
  window.location.hash = "";
});
afterEach(cleanup);

type Handler = (req: Request, body: unknown) => { status: number; body?: unknown } | "NETWORK_ERROR";

/** Fake API at the fetch boundary (component tests only; the real-API journey is the browser workflow). */
function server(routes: Record<string, Handler>) {
  const calls: { method: string; path: string; headers: Headers; body: unknown }[] = [];
  const fetchImpl = (async (input: Request) => {
    const url = new URL(input.url);
    const text = input.method === "GET" ? "" : await input.text();
    const body = text ? JSON.parse(text) : undefined;
    calls.push({ method: input.method, path: decodeURIComponent(url.pathname) + url.search, headers: input.headers, body });
    const handler = routes[`${input.method} ${decodeURIComponent(url.pathname)}`];
    const r = handler ? handler(input, body) : { status: 401, body: { code: "UNAUTHENTICATED" } };
    if (r === "NETWORK_ERROR") {
      throw new TypeError("Failed to fetch");
    }
    return new Response(r.body === undefined ? null : JSON.stringify(r.body), { status: r.status, headers: { "Content-Type": "application/json" } });
  }) as unknown as typeof fetch;
  return { fetchImpl, calls };
}

const egp = (amountMinor: number) => ({ amountMinor, currency: "EGP" });
const me = { id: "u1", displayName: "Mona", locale: "ar", emailVerified: true, phoneVerified: true, version: 0, capabilities: ["BROWSE", "ORDER"] };
const order = { id: "11111111-1111-4111-8111-111111111111", publicNumber: "MB-2001" };

describe("D13: changed prices need informed reconfirmation", () => {
  const prices: Record<string, number> = {};
  let fee = 0;
  let version = 0;
  let quoteFails = false;
  const quoteBody = () => {
    const items = [
      { mealId: "m1", mealRevision: version + 1, title: "Dish m1", quantity: 2, unitPrice: egp(prices.m1 ?? 0), lineTotal: egp(2 * (prices.m1 ?? 0)) },
      { mealId: "m2", mealRevision: version + 1, title: "Dish m2", quantity: 1, unitPrice: egp(prices.m2 ?? 0), lineTotal: egp(prices.m2 ?? 0) },
    ];
    const subtotal = items.reduce((s, i) => s + i.lineTotal.amountMinor, 0);
    return {
      quoteId: `q${version}`, expiresAt: "2026-10-13T10:05:00Z", kitchenId: "k1", items, subtotal: egp(subtotal), deliveryFee: egp(fee), tax: egp(0),
      total: egp(subtotal + fee), cancellationPolicyVersion: "c1", termsVersion: "t1", acceptanceDeadlinePreview: "2026-10-13T10:15:00Z", warnings: [],
    };
  };
  function setup(modes: string[] = ["PICKUP"]) {
    prices.m1 = 12000;
    prices.m2 = 8000;
    fee = modes.includes("DELIVERY") ? 3000 : 0;
    version = 0;
    quoteFails = false;
    window.localStorage.setItem("matbakhy.cart", JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "s1", slotStartsAt: "2026-10-13T10:00:00Z", modes,
      lines: [{ mealId: "m1", title: "Dish m1", quantity: 2, unitPriceMinor: 12000, maxQuantity: 5 }, { mealId: "m2", title: "Dish m2", quantity: 1, unitPriceMinor: 8000, maxQuantity: 5 }] }));
    window.location.hash = "#/checkout";
    return server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      "GET /api/v1/me/addresses": () => ({ status: 200, body: { items: [{ id: "a1", label: "Home", street: "Street", building: "1", zoneId: "z1", version: 0 }], nextCursor: null, hasMore: false } }),
      "GET /api/v1/zones": () => ({ status: 200, body: { items: [{ id: "z1", code: "a", name: "Zone A" }], nextCursor: null, hasMore: false } }),
      "POST /api/v1/quotes": () => (quoteFails ? { status: 503, body: { code: "SERVICE_UNAVAILABLE" } } : { status: 201, body: quoteBody() }),
      "POST /api/v1/orders": (_req, body) =>
        (body as { quoteId: string }).quoteId === `q${version}`
          ? { status: 201, body: order }
          : { status: 409, body: { code: "PRICE_CHANGED", fieldErrors: [{ field: "quoteId", code: "MEAL_CHANGED" }] } },
    });
  }
  const changePrices = (next: Record<string, number>, nextFee = fee) => {
    Object.assign(prices, next);
    fee = nextFee;
    version += 1;
  };
  const ar = (minor: number) => new Intl.NumberFormat("ar-EG", { style: "currency", currency: "EGP" }).format(minor / 100);
  const placeButton = (name: string) => screen.getByRole("button", { name }) as HTMLButtonElement;

  it("AUDIT reproduce changed-price acceptance bypass through Calculate total", async () => {
    const { fetchImpl, calls } = setup();
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "احسب الإجمالي" }));
    fireEvent.click(await screen.findByRole("checkbox"));
    changePrices({ m1: 13000 });
    fireEvent.click(placeButton("تأكيد الطلب"));
    const notice = await screen.findByTestId("price-change");
    expect(notice.textContent).toContain("Dish m1");
    expect(placeButton("تأكيد الطلب").disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "احسب الإجمالي" }));
    await waitFor(() => expect(screen.queryByTestId("price-change")).toBeNull());
    fireEvent.click(screen.getByRole("checkbox"));
    expect(placeButton("تأكيد الطلب").disabled).toBe(false);
    // No click on Accept new total occurred; current-price submission nevertheless succeeds.
    fireEvent.click(placeButton("تأكيد الطلب"));
    await waitFor(() => expect(window.location.hash).toBe(`#/orders/${order.id}`));
    expect(calls.filter(c => c.method === "POST" && c.path === "/api/v1/orders").map(c => (c.body as {quoteId:string}).quoteId)).toEqual(["q0", "q1"]);
  });
});
