import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { strings } from "../src/i18n";
import { cairoDate, env, png, sql } from "./fixtures";

/**
 * TC-122 against the real local stack: a cook signs in through the identity provider, waits for approval, creates and
 * publishes a meal with an uploaded photo, opens a slot, and fulfils one pickup and one delivery order placed by a
 * synthetic customer through the real API; then reports cash, reads the statement and pauses sales. A non-cook sees
 * no kitchen data. Approval itself is a staff step that needs MFA, which the local identity provider does not offer,
 * so this test inserts the approved application directly (synthetic setup; the review workflow has its own tests).
 */
const t = strings.ar;
const API = env("E2E_API_URL");
const PASSWORD = env("E2E_USER_PASSWORD");
const COOK_SUBJECT = "00000000-0000-4000-8000-0000000000c1";
const CUSTOMER_SUBJECT = "00000000-0000-4000-8000-0000000000a1";
const ZONE = "00000000-0000-4000-8000-00000000a001";

async function idpLogin(page: Page, user: string) {
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

async function xsrf(context: BrowserContext): Promise<string> {
  const cookie = (await context.cookies(API)).find((c) => c.name === "XSRF-TOKEN");
  if (!cookie) {
    throw new Error("no XSRF-TOKEN cookie");
  }
  return decodeURIComponent(cookie.value);
}

async function customerCall(context: BrowserContext, request: APIRequestContext, method: "GET" | "POST", path: string, data?: unknown, key = false) {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (method !== "GET") {
    headers["X-XSRF-TOKEN"] = await xsrf(context);
  }
  if (key) {
    headers["Idempotency-Key"] = `e2e-${crypto.randomUUID()}`;
  }
  const r = await request.fetch(`${API}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  const body = await r.text();
  return { status: r.status(), body: body ? (JSON.parse(body) as Record<string, unknown>) : {} };
}

/** Label lookup that fails loudly instead of matching "undefined". */
function label(v: string | undefined): string {
  if (v === undefined) {
    throw new Error("missing label");
  }
  return v;
}

function card(page: Page, name: string) {
  return page.getByRole("article", { name });
}

test("cook fulfils pickup and delivery orders through the real API", async ({ browser }) => {
  // ---- cook signs in; no kitchen yet ----
  const cookContext = await browser.newContext();
  const cook = await cookContext.newPage();
  await cook.goto("/");
  await expect(cook.locator("html")).toHaveAttribute("dir", "rtl");
  await cook.getByRole("link", { name: t.signIn }).click();
  await idpLogin(cook, "cook-a");
  await expect(cook.getByText(t.noKitchen)).toBeVisible();
  await expect(cook.getByText(t.noApplication)).toBeVisible();

  // ---- synthetic approval (staff MFA step not available locally) ----
  sql(`
    INSERT INTO app_user (id, issuer, subject, display_name) VALUES ('00000000-0000-4000-8000-0000000000e1', 'e2e-setup', 'e2e-reviewer', 'E2E reviewer');
    INSERT INTO cook_application (id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, current_revision)
      SELECT '00000000-0000-4000-8000-0000000000f1', id, 'APPROVED', 'مطبخ تجريبي', 'أكل بيتي تجريبي', '+201001234567', 'Synthetic location', true, '{${ZONE}}', 1
      FROM app_user WHERE subject = '${COOK_SUBJECT}';
    INSERT INTO cook_application_revision (application_id, revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids,
                                           submitted_at, decision, decision_reason, reviewer_id, decided_at)
      VALUES ('00000000-0000-4000-8000-0000000000f1', 1, 'مطبخ تجريبي', 'أكل بيتي تجريبي', '+201001234567', 'Synthetic location', true, '{${ZONE}}',
              now(), 'APPROVED', 'e2e synthetic approval', '00000000-0000-4000-8000-0000000000e1', now());
    INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description, pickup_enabled, contact_phone, private_location)
      SELECT '00000000-0000-4000-8000-0000000000d1', id, '00000000-0000-4000-8000-0000000000f1', 1, 'e2e-kitchen', 'مطبخ تجريبي', 'أكل بيتي تجريبي', true,
             '+201001234567', 'Synthetic location' FROM app_user WHERE subject = '${COOK_SUBJECT}';
    INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES ('00000000-0000-4000-8000-0000000000d1', '${ZONE}');`);
  await cook.reload();
  await expect(cook.getByRole("heading", { name: "مطبخ تجريبي" })).toBeVisible();

  // ---- meal with an uploaded photo, published ----
  await cook.getByRole("button", { name: t.tabs.meals }).click();
  await cook.getByLabel(t.titleAr).fill("كشري تجريبي");
  await cook.getByLabel(t.titleEn).fill("Synthetic koshary");
  await cook.getByLabel(t.description).fill("وصف تجريبي");
  await cook.getByLabel(t.portion).fill("طبق واحد");
  await cook.getByLabel(t.ingredients).fill("أرز، عدس، مكرونة");
  await cook.getByLabel(t.price).fill("120");
  await cook.getByLabel(t.photo).setInputFiles({ name: "koshary.png", mimeType: "image/png", buffer: png(640, 480) });
  await cook.getByRole("button", { name: t.create }).click();
  const meal = card(cook, "كشري تجريبي");
  await expect(meal.getByText(label(t.mealStatus.DRAFT))).toBeVisible();
  await meal.getByRole("button", { name: t.publish }).click();
  await expect(meal.getByText(label(t.mealStatus.PUBLISHED))).toBeVisible();

  // ---- slot tomorrow, opened ----
  await cook.getByRole("button", { name: t.tabs.slots }).click();
  await cook.getByLabel(t.date).fill(cairoDate(1));
  await cook.getByLabel(t.start).fill("13:00");
  await cook.getByLabel(t.end).fill("15:00");
  await cook.getByLabel(t.maxPortions).fill("10");
  await cook.getByLabel("كشري تجريبي").fill("10");
  await cook.getByRole("button", { name: t.create }).click();
  const slotCard = cook.getByRole("article").filter({ hasText: label(t.slotState.DRAFT) });
  await slotCard.getByRole("button", { name: t.open }).click();
  await expect(cook.getByRole("article").filter({ hasText: label(t.slotState.OPEN) })).toBeVisible();

  // ---- customer: sign in (no kitchen data visible), verified contact, address, two orders ----
  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  await customer.goto(`${API}/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(new URL("/", cook.url()).toString())}`);
  await idpLogin(customer, "customer-a");
  await expect(customer.getByText(t.noKitchen)).toBeVisible();
  sql(`UPDATE app_user SET phone_e164 = '+201001112223', phone_verified_at = now(), email_verified = true WHERE subject = '${CUSTOMER_SUBJECT}'`);
  const req = customer.request;
  expect((await customerCall(customerContext, req, "GET", "/api/v1/cook/dashboard")).status, "a customer has no kitchen data").toBe(403);
  const address = await customerCall(customerContext, req, "POST", "/api/v1/me/addresses", {
    label: "Home",
    recipientName: "Customer A",
    phone: "+201001112223",
    governorate: "Cairo",
    city: "Test city",
    zoneId: ZONE,
    street: "Synthetic street 1",
    building: "7",
  });
  expect(address.status, JSON.stringify(address.body)).toBe(201);
  const mealId = sql(`SELECT id FROM meal WHERE kitchen_id = '00000000-0000-4000-8000-0000000000d1'`);
  const slotId = sql(`SELECT id FROM cook_slot WHERE kitchen_id = '00000000-0000-4000-8000-0000000000d1'`);
  const policy = JSON.parse(sql(`SELECT json_build_object('terms', terms_version, 'cancellation', cancellation_policy_version) FROM commerce_policy WHERE status = 'ACTIVE'`)) as {
    terms: string;
    cancellation: string;
  };

  async function place(mode: "PICKUP" | "DELIVERY"): Promise<{ id: string; number: string }> {
    const quote = await customerCall(customerContext, req, "POST", "/api/v1/quotes", {
      items: [{ mealId, quantity: 2 }],
      slotId,
      fulfillmentMode: mode,
      ...(mode === "DELIVERY" ? { addressId: address.body.id } : {}),
    });
    expect(quote.status, JSON.stringify(quote.body)).toBe(201);
    const order = await customerCall(
      customerContext,
      req,
      "POST",
      "/api/v1/orders",
      { quoteId: quote.body.quoteId, clientRequestId: crypto.randomUUID(), acceptedPolicyVersions: policy },
      true,
    );
    expect(order.status, JSON.stringify(order.body)).toBe(201);
    return { id: order.body.id as string, number: order.body.publicNumber as string };
  }
  const pickup = await place("PICKUP");
  const delivery = await place("DELIVERY");

  // ---- cook: dashboard deadlines, then fulfil both orders ----
  await cook.getByRole("button", { name: t.tabs.dashboard }).click();
  await expect(cook.getByRole("button", { name: pickup.number })).toBeVisible();
  await expect(cook.getByText(/باقي \d+ دقيقة/).first()).toBeVisible();
  await cook.getByRole("button", { name: t.tabs.orders }).click();

  async function act(order: { number: string }, action: string) {
    const c = card(cook, order.number);
    await c.getByRole("button", { name: label(t.action[action]) }).click();
  }
  async function expectState(order: { number: string }, state: string) {
    await expect(card(cook, order.number).getByText(label(t.state[state]), { exact: true })).toBeVisible();
  }
  async function handoverCode(orderId: string): Promise<string> {
    const o = await customerCall(customerContext, req, "GET", `/api/v1/orders/${orderId}`);
    return o.body.handoverCode as string;
  }

  for (const o of [pickup, delivery]) {
    await act(o, "ACCEPT");
    await expectState(o, "ACCEPTED");
    await act(o, "PREPARE");
    await expectState(o, "PREPARING");
    await act(o, "READY");
    await expectState(o, "READY");
  }
  // Customer contact is visible to the cook while fulfilling the delivery order.
  await expect(card(cook, delivery.number).getByText("+201001112223")).toBeVisible();
  await expect(card(cook, delivery.number).getByText(/Synthetic street 1/)).toBeVisible();

  // Pickup: wrong code refused, real code completes.
  await card(cook, pickup.number).getByLabel(t.handoverCode).fill("000000");
  await act(pickup, "HANDOVER");
  await expect(card(cook, pickup.number).getByText(label(t.errors.VERIFICATION_FAILED))).toBeVisible();
  await card(cook, pickup.number).getByLabel(t.handoverCode).fill(await handoverCode(pickup.id));
  await act(pickup, "HANDOVER");
  await expect(card(cook, pickup.number)).toHaveCount(0);

  // Delivery: dispatch with a reference, then hand over with the customer's code.
  await card(cook, delivery.number).getByLabel(t.deliveryReference).fill("Synthetic courier");
  await act(delivery, "DISPATCH");
  await expectState(delivery, "OUT_FOR_DELIVERY");
  await card(cook, delivery.number).getByLabel(t.handoverCode).fill(await handoverCode(delivery.id));
  await act(delivery, "HANDOVER");
  await expect(card(cook, delivery.number)).toHaveCount(0);

  // ---- cash report on the completed pickup order ----
  await cook.getByRole("button", { name: t.filter.COMPLETED }).click();
  const done = card(cook, pickup.number);
  await expect(done.getByText(label(t.cash.DUE))).toBeVisible();
  await done.getByRole("button", { name: t.submit }).click();
  await expect(done.getByText(label(t.cash.REPORTED))).toBeVisible();
  const customerView = await customerCall(customerContext, req, "GET", `/api/v1/orders/${pickup.id}`);
  expect(customerView.body.state).toBe("COMPLETED");
  expect(customerView.body.cashState).toBe("REPORTED");

  // ---- statement ----
  await cook.getByRole("button", { name: t.tabs.statement }).click();
  await expect(cook.getByRole("cell", { name: pickup.number })).toBeVisible();
  await expect(cook.getByRole("cell", { name: delivery.number })).toBeVisible();
  await expect(cook.getByText(t.statementNote)).toBeVisible();

  // ---- pause stops new sales; resume restores them ----
  await cook.getByRole("button", { name: t.tabs.settings }).click();
  await cook.getByRole("button", { name: t.pauseKitchen }).click();
  await expect(cook.getByRole("button", { name: t.resumeKitchen })).toBeVisible();
  const blocked = await customerCall(customerContext, req, "POST", "/api/v1/quotes", { items: [{ mealId, quantity: 1 }], slotId, fulfillmentMode: "PICKUP" });
  expect(blocked.status, "paused kitchen does not sell").not.toBe(201);
  await cook.getByRole("button", { name: t.resumeKitchen }).click();
  await expect(cook.getByRole("button", { name: t.pauseKitchen })).toBeVisible();
  const allowed = await customerCall(customerContext, req, "POST", "/api/v1/quotes", { items: [{ mealId, quantity: 1 }], slotId, fulfillmentMode: "PICKUP" });
  expect(allowed.status, JSON.stringify(allowed.body)).toBe(201);

  // ---- English left-to-right ----
  await cook.getByRole("button", { name: t.language }).click();
  await expect(cook.locator("html")).toHaveAttribute("dir", "ltr");
  await expect(cook.getByRole("button", { name: strings.en.tabs.orders })).toBeVisible();

  await cookContext.close();
  await customerContext.close();
});
