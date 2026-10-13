import { brand } from "@matbakhy/design-tokens";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { App, title } from "./App";

afterEach(() => {
  cleanup();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

type Handler = (req: Request, body: unknown) => { status: number; body?: unknown };

/** Fake API at the fetch boundary; records every request. Component tests only: real-API behaviour is the browser test. */
function server(routes: Record<string, Handler>) {
  const calls: { method: string; path: string; headers: Headers; body: unknown }[] = [];
  const fetchImpl = (async (input: Request) => {
    const url = new URL(input.url);
    const text = input.method === "GET" ? "" : await input.text();
    const body = text ? JSON.parse(text) : undefined;
    calls.push({ method: input.method, path: url.pathname + url.search, headers: input.headers, body });
    const key = `${input.method} ${decodeURIComponent(url.pathname)}`;
    const handler = routes[key];
    const r = handler ? handler(input, body) : { status: 403, body: { code: "FORBIDDEN" } };
    return new Response(r.body === undefined ? null : JSON.stringify(r.body), {
      status: r.status,
      headers: { "Content-Type": "application/json" },
    });
  }) as unknown as typeof fetch;
  return { fetchImpl, calls };
}

const egp = (amountMinor: number) => ({ amountMinor, currency: "EGP" });
const kitchen = {
  kitchenId: "k1",
  publicSlug: "umm-ali",
  displayName: "مطبخ أم علي",
  description: "أكل بيتي",
  operatingState: "ACTIVE",
  pausedBy: null as string | null,
  pickupEnabled: true,
  zones: [{ zoneId: "z1", nameAr: "المعادي", nameEn: "Maadi", deliveryEnabled: true }],
  version: 3,
};
const SERVER_NOW = "2026-10-11T10:00:00Z";

function dashboard(over: Partial<typeof kitchen> = {}) {
  return {
    serverTime: SERVER_NOW,
    kitchen: { ...kitchen, ...over },
    activeCounts: { PENDING_ACCEPTANCE: 1, ACCEPTED: 0, PREPARING: 0, READY: 0, OUT_FOR_DELIVERY: 0 },
    pendingAcceptance: [
      { orderId: "o1", publicNumber: "MB-1001", acceptBy: "2026-10-11T10:25:00Z", fulfillmentMode: "PICKUP", total: egp(24000), version: 0 },
    ],
    upcomingSlots: [],
  };
}

function order(state: string, allowedActions: string[], version = 0) {
  return {
    id: "o1",
    publicNumber: "MB-1001",
    version,
    state,
    fulfillmentMode: "PICKUP",
    items: [{ mealId: "m1", title: "كشري", allergenStatement: "NONE_DECLARED", quantity: 2, unitPrice: egp(12000), lineTotal: egp(24000) }],
    subtotal: egp(24000),
    deliveryFee: egp(0),
    tax: egp(0),
    total: egp(24000),
    slot: { slotId: "s1", startsAt: "2026-10-11T13:00:00Z", endsAt: "2026-10-11T15:00:00Z", cutoffAt: "2026-10-11T11:00:00Z" },
    acceptBy: "2026-10-11T10:25:00Z",
    events: [],
    allowedActions,
    cashState: "DUE",
    fulfillmentNote: null,
    dispatchReference: null,
    contact: null,
  };
}

const me = () => ({ status: 200, body: { id: "u1", displayName: "Cook", locale: "ar", emailVerified: true, phoneVerified: true, version: 0, capabilities: [] } });

describe("operations-web cook workspace", () => {
  it("starts in Arabic right-to-left and switches to English", async () => {
    const { fetchImpl } = server({ "GET /api/v1/me": () => ({ status: 401, body: { code: "UNAUTHENTICATED" } }) });
    render(<App fetchImpl={fetchImpl} />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title("ar"));
    expect(title("ar")).toContain(brand.name.ar);
    expect(document.documentElement.dir).toBe("rtl");
    expect(document.documentElement.lang).toBe("ar");
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByRole("heading", { level: 1 }).textContent).toBe(title("en"));
    expect(document.documentElement.dir).toBe("ltr");
  });

  it("offers sign-in through the API with a return address when signed out", async () => {
    const { fetchImpl } = server({ "GET /api/v1/me": () => ({ status: 401, body: { code: "UNAUTHENTICATED" } }) });
    render(<App fetchImpl={fetchImpl} />);
    const link = await screen.findByRole("link", { name: "تسجيل الدخول" });
    expect(link.getAttribute("href")).toMatch(/\/oauth2\/authorization\/keycloak\?returnTo=http/);
  });

  it("shows the application state when the account has no approved kitchen", async () => {
    const { fetchImpl } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/cook/application": () => ({ status: 200, body: { id: "a1", state: "UNDER_REVIEW", draft: {}, currentRevision: 1, history: [], version: 1 } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByText("لا يوجد مطبخ معتمد لهذا الحساب بعد.")).toBeTruthy();
    expect(await screen.findByText("UNDER_REVIEW")).toBeTruthy();
    expect(screen.queryByRole("navigation")).toBeNull();
  });

  it("renders server counts and deadlines measured against server time", async () => {
    const { fetchImpl } = server({ "GET /api/v1/me": me, "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }) });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("heading", { name: "مطبخ أم علي" })).toBeTruthy();
    // 25 minutes between serverTime and acceptBy regardless of the device clock.
    expect(await screen.findByText(/باقي (24|25) دقيقة/)).toBeTruthy();
    expect(screen.getByText("MB-1001")).toBeTruthy();
  });

  it("sends order commands with the version, an idempotency key and the CSRF token, then refreshes from the server", async () => {
    document.cookie = "XSRF-TOKEN=csrf-123";
    let current = order("PENDING_ACCEPTANCE", ["ACCEPT", "REJECT"]);
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/orders": (req) => {
        const state = new URL(req.url).searchParams.get("state");
        return { status: 200, body: { items: state === current.state ? [current] : [], nextCursor: null, hasMore: false } };
      },
      "POST /api/v1/cook/orders/o1/accept": () => {
        current = order("ACCEPTED", ["PREPARE", "CANCEL"], 1);
        return { status: 200, body: current };
      },
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الطلبات" }));
    const card = await screen.findByRole("article", { name: "MB-1001" });
    fireEvent.click(within(card).getByRole("button", { name: "قبول" }));
    expect(await screen.findByRole("button", { name: "بدء التحضير" })).toBeTruthy();
    const accept = calls.find((c) => c.method === "POST");
    expect(accept?.path).toBe("/api/v1/cook/orders/o1/accept");
    expect(accept?.body).toEqual({ expectedVersion: 0 });
    expect(accept?.headers.get("Idempotency-Key")).toMatch(/^ops-/);
    expect(accept?.headers.get("X-XSRF-TOKEN")).toBe("csrf-123");
    expect(accept?.headers.get("Accept-Language")).toBe("ar");
  });

  // Follow-up to customer-web QA finding MBQA-001 (same request-language pattern). Against the earlier code the
  // open Orders section reloaded in the switching render and still sent the previous language (red log kept).
  it("requests made after a language switch carry the new language in both directions", async () => {
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/orders": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("heading", { name: "مطبخ أم علي" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(await screen.findByRole("button", { name: "Orders" }));
    await waitFor(() => expect(calls.some((c) => c.path.startsWith("/api/v1/cook/orders"))).toBe(true));
    expect(calls.filter((c) => c.path.startsWith("/api/v1/cook/orders")).every((c) => c.headers.get("Accept-Language") === "en")).toBe(true);
    const before = calls.length;
    fireEvent.click(screen.getByRole("button", { name: "العربية" }));
    fireEvent.click(await screen.findByRole("button", { name: "الرئيسية" }));
    await waitFor(() => expect(calls.length).toBeGreaterThan(before));
    expect(calls.slice(before).map((c) => `${c.path} ${c.headers.get("Accept-Language")}`)).toEqual(calls.slice(before).map((c) => `${c.path} ar`));
  });

  it("shows the server's conflict and reloads instead of assuming the transition", async () => {
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/orders": (req) => ({
        status: 200,
        body: { items: new URL(req.url).searchParams.get("state") === "PENDING_ACCEPTANCE" ? [order("PENDING_ACCEPTANCE", ["ACCEPT", "REJECT"])] : [], nextCursor: null, hasMore: false },
      }),
      "POST /api/v1/cook/orders/o1/reject": () => ({ status: 409, body: { code: "VERSION_CONFLICT" } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الطلبات" }));
    const card = await screen.findByRole("article", { name: "MB-1001" });
    fireEvent.change(within(card).getByRole("combobox"), { target: { value: "INGREDIENT_UNAVAILABLE" } });
    fireEvent.click(within(card).getByRole("button", { name: "رفض" }));
    expect(await screen.findByText("تغيّرت البيانات. تم التحديث، حاول مرة أخرى.")).toBeTruthy();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ expectedVersion: 0, reasonCode: "INGREDIENT_UNAVAILABLE" });
    await waitFor(() => expect(calls.filter((c) => c.path.startsWith("/api/v1/cook/orders?")).length).toBeGreaterThan(5));
  });

  it("requires a six-digit handover code before confirming", async () => {
    const ready = { ...order("READY", ["HANDOVER"]), contact: { name: "Mona", phone: "+201001234567", deliveryAddress: null } };
    const { fetchImpl } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/orders": (req) => ({ status: 200, body: { items: new URL(req.url).searchParams.get("state") === "READY" ? [ready] : [], nextCursor: null, hasMore: false } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الطلبات" }));
    const card = await screen.findByRole("article", { name: "MB-1001" });
    const confirm = within(card).getByRole("button", { name: "تأكيد التسليم" }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(within(card).getByLabelText("رمز التسليم من العميل"), { target: { value: "12a34" } });
    expect(confirm.disabled).toBe(true);
    fireEvent.change(within(card).getByLabelText("رمز التسليم من العميل"), { target: { value: "123456" } });
    expect(confirm.disabled).toBe(false);
    expect(within(card).getByText("+201001234567")).toBeTruthy();
  });

  it("offers only the slot transitions the server allows: no Close for a draft (MBQA-005)", async () => {
    const slot = (id: string, state: string, hour: number) => ({
      id, kitchenId: "k1", state, startsAt: `2026-10-14T${hour}:00:00Z`, endsAt: `2026-10-14T${hour + 1}:00:00Z`, cutoffAt: `2026-10-14T${hour - 2}:00:00Z`,
      leadMinutes: 120, maxPortions: 8, reserved: 0, allocated: 0, consumed: 0, meals: [], version: 2,
    });
    let state = "OPEN";
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/meals": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "GET /api/v1/cook/slots": () => ({
        status: 200,
        body: { items: [slot("d", "DRAFT", 10), slot("o", state, 12), slot("p", "PAUSED", 14), slot("c", "CLOSED", 16)], nextCursor: null, hasMore: false },
      }),
      // Another device closed the open slot meanwhile: the server refuses and the card follows the server's state.
      "PATCH /api/v1/cook/slots/o": () => {
        state = "CLOSED";
        return { status: 409, body: { code: "STATE_CONFLICT" } };
      },
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "المواعيد" }));
    const cards = await screen.findAllByRole("article");
    const cardAt = (i: number) => cards[i] as HTMLElement;
    const actions = (i: number) => within(cardAt(i)).queryAllByRole("button").map((b) => b.textContent);
    expect(cards).toHaveLength(4);
    expect(actions(0)).toEqual(["فتح"]);
    expect(actions(1)).toEqual(["إيقاف مؤقت", "إغلاق"]);
    expect(actions(2)).toEqual(["فتح", "إغلاق"]);
    expect(actions(3)).toEqual([]);

    fireEvent.click(within(cardAt(1)).getByRole("button", { name: "إغلاق" }));
    expect(await screen.findByText("لم يعد هذا الإجراء متاحًا. تم التحديث.")).toBeTruthy();
    expect(calls.find((c) => c.method === "PATCH")?.body).toEqual({ state: "CLOSED", expectedVersion: 2 });
    await waitFor(() => expect(within(screen.getAllByRole("article")[1] as HTMLElement).queryAllByRole("button")).toHaveLength(0));
  });

  it("shows a meal's active price and previous changes in Cairo time and changes the price with the meal version (MB-048)", async () => {
    let price = 11500;
    let version = 7;
    const meal = () => ({
      id: "m1", kitchenId: "k1", status: "PUBLISHED", revision: 4, publishedAt: "2026-10-01T08:00:00Z", archivedAt: null, updatedAt: "2026-10-03T08:00:00Z",
      version, moderationState: "VISIBLE", moderationReason: null,
      content: { titleAr: "كشري", titleEn: "Koshari", priceMinor: price, currency: "EGP", allergenCodes: [], photos: [] },
    });
    const history = () => ({
      mealId: "m1",
      activePrice: egp(price),
      changes: [
        ...(price !== 11500 ? [{ price: egp(price), changedAt: "2026-10-13T09:00:00Z", revision: 5 }] : []),
        { price: egp(11500), changedAt: "2026-10-03T08:00:00Z", revision: 4 },
        { price: egp(13000), changedAt: "2026-10-02T08:00:00Z", revision: 3 },
        { price: egp(12000), changedAt: "2026-10-01T08:00:00Z", revision: 1 },
      ],
    });
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/meals": () => ({ status: 200, body: { items: [meal()], nextCursor: null, hasMore: false } }),
      "GET /api/v1/cook/meals/m1/price-history": () => ({ status: 200, body: history() }),
      "PATCH /api/v1/cook/meals/m1": (_req, body) => {
        price = (body as { priceMinor: number }).priceMinor;
        version += 1;
        return { status: 200, body: meal() };
      },
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الأطباق" }));
    const card = await screen.findByRole("article", { name: "كشري" });
    const toggle = within(card).getByRole("button", { name: "سجل الأسعار" });
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    fireEvent.click(toggle);
    const section = await within(card).findByRole("region", { name: "سجل الأسعار" });
    expect(await within(section).findByText(/السعر الحالي/)).toBeTruthy();
    const egpAr = (minor: number) => new Intl.NumberFormat("ar-EG", { style: "currency", currency: "EGP" }).format(minor / 100);
    expect(within(section).getByText(/السعر الحالي/).parentElement?.textContent).toContain(egpAr(11500));
    const previous = within(within(section).getByRole("list", { name: "الأسعار السابقة" })).getAllByRole("listitem").map((li) => li.textContent ?? "");
    expect(previous).toHaveLength(2);
    expect(previous[0]).toContain(egpAr(13000));
    expect(previous[1]).toContain(egpAr(12000));
    const cairo = new Intl.DateTimeFormat("ar-EG", { timeZone: "Africa/Cairo", dateStyle: "medium", timeStyle: "short" }).format(new Date("2026-10-02T08:00:00Z"));
    expect(previous[0]).toContain(cairo);
    expect(within(section).getByText("السعر الجديد يسري على الطلبات التي لم تُرسل بعد؛ الطلبات القائمة تحتفظ بالأسعار المتفق عليها.")).toBeTruthy();

    fireEvent.change(within(section).getByLabelText(/السعر الجديد/), { target: { value: "125.50" } });
    fireEvent.click(within(section).getByRole("button", { name: "تغيير السعر" }));
    await waitFor(() => expect(calls.find((c) => c.method === "PATCH")?.body).toEqual({ expectedVersion: 7, priceMinor: 12550 }));
    await waitFor(() => expect(within(section).getByText(/السعر الحالي/).parentElement?.textContent).toContain(egpAr(12550)));
    expect(within(within(section).getByRole("list", { name: "الأسعار السابقة" })).getAllByRole("listitem")).toHaveLength(3);
  });

  it("offers resume only for a cook pause and explains a staff pause", async () => {
    const { fetchImpl } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard({ operatingState: "PAUSED", pausedBy: "STAFF" }) }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الإعدادات" }));
    expect(await screen.findByText("أوقفته الإدارة مؤقتًا؛ لا يمكنك استئنافه.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "استئناف البيع" })).toBeNull();
  });

  it("explains that collected cash is the cook's and shows the commission still owed", async () => {
    const { fetchImpl } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/statements": () => ({
        status: 200,
        body: {
          from: "2026-10-01",
          to: "2026-10-11",
          includes: ["COMPLETED_ORDERS", "CASH_POSITION", "COMMISSION"],
          kitchenId: "k1",
          direction: "COOK_PAYS_PLATFORM",
          refunds: [],
          settlements: [],
          unsettledEntries: [],
          summary: {
            completedOrders: 1,
            ordersTotal: egp(24000),
            cashReported: egp(0),
            cashVerified: egp(0),
            cashOutstanding: egp(24000),
            commissionPosted: egp(2400),
            commissionPending: 0,
            commissionReversed: egp(0),
            commissionSettled: egp(0),
            commissionOutstanding: egp(2400),
          },
          items: [{ orderId: "o1", publicNumber: "MB-1001", completedAt: "2026-10-11T09:00:00Z", orderTotal: egp(24000), cashState: "DUE", commission: egp(2400), commissionState: "POSTED" }],
          nextCursor: null,
          hasMore: false,
        },
      }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الكشف" }));
    expect(await screen.findByText(/المستحق للمنصة هو العمولة فقط/)).toBeTruthy();
    expect(screen.getByText("عمولة مستحقة الدفع للمنصة")).toBeTruthy();
    expect(screen.getByRole("cell", { name: "MB-1001" })).toBeTruthy();
  });

  it("MB-050 (D10): with no commission the cook statement says so and shows no commission rows or column", async () => {
    const zero = egp(0);
    const { fetchImpl } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/statements": () => ({
        status: 200,
        body: {
          from: "2026-10-01", to: "2026-10-13", includes: ["COMPLETED_ORDERS", "CASH_POSITION", "COMMISSION"], kitchenId: "k1",
          direction: "COOK_PAYS_PLATFORM", refunds: [], settlements: [], unsettledEntries: [],
          summary: { completedOrders: 1, ordersTotal: egp(27000), cashReported: zero, cashVerified: zero, cashOutstanding: egp(27000),
            commissionPosted: zero, commissionPending: 0, commissionReversed: zero, commissionSettled: zero, commissionOutstanding: zero },
          items: [{ orderId: "o1", publicNumber: "MB-1002", completedAt: "2026-10-13T09:00:00Z", orderTotal: egp(27000), cashState: "DUE", commission: zero, commissionState: "POSTED" }],
          nextCursor: null, hasMore: false,
        },
      }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الكشف" }));
    expect(await screen.findByText("لا توجد عمولة للمنصة: كل مبلغ الطلب لك.")).toBeTruthy();
    expect(screen.queryByText(/عمولة/, { selector: "dt" })).toBeNull();
    expect(screen.queryByRole("columnheader", { name: "العمولة" })).toBeNull();
    expect(screen.queryByText(/المستحق للمنصة هو العمولة فقط/)).toBeNull();
    expect(screen.getByRole("cell", { name: "MB-1002" })).toBeTruthy();
  });

  it("MB-052 (D16): the cook sees the deposit and reports receipt or a problem; it is never called a bank confirmation", async () => {
    const pay = (state: string, version = 1) => ({
      method: "DEPOSIT", state, deposit: egp(13500), remainder: egp(13500), version, depositExpiresAt: "2026-10-11T10:25:00Z",
      recipient: { method: "INSTAPAY", identifier: "kitchen@instapay", name: "Synthetic Kitchen" },
    });
    const current = { ...order("ACCEPTED", ["PREPARE", "CANCEL"], 2), payment: pay("CUSTOMER_TRANSFER_REPORTED") };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 200, body: dashboard() }),
      "GET /api/v1/cook/orders": (req) => ({
        status: 200,
        body: { items: new URL(req.url).searchParams.get("state") === "ACCEPTED" ? [current] : [], nextCursor: null, hasMore: false },
      }),
      "POST /api/v1/cook/orders/o1/payment/receipt": () => ({ status: 200, body: pay("KITCHEN_RECEIPT_REPORTED", 2) }),
      "POST /api/v1/cook/orders/o1/payment/problem": () => ({ status: 200, body: pay("DISPUTED", 2) }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.click(await screen.findByRole("button", { name: "الطلبات" }));
    const card = await screen.findByRole("article", { name: "MB-1001" });
    expect(within(card).getByText("العميل أبلغ بالتحويل")).toBeTruthy();
    expect(within(card).getByText("kitchen@instapay")).toBeTruthy();
    expect(within(card).getByText(/ليس تأكيدًا من البنك/)).toBeTruthy();
    fireEvent.click(within(card).getByRole("button", { name: /وصلني العربون/ }));
    await waitFor(() => expect(calls.some((c) => c.path.endsWith("/payment/receipt"))).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/payment/receipt"))?.body).toEqual({ expectedVersion: 1, amountMinor: 13500 });
    fireEvent.click(within(card).getByRole("button", { name: "لم يصلني" }));
    await waitFor(() => expect(calls.some((c) => c.path.endsWith("/payment/problem"))).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/payment/problem"))?.body).toEqual({ expectedVersion: 1, kind: "NOT_RECEIVED" });
    expect(card.textContent).not.toMatch(/verified|مؤكد من البنك/i);
  });

  it("MB-052 (D16): finance staff approve an account and decide an ended order's deposit, with no way to mark money received", async () => {
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/finance/statements": () => ({ status: 404, body: { code: "NOT_FOUND" } }),
      "GET /api/v1/ops/payment-recipients": () => ({
        status: 200,
        body: { items: [{ kitchenId: "k9", kitchenName: "مطبخ تجريبي", recipient: { method: "INSTAPAY", identifier: "k9@instapay", name: "Synthetic" }, version: 3 }] },
      }),
      "GET /api/v1/ops/payments": () => ({
        status: 200,
        body: { items: [{ orderId: "o7", publicNumber: "MB-1007", kitchenId: "k9", orderState: "REJECTED", state: "DISPUTED", deposit: egp(13500), lastReason: "NOT_RECEIVED", updatedAt: SERVER_NOW, version: 4 }] },
      }),
      "POST /api/v1/ops/kitchens/k9/payment-recipient/approval": () => ({ status: 200, body: {} }),
      "POST /api/v1/ops/orders/o7/payment/decision": () => ({ status: 200, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByText("k9@instapay")).toBeTruthy();
    const reasons = await screen.findAllByLabelText("السبب");
    fireEvent.change(reasons[0] as HTMLElement, { target: { value: "name matches the approved cook" } });
    fireEvent.click(screen.getByRole("button", { name: "موافقة على الحساب" }));
    await waitFor(() => expect(calls.some((c) => c.path.endsWith("/payment-recipient/approval"))).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/payment-recipient/approval"))?.body).toEqual({ expectedVersion: 3, reason: "name matches the approved cook" });

    expect(await screen.findByText(/MB-1007/)).toBeTruthy();
    const outcome = screen.getByLabelText("القرار") as HTMLSelectElement;
    expect([...outcome.options].map((o) => o.value)).toEqual(["REFUND_OWED", "CLOSED_NO_FUNDS"]);
    fireEvent.change(screen.getAllByLabelText("السبب")[1] as HTMLElement, { target: { value: "kitchen rejected after the transfer" } });
    fireEvent.click(screen.getByRole("button", { name: "تسجيل القرار" }));
    await waitFor(() => expect(calls.some((c) => c.path.endsWith("/payment/decision"))).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/payment/decision"))?.body).toEqual({ expectedVersion: 4, outcome: "REFUND_OWED", reason: "kitchen rejected after the transfer" });
  });

  it("gives a reviewer the application queue and asks for MFA step-up when a decision needs it", async () => {
    const review = {
      id: "a1",
      applicantId: "u2",
      state: "UNDER_REVIEW",
      currentRevision: 1,
      revisions: [
        {
          revision: 1,
          publicName: "مطبخ سلمى",
          bio: "أكل بيتي",
          contactPhone: "+201001234567",
          privateLocation: "Synthetic location",
          pickupEnabled: true,
          zoneIds: ["z1"],
          documentIds: ["d1"],
          submittedAt: SERVER_NOW,
          decision: null,
          decisionReason: null,
          reviewerId: null,
          decidedAt: null,
        },
      ],
      kitchenId: null,
      version: 2,
      kitchen: null,
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/catalogue": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/cooks": () => ({
        status: 200,
        body: { items: [{ id: "a1", state: "UNDER_REVIEW", publicName: "مطبخ سلمى", currentRevision: 1, submittedAt: SERVER_NOW, updatedAt: SERVER_NOW, version: 2 }], nextCursor: null, hasMore: false },
      }),
      "GET /api/v1/ops/cooks/a1": () => ({ status: 200, body: review }),
      "GET /api/v1/uploads/d1": () => ({ status: 200, body: { id: "d1", state: "APPROVED", privateReadUrl: "http://localhost:9090/private/d1?sig=1" } }),
      "POST /api/v1/ops/cooks/a1/decision": () => ({ status: 403, body: { code: "STEP_UP_REQUIRED" } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByRole("button", { name: "طلبات الطهاة" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "الطلبات" })).toBeNull();
    fireEvent.click(await screen.findByRole("button", { name: "قيد المراجعة" }));
    fireEvent.click(await screen.findByRole("button", { name: "مطبخ سلمى" }));
    expect(await screen.findByText("Synthetic location")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "عرض المستند #1" }));
    expect((await screen.findByRole("link", { name: /رابط مؤقت/ })).getAttribute("href")).toContain("sig=1");
    fireEvent.change(screen.getByLabelText("سبب القرار"), { target: { value: "documents complete" } });
    fireEvent.click(screen.getByRole("button", { name: "اعتماد" }));
    const link = await screen.findByRole("link", { name: "إعادة التحقق" });
    expect(link.getAttribute("href")).toMatch(/\/oauth2\/authorization\/keycloak\?stepUp=true&returnTo=/);
    const decision = calls.find((c) => c.method === "POST");
    expect(decision?.body).toEqual({ decision: "APPROVE", revision: 1, expectedVersion: 2, reason: "documents complete" });
    expect(decision?.headers.get("Idempotency-Key")).toMatch(/^ops-/);
  });

  it("lets a moderator hide a meal only with a reason", async () => {
    const item = {
      id: "m1",
      kitchenId: "k1",
      kitchenName: "مطبخ أم علي",
      status: "PUBLISHED",
      moderationState: "VISIBLE",
      moderationReason: null,
      titleAr: "كشري",
      titleEn: null,
      priceMinor: 9000,
      updatedAt: SERVER_NOW,
      version: 4,
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/cooks": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/catalogue": () => ({ status: 200, body: { items: [item], nextCursor: null, hasMore: false } }),
      "POST /api/v1/ops/meals/m1/moderation": () => ({ status: 200, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    const card = await screen.findByRole("article", { name: "كشري" });
    const hide = within(card).getByRole("button", { name: "إخفاء" }) as HTMLButtonElement;
    expect(hide.disabled).toBe(true);
    fireEvent.change(within(card).getByLabelText("السبب"), { target: { value: "misleading photo" } });
    expect(hide.disabled).toBe(false);
    fireEvent.click(hide);
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ action: "HIDE", reason: "misleading photo", expectedVersion: 4 });
  });

  it("lists reported reviews and hides one only with a reason, sending the review version", async () => {
    const review = {
      id: "r1",
      orderId: "o1",
      kitchenId: "k1",
      rating: 1,
      text: "call me on 0100",
      visibility: "VISIBLE",
      moderationReason: null,
      reportCount: 2,
      reportReasons: ["PRIVATE_INFO", "SPAM"],
      createdAt: SERVER_NOW,
      version: 0,
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/cooks": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/catalogue": () => ({ status: 200, body: { items: [], nextCursor: null, hasMore: false } }),
      "GET /api/v1/ops/reviews": () => ({ status: 200, body: { items: [review] } }),
      "POST /api/v1/ops/reviews/r1/moderation": () => ({ status: 200, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    const card = await screen.findByRole("article", { name: "1/5 call me on 0100" });
    expect(within(card).getByText("2 بلاغ")).toBeTruthy();
    expect(within(card).getByText("PRIVATE_INFO · SPAM")).toBeTruthy();
    const hide = within(card).getByRole("button", { name: "إخفاء" }) as HTMLButtonElement;
    expect(hide.disabled).toBe(true);
    fireEvent.change(within(card).getByLabelText("السبب"), { target: { value: "phone number" } });
    fireEvent.click(hide);
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ visibility: "HIDDEN", reason: "phone number", expectedVersion: 0 });
    expect(calls.some((c) => c.method === "GET" && c.path === "/api/v1/ops/reviews?visibility=VISIBLE&limit=50")).toBe(true);
  });

  it("shows the support queue most urgent first and sends internal notes as staff-only by default", async () => {
    const base = {
      orderId: "o1",
      orderNumber: "MB-1001",
      customerId: "c1",
      state: "OPEN",
      description: "Felt unwell after the meal",
      cancellationReason: null,
      assigneeId: null,
      firstResponseAt: null,
      resolutionCode: null,
      resolvedAt: null,
      createdAt: SERVER_NOW,
      updatedAt: SERVER_NOW,
      messages: [],
      version: 0,
    };
    const safety = { ...base, id: "s1", reference: "CS-0000000A", category: "FOOD_SAFETY", severity: "CRITICAL", slaDueAt: SERVER_NOW, overdue: true, escalatedAt: SERVER_NOW };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/cases": () => ({ status: 200, body: { items: [safety], nextCursor: null, hasMore: false } }),
      "GET /api/v1/ops/cases/s1": () => ({
        status: 200,
        body: { ...safety, messages: [{ id: "m1", authorRole: "STAFF", visibility: "STAFF_ONLY", body: "check kitchen history", createdAt: SERVER_NOW }] },
      }),
      "POST /api/v1/ops/cases/s1/messages": () => ({ status: 201, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    expect(await screen.findByText("حرج")).toBeTruthy();
    expect(screen.getByText(/\(متجاوزة للمهلة\)/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "CS-0000000A" }));
    expect(await screen.findByText("check kitchen history")).toBeTruthy();
    fireEvent.change(screen.getByRole("textbox", { name: "ملاحظة داخلية" }), { target: { value: "calling the customer now" } });
    fireEvent.click(screen.getByRole("button", { name: "إرسال" }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ body: "calling the customer now", visibility: "STAFF_ONLY" });
  });

  it("lets finance staff act on refunds and settlements with the current versions and evidence", async () => {
    const statement = {
      kitchenId: "11111111-1111-4111-8111-111111111111",
      from: "2026-10-01",
      to: "2026-10-11",
      direction: "COOK_PAYS_PLATFORM",
      includes: ["COMPLETED_ORDERS", "CASH_POSITION", "COMMISSION", "REFUNDS", "SETTLEMENTS"],
      summary: {
        completedOrders: 1,
        ordersTotal: egp(24000),
        cashReported: egp(0),
        cashVerified: egp(24000),
        cashOutstanding: egp(0),
        commissionPosted: egp(2400),
        commissionPending: 0,
        commissionReversed: egp(0),
        commissionSettled: egp(0),
        commissionOutstanding: egp(2400),
      },
      items: [{ orderId: "o1", publicNumber: "MB-1001", completedAt: SERVER_NOW, orderTotal: egp(24000), cashState: "VERIFIED", commission: egp(2400), commissionState: "POSTED" }],
      refunds: [{ id: "r1", orderId: "o1", amount: egp(24000), state: "APPROVED", paymentReference: null, version: 3 }],
      settlements: [],
      unsettledEntries: [{ ledgerGroupId: "g1", kind: "COMMISSION", orderId: "o1", amount: egp(2400) }],
      nextCursor: null,
      hasMore: false,
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/finance/statements": (req) =>
        new URL(req.url).searchParams.get("kitchenId") === statement.kitchenId ? { status: 200, body: statement } : { status: 404, body: { code: "NOT_FOUND" } },
      "POST /api/v1/ops/finance/refunds/r1/action": () => ({ status: 200, body: {} }),
      "POST /api/v1/ops/finance/settlements": () => ({ status: 200, body: {} }),
    });
    render(<App fetchImpl={fetchImpl} />);
    fireEvent.change(await screen.findByLabelText("معرّف المطبخ"), { target: { value: statement.kitchenId } });
    fireEvent.click(screen.getByRole("button", { name: "عرض" }));
    expect(await screen.findByText(/معتمد \(غير مدفوع\)/)).toBeTruthy();
    const pay = screen.getByRole("button", { name: "تسجيل الدفع" }) as HTMLButtonElement;
    expect(pay.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("مرجع الدفع الخارجي"), { target: { value: "bank-ref-9" } });
    fireEvent.click(pay);
    await waitFor(() => expect(calls.some((c) => c.path.endsWith("/refunds/r1/action"))).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/refunds/r1/action"))?.body).toEqual({ action: "PAYMENT_RECORDED", expectedVersion: 3, evidenceRef: "bank-ref-9" });
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "إنشاء دفعة تسوية" }));
    await waitFor(() => expect(calls.some((c) => c.path === "/api/v1/ops/finance/settlements")).toBe(true));
    expect(calls.find((c) => c.path === "/api/v1/ops/finance/settlements")?.body).toEqual({
      kitchenId: statement.kitchenId,
      period: { from: "2026-10-01", to: "2026-10-11" },
      entryIds: ["g1"],
    });
  });

  it("changes a zone fee with a reason and the current version, and asks for MFA when the server requires it", async () => {
    const config = {
      commercePolicy: null,
      policyHistory: [],
      zones: [{ id: "z1", code: "test-zone-a", nameAr: "المعادي", nameEn: "Maadi", active: true, deliveryFeeMinor: 3000, version: 2 }],
      flags: [{ key: "ordering-enabled", enabled: true, version: 0 }],
      changes: [],
    };
    const { fetchImpl, calls } = server({
      "GET /api/v1/me": me,
      "GET /api/v1/cook/dashboard": () => ({ status: 403, body: { code: "FORBIDDEN" } }),
      "GET /api/v1/ops/configuration": () => ({ status: 200, body: config }),
      "POST /api/v1/ops/configuration/zone:z1/versions": () => ({ status: 403, body: { code: "STEP_UP_REQUIRED" } }),
    });
    render(<App fetchImpl={fetchImpl} />);
    const zone = await screen.findByRole("article", { name: "test-zone-a" });
    fireEvent.change(within(zone).getByLabelText("رسوم التوصيل (جنيه)"), { target: { value: "45" } });
    fireEvent.change(within(zone).getByLabelText("سبب التغيير"), { target: { value: "pilot fee review" } });
    fireEvent.click(within(zone).getByRole("button", { name: "حفظ" }));
    expect(await within(zone).findByRole("link", { name: "إعادة التحقق" })).toBeTruthy();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ value: { active: true, deliveryFeeMinor: 4500 }, reason: "pilot fee review", expectedVersion: 2 });
  });
});

