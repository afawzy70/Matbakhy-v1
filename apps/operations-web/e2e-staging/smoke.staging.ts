import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { strings as customerStrings } from "../../customer-web/src/i18n";
import { strings } from "../src/i18n";

/**
 * Browser smoke of a deployed preview (owner direction 2026-10-14): customer web, operations web, API, authentication and a
 * SIMULATED deposit order. Nothing real happens in the preview: no money moves, no message is sent. The customer "reports" a
 * transfer, the kitchen "reports" receipt, and preparation cannot start before that report. It also checks that privileged
 * operations refuse the customer and the kitchen. Seeded accounts only (deploy.sh seed): preview-customer-1 and preview-cook-1.
 */
const APP = need("STAGING_APP_URL");
const OPS = need("STAGING_OPS_URL");
const API = need("STAGING_API_URL");
const PASSWORD = need("STAGING_USER_PASSWORD");
const CUSTOMER_USER = process.env.STAGING_CUSTOMER_USER ?? "preview-customer-1";
const COOK_USER = process.env.STAGING_COOK_USER ?? "preview-cook-1";
const c = customerStrings.ar;
const t = strings.ar;

function need(name: string): string {
  const v = process.env[name];
  if (!v) {
    throw new Error(`${name} is not set (see playwright.staging.config.ts)`);
  }
  return v.replace(/\/$/, "");
}

async function signIn(page: Page, entry: string, user: string) {
  await page.goto(entry);
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

/** The few response fields this smoke reads; the assertions fail loudly when the server answers otherwise. */
type Payment = { method: string; state: string; version: number; deposit: { amountMinor: number }; recipient: { identifier: string } };
type Body = {
  id: string;
  version: number;
  state: string;
  quoteId: string;
  termsVersion: string;
  cancellationPolicyVersion: string;
  payment: Payment;
  environment: unknown;
  orderingAvailable: boolean;
  items: unknown;
  pendingAcceptance: unknown;
};

async function call(context: BrowserContext, request: APIRequestContext, method: "GET" | "POST", path: string, data?: unknown, key = false) {
  const headers: Record<string, string> = { "Content-Type": "application/json", "Accept-Language": "ar" };
  if (method !== "GET") {
    const cookie = (await context.cookies(API)).find((x) => x.name === "XSRF-TOKEN");
    if (!cookie) {
      throw new Error("no XSRF-TOKEN cookie: not signed in?");
    }
    headers["X-XSRF-TOKEN"] = decodeURIComponent(cookie.value);
  }
  if (key) {
    headers["Idempotency-Key"] = `smoke-${crypto.randomUUID()}`;
  }
  const r = await request.fetch(`${API}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  const text = await r.text();
  return { status: r.status(), body: (text ? JSON.parse(text) : {}) as Body };
}

test.describe.configure({ mode: "serial" });

test("customer web: Arabic RTL, a visible demo notice and a way to sign in", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${APP}/`);
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  const note = page.getByRole("note", { name: "بيئة تجريبية" });
  await expect(note).toBeVisible();
  await expect(note).toContainText("محاكاة");
  await expect(page.getByRole("link", { name: c.signIn }).first()).toBeVisible();
  await page.getByRole("button", { name: "English" }).click();
  await expect(page.getByRole("note", { name: "Demo environment" })).toBeVisible();
  await context.close();
});

let orderId = "";
let depositMinor = 0;

