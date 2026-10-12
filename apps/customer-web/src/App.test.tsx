import { brand } from "@matbakhy/design-tokens";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    const prompt = await screen.findByRole("alertdialog", { name: "Replace your cart?" });
    expect(document.activeElement?.textContent).toBe("Keep my current cart");
    fireEvent.click(within(prompt).getByRole("button", { name: "Keep my current cart" }));
    expect(screen.getByText("Your cart is unchanged.")).toBeTruthy();
    await waitFor(() => expect(document.activeElement?.textContent ?? "").toMatch(/^Add to cart/));
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    fireEvent.keyDown(await screen.findByRole("alertdialog"), { key: "Escape" });
    expect(screen.queryByRole("alertdialog")).toBeNull();
    await waitFor(() => expect(document.activeElement?.textContent ?? "").toMatch(/^Add to cart/));
    expect(JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").kitchenId).toBe("k1");
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    fireEvent.click(within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Replace cart" }));
    const cart = JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}");
    expect(cart.kitchenId).toBe("k2");
    expect(cart.slotId).toBe("s2");
    expect(cart.kitchenName).toBe("Other kitchen");
    expect(cart.lines).toEqual([{ mealId: meal.id, title: "Fatta", quantity: 1, unitPriceMinor: 15000, maxQuantity: 5 }]);
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
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    expect(await screen.findByText("An order from your cart is still being confirmed. Finish it on the cart page first.")).toBeTruthy();
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").kitchenId).toBe("k1");
  });

  it("home follows the approved hierarchy and sends every filter to the server", async () => {
    const meal = {
      id: "55555555-5555-4555-8555-555555555555",
      title: "Mahshi",
      description: "Stuffed vine leaves and courgettes",
      kitchen: { id: "k5", slug: "amina-kitchen", name: "Amina", ratingSummary: { count: 0, averageTenths: null } },
      photo: null,
      portionText: "Serves two",
      price: egp(18000),
      nextSlots: [],
      availability: "AVAILABLE",
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/zones": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "GET /api/v1/meals": (req) =>
        new URL(req.url).searchParams.get("maxPriceMinor") === "10000"
          ? { status: 200, body: { items: [], nextCursor: null, hasMore: false } }
          : { status: 200, body: { items: [meal], nextCursor: null, hasMore: false } },
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const intents = ["A meal for me or us", "Complete my table", "Prepare a gathering"].map((n) => screen.getByRole("button", { name: new RegExp(n) }));
    expect(intents.every((b) => b.getAttribute("aria-pressed") === "false")).toBe(true);
    expect(screen.queryByLabelText("Helpful choices")).toBeNull();
    fireEvent.click(intents[0] as HTMLElement);
    expect(screen.getByLabelText("Helpful choices")).toBeTruthy();
    // D11: no portion-size control and no sort standing in for a filter in the intent panel.
    expect(screen.queryByText(/one person|two people/i, { selector: "button" })).toBeNull();
    expect(within(screen.getByLabelText("Helpful choices")).queryByRole("button", { name: "Lowest price" })).toBeNull();
    fireEvent.change(screen.getByLabelText("Your budget per dish (maximum price, EGP)"), { target: { value: "250" } });
    fireEvent.click(within(screen.getByLabelText("Helpful choices")).getByRole("button", { name: "Show results" }));
    await waitFor(() => expect(calls.some((c) => c.path.includes("maxPriceMinor=25000"))).toBe(true));
    fireEvent.click(screen.getByRole("button", { name: "Filter results" }));
    fireEvent.change(screen.getByLabelText("Sort results"), { target: { value: "PRICE_ASC" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Show results" }).at(-1) as HTMLElement);
    await waitFor(() => expect(calls.some((c) => c.path.includes("sort=PRICE_ASC"))).toBe(true));
    const card = await screen.findByRole("article", { name: "Mahshi" });
    expect(within(card).getByText("Serves two")).toBeTruthy();
    expect(within(card).getByText("Stuffed vine leaves and courgettes")).toBeTruthy();
    expect(within(card).getByRole("link", { name: "Amina" }).getAttribute("href")).toBe("#/kitchen/amina-kitchen");
    fireEvent.click(screen.getByRole("button", { name: "Soups" }));
    await waitFor(() => expect(calls.some((c) => c.path.includes("categoryId=SOUPS"))).toBe(true));
    fireEvent.click(screen.getByRole("button", { name: "Filter results" }));
    fireEvent.change(screen.getByLabelText("Maximum price (EGP)"), { target: { value: "100" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Show results" }).at(-1) as HTMLElement);
    expect(await screen.findByText("No meals match this search.")).toBeTruthy();
    expect(calls.some((c) => c.path.includes("maxPriceMinor=10000"))).toBe(true);
    fireEvent.click(screen.getAllByRole("button", { name: "Clear filters" })[0] as HTMLElement);
    expect(await screen.findByRole("article", { name: "Mahshi" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Kitchens" }));
    expect(await screen.findByRole("article", { name: "Amina" })).toBeTruthy();
    expect(screen.getByText("1 dish(es) in these results")).toBeTruthy();
  });

  it("shows a public kitchen profile with its meals and the operational-verification wording", async () => {
    window.location.hash = "#/kitchen/amina-kitchen";
    const { fetchImpl, calls } = server({
      "GET /api/v1/kitchens/amina-kitchen": () => ({
        status: 200,
        body: { kitchenId: "k5", slug: "amina-kitchen", displayName: "Amina", description: "Stuffed vegetables", verificationLabel: "OPERATIONALLY_VERIFIED", available: true, pickupEnabled: true, deliveryZoneIds: [] },
      }),
      "GET /api/v1/meals": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByRole("heading", { name: "Amina" })).toBeTruthy();
    expect(screen.getByText(/not a food-safety certification/)).toBeTruthy();
    await waitFor(() => expect(calls.some((c) => c.path.startsWith("/api/v1/meals?") && c.path.includes("kitchenId=k5"))).toBe(true));
  });

  it("bounds the quantity by the slot's remaining portions, shows the line total and explains unknown allergens", async () => {
    window.location.hash = "#/meal/66666666-6666-4666-8666-666666666666";
    const { fetchImpl } = server({
      "GET /api/v1/meals/66666666-6666-4666-8666-666666666666": () => ({
        status: 200,
        body: { id: "66666666-6666-4666-8666-666666666666", title: "Okra", kitchen: { id: "k6", slug: "k-six", name: "K6" }, portionText: "Serves two", price: egp(18000), description: "Okra stew", ingredients: "Okra, tomato", allergenCodes: ["UNKNOWN"], allergenStatement: null, photo: null, nextSlots: [{ id: "s6", startsAt: "2026-10-12T12:00:00Z", availablePortions: 2, fulfillmentModes: ["PICKUP"] }], availability: "AVAILABLE" },
      }),
      "GET /api/v1/kitchens/k6/reviews": () => ({ status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByText(/Detailed allergy information is not available/)).toBeTruthy();
    fireEvent.click(screen.getByRole("radio"));
    const minus = screen.getByRole("button", { name: "Decrease quantity" }) as HTMLButtonElement;
    const plus = screen.getByRole("button", { name: "Increase quantity" }) as HTMLButtonElement;
    expect(minus.disabled).toBe(true);
    fireEvent.click(plus);
    expect(plus.disabled).toBe(true);
    expect(screen.getByRole("button", { name: /^Add to cart/ }).textContent).toMatch(/360/);
  });
});


/**
 * Retrospective regression coverage (2026-10-12, owner-requested hardening pass). Added after the features were
 * implemented; expected behaviour is taken from the approved rules (BR-017, BR-018, CW03/CW05), not from the code.
 */
describe("retrospective regression: basket rules", () => {
  const meal = (id: string, kitchenId: string, slotId: string, availability = "AVAILABLE") => ({
    id,
    title: `Dish ${id.slice(0, 4)}`,
    kitchen: { id: kitchenId, slug: kitchenId, name: `Kitchen ${kitchenId}` },
    portionText: "1 plate",
    price: egp(10000),
    description: "",
    ingredients: "",
    allergenCodes: ["NONE_DECLARED"],
    allergenStatement: null,
    photo: null,
    nextSlots: [{ id: slotId, startsAt: "2026-10-12T10:00:00Z", availablePortions: 5, fulfillmentModes: ["PICKUP"] }],
    availability,
  });
  const reviews = { status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } };

  it("adds another meal of the same kitchen and slot without asking, and re-adding a meal updates its line instead of duplicating it (BR-018)", async () => {
    const m1 = "99999999-9999-4999-8999-999999999991";
    const m2 = "99999999-9999-4999-8999-999999999992";
    window.localStorage.setItem(
      "matbakhy.cart",
      JSON.stringify({ kitchenId: "k1", slotId: "s1", slotStartsAt: "2026-10-12T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: m1, title: "Dish one", quantity: 2 }] }),
    );
    const { fetchImpl } = server({
      [`GET /api/v1/meals/${m1}`]: () => ({ status: 200, body: meal(m1, "k1", "s1") }),
      [`GET /api/v1/meals/${m2}`]: () => ({ status: 200, body: meal(m2, "k1", "s1") }),
      "GET /api/v1/kitchens/k1/reviews": () => reviews,
    });
    const lines = () => JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").lines as { mealId: string; quantity: number }[];

    window.location.hash = `#/meal/${m2}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("radio"));
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(lines().map((l) => [l.mealId, l.quantity])).toEqual([[m1, 2], [m2, 1]]);

    cleanup();
    window.location.hash = `#/meal/${m1}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("radio"));
    fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }));
    fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }));
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(lines().filter((l) => l.mealId === m1)).toEqual([expect.objectContaining({ mealId: m1, quantity: 3 })]);
    expect(lines()).toHaveLength(2);
  });

  it("cannot add a sold-out or unavailable meal to the basket (BR-017, CW03)", async () => {
    const sold = "88888888-8888-4888-8888-888888888888";
    const { fetchImpl } = server({
      [`GET /api/v1/meals/${sold}`]: () => ({ status: 200, body: meal(sold, "k8", "s8", "SOLD_OUT") }),
      "GET /api/v1/kitchens/k8/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${sold}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("radio"));
    expect((screen.getByRole("button", { name: /^Add to cart/ }) as HTMLButtonElement).disabled).toBe(true);
    expect(window.localStorage.getItem("matbakhy.cart")).toBeNull();
  });
});

/**
 * Repairs for the independent Codex QA findings on eb1a671 (qa/independent-eb1a671/report.md). Each test reproduces
 * the reported behaviour and was run against the unchanged product code before the fix.
 */
describe("QA repair MBQA-002: an exhausted selected slot cannot be added", () => {
  const id = "a1a1a1a1-0000-4000-8000-000000000001";
  const mixed = {
    id,
    title: "QA Meal A1",
    kitchen: { id: "kA", slug: "kitchen-a", name: "Kitchen A" },
    portionText: "1 plate",
    price: egp(12025),
    description: "",
    ingredients: "",
    allergenCodes: ["NONE_DECLARED"],
    allergenStatement: null,
    photo: null,
    // The meal as a whole is AVAILABLE because S2 has stock; S1 is exhausted (the QA fixture).
    nextSlots: [
      { id: "S1", startsAt: "2026-10-13T10:00:00Z", availablePortions: 0, fulfillmentModes: ["PICKUP"] },
      { id: "S2", startsAt: "2026-10-13T13:00:00Z", availablePortions: 3, fulfillmentModes: ["PICKUP"] },
    ],
    availability: "AVAILABLE",
  };
  const reviews = { status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } };

  it("refuses the exhausted slot without writing the basket, and adds the slot that has stock up to its portions", async () => {
    const { fetchImpl } = server({
      [`GET /api/v1/meals/${id}`]: () => ({ status: 200, body: mixed }),
      "GET /api/v1/kitchens/kA/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${id}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const [s1, s2] = (await screen.findAllByRole("radio")) as HTMLInputElement[];
    expect(screen.getByText(/0 left/)).toBeTruthy();
    fireEvent.click(s1 as HTMLInputElement);
    const add = () => screen.getByRole("button", { name: /^Add to cart/ }) as HTMLButtonElement;
    expect(add().disabled).toBe(true);
    fireEvent.click(add());
    expect(screen.queryByText("Added to cart.")).toBeNull();
    expect(window.localStorage.getItem("matbakhy.cart")).toBeNull();

    fireEvent.click(s2 as HTMLInputElement);
    const plus = screen.getByRole("button", { name: "Increase quantity" }) as HTMLButtonElement;
    for (let i = 0; i < 5; i++) {
      fireEvent.click(plus);
    }
    expect((screen.getByRole("spinbutton", { name: "Quantity" }) as HTMLInputElement).value).toBe("3");
    expect(plus.disabled).toBe(true);
    expect(add().disabled).toBe(false);
    fireEvent.click(add());
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    const cart = JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}");
    expect(cart.slotId).toBe("S2");
    expect(cart.lines.map((l: { mealId: string; quantity: number }) => [l.mealId, l.quantity])).toEqual([[id, 3]]);

    // Switching back to the exhausted slot after choosing a quantity still cannot write the basket.
    fireEvent.click(s1 as HTMLInputElement);
    expect(add().disabled).toBe(true);
    fireEvent.click(add());
    expect(JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").slotId).toBe("S2");
  });

  it("keeps an entirely sold-out meal unpurchasable", async () => {
    const { fetchImpl } = server({
      [`GET /api/v1/meals/${id}`]: () => ({
        status: 200,
        body: { ...mixed, availability: "SOLD_OUT", nextSlots: mixed.nextSlots.map((s) => ({ ...s, availablePortions: 0 })) },
      }),
      "GET /api/v1/kitchens/kA/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${id}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    for (const radio of (await screen.findAllByRole("radio")) as HTMLInputElement[]) {
      fireEvent.click(radio);
      const add = screen.getByRole("button", { name: /^Add to cart/ }) as HTMLButtonElement;
      expect(add.disabled).toBe(true);
      fireEvent.click(add);
    }
    expect(window.localStorage.getItem("matbakhy.cart")).toBeNull();
  });
});

describe("QA repair MBQA-001: catalogue requests use the language the customer just chose", () => {
  // A fresh page starts as index.html declares it (Arabic); earlier tests in this file leave the document in English.
  beforeEach(() => {
    document.documentElement.lang = "ar";
    document.documentElement.dir = "rtl";
  });
  const mealId = "b1b1b1b1-0000-4000-8000-000000000001";
  // Bilingual fixture: the fake API answers in the language of the request, like the real API.
  const t = (req: Request, ar: string, en: string) => (req.headers.get("Accept-Language") === "en" ? en : ar);
  const card = (req: Request) => ({
    id: mealId,
    title: t(req, "كشري", "Koshari"),
    description: t(req, "عدس وأرز", "Lentils and rice"),
    kitchen: { id: "kB", slug: "kitchen-b", name: t(req, "مطبخ منى", "Mona's kitchen"), ratingSummary: { count: 0, averageTenths: null } },
    photo: null,
    portionText: t(req, "طبق واحد", "One plate"),
    price: egp(9550),
    nextSlots: [],
    availability: "AVAILABLE",
  });
  const routes = {
    "GET /api/v1/zones": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
    "GET /api/v1/meals": (req: Request) => ({ status: 200, body: { items: [card(req)], nextCursor: null, hasMore: false } }),
    [`GET /api/v1/meals/${mealId}`]: (req: Request) => ({
      status: 200,
      body: { ...card(req), kitchen: { id: "kB", slug: "kitchen-b", name: t(req, "مطبخ منى", "Mona's kitchen") }, ingredients: "", allergenCodes: ["NONE_DECLARED"], allergenStatement: null },
    }),
    "GET /api/v1/kitchens/kB/reviews": () => ({ status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } }),
    "GET /api/v1/kitchens/kitchen-b": (req: Request) => ({
      status: 200,
      body: { kitchenId: "kB", slug: "kitchen-b", displayName: t(req, "مطبخ منى", "Mona's kitchen"), description: t(req, "أكل بيتي", "Home food"), verificationLabel: "OPERATIONALLY_VERIFIED", available: true, pickupEnabled: true, deliveryZoneIds: [] },
    }),
  };
  const lastLanguage = (calls: { path: string; headers: Headers }[], prefix: string) => calls.filter((c) => c.path.startsWith(prefix)).at(-1)?.headers.get("Accept-Language");

  it("browse: switching Arabic → English → Arabic requests and shows each language", async () => {
    const { fetchImpl, calls } = server(routes);
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("article", { name: "كشري" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const en = await screen.findByRole("article", { name: "Koshari" });
    expect(within(en).getByText("Lentils and rice")).toBeTruthy();
    expect(within(en).getByText("One plate")).toBeTruthy();
    expect(lastLanguage(calls, "/api/v1/meals")).toBe("en");
    fireEvent.click(screen.getByRole("button", { name: "العربية" }));
    const ar = await screen.findByRole("article", { name: "كشري" });
    expect(within(ar).getByText("عدس وأرز")).toBeTruthy();
    expect(screen.queryByRole("article", { name: "Koshari" })).toBeNull();
    expect(lastLanguage(calls, "/api/v1/meals")).toBe("ar");
  });

  it("meal detail and kitchen page follow both switch directions", async () => {
    const { fetchImpl, calls } = server(routes);
    window.location.hash = `#/meal/${mealId}`;
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("heading", { name: "كشري" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByRole("heading", { name: "Koshari" })).toBeTruthy();
    expect(screen.getByText("Lentils and rice")).toBeTruthy();
    expect(lastLanguage(calls, `/api/v1/meals/${mealId}`)).toBe("en");
    fireEvent.click(screen.getByRole("button", { name: "العربية" }));
    expect(await screen.findByRole("heading", { name: "كشري" })).toBeTruthy();
    expect(lastLanguage(calls, `/api/v1/meals/${mealId}`)).toBe("ar");
    cleanup();

    window.location.hash = "#/kitchen/kitchen-b";
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("heading", { name: "مطبخ منى" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByRole("heading", { name: "Mona's kitchen" })).toBeTruthy();
    expect(screen.getByText("Home food")).toBeTruthy();
    expect(await screen.findByRole("article", { name: "Koshari" })).toBeTruthy();
    expect(lastLanguage(calls, "/api/v1/kitchens/kitchen-b")).toBe("en");
    fireEvent.click(screen.getByRole("button", { name: "العربية" }));
    expect(await screen.findByRole("heading", { name: "مطبخ منى" })).toBeTruthy();
    expect(await screen.findByRole("article", { name: "كشري" })).toBeTruthy();
  });

  it("a slow answer for a language the customer already left is never shown", async () => {
    const held: (() => void)[] = [];
    const base = server(routes);
    // English answers are held until released; Arabic answers return at once.
    const fetchImpl = (async (input: Request) => {
      if (input.headers.get("Accept-Language") === "en") {
        await new Promise<void>((resolve) => held.push(resolve));
      }
      return base.fetchImpl(input);
    }) as unknown as typeof fetch;
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("article", { name: "كشري" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(screen.getByRole("button", { name: "العربية" }));
    expect(await screen.findByRole("article", { name: "كشري" })).toBeTruthy();
    held.forEach((release) => release());
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByRole("article", { name: "Koshari" })).toBeNull();
    expect(screen.getByRole("article", { name: "كشري" })).toBeTruthy();
    expect(document.documentElement.lang).toBe("ar");
  });
});

/**
 * FG-001 (CW05): quantity editing, individual removal, an estimated subtotal clearly separate from the server quote,
 * and the kitchen shown. Fixture prices follow the QA report (A1 120.25, A2 95.50, B1 80.25 EGP, in piastres).
 */
describe("QA repair FG-001: the basket can be edited and shows an estimate (CW05)", () => {
  const A1 = "a1a1a1a1-0000-4000-8000-0000000000a1";
  const A2 = "a2a2a2a2-0000-4000-8000-0000000000a2";
  const B1 = "b1b1b1b1-0000-4000-8000-0000000000b1";
  const slotsA = [
    { id: "S1", startsAt: "2026-10-13T10:00:00Z", availablePortions: 3, fulfillmentModes: ["PICKUP"] },
    { id: "S2", startsAt: "2026-10-13T13:00:00Z", availablePortions: 3, fulfillmentModes: ["PICKUP"] },
  ];
  const meal = (id: string, title: string, kitchen: { id: string; name: string }, amountMinor: number, nextSlots: unknown[]) => ({
    id,
    title,
    kitchen: { ...kitchen, slug: kitchen.id },
    portionText: "1 plate",
    price: egp(amountMinor),
    description: "",
    ingredients: "",
    allergenCodes: ["NONE_DECLARED"],
    allergenStatement: null,
    photo: null,
    nextSlots,
    availability: "AVAILABLE",
  });
  const kitchenA = { id: "kA", name: "Kitchen A" };
  const kitchenB = { id: "kB", name: "Kitchen B" };
  const reviews = { status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } };
  const routes = {
    [`GET /api/v1/meals/${A1}`]: () => ({ status: 200, body: meal(A1, "QA Meal A1", kitchenA, 12025, slotsA) }),
    [`GET /api/v1/meals/${A2}`]: () => ({ status: 200, body: meal(A2, "QA Meal A2", kitchenA, 9550, slotsA) }),
    [`GET /api/v1/meals/${B1}`]: () => ({
      status: 200,
      body: meal(B1, "QA Meal B1", kitchenB, 8025, [{ id: "BS1", startsAt: "2026-10-13T11:00:00Z", availablePortions: 5, fulfillmentModes: ["PICKUP"] }]),
    }),
    "GET /api/v1/kitchens/kA/reviews": () => reviews,
    "GET /api/v1/kitchens/kB/reviews": () => reviews,
  };

  async function go(hash: string) {
    await act(async () => {
      window.location.hash = hash;
      window.dispatchEvent(new HashChangeEvent("hashchange"));
    });
  }

  async function addFromMeal(id: string, slotIndex: number, quantity: number) {
    await go(`#/meal/${id}`);
    const radios = await screen.findAllByRole("radio");
    fireEvent.click(radios[slotIndex] as HTMLElement);
    for (let i = 1; i < quantity; i++) {
      fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }));
    }
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
  }

  const cartSection = async () => (await screen.findByRole("heading", { name: "Cart", level: 2 })).closest("section") as HTMLElement;
  const estimate = async () => within(await cartSection()).getByText("Estimated subtotal").nextElementSibling?.textContent ?? "";
  const saved = () => JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "null") as { kitchenId: string; slotId: string; lines: { mealId: string; quantity: number }[] } | null;
  const savedLines = () => saved()?.lines.map((l) => [l.mealId, l.quantity]);

  it("edits quantities and removes lines with correct integer arithmetic, then shows the empty state", async () => {
    const { fetchImpl } = server(routes);
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    await addFromMeal(A1, 0, 2);
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    await addFromMeal(A2, 0, 1);
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    await go("#/checkout");
    const cart = await cartSection();
    expect(within(cart).getByText("Kitchen A")).toBeTruthy();
    expect(await estimate()).toMatch(/336\.00/); // 2 × 120.25 + 95.50
    expect(within(cart).getByText(/estimate/i, { selector: "p" }).textContent).toMatch(/Calculate total/);

    fireEvent.click(within(cart).getByRole("button", { name: "Increase quantity: QA Meal A2" }));
    expect(await estimate()).toMatch(/431\.50/); // 2 × 120.25 + 2 × 95.50
    fireEvent.click(within(cart).getByRole("button", { name: "Decrease quantity: QA Meal A1" }));
    expect(await estimate()).toMatch(/311\.25/); // 120.25 + 2 × 95.50
    expect(savedLines()).toEqual([
      [A1, 1],
      [A2, 2],
    ]);
    fireEvent.click(within(cart).getByRole("button", { name: "Remove QA Meal A1" }));
    expect(await estimate()).toMatch(/191\.00/);
    expect(within(await cartSection()).queryByText(/QA Meal A1/)).toBeNull();
    expect(savedLines()).toEqual([[A2, 2]]);
    fireEvent.click(within(await cartSection()).getByRole("button", { name: "Remove QA Meal A2" }));
    expect(await screen.findByText("Your cart is empty.")).toBeTruthy();
    expect(saved()).toBeNull();
  });

  it("keeps each line's quantity between 1 and the portions that were available, up to 20", async () => {
    const { fetchImpl } = server(routes);
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    await addFromMeal(A1, 0, 1);
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    await go("#/checkout");
    const cart = await cartSection();
    const minus = within(cart).getByRole("button", { name: "Decrease quantity: QA Meal A1" }) as HTMLButtonElement;
    const plus = within(cart).getByRole("button", { name: "Increase quantity: QA Meal A1" }) as HTMLButtonElement;
    expect(minus.disabled).toBe(true);
    fireEvent.click(plus);
    fireEvent.click(plus);
    expect(plus.disabled).toBe(true); // the slot had 3 portions
    fireEvent.click(plus);
    expect(savedLines()).toEqual([[A1, 3]]);
    expect(await estimate()).toMatch(/360\.75/);
  });

  it("all four replacement paths keep or replace the basket and its estimate exactly (TC-147)", async () => {
    const { fetchImpl } = server(routes);
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const prompt = async (choice: "Keep my current cart" | "Replace cart") =>
      fireEvent.click(within(await screen.findByRole("alertdialog")).getByRole("button", { name: choice }));
    const expectCart = async (kitchen: string, lines: (string | number)[][], amount: RegExp, slotId: string) => {
      await go("#/checkout");
      expect(within(await cartSection()).getByText(kitchen)).toBeTruthy();
      expect(await estimate()).toMatch(amount);
      expect(savedLines()).toEqual(lines);
      expect(saved()?.slotId).toBe(slotId);
    };

    await addFromMeal(A1, 0, 2);
    await addFromMeal(B1, 0, 1); // another kitchen: cancel
    await prompt("Keep my current cart");
    await expectCart("Kitchen A", [[A1, 2]], /240\.50/, "S1");
    await addFromMeal(B1, 0, 1); // another kitchen: confirm
    await prompt("Replace cart");
    await expectCart("Kitchen B", [[B1, 1]], /80\.25/, "BS1");

    fireEvent.click(within(await cartSection()).getByRole("button", { name: "Empty cart" }));
    await addFromMeal(A1, 0, 2);
    await addFromMeal(A2, 1, 1); // same kitchen, another slot: cancel
    await prompt("Keep my current cart");
    await expectCart("Kitchen A", [[A1, 2]], /240\.50/, "S1");
    await addFromMeal(A2, 1, 1); // same kitchen, another slot: confirm
    await prompt("Replace cart");
    await expectCart("Kitchen A", [[A2, 1]], /95\.50/, "S2");
  });

  it("an edit discards a quote already shown, so only a fresh server quote can be accepted; a pending order locks the basket", async () => {
    window.localStorage.setItem(
      "matbakhy.cart",
      JSON.stringify({ kitchenId: "kA", kitchenName: "Kitchen A", slotId: "S1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: A1, title: "QA Meal A1", quantity: 1, unitPriceMinor: 12025, maxQuantity: 3 }] }),
    );
    const quotes: unknown[] = [];
    const { fetchImpl } = server({
      "GET /api/v1/me": () => ({ status: 200, body: { ...me, locale: "en" } }),
      "GET /api/v1/me/addresses": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "POST /api/v1/quotes": (_req, body) => {
        quotes.push(body);
        return {
          status: 201,
          body: { quoteId: `q${quotes.length}`, expiresAt: "2026-10-13T09:05:00Z", kitchenId: "kA", items: [], subtotal: egp(12025), deliveryFee: egp(0), tax: egp(1684), total: egp(13709), cancellationPolicyVersion: "c1", termsVersion: "t1", acceptanceDeadlinePreview: "2026-10-13T09:15:00Z", warnings: [] },
        };
      },
    });
    window.location.hash = "#/checkout";
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("button", { name: "Calculate total" }));
    expect(await screen.findByText("Total")).toBeTruthy();
    // The estimate and the server quote are separate figures with separate labels.
    expect(await estimate()).toMatch(/120\.25/);
    expect(screen.getByText(/137\.09/)).toBeTruthy();
    fireEvent.click(within(await cartSection()).getByRole("button", { name: "Increase quantity: QA Meal A1" }));
    await waitFor(() => expect(screen.queryByText("Total")).toBeNull());
    expect(screen.queryByRole("button", { name: "Place order" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Calculate total" }));
    await waitFor(() => expect(quotes).toHaveLength(2));
    expect((quotes[1] as { items: unknown[] }).items).toEqual([{ mealId: A1, quantity: 2 }]);
    cleanup();

    savePending({ clientRequestId: "c1", idempotencyKey: "i1", quoteId: "q2", terms: "t1", cancellation: "c1" });
    window.location.hash = "#/checkout";
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const cart = await cartSection();
    expect((within(cart).getByRole("button", { name: "Increase quantity: QA Meal A1" }) as HTMLButtonElement).disabled).toBe(true);
    expect((within(cart).getByRole("button", { name: "Remove QA Meal A1" }) as HTMLButtonElement).disabled).toBe(true);
  });
});

/**
 * Q1 option A: the quantity controls follow the server's published per-line limit (API-001), not a hard-coded 20.
 * The limit value is the active commerce policy's; the server still refuses anything above it at quote time.
 */
describe("Q1: quantity limits come from the server's active policy", () => {
  const id = "c1c1c1c1-0000-4000-8000-0000000000c1";
  const limits = (maxQuantityPerLine: number) => ({ status: 200, body: { orderingAvailable: true, orderLimits: { maxQuantityPerLine, maxLinesPerOrder: 20, policyVersion: 7 } } });
  const meal = (availablePortions: number) => ({
    id,
    title: "QA Meal Q1",
    kitchen: { id: "kQ", slug: "kitchen-q", name: "Kitchen Q" },
    portionText: "1 plate",
    price: egp(5000),
    description: "",
    ingredients: "",
    allergenCodes: ["NONE_DECLARED"],
    allergenStatement: null,
    photo: null,
    nextSlots: [{ id: "SQ", startsAt: "2026-10-13T10:00:00Z", availablePortions, fulfillmentModes: ["PICKUP"] }],
    availability: "AVAILABLE",
  });
  const reviews = { status: 200, body: { items: [], summary: { count: 0, averageTenths: null }, nextCursor: null, hasMore: false } };

  async function maxReachable() {
    fireEvent.click(await screen.findByRole("radio"));
    const plus = screen.getByRole("button", { name: "Increase quantity" }) as HTMLButtonElement;
    for (let i = 0; i < 30 && !plus.disabled; i++) {
      fireEvent.click(plus);
    }
    return Number((screen.getByRole("spinbutton", { name: "Quantity" }) as HTMLInputElement).value);
  }

  it("caps the meal quantity at the policy limit when the slot has more portions, and at the portions when fewer", async () => {
    const { fetchImpl } = server({
      "GET /api/v1/configuration/public": () => limits(10),
      [`GET /api/v1/meals/${id}`]: () => ({ status: 200, body: meal(15) }),
      "GET /api/v1/kitchens/kQ/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${id}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    await waitFor(async () => expect(await maxReachable()).toBe(10));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Quantity" }), { target: { value: "11" } });
    expect((screen.getByRole("spinbutton", { name: "Quantity" }) as HTMLInputElement).value).toBe("10");
    fireEvent.click(screen.getByRole("button", { name: /^Add to cart/ }));
    expect(await screen.findByText("Added to cart.")).toBeTruthy();
    const line = JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "{}").lines[0];
    expect([line.quantity, line.maxQuantity]).toEqual([10, 10]);
    cleanup();

    const fewer = server({
      "GET /api/v1/configuration/public": () => limits(10),
      [`GET /api/v1/meals/${id}`]: () => ({ status: 200, body: meal(3) }),
      "GET /api/v1/kitchens/kQ/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${id}`;
    render(<App fetchImpl={fewer.fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await maxReachable()).toBe(3);
  });

  it("explains a basket line above a lowered limit and offers no quote until it is reduced", async () => {
    window.localStorage.setItem(
      "matbakhy.cart",
      JSON.stringify({ kitchenId: "kQ", kitchenName: "Kitchen Q", slotId: "SQ", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: id, title: "QA Meal Q1", quantity: 12, unitPriceMinor: 5000, maxQuantity: 20 }] }),
    );
    const { fetchImpl, calls } = server({
      "GET /api/v1/configuration/public": () => limits(10),
      "GET /api/v1/me": () => ({ status: 200, body: { ...me, locale: "en" } }),
      "GET /api/v1/me/addresses": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
    });
    window.location.hash = "#/checkout";
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await screen.findByText("At most 10 per meal can be ordered now. Reduce the quantity to continue.")).toBeTruthy();
    expect((await screen.findByRole("button", { name: "Calculate total" }) as HTMLButtonElement).disabled).toBe(true);
    const minus = screen.getByRole("button", { name: "Decrease quantity: QA Meal Q1" });
    fireEvent.click(minus);
    fireEvent.click(minus);
    await waitFor(() => expect(screen.queryByText(/At most 10 per meal/)).toBeNull());
    expect((screen.getByRole("button", { name: "Calculate total" }) as HTMLButtonElement).disabled).toBe(false);
    expect((screen.getByRole("button", { name: "Increase quantity: QA Meal Q1" }) as HTMLButtonElement).disabled).toBe(true);
    expect(calls.some((c) => c.path === "/api/v1/quotes")).toBe(false);
  });

  it("without the published limit, still bounds by the slot's portions and at most 20", async () => {
    const { fetchImpl } = server({
      "GET /api/v1/configuration/public": () => ({ status: 503, body: { code: "SERVICE_UNAVAILABLE" } }),
      [`GET /api/v1/meals/${id}`]: () => ({ status: 200, body: meal(40) }),
      "GET /api/v1/kitchens/kQ/reviews": () => reviews,
    });
    window.location.hash = `#/meal/${id}`;
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(await maxReachable()).toBe(20);
  });
});
