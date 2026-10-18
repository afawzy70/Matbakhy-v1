import { brand } from "@matbakhy/design-tokens";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert, Linking } from "react-native";
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
const cod = (total: number) => ({ method: "COD", depositBps: 0, deposit: money(0), remainder: money(total), recipient: null });
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
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 }));
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

/** Refuses with STEP_UP_REQUIRED until the injected sign-in has run, like the server after a verified second factor. */
const refusedUntil = (signIn: jest.Mock) => () => (signIn.mock.calls.length > 0 ? json(200, { items: [meal("m1", "k1"), meal("m2", "k2")], nextCursor: null, hasMore: false }) : json(403, { code: "STEP_UP_REQUIRED" }));

describe("two-step verification step-up (D18)", () => {
  async function signedInSession() {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    return session;
  }

  it("offers a step-up sign-in when the server asks for a second factor, instead of a broken screen", async () => {
    const session = await signedInSession();
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r2", expiresIn: 300 }));
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile), "GET /api/v1/meals": refusedUntil(signIn) })} />);
    expect(await screen.findByText(t.stepUp.message)).toBeTruthy();
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    await waitFor(() => expect(screen.queryByText(t.stepUp.message)).toBeNull());
    expect(signIn).toHaveBeenCalledWith(config, session, { stepUp: true });
    expect(session.accessToken).toBe(fakeJwt("customer-a"));
    expect(mockStore.get("matbakhy.basket.v1")).toBeTruthy();
  });

  it("treats a step-up that signs in as another person as an account switch: basket cleared, profile re-read", async () => {
    const session = await signedInSession();
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-b"), idToken: fakeJwt("customer-b"), refreshToken: "r3", expiresIn: 300 }));
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    let profileCalls = 0;
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => { profileCalls += 1; return json(200, profile); }, "GET /api/v1/meals": refusedUntil(signIn) })} />);
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    await waitFor(() => expect(screen.queryByText(t.stepUp.message)).toBeNull());
    expect(mockStore.get("matbakhy.basket.v1")).toBeUndefined();
    expect(session.accessToken).toBe(fakeJwt("customer-b"));
    expect(profileCalls).toBeGreaterThanOrEqual(2);
  });

  it("an account switch during step-up leaves nothing of account A on any screen: orders, navigation, basket and pending order are gone", async () => {
    const session = await signedInSession();
    let who = "A";
    const orderOf = (n: string) => ({ id: `o-${n}`, publicNumber: n, state: "PLACED", total: money(5000) });
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => {
      who = "B";
      return s.signedIn({ accessToken: fakeJwt("customer-b"), idToken: fakeJwt("customer-b"), refreshToken: "r3", expiresIn: 300 });
    });
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify({ clientRequestId: "c", idempotencyKey: "k", quoteId: "q", terms: "t", cancellation: "c" }));
    const routes = {
      ...discover,
      "GET /api/v1/me": () => json(200, { ...profile, displayName: who === "A" ? "Account A" : "Account B" }),
      "GET /api/v1/orders": () => json(200, { items: [orderOf(`${who}-1`)], nextCursor: null, hasMore: false }),
      "GET /api/v1/meals": () => refusedUntil(signIn)(),
    };
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api(routes)} />);
    await fireEvent.press(await screen.findByTestId("tab-orders"));
    expect(await screen.findByTestId("order-A-1")).toBeTruthy();
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    await waitFor(() => expect(screen.queryByText(t.stepUp.message)).toBeNull());
    // Navigation was reset to the first tab and A's order list was unmounted; B's data is never mixed with A's.
    await waitFor(() => expect(screen.queryByTestId("order-A-1")).toBeNull());
    expect(screen.queryByTestId("orders")).toBeNull();
    await fireEvent.press(await screen.findByTestId("tab-orders"));
    expect(await screen.findByTestId("order-B-1")).toBeTruthy();
    expect(screen.queryByTestId("order-A-1")).toBeNull();
    expect(mockStore.get("matbakhy.basket.v1")).toBeUndefined();
    expect(mockStore.get("matbakhy.pendingPlacement.v1")).toBeUndefined();
    expect(session.cachedKeys.filter((k) => k !== "me")).toEqual([]);
    expect(session.cache<{ displayName: string }>("me")?.displayName).toBe("Account B");
  });

  it("if the previous account's storage cannot be cleared during an account switch, the new session is ended instead of continuing on top of it", async () => {
    const session = await signedInSession();
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-b"), idToken: fakeJwt("customer-b"), refreshToken: "r3", expiresIn: 300 }));
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    const store = jest.requireMock("expo-secure-store") as { deleteItemAsync: jest.Mock };
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile), "GET /api/v1/meals": () => refusedUntil(signIn)() })} />);
    await fireEvent.press(await screen.findByTestId("tab-orders"));
    const failing = store.deleteItemAsync.getMockImplementation();
    store.deleteItemAsync.mockImplementation(async (k: string) => {
      if (k === "matbakhy.basket.v1") throw new Error("keychain unavailable");
      mockStore.delete(k);
    });
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    await waitFor(() => expect(session.accessToken).toBeNull());
    store.deleteItemAsync.mockImplementation(failing);
    expect(screen.queryByTestId("orders")).toBeNull();
    expect(screen.queryByText(t.stepUp.message)).toBeNull();
  });

  it("an answer for the previous person's profile that arrives after the switch is dropped", async () => {
    const session = await signedInSession();
    let release: (r: Response) => void = () => undefined;
    const slowA = new Promise<Response>((resolve) => (release = resolve));
    let calls = 0;
    const signIn: jest.Mock = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-b"), idToken: fakeJwt("customer-b"), refreshToken: "r3", expiresIn: 300 }));
    const fetchImpl = jest.fn(async (input: Request | string) => {
      const req = typeof input === "string" ? new Request(input) : input;
      const path = new URL(req.url).pathname;
      if (path === "/api/v1/me") {
        calls += 1;
        return calls === 1 ? slowA : json(200, { ...profile, displayName: "Account B" });
      }
      if (path === "/api/v1/meals") return refusedUntil(signIn)();
      if (path === "/api/v1/configuration/public") return json(200, {});
      throw new Error(`unrouted ${path}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    await waitFor(() => expect(calls).toBeGreaterThanOrEqual(2));
    await act(async () => release(json(200, { ...profile, displayName: "Account A" })));
    await waitFor(() => expect(session.cache<{ displayName: string }>("me")?.displayName).toBe("Account B"));
  });

  it("after a step-up for the same person the refused reads load again without a manual action", async () => {
    const session = await signedInSession();
    let verified = false;
    const signIn = jest.fn(async (_c: AppConfig, s: Session) => {
      verified = true;
      return s.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r2", expiresIn: 300 });
    });
    const meals = () => (verified ? discover["GET /api/v1/meals"]() : json(403, { code: "STEP_UP_REQUIRED" }));
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile), "GET /api/v1/meals": meals })} />);
    expect(screen.queryByTestId("meal-m1")).toBeNull();
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    expect(await screen.findByTestId("meal-m1")).toBeTruthy();
    expect(screen.queryByTestId("step-up-banner")).toBeNull();
  });

  it("keeps showing the request when the user cancels the verification, and never signs them out", async () => {
    const session = await signedInSession();
    const { SignInCancelled } = jest.requireMock("./auth") as { SignInCancelled: new () => Error };
    const signIn = jest.fn(async () => {
      throw new SignInCancelled();
    });
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile), "GET /api/v1/meals": () => json(403, { code: "STEP_UP_REQUIRED" }) })} />);
    await fireEvent.press(await screen.findByTestId("step-up-verify"));
    expect(await screen.findByText(t.stepUp.message)).toBeTruthy();
    expect(session.accessToken).not.toBeNull();
  });

  it("does not show the request for other 403 answers", async () => {
    const session = await signedInSession();
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(200, profile), "GET /api/v1/meals": () => json(403, { code: "FORBIDDEN" }) })} />);
    await screen.findByTestId("tab-discover");
    expect(screen.queryByTestId("step-up-banner")).toBeNull();
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

/** MB-033 deep links in the app's scheme: an order link opens the order for the signed-in account only. */
describe("deep links (MB-033)", () => {
  const order = {
    id: "o9", publicNumber: "MB-000900", version: 1, state: "PENDING_ACCEPTANCE", fulfillmentMode: "PICKUP",
    items: [{ mealId: "m1", title: "Dish m1", quantity: 1, unitPrice: money(12000), lineTotal: money(12000) }],
    subtotal: money(12000), deliveryFee: money(0), tax: money(0), total: money(12000), slot: slot("slot-k1"),
    acceptBy: "2026-10-13T09:00:00Z", events: [], allowedActions: [], cashState: "DUE", supportCaseIds: [], handoverCode: null, contact: null,
  };
  const routes = (orderResponse: () => Response) =>
    api({
      "GET /api/v1/me": () => json(200, profile),
      "GET /api/v1/meals": () => json(200, { items: [], nextCursor: null, hasMore: false }),
      "GET /api/v1/configuration/public": () => json(200, { orderingAvailable: true, orderLimits: null }),
      "GET /api/v1/orders/o9": orderResponse,
    }) as unknown as typeof fetch;
  async function signedIn() {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    return session;
  }
  afterEach(() => jest.restoreAllMocks());

  it("opens the linked order for the signed-in customer", async () => {
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://orders/o9");
    await render(<App config={config} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes(() => json(200, order))} />);
    expect(await screen.findByText("MB-000900")).toBeTruthy();
  });

  it("asks a signed-out visitor to sign in and requests nothing", async () => {
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://orders/o9");
    const fetchImpl = routes(() => json(200, order));
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    expect(await screen.findByTestId("order-sign-in")).toBeTruthy();
    const paths = (fetchImpl as unknown as jest.Mock).mock.calls.map((c) => new URL((c[0] as Request).url).pathname);
    expect(paths).not.toContain("/api/v1/orders/o9");
  });

  it("shows another account's order as not found, with none of its data", async () => {
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://orders/o9");
    await render(<App config={config} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes(() => json(404, { code: "NOT_FOUND" }))} />);
    expect(await screen.findByTestId("order")).toBeTruthy();
    await waitFor(() => expect(screen.queryByText("MB-000900")).toBeNull());
    expect(screen.queryByText(/Dish m1/)).toBeNull();
  });

  it("keeps the customer signed in when the first secure-storage read of the session fails", async () => {
    const SecureStore = jest.requireMock<{ getItemAsync: jest.Mock }>("expo-secure-store");
    const session = await signedIn();
    const original = SecureStore.getItemAsync.getMockImplementation();
    let failed = false;
    SecureStore.getItemAsync.mockImplementation(async (k: string) => {
      if (k.includes("session") && !failed) {
        failed = true;
        throw new Error("keystore not ready");
      }
      return mockStore.get(k) ?? null;
    });
    jest.spyOn(console, "warn").mockImplementation(() => undefined);
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://orders/o9");
    try {
      await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes(() => json(200, order))} />);
      expect(await screen.findByText("MB-000900")).toBeTruthy();
      expect(failed).toBe(true);
    } finally {
      SecureStore.getItemAsync.mockImplementation(original);
    }
  });

  it("never treats the sign-in callback as navigation", async () => {
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://auth/callback?code=x&state=y");
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes(() => json(200, order))} />);
    expect(await screen.findByTestId("tab-discover")).toBeTruthy();
    expect(screen.queryByTestId("order-sign-in")).toBeNull();
  });
});

/** MB-033 retry review: "Check and retry" pressed repeatedly while a check is still running sends one check at a time. */
describe("repeated Check and retry", () => {
  it("two presses in the same frame send one recovery check and at most one re-send with the same keys", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    const pendingKeys = { clientRequestId: "33333333-3333-4333-8333-333333333333", idempotencyKey: "app-k2", quoteId: "q", terms: "t", cancellation: "c" };
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: "m1", title: "Dish m1", quantity: 1 }] }));
    mockStore.set("matbakhy.pendingPlacement.v1", JSON.stringify(pendingKeys));
    let online = false;
    let created = false;
    const order = {
      id: "o2", publicNumber: "MB-000124", version: 1, state: "PENDING_ACCEPTANCE", fulfillmentMode: "PICKUP",
      items: [{ mealId: "m1", title: "Dish m1", quantity: 1, unitPrice: money(12000), lineTotal: money(12000) }],
      subtotal: money(12000), deliveryFee: money(0), tax: money(0), total: money(12000), slot: slot("slot-k1"),
      acceptBy: "2026-10-13T09:00:00Z", events: [], allowedActions: [], cashState: "DUE", supportCaseIds: [], handoverCode: null, contact: null,
    };
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    const fetchImpl = jest.fn(async (input: Request) => {
      const path = new URL(input.url).pathname;
      if (!online) {
        throw new TypeError("Network request failed");
      }
      if (path === `/api/v1/orders/by-request/${pendingKeys.clientRequestId}`) {
        await gate;
        return created ? json(200, order) : json(404, { code: "NOT_FOUND" });
      }
      if (input.method === "POST" && path === "/api/v1/orders") {
        created = true;
        return json(201, order);
      }
      if (path === "/api/v1/orders/o2") return json(200, order);
      if (path === "/api/v1/me") return json(200, profile);
      if (path === "/api/v1/meals") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (path === "/api/v1/configuration/public") return json(200, { orderingAvailable: true, orderLimits: null });
      throw new Error(`unrouted ${input.method} ${path}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
    expect(await screen.findByText(t.pendingOffline)).toBeTruthy();

    online = true;
    const before = (fetchImpl as jest.Mock).mock.calls.length;
    const retry = screen.getByTestId("retry-order");
    await act(async () => {
      fireEvent.press(retry);
      fireEvent.press(retry);
    });
    release();
    expect(await screen.findByText("MB-000124")).toBeTruthy();
    const calls = (fetchImpl as jest.Mock).mock.calls.slice(before).map((c) => `${(c[0] as Request).method} ${new URL((c[0] as Request).url).pathname}`);
    expect(calls.filter((c) => c === `GET /api/v1/orders/by-request/${pendingKeys.clientRequestId}`)).toHaveLength(1);
    const posts = (fetchImpl as jest.Mock).mock.calls.filter((c) => (c[0] as Request).method === "POST");
    expect(posts).toHaveLength(1);
    expect((posts[0][0] as Request).headers.get("Idempotency-Key")).toBe("app-k2");
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
    // The first read of the basket key fails (whatever else is read first, e.g. once deep links delay the first render).
    const original = SecureStore.getItemAsync.getMockImplementation();
    let failed = false;
    SecureStore.getItemAsync.mockImplementation(async (k: string) => {
      if (k === "matbakhy.basket.v1" && !failed) {
        failed = true;
        throw new Error("keystore not ready");
      }
      return mockStore.get(k) ?? null;
    });
    try {
      await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={offline} />);
      await fireEvent.press(await screen.findByTestId("open-cart"));
      expect(await screen.findByText(/1 × Dish m1/)).toBeTruthy();
      expect(failed).toBe(true);
      expect(warn).toHaveBeenCalled();
    } finally {
      SecureStore.getItemAsync.mockImplementation(original);
      warn.mockRestore();
    }
  });
});