test("authentication and a simulated deposit order as the preview customer", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await signIn(page, `${API}/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(`${APP}/`)}`, CUSTOMER_USER);
  await expect(page).toHaveURL(new RegExp(`^${APP.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`));
  await expect(page.getByRole("note", { name: "بيئة تجريبية" })).toBeVisible();
  const request = page.request;

  const me = await call(context, request, "GET", "/api/v1/me");
  expect(me.status, JSON.stringify(me.body)).toBe(200);

  const config = await call(context, request, "GET", "/api/v1/configuration/public");
  expect(config.body.environment).toEqual({ name: "PREVIEW", demo: true });
  expect(config.body.orderingAvailable).toBe(true);

  const meals = await call(context, request, "GET", "/api/v1/meals?limit=20");
  const meal = (meals.body.items as { id: string; title: string; nextSlots: { id: string; fulfillmentModes: string[] }[] }[]).find((m) => m.title === "كشري تجريبي");
  expect(meal, "the seeded meal is listed").toBeTruthy();
  const slot = meal?.nextSlots.find((s) => s.fulfillmentModes.includes("PICKUP"));
  expect(slot, "the seeded meal has a pickup slot").toBeTruthy();

  const quote = await call(context, request, "POST", "/api/v1/quotes", {
    items: [{ mealId: meal?.id, quantity: 1 }],
    slotId: slot?.id,
    fulfillmentMode: "PICKUP",
    paymentMethod: "DEPOSIT",
  });
  expect(quote.status, JSON.stringify(quote.body)).toBe(201);
  expect(quote.body.payment.method).toBe("DEPOSIT");
  expect(quote.body.payment.deposit.amountMinor).toBe(3750);
  expect(quote.body.payment.recipient.identifier).toContain("demo-kitchen");

  const placed = await call(context, request, "POST", "/api/v1/orders", {
    quoteId: quote.body.quoteId,
    clientRequestId: crypto.randomUUID(),
    acceptedPolicyVersions: { terms: quote.body.termsVersion, cancellation: quote.body.cancellationPolicyVersion },
  }, true);
  expect(placed.status, JSON.stringify(placed.body)).toBe(201);
  orderId = placed.body.id as string;
  depositMinor = placed.body.payment.deposit.amountMinor as number;
  expect(placed.body.payment.state).toBe("WAITING_FOR_DEPOSIT");

  const reported = await call(context, request, "POST", `/api/v1/orders/${orderId}/payment/transfer-report`, { reference: "DEMO-TRANSFER-1" });
  expect(reported.status, JSON.stringify(reported.body)).toBe(200);
  expect(reported.body.state).toBe("CUSTOMER_TRANSFER_REPORTED");
  expect(JSON.stringify(reported.body), "a report is not bank verification").not.toMatch(/verified/i);

  // Privileged operations refuse the customer.
  expect((await call(context, request, "GET", "/api/v1/ops/cases")).status).toBe(403);
  expect((await call(context, request, "GET", "/api/v1/cook/dashboard")).status).toBe(403);
  await context.close();
});

test("operations web: the kitchen cannot start preparing before it reports the deposit", async ({ browser }) => {
  expect(orderId, "the order from the previous test").not.toBe("");
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${OPS}/`);
  await expect(page.getByRole("note", { name: "بيئة تجريبية" })).toBeVisible();
  await page.getByRole("link", { name: t.signIn }).click();
  await page.locator("#username").fill(COOK_USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  await expect(page.getByRole("navigation", { name: t.section })).toBeVisible();
  const request = page.request;

  const dashboard = await call(context, request, "GET", "/api/v1/cook/dashboard");
  expect(dashboard.status, JSON.stringify(dashboard.body)).toBe(200);
  const pending = (dashboard.body.pendingAcceptance as { orderId: string; version: number }[]).find((o) => o.orderId === orderId);
  expect(pending, "the new order is waiting for the kitchen").toBeTruthy();

  const accepted = await call(context, request, "POST", `/api/v1/cook/orders/${orderId}/accept`, { expectedVersion: pending?.version }, true);
  expect(accepted.status, JSON.stringify(accepted.body)).toBe(200);
  const early = await call(context, request, "POST", `/api/v1/cook/orders/${orderId}/prepare`, { expectedVersion: accepted.body.version }, true);
  expect(early.status, "no preparation before the kitchen reports receipt").toBe(409);

  const receipt = await call(context, request, "POST", `/api/v1/cook/orders/${orderId}/payment/receipt`,
    { expectedVersion: accepted.body.payment.version, amountMinor: depositMinor, reference: "DEMO-RECEIPT-1" });
  expect(receipt.status, JSON.stringify(receipt.body)).toBe(200);
  expect(receipt.body.state).toBe("KITCHEN_RECEIPT_REPORTED");
  const prepared = await call(context, request, "POST", `/api/v1/cook/orders/${orderId}/prepare`, { expectedVersion: accepted.body.version }, true);
  expect(prepared.status, JSON.stringify(prepared.body)).toBe(200);
  expect(prepared.body.state).toBe("PREPARING");

  // Staff operations refuse the kitchen.
  expect((await call(context, request, "GET", "/api/v1/ops/cases")).status).toBe(403);
  expect((await call(context, request, "GET", "/api/v1/ops/payments?state=DISPUTED")).status).toBe(403);
  await context.close();
});
