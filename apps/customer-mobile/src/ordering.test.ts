import { decideAdd, loadBasket, loadPending, saveBasket, savePending, type Basket, type PendingPlacement } from "./basket";
import { createMobileApi } from "./mobileApi";
import { place, recover } from "./placement";
import { Session } from "./session";

const mockStore = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: "x",
  setItemAsync: jest.fn(async (k: string, v: string) => void mockStore.set(k, v)),
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  deleteItemAsync: jest.fn(async (k: string) => void mockStore.delete(k)),
}));
jest.mock("expo-crypto", () => ({ randomUUID: () => jest.requireActual<{ randomUUID: () => string }>("crypto").randomUUID() }));

beforeEach(() => mockStore.clear());

const target = { kitchenId: "k1", slotId: "s1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["DELIVERY"] };
const line = (mealId: string, quantity = 1) => ({ mealId, title: mealId, quantity });

describe("basket rules (BR-018, TC-148)", () => {
  it("merges the same kitchen and slot, replacing a meal's quantity", () => {
    const first = decideAdd(null, null, target, line("m1"));
    expect(first.kind).toBe("added");
    const basket = (first as { basket: Basket }).basket;
    const second = decideAdd(basket, null, target, line("m2", 2));
    expect(second).toEqual({ kind: "added", basket: { ...target, lines: [line("m1"), line("m2", 2)] } });
    const third = decideAdd((second as { basket: Basket }).basket, null, target, line("m1", 3));
    expect((third as { basket: Basket }).basket.lines).toEqual([line("m2", 2), line("m1", 3)]);
  });

  it("asks before replacing a basket from another kitchen or another slot, and never while an order is pending", () => {
    const basket: Basket = { ...target, lines: [line("m1")] };
    expect(decideAdd(basket, null, { ...target, kitchenId: "k2" }, line("x"))).toEqual({ kind: "confirmReplace", replacement: { ...target, kitchenId: "k2", lines: [line("x")] } });
    expect(decideAdd(basket, null, { ...target, slotId: "s2" }, line("x")).kind).toBe("confirmReplace");
    const pending: PendingPlacement = { clientRequestId: "c", idempotencyKey: "i", quoteId: "q", terms: "t", cancellation: "c" };
    expect(decideAdd(basket, pending, { ...target, kitchenId: "k2" }, line("x"))).toEqual({ kind: "placementPending" });
  });
});

type Call = { method: string; url: string; headers: Headers; body: string };

function server(handler: (c: Call) => Response | Promise<Response>) {
  const calls: Call[] = [];
  const fetchImpl = jest.fn(async (input: Request) => {
    const call = { method: input.method, url: input.url, headers: input.headers, body: input.method === "GET" ? "" : await input.text() };
    calls.push(call);
    return handler(call);
  }) as unknown as typeof fetch;
  return { calls, fetchImpl };
}

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const config = { environment: "development" as const, apiBaseUrl: "http://api", issuer: "http://id", clientId: "m", redirectUri: "matbakhy-dev://auth/callback" };
const pending: PendingPlacement = { clientRequestId: "11111111-1111-4111-8111-111111111111", idempotencyKey: "app-key-1", quoteId: "q1", terms: "t1", cancellation: "c1" };

function apiFor(fetchImpl: typeof fetch) {
  return createMobileApi(config, new Session(), { locale: "en", onSignedOut: () => undefined }, fetchImpl);
}

describe("placement that cannot duplicate an order (BR-023, TC-049)", () => {
  it("keeps the stored keys when the outcome is unknown and clears basket and keys once the order exists", async () => {
    await saveBasket({ ...target, lines: [line("m1")] });
    await savePending(pending);
    const offline = server(() => {
      throw new TypeError("Network request failed");
    });
    expect(await place(apiFor(offline.fetchImpl), pending)).toEqual({ kind: "uncertain", offline: true });
    expect(await loadPending()).toEqual(pending);

    const flaky = server(() => json(503, { code: "DEPENDENCY_UNAVAILABLE" }));
    expect(await place(apiFor(flaky.fetchImpl), pending)).toEqual({ kind: "uncertain", offline: false });
    expect(await loadPending()).toEqual(pending);

    const ok = server(() => json(201, { id: "order-1" }));
    expect(await place(apiFor(ok.fetchImpl), pending)).toEqual({ kind: "placed", orderId: "order-1" });
    expect(ok.calls[0]?.headers.get("Idempotency-Key")).toBe("app-key-1");
    expect(JSON.parse(ok.calls[0]?.body ?? "{}")).toMatchObject({ quoteId: "q1", clientRequestId: pending.clientRequestId });
    expect(await loadPending()).toBeNull();
    expect(await loadBasket()).toBeNull();
  });

  it("drops the keys after a definitive refusal so a new quote is needed", async () => {
    await savePending(pending);
    const refused = server(() => json(409, { code: "QUOTE_EXPIRED" }));
    expect(await place(apiFor(refused.fetchImpl), pending)).toEqual({ kind: "rejected", code: "QUOTE_EXPIRED" });
    expect(await loadPending()).toBeNull();
  });

  it("keeps the stored keys when the server asks for a second factor and sends the same keys on the explicit retry", async () => {
    await saveBasket({ ...target, lines: [line("m1")] });
    await savePending(pending);
    const stepUp = server(() => json(403, { code: "STEP_UP_REQUIRED" }));
    expect(await place(apiFor(stepUp.fetchImpl), pending)).toEqual({ kind: "stepUp" });
    expect(await loadPending()).toEqual({ ...pending, awaitingExplicitRetry: true });
    expect(await loadBasket()).not.toBeNull();
    // Nothing is replayed by itself: the retry is the customer's explicit action and reuses the very same keys.
    const ok = server(() => json(201, { id: "order-1" }));
    expect(await place(apiFor(ok.fetchImpl), pending)).toEqual({ kind: "placed", orderId: "order-1" });
    expect(ok.calls[0]?.headers.get("Idempotency-Key")).toBe(pending.idempotencyKey);
    expect(JSON.parse(ok.calls[0]?.body ?? "{}")).toMatchObject({ clientRequestId: pending.clientRequestId });
  });

  it("a refused placement is never re-sent by a focus or restart check, only by the explicit retry, with the same keys", async () => {
    await savePending({ ...pending, awaitingExplicitRetry: true });
    const quiet = server(() => {
      throw new Error("no request may be sent without the explicit retry");
    });
    expect(await recover(apiFor(quiet.fetchImpl))).toEqual({ kind: "stepUp" });
    expect(quiet.calls).toHaveLength(0);
    const answers = server((req) => (req.method === "GET" ? json(404, { code: "NOT_FOUND" }) : json(201, { id: "order-7" })));
    expect(await recover(apiFor(answers.fetchImpl), true)).toEqual({ kind: "placed", orderId: "order-7" });
    const post = answers.calls.find((c) => c.method === "POST");
    expect(post?.headers.get("Idempotency-Key")).toBe(pending.idempotencyKey);
    expect(JSON.parse(post?.body ?? "{}")).toMatchObject({ clientRequestId: pending.clientRequestId });
    expect(await loadPending()).toBeNull();
  });

  it("after a restart finds the order by client request id, or re-sends with the same keys, and stays uncertain offline", async () => {
    await savePending(pending);
    const offline = server(() => {
      throw new TypeError("Network request failed");
    });
    expect(await recover(apiFor(offline.fetchImpl))).toEqual({ kind: "uncertain", offline: true });
    expect(await loadPending()).toEqual(pending);

    const found = server((c) => (c.url.endsWith(`/api/v1/orders/by-request/${pending.clientRequestId}`) ? json(200, { id: "order-1" }) : json(500, {})));
    expect(await recover(apiFor(found.fetchImpl))).toEqual({ kind: "placed", orderId: "order-1" });
    expect(found.calls.map((c) => c.method)).toEqual(["GET"]);

    await savePending(pending);
    const missing = server((c) => (c.method === "GET" ? json(404, { code: "NOT_FOUND" }) : json(201, { id: "order-2" })));
    expect(await recover(apiFor(missing.fetchImpl))).toEqual({ kind: "placed", orderId: "order-2" });
    expect(missing.calls[1]?.headers.get("Idempotency-Key")).toBe("app-key-1");
    expect(JSON.parse(missing.calls[1]?.body ?? "{}").clientRequestId).toBe(pending.clientRequestId);
    expect(await recover(apiFor(missing.fetchImpl))).toBeNull();
  });
});

describe("platform fetch compatibility (native run 21)", () => {
  it("reads responses whose class is not the global Response, as React Native/Expo fetch returns", async () => {
    class PlatformResponse {
      readonly status = 200;
      readonly ok = true;
      readonly headers = new Headers({ "Content-Type": "application/json" });
      async text() {
        return JSON.stringify({ id: "u", displayName: "Mona" });
      }
      async json() {
        return JSON.parse(await this.text());
      }
      clone() {
        return this;
      }
    }
    const fetchImpl = jest.fn(async () => new PlatformResponse()) as unknown as typeof fetch;
    const r = await apiFor(fetchImpl).GET("/api/v1/me");
    expect(r.data).toMatchObject({ displayName: "Mona" });
  });
});

/** Retrospective regression coverage (2026-10-12 hardening pass); expected behaviour from BR-018, BR-044 and CW05. */
describe("retrospective regression: basket and placement", () => {
  it("keeps the basket after a definitive refusal (no surprise clearing) while dropping the spent keys (CW05)", async () => {
    const basket: Basket = { ...target, lines: [line("m1", 2)] };
    await saveBasket(basket);
    await savePending(pending);
    for (const [status, code] of [[409, "STOCK_CHANGED"], [422, "VALIDATION_FAILED"], [409, "QUOTE_EXPIRED"]] as const) {
      await savePending(pending);
      const refused = server(() => json(status, { code }));
      expect(await place(apiFor(refused.fetchImpl), pending)).toEqual({ kind: "rejected", code });
      expect(await loadPending()).toBeNull();
      expect(await loadBasket()).toEqual(basket);
    }
  });

  it("asks before replacing when the same kitchen offers another slot (BR-018)", () => {
    const basket: Basket = { ...target, lines: [line("m1")] };
    expect(decideAdd(basket, null, { ...target, slotId: "s2", slotStartsAt: "2026-10-14T10:00:00Z" }, line("m1"))).toEqual({
      kind: "confirmReplace",
      replacement: { ...target, slotId: "s2", slotStartsAt: "2026-10-14T10:00:00Z", lines: [line("m1")] },
    });
  });
});