/** Q1 option A on native: quantity controls follow the server's published per-line limit (API-001). */
describe("Q1 on native: quantity limits come from the server's active policy", () => {
  const limits = () => json(200, { orderingAvailable: true, orderLimits: { maxQuantityPerLine: 10, maxLinesPerOrder: 20, policyVersion: 7 } });
  const many = { ...meal("mq", "k1"), nextSlots: [{ ...slot("sq"), availablePortions: 15 }] };

  it("caps the meal quantity at the policy limit although the slot has more portions", async () => {
    const routes = { "GET /api/v1/configuration/public": limits, "GET /api/v1/meals": () => json(200, { items: [many], nextCursor: null, hasMore: false }), "GET /api/v1/meals/mq": () => json(200, many) };
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api(routes)} />);
    await fireEvent.press(await screen.findByTestId("meal-mq"));
    await fireEvent.press(await screen.findByTestId("slot-0", {}, { timeout: 5000 }));
    for (let i = 0; i < 20; i++) {
      await fireEvent.press(screen.getByLabelText(t.increase));
    }
    await fireEvent.press(screen.getByTestId("add-to-cart"));
    expect(await screen.findByText(t.added)).toBeTruthy();
    expect(JSON.parse(mockStore.get("matbakhy.basket.v1") ?? "{}").lines[0]).toEqual(expect.objectContaining({ quantity: 10, maxQuantity: 10 }));
  });

  it("explains a basket line above a lowered limit, and lets the customer reduce it", async () => {
    mockStore.set(
      "matbakhy.basket.v1",
      JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "sq", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: "mq", title: "Dish mq", quantity: 12, unitPriceMinor: 12000, maxQuantity: 20 }] }),
    );
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/configuration/public": limits })} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
    expect(await screen.findByText(t.overLineLimit(10))).toBeTruthy();
    expect(screen.getByTestId("qty-inc-mq").props.accessibilityState.disabled).toBe(true);
    await fireEvent.press(screen.getByTestId("qty-dec-mq"));
    await fireEvent.press(screen.getByTestId("qty-dec-mq"));
    await waitFor(() => expect(screen.queryByText(t.overLineLimit(10))).toBeNull());
  });

  it("re-reads the limit when the cart opens, so a policy lowered after launch applies before any quote", async () => {
    mockStore.set(
      "matbakhy.basket.v1",
      JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "sq", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"], lines: [{ mealId: "mq", title: "Dish mq", quantity: 8, unitPriceMinor: 12000, maxQuantity: 10 }] }),
    );
    let policy = 10;
    const configured = () => json(200, { orderingAvailable: true, orderLimits: { maxQuantityPerLine: policy, maxLinesPerOrder: 20, policyVersion: 7 } });
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/configuration/public": configured })} />);
    const cart = await screen.findByTestId("open-cart");
    policy = 5;
    await fireEvent.press(cart);
    expect(await screen.findByText(t.overLineLimit(5))).toBeTruthy();
  });
});

