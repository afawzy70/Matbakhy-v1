import { brand } from "@matbakhy/design-tokens";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert } from "react-native";
import { App } from "./App";
import type { AppConfig } from "./config";
import { ErrorBoundary } from "./ErrorBoundary";
import { strings } from "./i18n";
import { Session } from "./session";
import { fakeJwt } from "./testing";

const mockStore = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: "x",
  setItemAsync: jest.fn(async (k: string, v: string) => void mockStore.set(k, v)),
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  deleteItemAsync: jest.fn(async (k: string) => void mockStore.delete(k)),
}));
jest.mock("expo-localization", () => ({ getLocales: () => [{ languageCode: "en" }] }));
jest.mock("expo-crypto", () => ({ randomUUID: () => jest.requireActual<{ randomUUID: () => string }>("crypto").randomUUID() }));
jest.mock("./auth", () => ({ signIn: jest.fn(), signOut: jest.fn(), SignInCancelled: class extends Error {} }));
jest.mock("react-native-safe-area-context", () => jest.requireActual("react-native-safe-area-context/jest/mock").default);

beforeEach(() => mockStore.clear());

const t = strings.en;
const config: AppConfig = { environment: "development", apiBaseUrl: "http://api", issuer: "http://id", clientId: "matbakhy-mobile", redirectUri: "matbakhy-dev://auth/callback" };
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const money = (amountMinor: number) => ({ amountMinor, currency: "EGP" });
const slot = (id: string) => ({ id, startsAt: "2026-10-13T10:00:00Z", endsAt: "2026-10-13T11:00:00Z", timezone: "Africa/Cairo", cutoffAt: "2026-10-13T08:00:00Z", availablePortions: 5, fulfillmentModes: ["DELIVERY", "PICKUP"] });
const meal = (id: string, kitchenId: string) => ({
  id,
  kitchen: { id: kitchenId, slug: kitchenId, name: `Kitchen ${kitchenId}` },
  title: `Dish ${id}`,
  description: null,
  photo: null,
  portionText: "1 box",
  price: money(12000),
  nextSlots: [slot(`slot-${kitchenId}`)],
  availability: "AVAILABLE",
  ingredients: "rice",
  allergenCodes: ["NONE_DECLARED"],
  allergenStatement: "",
  photos: [],
  reviewSummary: { count: 0, averageTenths: null },
  publishedRevision: 1,
});
const profile = { id: "u", displayName: "Mona", locale: "en", emailVerified: true, phoneVerified: true, version: 1, capabilities: [] };

/** A fake API routed by method and path; anything unrouted fails loudly. */
function api(routes: Record<string, () => Response>) {
  return jest.fn(async (input: Request | string) => {
    const req = typeof input === "string" ? new Request(input) : input;
    const path = new URL(req.url).pathname;
    const key = `${req.method} ${path}`;
    const route = routes[key];
    if (!route) {
      throw new Error(`unrouted ${key}`);
    }
    return route();
  }) as unknown as typeof fetch;
}

const discover = { "GET /api/v1/meals": () => json(200, { items: [meal("m1", "k1"), meal("m2", "k2")], nextCursor: null, hasMore: false }) };

async function openAccount() {
  await fireEvent.press(await screen.findByTestId("tab-account"));
}

