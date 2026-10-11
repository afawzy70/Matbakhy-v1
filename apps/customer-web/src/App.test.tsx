import { brand } from "@matbakhy/design-tokens";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
});
