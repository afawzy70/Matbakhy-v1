import { brand } from "@matbakhy/design-tokens";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { App, title } from "./App";
import { savePending } from "./cart";

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

function cartWithOneMeal() {
  window.localStorage.setItem(
    "matbakhy.cart",
    JSON.stringify({ kitchenId: "k1", slotId: "s1", slotStartsAt: "2026-10-12T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: "m1", title: "كشري", quantity: 2 }] }),
  );
}

describe("customer web", () => {
  it("starts in Arabic right-to-left and switches to English", () => {
    const { fetchImpl } = server({});
    render(<App fetchImpl={fetchImpl} />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title("ar"));
    expect(title("ar")).toBe(brand.name.ar);
    expect(document.documentElement.dir).toBe("rtl");
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(document.documentElement.dir).toBe("ltr");
  });

  it("shows server meals and an empty state, and retries after an error", async () => {
    let fail = true;
    const { fetchImpl } = server({
      "GET /api/v1/zones": () => ({ status: 200, body: { items: [{ id: "z1", code: "a", name: "المعادي" }], nextCursor: null, hasMore: false } }),
      "GET /api/v1/meals": () =>
        fail
          ? { status: 503, body: { code: "SERVICE_UNAVAILABLE" } }
          : { status: 200, body: { items: [], nextCursor: null, hasMore: false } },
    });
    render(<App fetchImpl={fetchImpl} />);
    const retry = await screen.findByRole("button", { name: "إعادة المحاولة" });
    fail = false;
    fireEvent.click(retry);
    expect(await screen.findByText("لا توجد أطباق متاحة لهذا البحث.")).toBeTruthy();
  });

  it("stores the order keys before sending and retries with the same keys after a lost response", async () => {
    cartWithOneMeal();
    window.location.hash = "#/checkout";
    let attempts = 0;
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      "GET /api/v1/me/addresses": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "POST /api/v1/quotes": () => ({
        status: 201,
        body: { quoteId: "q1", expiresAt: "2026-10-11T10:05:00Z", kitchenId: "k1", items: [], subtotal: egp(24000), deliveryFee: egp(0), tax: egp(0), total: egp(24000), cancellationPolicyVersion: "c1", termsVersion: "t1", acceptanceDeadlinePreview: "2026-10-11T10:15:00Z", warnings: ["SYNTHETIC_POLICY"] },
      }),
      "POST /api/v1/orders": () => (++attempts === 1 ? "NETWORK_ERROR" : { status: 201, body: order }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "احسب الإجمالي" }));
    fireEvent.click(await screen.findByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "تأكيد الطلب" }));
    const retry = await screen.findByRole("button", { name: "إعادة المحاولة بنفس الطلب" });
    const pending = JSON.parse(window.localStorage.getItem("matbakhy.pendingPlacement") ?? "{}");
    expect(pending.quoteId).toBe("q1");
    fireEvent.click(retry);
    await waitFor(() => expect(window.location.hash).toBe(`#/orders/${order.id}`));
    const posts = calls.filter((c) => c.method === "POST" && c.path === "/api/v1/orders");
    expect(posts).toHaveLength(2);
    expect(posts[0]?.headers.get("Idempotency-Key")).toBe(posts[1]?.headers.get("Idempotency-Key"));
    expect((posts[0]?.body as { clientRequestId: string }).clientRequestId).toBe((posts[1]?.body as { clientRequestId: string }).clientRequestId);
    expect(window.localStorage.getItem("matbakhy.pendingPlacement")).toBeNull();
    expect(window.localStorage.getItem("matbakhy.cart")).toBeNull();
  });

  it("recovers an order that was created before the page was reloaded", async () => {
    cartWithOneMeal();
    savePending({ clientRequestId: "22222222-2222-4222-8222-222222222222", idempotencyKey: "web-x", quoteId: "q1", terms: "t1", cancellation: "c1" });
    window.location.hash = "#/checkout";
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      "GET /api/v1/me/addresses": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "GET /api/v1/orders/by-request/22222222-2222-4222-8222-222222222222": () => ({ status: 200, body: order }),
    });
    render(<App fetchImpl={fetchImpl} />);
    await waitFor(() => expect(window.location.hash).toBe(`#/orders/${order.id}`));
    expect(calls.some((c) => c.method === "POST")).toBe(false);
  });

  it("asks a signed-out customer to sign in with a return address before ordering", async () => {
    cartWithOneMeal();
    window.location.hash = "#/checkout";
    const { fetchImpl } = server({});
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByText("سجّل الدخول لإتمام الطلب.")).toBeTruthy();
    const links = screen.getAllByRole("link", { name: "تسجيل الدخول" });
    expect(links[links.length - 1]?.getAttribute("href")).toMatch(/returnTo=.*checkout/);
  });

  it("toggles a favorite on the meal page and reports a review with the server's visible summary", async () => {
    window.location.hash = "#/meal/22222222-2222-4222-8222-222222222222";
    let favorites: { mealId: string }[] = [];
    const meal = {
      id: "22222222-2222-4222-8222-222222222222",
      title: "Koshari",
      kitchen: { id: "33333333-3333-4333-8333-333333333333", name: "Mona's kitchen" },
      portionText: "1 bowl",
      price: egp(12000),
      description: "",
      ingredients: "",
      allergenCodes: [],
      allergenStatement: null,
      photo: null,
      nextSlots: [],
      availability: "AVAILABLE",
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      [`GET /api/v1/meals/${meal.id}`]: () => ({ status: 200, body: meal }),
      "GET /api/v1/me/favorites": () => ({ status: 200, body: { items: favorites.map((f) => ({ ...f, title: "Koshari", kitchenName: "Mona", priceMinor: 12000, available: true })), nextCursor: null, hasMore: false } }),
      [`PUT /api/v1/me/favorites/${meal.id}`]: () => {
        favorites = [{ mealId: meal.id }];
        return { status: 200, body: {} };
      },
      [`DELETE /api/v1/me/favorites/${meal.id}`]: () => {
        favorites = [];
        return { status: 204 };
      },
      [`GET /api/v1/kitchens/${meal.kitchen.id}/reviews`]: () => ({
        status: 200,
        body: { items: [{ id: "r1", rating: 4, text: "Tasty", createdAt: "2026-10-10T10:00:00Z" }], summary: { count: 1, averageTenths: 40 }, nextCursor: null, hasMore: false },
      }),
      "POST /api/v1/reviews/r1/reports": () => ({ status: 204 }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("button", { name: "Add to favorites" }));
    const remove = await screen.findByRole("button", { name: "Remove from favorites" });
    expect(remove.getAttribute("aria-pressed")).toBe("true");
    fireEvent.click(remove);
    expect(await screen.findByRole("button", { name: "Add to favorites" })).toBeTruthy();
    expect(await screen.findByText("4.0 of 5 (1 reviews)")).toBeTruthy();
    fireEvent.change(screen.getByRole("combobox", { name: "Report" }), { target: { value: "SPAM" } });
    expect(await screen.findByText("Reported. Thank you.")).toBeTruthy();
    expect(calls.find((c) => c.method === "POST" && c.path === "/api/v1/reviews/r1/reports")?.body).toEqual({ reasonCode: "SPAM" });
    expect(calls.filter((c) => c.method === "PUT").length).toBe(1);
  });

  it("reviews a completed order once and offers ordering again at a new price", async () => {
    window.location.hash = `#/orders/${order.id}`;
    let reviewed = false;
    const view = {
      ...order,
      version: 3,
      state: "COMPLETED",
      fulfillmentMode: "PICKUP",
      items: [{ mealId: "m1", title: "Koshari", quantity: 1, unitPrice: egp(12000), lineTotal: egp(12000) }],
      subtotal: egp(12000),
      deliveryFee: egp(0),
      tax: egp(0),
      total: egp(12000),
      slot: { id: "s1", startsAt: "2026-10-10T10:00:00Z" },
      acceptBy: "2026-10-10T09:00:00Z",
      events: [],
      allowedActions: [],
      cashState: "COLLECTED",
      fulfillmentNote: null,
      dispatchReference: null,
      contact: null,
      handoverCode: null,
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      [`GET /api/v1/orders/${order.id}`]: () => ({ status: 200, body: view }),
      [`POST /api/v1/orders/${order.id}/reviews`]: () => {
        if (reviewed) {
          return { status: 409, body: { code: "STATE_CONFLICT" } };
        }
        reviewed = true;
        return { status: 201, body: {} };
      },
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.change(await screen.findByRole("combobox", { name: "Rating (1 to 5)" }), { target: { value: "4" } });
    fireEvent.change(screen.getByRole("textbox", { name: "Comment (optional)" }), { target: { value: " Very good " } });
    fireEvent.click(screen.getByRole("button", { name: "Submit review" }));
    expect(await screen.findByText("Thank you for your review.")).toBeTruthy();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ rating: 4, text: "Very good" });
    expect(screen.getByRole("link", { name: "Order again (at today's price)" }).getAttribute("href")).toBe("#/meal/m1");
  });

  it("shows the account page only when signed in and sends the deletion request after confirmation", async () => {
    window.location.hash = "#/account";
    const empty = { status: 200, body: { items: [], nextCursor: null, hasMore: false } };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": () => ({ status: 200, body: me }),
      "GET /api/v1/me/favorites": () => ({
        status: 200,
        body: { items: [{ mealId: "m9", title: "Molokhia", kitchenName: "Mona", priceMinor: null, available: false }], nextCursor: null, hasMore: false },
      }),
      "GET /api/v1/notifications": () => empty,
      "GET /api/v1/support/cases": () => empty,
      "GET /api/v1/orders": () => empty,
      "GET /api/v1/me/addresses": () => empty,
      "POST /api/v1/me/deletion-requests": () => ({ status: 202, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByText("Not available right now")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Molokhia" })).toBeNull();
    const del = screen.getByRole("button", { name: "Request account deletion" }) as HTMLButtonElement;
    expect(del.disabled).toBe(true);
    fireEvent.click(screen.getByRole("checkbox", { name: "I confirm I want my account deleted" }));
    fireEvent.click(del);
    expect(await screen.findByText("Deletion request received; the account is disabled.")).toBeTruthy();
    expect(calls.find((c) => c.path === "/api/v1/me/deletion-requests")?.body).toEqual({ confirm: true, policyVersion: "retention-policy-draft" });
  });

  it("asks before replacing a basket of another kitchen or slot and keeps it when the customer cancels", async () => {
    cartWithOneMeal();
    window.location.hash = "#/meal/44444444-4444-4444-8444-444444444444";
    const meal = {
      id: "44444444-4444-4444-8444-444444444444",
      title: "Fatta",
      kitchen: { id: "k2", name: "Other kitchen" },
      portionText: "1 plate",
      price: egp(15000),
      description: "",
      ingredients: "",
      allergenCodes: [],
      allergenStatement: null,
      photo: null,
      nextSlots: [{ id: "s2", startsAt: "2026-10-12T12:00:00Z", availablePortions: 5, fulfillmentModes: ["PICKUP"] }],
      availability: "AVAILABLE",
    };
    const { fetchImpl } = server({
      [`GET /api/v1/meals/${meal.id}`]: () => ({ status: 200, body: meal }),
      "GET /api/v1/kitchens/k2/reviews": () => ({ status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("radio"));
    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }));
    const prompt = await screen.findByRole("alertdialog", { name: "Replace your cart?" });
    fireEvent.click(within(prompt).getByRole("button", { name: "Keep my current cart" }));
    expect(screen.getByText("Your cart is unchanged.")).toBeTruthy();
    expect(JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").kitchenId).toBe("k1");
    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }));
    fireEvent.click(within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Replace cart" }));
    const cart = JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}");
    expect(cart.kitchenId).toBe("k2");
    expect(cart.slotId).toBe("s2");
    expect(cart.lines).toEqual([{ mealId: meal.id, title: "Fatta", quantity: 1 }]);
  });

  it("does not replace the basket while a placement from it is still pending", async () => {
    cartWithOneMeal();
    savePending({ clientRequestId: "c1", idempotencyKey: "i1", quoteId: "q1", terms: "t1", cancellation: "c1" });
    window.location.hash = "#/meal/44444444-4444-4444-8444-444444444444";
    const { fetchImpl } = server({
      "GET /api/v1/meals/44444444-4444-4444-8444-444444444444": () => ({
        status: 200,
        body: { id: "44444444-4444-4444-8444-444444444444", title: "Fatta", kitchen: { id: "k2", name: "Other" }, portionText: "", price: egp(1), description: "", ingredients: "", allergenCodes: [], allergenStatement: null, photo: null, nextSlots: [{ id: "s2", startsAt: "2026-10-12T12:00:00Z", availablePortions: 5, fulfillmentModes: ["PICKUP"] }], availability: "AVAILABLE" },
      }),
      "GET /api/v1/kitchens/k2/reviews": () => ({ status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("radio"));
    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }));
    expect(await screen.findByText("An order from your cart is still being confirmed. Finish it on the cart page first.")).toBeTruthy();
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").kitchenId).toBe("k1");
  });
});