describe("customer app shell (MB-031, kept in MB-032)", () => {
  it("signs in through the injected PKCE flow from the Account tab, shows the profile and switches language", async () => {
    const session = new Session();
    const signIn = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 }));
    const fetchImpl = api({ ...discover, "GET /api/v1/me": () => json(200, profile) });
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await openAccount();
    expect(await screen.findByText(brand.name.en)).toBeTruthy();
    await fireEvent.press(await screen.findByRole("button", { name: t.signIn }));
    expect(await screen.findByText(t.signedInAs("Mona"))).toBeTruthy();
    const meCall = (fetchImpl as jest.Mock).mock.calls.map((c) => c[0] as Request).find((r) => r.url.endsWith("/api/v1/me"));
    expect(meCall?.headers.get("Authorization")).toBe(`Bearer ${fakeJwt("customer-a")}`);
    await fireEvent.press(screen.getByRole("button", { name: t.language }));
    expect(await screen.findByText(brand.name.ar)).toBeTruthy();
  });

  it("ends the session and clears private data, basket and pending order when the server says 401", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(401, {}) })} />);
    await openAccount();
    expect(await screen.findByText(t.sessionEnded)).toBeTruthy();
    expect(mockStore.size).toBe(0);
    expect(session.cachedKeys).toEqual([]);
  });

  it("signs out through the provider hook, clearing the session and the basket", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    const signOut = jest.fn(async (_c: AppConfig, s: Session) => s.signOut());
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={signOut} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile) })} />);
    await openAccount();
    await fireEvent.press(await screen.findByRole("button", { name: t.signOut }));
    expect(await screen.findByRole("button", { name: t.signIn })).toBeTruthy();
    expect(signOut).toHaveBeenCalledWith(config, session, "r");
    expect(mockStore.size).toBe(0);
  });

  it("shows a recoverable crash screen without technical details", async () => {
    const Boom = () => {
      throw new Error("secret stack detail");
    };
    jest.spyOn(console, "error").mockImplementation(() => undefined);
    await render(
      <ErrorBoundary t={strings.en}>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText(strings.en.crashTitle)).toBeTruthy();
    expect(screen.queryByText(/secret stack detail/)).toBeNull();
  });
});