/**
 * Owner decision D13 (RQ-004) on native: a refused stale quote (409 PRICE_CHANGED) is re-quoted, the change is shown with
 * accurate attribution and the previous and new totals, and the new quote can be placed only after explicit acceptance.
 */
describe("D13 on native: changed prices need informed reconfirmation", () => {
  const prices: Record<string, number> = {};
  let fee = 0;
  let version = 0;
  let quoteFails = false;
  const quoteBody = () => {
    const items = [
      { mealId: "m1", mealRevision: version + 1, title: "Dish m1", quantity: 2, unitPrice: money(prices.m1 ?? 0), lineTotal: money(2 * (prices.m1 ?? 0)) },
      { mealId: "m2", mealRevision: version + 1, title: "Dish m2", quantity: 1, unitPrice: money(prices.m2 ?? 0), lineTotal: money(prices.m2 ?? 0) },
    ];
    const subtotal = items.reduce((s, i) => s + i.lineTotal.amountMinor, 0);
    return {
      quoteId: `q${version}`, expiresAt: "2026-10-13T10:05:00Z", kitchenId: "k1", items, subtotal: money(subtotal), deliveryFee: money(fee), tax: money(0),
      total: money(subtotal + fee), payment: cod(subtotal + fee), cancellationPolicyVersion: "c1", termsVersion: "t1", acceptanceDeadlinePreview: "2026-10-13T10:15:00Z", warnings: [],
    };
  };
  const placed = {
    id: "o9", publicNumber: "MB-000901", version: 1, state: "PENDING_ACCEPTANCE", fulfillmentMode: "PICKUP",
    items: [], subtotal: money(0), deliveryFee: money(0), tax: money(0), total: money(0), slot: slot("slot-k1"),
    acceptBy: "2026-10-13T09:00:00Z", events: [], allowedActions: [], cashState: "DUE", supportCaseIds: [], handoverCode: null, contact: null,
  };
  async function setup(modes: string[]) {
    prices.m1 = 12000;
    prices.m2 = 8000;
    fee = modes.includes("DELIVERY") ? 3000 : 0;
    version = 0;
    quoteFails = false;
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes,
      lines: [{ mealId: "m1", title: "Dish m1", quantity: 2, unitPriceMinor: 12000, maxQuantity: 5 }, { mealId: "m2", title: "Dish m2", quantity: 1, unitPriceMinor: 8000, maxQuantity: 5 }] }));
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    const orderPosts: { quoteId: string; clientRequestId: string }[] = [];
    const fetchImpl = jest.fn(async (input: Request) => {
      const path = new URL(input.url).pathname;
      const key = `${input.method} ${path}`;
      if (key === "GET /api/v1/me") return json(200, profile);
      if (key === "GET /api/v1/meals") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (key === "GET /api/v1/configuration/public") return json(200, { orderingAvailable: true, orderLimits: null });
      if (key === "GET /api/v1/me/addresses") return json(200, { items: [{ id: "a1", label: "Home", street: "Street", building: "1", zoneId: "z1", version: 0 }], nextCursor: null, hasMore: false });
      if (key === "GET /api/v1/zones") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (key === "POST /api/v1/quotes") return quoteFails ? json(503, { code: "SERVICE_UNAVAILABLE" }) : json(201, quoteBody());
      if (key === "POST /api/v1/orders") {
        const body = (await input.json()) as { quoteId: string; clientRequestId: string };
        orderPosts.push(body);
        return body.quoteId === `q${version}` ? json(201, placed) : json(409, { code: "PRICE_CHANGED", fieldErrors: [{ field: "quoteId", code: "MEAL_CHANGED" }] });
      }
      if (key === "GET /api/v1/orders/o9") return json(200, placed);
      throw new Error(`unrouted ${key}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
    return orderPosts;
  }
  const changePrices = (next: Record<string, number>, nextFee = fee) => {
    Object.assign(prices, next);
    fee = nextFee;
    version += 1;
  };
  const en = (minor: number) => new Intl.NumberFormat("en-EG", { style: "currency", currency: "EGP" }).format(minor / 100);
  const disabled = (id: string) => screen.getByTestId(id).props.accessibilityState?.disabled === true;

  it("shows the kitchen's increase and decrease and the old and new totals, and places only after explicit acceptance", async () => {
    const posts = await setup(["PICKUP"]);
    await fireEvent.press(await screen.findByTestId("get-quote"));
    await fireEvent.press(await screen.findByTestId("accept-terms"));
    changePrices({ m1: 13000, m2: 7500 });
    await fireEvent.press(screen.getByTestId("place-order"));
    expect(await screen.findByText(`The kitchen updated the price of “Dish m1” from ${en(12000)} to ${en(13000)}.`)).toBeTruthy();
    expect(screen.getByText(`The kitchen updated the price of “Dish m2” from ${en(8000)} to ${en(7500)}.`)).toBeTruthy();
    expect(screen.getByTestId("price-change-total").props.children).toBe(`The total was ${en(32000)} and is now ${en(33500)}. Review the new total before confirming your order.`);
    expect(screen.queryByText("The price changed. Calculate the total again.")).toBeNull();
    expect(posts).toHaveLength(1);
    await fireEvent.press(screen.getByTestId("accept-terms"));
    expect(disabled("place-order")).toBe(true);
    await fireEvent.press(screen.getByTestId("accept-new-total"));
    expect(disabled("place-order")).toBe(false);
    await fireEvent.press(screen.getByTestId("place-order"));
    expect(await screen.findByText("MB-000901")).toBeTruthy();
    expect(posts.map((p) => p.quoteId)).toEqual(["q0", "q1"]);
    expect(posts[0]?.clientRequestId).not.toBe(posts[1]?.clientRequestId);
  });

  it("attributes a delivery-fee-only change to the fee, and asks again after a refresh failure and a second change", async () => {
    const posts = await setup(["DELIVERY"]);
    await fireEvent.press(await screen.findByTestId("get-quote"));
    await fireEvent.press(await screen.findByTestId("accept-terms"));
    changePrices({}, 3125);
    await fireEvent.press(screen.getByTestId("place-order"));
    expect(await screen.findByText(`The delivery fee changed from ${en(3000)} to ${en(3125)}.`)).toBeTruthy();
    expect(screen.queryByText(/The kitchen updated/)).toBeNull();

    await fireEvent.press(screen.getByTestId("accept-terms"));
    await fireEvent.press(screen.getByTestId("accept-new-total"));
    changePrices({ m1: 14000 });
    quoteFails = true;
    await fireEvent.press(screen.getByTestId("place-order"));
    expect(await screen.findByText("We couldn't calculate the new total. Calculate the total again.")).toBeTruthy();
    expect(screen.queryByTestId("place-order")).toBeNull();
    expect(mockStore.has("matbakhy.basket.v1")).toBe(true);
    expect(posts).toHaveLength(2);
  });

  it("explains the change in Arabic", async () => {
    const localization = jest.requireMock<{ getLocales: () => { languageCode: string }[] }>("expo-localization");
    const original = localization.getLocales;
    localization.getLocales = () => [{ languageCode: "ar" }];
    try {
      await setup(["PICKUP"]);
      await fireEvent.press(await screen.findByTestId("get-quote"));
      await fireEvent.press(await screen.findByTestId("accept-terms"));
      changePrices({ m1: 13000 });
      await fireEvent.press(screen.getByTestId("place-order"));
      const ar = (minor: number) => new Intl.NumberFormat("ar-EG", { style: "currency", currency: "EGP" }).format(minor / 100);
      expect(await screen.findByText(`المطبخ حدّث سعر «Dish m1» من ${ar(12000)} إلى ${ar(13000)}.`)).toBeTruthy();
      expect(screen.getByTestId("accept-new-total")).toBeTruthy();
    } finally {
      localization.getLocales = original;
    }
  });
});

describe("MB-033 notification permission (provider-independent; push delivery is O07)", () => {
  const promptConfig: AppConfig = { ...config, notificationPrompt: true };
  const routes = () =>
    api({
      ...discover,
      "GET /api/v1/me": () => json(200, profile),
      "GET /api/v1/notifications": () => json(200, { items: [{ id: "n1", type: "ORDER_STATE", params: { state: "ACCEPTED", orderNumber: "MB-000901" }, orderId: null, createdAt: "2026-10-13T09:00:00Z", readAt: null }], nextCursor: null, hasMore: false }),
    });
  async function signedIn() {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    return session;
  }
  const fake = (initial: string, afterRequest: string) => ({
    get: jest.fn(async () => initial as never),
    request: jest.fn(async () => afterRequest as never),
    openSettings: jest.fn(async () => undefined),
  });

  it("never asks on launch; asks only when the customer presses Allow", async () => {
    const permissions = fake("undetermined", "granted");
    await render(<App config={promptConfig} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes()} permissions={permissions} />);
    await openAccount();
    expect(await screen.findByText(t.alertsAsk)).toBeTruthy();
    expect(permissions.request).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId("allow-alerts"));
    expect(await screen.findByText(t.alertsGranted)).toBeTruthy();
    expect(permissions.request).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId("allow-alerts")).toBeNull();
  });

  it("a refusal is safe: it explains, offers Settings and the in-app notifications still work", async () => {
    const permissions = fake("undetermined", "denied");
    await render(<App config={promptConfig} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes()} permissions={permissions} />);
    await openAccount();
    await fireEvent.press(await screen.findByTestId("allow-alerts"));
    expect(await screen.findByText(t.alertsDenied(brand.name.en))).toBeTruthy();
    expect(screen.queryByTestId("allow-alerts")).toBeNull();
    await fireEvent.press(screen.getByTestId("open-alert-settings"));
    expect(permissions.openSettings).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByRole("button", { name: t.notifications }));
    expect(await screen.findByText(/MB-000901/)).toBeTruthy();
  });

  it("an earlier refusal shows the Settings path without the system prompt", async () => {
    const permissions = fake("denied", "denied");
    await render(<App config={promptConfig} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes()} permissions={permissions} />);
    await openAccount();
    expect(await screen.findByText(t.alertsDenied(brand.name.en))).toBeTruthy();
    expect(screen.queryByTestId("allow-alerts")).toBeNull();
    expect(permissions.request).not.toHaveBeenCalled();
  });

  it("states what it cannot check, and builds without the prompt never touch the permission", async () => {
    const unavailable = fake("unavailable", "unavailable");
    const first = await render(<App config={promptConfig} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes()} permissions={unavailable} />);
    await openAccount();
    expect(await screen.findByText(t.alertsUnavailable)).toBeTruthy();
    first.unmount();

    const untouched = fake("undetermined", "granted");
    await render(<App config={config} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={routes()} permissions={untouched} />);
    await openAccount();
    expect(await screen.findByText(t.alertsInApp)).toBeTruthy();
    expect(untouched.get).not.toHaveBeenCalled();
    expect(untouched.request).not.toHaveBeenCalled();
  });

  it("maps the system statuses: a refusal the system will not ask again counts as denied", () => {
    const { stateOf } = jest.requireActual<typeof import("./notificationPermission")>("./notificationPermission");
    expect(stateOf({ status: "granted", granted: true, canAskAgain: true })).toBe("granted");
    expect(stateOf({ status: "undetermined", granted: false, canAskAgain: true })).toBe("undetermined");
    expect(stateOf({ status: "undetermined", granted: false, canAskAgain: false })).toBe("denied");
    // Android 13+ before the first request: "denied" but askable.
    expect(stateOf({ status: "denied", granted: false, canAskAgain: true })).toBe("undetermined");
    expect(stateOf({ status: "denied", granted: false, canAskAgain: true }, true)).toBe("denied");
    expect(stateOf({ status: "denied", granted: false, canAskAgain: false })).toBe("denied");
  });
});

describe("an expired access token is refreshed instead of signing the customer out (native run 36)", () => {
  it("refreshes before the first request and stays signed in with the new token", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a") + ".old", idToken: fakeJwt("customer-a"), refreshToken: "r1", expiresIn: 0 });
    const refresh = jest.fn(async () => ({ accessToken: fakeJwt("customer-a") + ".new", refreshToken: "r2", idToken: fakeJwt("customer-a"), expiresIn: 300 }));
    const fetchImpl = api({ ...discover, "GET /api/v1/me": () => json(200, profile) });
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} refresh={refresh} />);
    await openAccount();
    expect(await screen.findByText(t.signedInAs("Mona"))).toBeTruthy();
    const me = (fetchImpl as jest.Mock).mock.calls.map((c) => c[0] as Request).find((r) => r.url.endsWith("/api/v1/me"));
    expect(me?.headers.get("Authorization")).toBe(`Bearer ${fakeJwt("customer-a")}.new`);
    expect(refresh).toHaveBeenCalledWith(config, "r1");
  });

  it("ends the session cleanly when the provider refuses the refresh token", async () => {
    const { RefreshRejected } = jest.requireActual<typeof import("./session")>("./session");
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r1", expiresIn: 0 });
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", slotId: "s", slotStartsAt: "2026-10-13T10:00:00Z", modes: [], lines: [] }));
    const refresh = jest.fn(async () => {
      throw new RefreshRejected("invalid_grant");
    });
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={api({ ...discover, "GET /api/v1/me": () => json(401, {}) })} refresh={refresh} />);
    await openAccount();
    expect(await screen.findByRole("button", { name: t.signIn })).toBeTruthy();
    await waitFor(() => expect(mockStore.size).toBe(0));
    expect(session.accessToken).toBeNull();
  });
});

/** MB-053 (D16, TC-163) on native: the same deposit choice, terms and honest status as the web. */
describe("MB-053 on native: direct deposit checkout and payment status", () => {
  const recipient = { method: "INSTAPAY", identifier: "kitchen@instapay", name: "Kitchen K1" };
  const en = (minor: number) => new Intl.NumberFormat("en-EG", { style: "currency", currency: "EGP" }).format(minor / 100);
  async function signedIn() {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    return session;
  }

  it("quotes a deposit with the recipient and the direct-payment note, and explains when cash is not eligible", async () => {
    mockStore.set("matbakhy.basket.v1", JSON.stringify({ kitchenId: "k1", kitchenName: "Kitchen k1", slotId: "slot-k1", slotStartsAt: "2026-10-13T10:00:00Z", modes: ["PICKUP"],
      lines: [{ mealId: "m1", title: "Dish m1", quantity: 2, unitPriceMinor: 12000, maxQuantity: 5 }] }));
    const quoteBodies: { paymentMethod?: string }[] = [];
    const fetchImpl = jest.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      if (key === "GET /api/v1/me") return json(200, profile);
      if (key === "GET /api/v1/meals") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (key === "GET /api/v1/configuration/public") return json(200, { orderingAvailable: true, orderLimits: null });
      if (key === "GET /api/v1/me/addresses") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (key === "POST /api/v1/quotes") {
        const body = (await input.json()) as { paymentMethod?: string };
        quoteBodies.push(body);
        if (body.paymentMethod === "COD") return json(422, { code: "VALIDATION_FAILED", fieldErrors: [{ field: "paymentMethod", code: "COD_NOT_ELIGIBLE" }] });
        return json(201, { quoteId: "q1", expiresAt: "2026-10-13T10:05:00Z", kitchenId: "k1", items: [], subtotal: money(24000), deliveryFee: money(0), tax: money(0), total: money(24000),
          payment: { method: "DEPOSIT", depositBps: 5000, deposit: money(12000), remainder: money(12000), recipient },
          cancellationPolicyVersion: "c1", termsVersion: "t1", acceptanceDeadlinePreview: "2026-10-13T10:15:00Z", warnings: [] });
      }
      throw new Error(`unrouted ${key}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("open-cart"));
    await fireEvent.press(await screen.findByTestId("get-quote"));
    expect(await screen.findByText(t.errors.COD_NOT_ELIGIBLE ?? "missing")).toBeTruthy();
    await fireEvent.press(screen.getByTestId("pay-DEPOSIT"));
    expect(screen.queryByText(t.errors.COD_NOT_ELIGIBLE ?? "missing")).toBeNull();
    await fireEvent.press(screen.getByTestId("get-quote"));
    await screen.findByTestId("deposit-terms");
    expect(screen.getByText(`Deposit now: ${en(12000)}`)).toBeTruthy();
    expect(screen.getByText(`Rest on receipt: ${en(12000)}`)).toBeTruthy();
    expect(screen.getByText(/kitchen@instapay/)).toBeTruthy();
    expect(screen.getByText(t.pay.directNote)).toBeTruthy();
    expect(quoteBodies.map((b) => b.paymentMethod)).toEqual(["COD", "DEPOSIT"]);
  });

  it("lets the customer say the deposit was sent, then shows that the kitchen still has to confirm", async () => {
    let state = "WAITING_FOR_DEPOSIT";
    let reported: unknown = null;
    const view = () => ({
      id: "o9", publicNumber: "MB-000902", version: 1, state: "PENDING_ACCEPTANCE", fulfillmentMode: "PICKUP", items: [], subtotal: money(24000), deliveryFee: money(0), tax: money(0),
      total: money(24000), slot: slot("slot-k1"), acceptBy: "2026-10-13T09:00:00Z", events: [], allowedActions: [], cashState: "NOT_DUE", supportCaseIds: [], handoverCode: null, contact: null,
      payment: { method: "DEPOSIT", state, deposit: money(12000), remainder: money(12000), recipient, depositExpiresAt: "2026-10-13T09:00:00Z", version: 0 },
    });
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue("matbakhy-dev://orders/o9");
    const fetchImpl = jest.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      if (key === "GET /api/v1/me") return json(200, profile);
      if (key === "GET /api/v1/meals") return json(200, { items: [], nextCursor: null, hasMore: false });
      if (key === "GET /api/v1/configuration/public") return json(200, { orderingAvailable: true, orderLimits: null });
      if (key === "GET /api/v1/orders/o9") return json(200, view());
      if (key === "POST /api/v1/orders/o9/payment/transfer-report") {
        reported = await input.json();
        state = "CUSTOMER_TRANSFER_REPORTED";
        return json(200, view().payment);
      }
      throw new Error(`unrouted ${key}`);
    }) as unknown as typeof fetch;
    try {
      await render(<App config={config} session={await signedIn()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
      expect(await screen.findByText(`${t.pay.title}: ${t.pay.states.WAITING_FOR_DEPOSIT}`)).toBeTruthy();
      await fireEvent.changeText(screen.getByTestId("transfer-reference"), " IP-123 ");
      await fireEvent.press(screen.getByTestId("report-transfer"));
      expect(await screen.findByText(`${t.pay.title}: ${t.pay.states.CUSTOMER_TRANSFER_REPORTED}`)).toBeTruthy();
      expect(screen.getByText(t.pay.explain.CUSTOMER_TRANSFER_REPORTED ?? "missing")).toBeTruthy();
      expect(screen.queryByTestId("report-transfer")).toBeNull();
      expect(reported).toEqual({ reference: "IP-123" });
    } finally {
      jest.restoreAllMocks();
    }
  });
});