describe("native discovery and ordering (MB-032)", () => {
  const routes = {
    ...discover,
    "GET /api/v1/meals/m1": () => json(200, meal("m1", "k1")),
    "GET /api/v1/meals/m2": () => json(200, meal("m2", "k2")),
  };

  async function addFromMeal(id: string) {
    await fireEvent.press(await screen.findByTestId(`meal-${id}`));
    await fireEvent.press(await screen.findByTestId("slot-0", {}, { timeout: 5000 }));
    await fireEvent.press(screen.getByTestId("add-to-cart"));
  }

  const kitchenOf = () => JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "{}").kitchenId;

  it("adds a meal and slot to an empty basket without asking", async () => {
    const alert = jest.spyOn(Alert, "alert").mockImplementation(() => undefined);
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api(routes)} />);
    await addFromMeal("m1");
    expect(await screen.findByText(t.added)).toBeTruthy();
    expect(kitchenOf()).toBe("k1");
    expect(alert).not.toHaveBeenCalled();
    alert.mockRestore();
  });

  it("asks before replacing the basket with another kitchen's meal: keep leaves it, replace replaces it (TC-148)", async () => {
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["DELIVERY"], lines: [{ mealId: "m1", title: "Dish m1", quantity: 1 }] }));
    const alert = jest.spyOn(Alert, "alert").mockImplementation(() => undefined);
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api(routes)} />);
    await addFromMeal("m2");
    await waitFor(() => expect(alert).toHaveBeenCalledTimes(1));
    const [title, , buttons] = alert.mock.calls[0] ?? [];
    expect(title).toBe(t.replaceTitle);
    const keep = buttons?.find((b) => b.text === t.keepBasket);
    const replace = buttons?.find((b) => b.text === t.replaceBasket);
    expect(keep?.style).toBe("cancel");
    await act(async () => keep?.onPress?.());
    expect(await screen.findByText(t.basketKept)).toBeTruthy();
    expect(kitchenOf()).toBe("k1");
    await act(async () => replace?.onPress?.());
    expect(await screen.findByText(t.added)).toBeTruthy();
    expect(kitchenOf()).toBe("k2");
    expect(JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "{}").lines).toEqual([{ mealId: "m2", title: "Dish m2", quantity: 1, unitPriceMinor: 12000, maxQuantity: 5 }]);
    alert.mockRestore();
  });

  it("after a restart with an unconfirmed order: says plainly it is offline, then shows the one recovered order (TC-049)", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    const pendingKeys = { clientRequestId: "22222222-2222-4222-8222-222222222222", idempotencyKey: "app-k", quoteId: "q", terms: "t", cancellation: "c" };
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["DELIVERY"], lines: [{ mealId: "m1", title: "Dish m1", quantity: 2 }] }));
    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify(pendingKeys));
    let online = false;
    const order = {
      id: "o1", publicNumber: "MB-000123", version: 1, state: "PENDING_ACCEPTANCE", fulfillmentMode: "DELIVERY",
      items: [{ mealId: "m1", title: "Dish m1", quantity: 2, unitPrice: money(12000), lineTotal: money(24000) }],
      subtotal: money(24000), deliveryFee: money(3000), tax: money(0), total: money(27000), slot: slot("slot-k1"),
      acceptBy: "2026-10-13T09:00:00Z", events: [], allowedActions: [], cashState: "DUE", supportCaseIds: [], handoverCode: null, contact: null,
    };
    const offlineFetch = jest.fn(async (input: Request) => {
      const path = new URL(input.url).pathname;
      if (!online) {
        throw new TypeError("Network request failed");
      }
      if (path === `/api/v1/orders/by-request/${pendingKeys.clientRequestId}`) return json(200, order);
      if (path === "/api/v1/orders/o1") return json(200, order);
      if (path === "/api/v1/me") return json(200, profile);
      if (path === "/api/v1/meals") return json(200, { items: [], nextCursor: null, hasMore: false });
      throw new Error(`unrouted ${input.method} ${path}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={offlineFetch} />);
    expect(await screen.findByTestId("offline")).toBeTruthy();
    await fireEvent.press(await screen.findByTestId("open-cart"));
    expect(await screen.findByText(t.pendingOffline)).toBeTruthy();
    expect(screen.queryByText("MB-000123")).toBeNull();
    expect(screen.queryByTestId("place-order")).toBeNull();

    online = true;
    await fireEvent.press(screen.getByTestId("retry-order"));
    expect(await screen.findByText("MB-000123")).toBeTruthy();
    expect(screen.getByText(`${t.total}: ${new Intl.NumberFormat("en-EG", { style: "currency", currency: "EGP" }).format(270)}`)).toBeTruthy();
    const posts = (offlineFetch as jest.Mock).mock.calls.filter((c) => (c[0] as Request).method === "POST");
    expect(posts).toHaveLength(0);
    expect(mockStore.has("matbakhy.pendingPlacement.v1")).toBe(false);
    expect(mockStore.has("matbakhy.basket.v1")).toBe(false);
  });
});

/** Repair for the independent QA finding MBQA-002 (web) applied to the same rule on native; run red before the fix. */
describe("QA repair MBQA-002 on native: an exhausted selected slot cannot be added", () => {
  const mixed = {
    ...meal("mx", "k1"),
    nextSlots: [
      { ...slot("sx-empty"), availablePortions: 0 },
      { ...slot("sx-stock"), startsAt: "2026-10-13T13:00:00Z", availablePortions: 3 },
    ],
  };
  const routes = { "GET /api/v1/meals": () => json(200, { items: [mixed], nextCursor: null, hasMore: false }), "GET /api/v1/meals/mx": () => json(200, mixed) };

  it("refuses the exhausted slot without writing the basket and adds the slot that has stock up to its portions", async () => {
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api(routes)} />);
    await fireEvent.press(await screen.findByTestId("meal-mx"));
    await fireEvent.press(await screen.findByTestId("slot-0", {}, { timeout: 5000 }));
    await fireEvent.press(screen.getByTestId("add-to-cart"));
    await act(async () => undefined);
    expect(mockStore.get("matbakhy.basket.v1")).toBeUndefined();
    expect(screen.queryByText(t.added)).toBeNull();
    expect(screen.getByText(t.slotSoldOut)).toBeTruthy();

    await fireEvent.press(screen.getByTestId("slot-1"));
    for (let i = 0; i < 5; i++) {
      await fireEvent.press(screen.getByLabelText(t.increase));
    }
    await fireEvent.press(screen.getByTestId("add-to-cart"));
    expect(await screen.findByText(t.added)).toBeTruthy();
    const basket = JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "{}");
    expect(basket.slotId).toBe("sx-stock");
    expect(basket.lines).toEqual([{ mealId: "mx", title: "Dish mx", quantity: 3, unitPriceMinor: 12000, maxQuantity: 3 }]);
  });
});

/** FG-001 (CW05) on native, part of MB-032: editable basket with an estimate kept separate from the server quote. */
describe("native basket editing (CW05, MB-032; parity with QA finding FG-001)", () => {
  const line = (mealId: string, title: string, quantity: number, unitPriceMinor: number, maxQuantity: number) => ({ mealId, title, quantity, unitPriceMinor, maxQuantity });
  const seed = (lines: unknown[]) =>
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines }));
  const stored = () => JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "null") as { lines: { mealId: string; quantity: number }[] } | null;
  const openCart = async (fetchImpl: typeof fetch, session = new Session()) => {
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
  };
  const estimate = async () => (await screen.findByTestId("estimate")).props.children as string;

  it("records the price, portion limit and kitchen when adding, for the estimate only", async () => {
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/meals/m1": () => json(200, meal("m1", "k1")) })} />);
    await fireEvent.press(await screen.findByTestId("meal-m1"));
    await fireEvent.press(await screen.findByTestId("slot-0", {}, { timeout: 5000 }));
    await fireEvent.press(screen.getByTestId("add-to-cart"));
    expect(await screen.findByText(t.added)).toBeTruthy();
    const basket = JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "{}");
    expect(basket.kitchenName).toBe("Kitchen k1");
    expect(basket.lines).toEqual([{ mealId: "m1", title: "Dish m1", quantity: 1, unitPriceMinor: 12000, maxQuantity: 5 }]);
  });

  it("edits quantities and removes lines with integer arithmetic, then shows the empty state", async () => {
    seed([line("m1", "Dish m1", 2, 12000, 5), line("m3", "Dish m3", 1, 9000, 5)]);
    await openCart(api(discover));
    expect(await screen.findByText("Kitchen k1")).toBeTruthy();
    expect(await estimate()).toMatch(/330\.00/);
    expect(screen.getByText(t.estimateNote)).toBeTruthy();
    await fireEvent.press(screen.getByTestId("qty-inc-m3"));
    expect(await estimate()).toMatch(/420\.00/);
    await fireEvent.press(screen.getByTestId("qty-dec-m1"));
    expect(await estimate()).toMatch(/300\.00/);
    await waitFor(() => expect(stored()?.lines.map((l) => [l.mealId, l.quantity])).toEqual([["m1", 1], ["m3", 2]]));
    await fireEvent.press(screen.getByTestId("remove-m1"));
    expect(await estimate()).toMatch(/180\.00/);
    await fireEvent.press(screen.getByTestId("remove-m3"));
    expect(await screen.findByText(t.cartEmpty)).toBeTruthy();
    await waitFor(() => expect(mockStore.has("matbakhy.basket.v1")).toBe(false));
  });

  it("keeps quantity between 1 and the line's limit, and locks the basket while an order is unconfirmed", async () => {
    seed([line("m1", "Dish m1", 1, 12000, 2)]);
    await openCart(api(discover));
    expect(screen.getByTestId("qty-dec-m1").props.accessibilityState.disabled).toBe(true);
    await fireEvent.press(await screen.findByTestId("qty-inc-m1"));
    await waitFor(() => expect(screen.getByTestId("qty-inc-m1").props.accessibilityState.disabled).toBe(true));
    expect(await estimate()).toMatch(/240\.00/);
    await screen.unmount();

    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify({ clientRequestId: "c1", idempotencyKey: "i1", quoteId: "q1", terms: "t1", cancellation: "c1" }));
    await openCart(api(discover));
    expect((await screen.findByTestId("qty-inc-m1")).props.accessibilityState.disabled).toBe(true);
    expect(screen.getByTestId("remove-m1").props.accessibilityState.disabled).toBe(true);
  });
});

/**
 * MB-032 native run 27/28 (Android): after the app was killed during submit and reopened offline, Discover showed no
 * cart shortcut although the customer was still signed in, so the unconfirmed order could not be reached (TC-049).
 */
describe("the unconfirmed order stays reachable after a restart (TC-049, MB-032)", () => {
  const pending = { clientRequestId: "c1", idempotencyKey: "i1", quoteId: "q1", terms: "t1", cancellation: "c1" };
  const offline = jest.fn(async () => {
    throw new TypeError("Network request failed");
  }) as unknown as typeof fetch;

  it("shows the cart shortcut when an order is pending even if the basket cannot be found", async () => {
    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify(pending));
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={offline} />);
    expect(await screen.findByTestId("open-cart")).toBeTruthy();
  });

  it("retries a failed secure-storage read once instead of hiding the basket", async () => {
    const SecureStore = jest.requireMock<{ getItemAsync: jest.Mock }>("expo-secure-store");
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: "m1", title: "Dish m1", quantity: 1 }] }));
    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify(pending));
    const warn = jest.spyOn(console, "warn").mockImplementation(() => undefined);
    SecureStore.getItemAsync.mockImplementationOnce(async () => {
      throw new Error("keystore not ready");
    });
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={offline} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
    expect(await screen.findByText(/1 × Dish m1/)).toBeTruthy();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});