/** MB-054 (D17, TC-164) on native: browsing activity goes in one batch with a per-process visitor id, without signing in. */
describe("MB-054 on native: organic activity", () => {
  afterEach(() => jest.restoreAllMocks());

  it("sends the impressions, the meal view and the add to cart, and never blocks the screen on a failure", async () => {
    // Start on Discover regardless of a link another test opened.
    jest.spyOn(Linking, "getInitialURL").mockResolvedValue(null);
    const sent: { visitorId: string; events: { kind: string; subjectId: string }[] }[] = [];
    const fetchImpl = jest.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      if (key === "GET /api/v1/meals") return json(200, { items: [meal("m1", "k1"), meal("m2", "k2")], nextCursor: null, hasMore: false });
      if (key === "GET /api/v1/meals/m1") return json(200, meal("m1", "k1"));
      if (key === "GET /api/v1/configuration/public") return json(200, { orderingAvailable: true, orderLimits: null });
      if (key === "POST /api/v1/activity") {
        sent.push((await input.json()) as (typeof sent)[number]);
        return json(503, { code: "SERVICE_UNAVAILABLE" });
      }
      throw new Error(`unrouted ${key}`);
    }) as unknown as typeof fetch;
    await render(<App config={config} session={new Session()} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    await fireEvent.press(await screen.findByTestId("meal-m1"));
    await fireEvent.press(await screen.findByTestId("slot-0", {}, { timeout: 5000 }));
    await fireEvent.press(screen.getByTestId("add-to-cart"));
    expect(await screen.findByText(t.added)).toBeTruthy();
    await waitFor(() => expect(sent.flatMap((b) => b.events.map((e) => `${e.kind}:${e.subjectId}`))).toEqual(
      expect.arrayContaining(["MEAL_IMPRESSION:m1", "MEAL_IMPRESSION:m2", "MEAL_VIEW:m1", "ADD_TO_CART:m1"])), { timeout: 4000 });
    expect(new Set(sent.map((b) => b.visitorId)).size).toBe(1);
    expect(sent[0]?.visitorId).toMatch(/^app-[0-9a-f-]{36}$/);
    expect([...mockStore.keys()].some((k) => k.includes("visitor"))).toBe(false);
  });
});
