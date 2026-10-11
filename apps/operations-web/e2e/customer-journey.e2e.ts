import { expect, test, type Page } from "@playwright/test";
import { strings as customerStrings } from "../../customer-web/src/i18n";
import { strings } from "../src/i18n";
import { env, sql } from "./fixtures";

/**
 * TC-127 against the real local stack, after the cook workflow has published "كشري تجريبي" with an open slot: a
 * customer finds the meal in the customer web, signs in through the identity provider and returns to checkout, adds a
 * delivery address, gets a server quote and places the order; the first placement response is lost after the server
 * committed, and the retry with the same stored keys shows the one existing order. The cook accepts, prepares and
 * hands over in the operations web while the customer's order page follows each state and shows the handover code.
 * A pickup order is placed too. Phone verification is synthetic SQL (the fake SMS code is not exposed).
 *
 * MB-028 (TC-128): the customer reviews the completed order once, sees the visible rating on the meal page, keeps the
 * meal as a favorite in the account page and reports the review; a moderator (ops-a, granted by the cook workflow)
 * finds it in the reported-review queue and hides it with a reason, after which it leaves the public list and rating.
 *
 * TC-147 (BR-018 client part): with a second synthetic slot of the same kitchen (test-only SQL), adding the meal for
 * the other slot asks before replacing the basket; keeping it leaves the basket unchanged, replacing it switches slot.
 */
const c = customerStrings.ar;
const t = strings.ar;
const CUSTOMER = env("E2E_CUSTOMER_URL");
const API = env("E2E_API_URL");
const PASSWORD = env("E2E_USER_PASSWORD");
const CUSTOMER_B = "00000000-0000-4000-8000-0000000000b1";

async function idpLogin(page: Page, user: string) {
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

async function addToCart(page: Page) {
  await page.goto(`${CUSTOMER}/#/`);
  await page.getByLabel(c.search).fill("كشري");
  await page.getByRole("search").getByRole("button", { name: c.search }).click();
  await page.getByRole("link", { name: "كشري تجريبي" }).click();
  await page.getByRole("radio").first().check();
  await page.getByLabel(c.quantity).fill("1");
  await page.getByRole("button", { name: c.addToCart }).click();
  await expect(page.getByText(c.added)).toBeVisible();
}

test("customer orders delivery and pickup in the customer web and follows the cook's actions", async ({ browser }) => {
  expect(sql(`SELECT count(*) FROM meal WHERE title_ar = 'كشري تجريبي' AND status = 'PUBLISHED' AND moderation_state = 'VISIBLE'`),
    "cook workflow ran first").toBe("1");
  const customerContext = await browser.newContext();
  const page = await customerContext.newPage();
  await addToCart(page);
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");

  // Checkout needs a session: sign in and come back to the cart.
  await page.getByRole("link", { name: c.nav.cart }).click();
  await expect(page.getByText(c.signInToOrder)).toBeVisible();
  await page.getByRole("main").getByRole("link", { name: c.signIn }).click();
  await idpLogin(page, "customer-b");
  await expect(page).toHaveURL(/5173\/#\/checkout/);
  await expect(page.getByRole("heading", { name: c.contact })).toBeVisible();
  sql(`UPDATE app_user SET phone_e164 = '+201005556667', phone_verified_at = now() WHERE subject = '${CUSTOMER_B}'`);
  await page.reload();

  // Delivery address through the UI, then a server quote.
  await page.getByRole("button", { name: c.mode.DELIVERY as string }).click();
  await page.getByRole("button", { name: c.newAddress }).click();
  await page.getByLabel(c.addressFields.label as string).fill("Home");
  await page.getByLabel(c.addressFields.recipientName as string).fill("Customer B");
  await page.getByLabel(c.addressFields.governorate as string).fill("Cairo");
  await page.getByLabel(c.addressFields.city as string).fill("Test city");
  await page.getByLabel(c.addressFields.street as string).fill("Synthetic street 9");
  await page.getByLabel(c.addressFields.building as string).fill("12");
  await page.getByLabel(c.phone).last().fill("+201005556667");
  await page.getByLabel(c.zone).last().selectOption({ label: "منطقة تجريبية أ" });
  await page.getByRole("button", { name: c.saveAddress }).click();
  await page.getByRole("radio").first().check();
  await page.getByRole("button", { name: c.getQuote }).click();
  await expect(page.getByText(c.total, { exact: true })).toBeVisible();
  await page.getByLabel(c.terms).check();

  // The first placement reaches the server and commits, but the response is lost.
  let lost = false;
  await page.route(`${API}/api/v1/orders`, async (route) => {
    if (!lost && route.request().method() === "POST") {
      lost = true;
      await route.fetch();
      await route.abort("failed");
      return;
    }
    await route.continue();
  });
  await page.getByRole("button", { name: c.placeOrder }).click();
  await page.getByRole("button", { name: c.retryOrder }).first().click();
  await expect(page.getByRole("heading", { level: 2 }).getByText(c.state.PENDING_ACCEPTANCE as string)).toBeVisible();
  await page.unroute(`${API}/api/v1/orders`);
  expect(sql(`SELECT count(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id WHERE u.subject = '${CUSTOMER_B}'`),
    "the lost response did not create a second order").toBe("1");
  const number = (await page.getByRole("heading", { level: 2 }).textContent())?.split(" · ")[0]?.trim() ?? "";

  // The cook acts in the operations web; the customer page follows (10 s refresh).
  const cookContext = await browser.newContext();
  const cook = await cookContext.newPage();
  await cook.goto("/");
  await cook.getByRole("link", { name: t.signIn }).click();
  await idpLogin(cook, "cook-a");
  await cook.getByRole("button", { name: t.tabs.orders, exact: true }).click();
  const card = cook.getByRole("article", { name: number });
  await card.getByRole("button", { name: t.action.ACCEPT as string }).click();
  await expect(page.getByRole("heading", { level: 2 }).getByText(c.state.ACCEPTED as string)).toBeVisible({ timeout: 20_000 });
  await card.getByRole("button", { name: t.action.PREPARE as string }).click();
  await card.getByRole("button", { name: t.action.READY as string }).click();
  await expect(page.getByText(c.handoverCode)).toBeVisible({ timeout: 20_000 });
  const code = (await page.locator("strong[dir=ltr]").textContent())?.trim() ?? "";
  await card.getByLabel(t.deliveryReference).fill("Synthetic courier 2");
  await card.getByRole("button", { name: t.action.DISPATCH as string }).click();
  await expect(card.getByText(t.state.OUT_FOR_DELIVERY as string, { exact: true })).toBeVisible();
  await card.getByLabel(t.handoverCode).fill(code);
  await card.getByRole("button", { name: t.action.HANDOVER as string }).click();
  await expect(page.getByRole("heading", { level: 2 }).getByText(c.state.COMPLETED as string)).toBeVisible({ timeout: 20_000 });

  // One review per completed order, sent through the order page.
  await page.getByLabel(c.rating).selectOption("4");
  await page.getByLabel(c.comment).fill("طعم ممتاز - مراجعة تجريبية");
  await page.getByRole("button", { name: c.submitReview }).click();
  await expect(page.getByText(c.reviewed)).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: c.submitReview })).toBeVisible();
  await page.getByRole("button", { name: c.submitReview }).click();
  await expect(page.getByText(c.reviewed), "the server refuses a second review of the same order").toBeVisible();
  expect(sql(`SELECT count(*) FROM review r JOIN customer_order o ON o.id = r.order_id WHERE o.public_number = '${number}'`)).toBe("1");

  // A pickup order as well.
  await addToCart(page);
  await page.getByRole("link", { name: c.nav.cart }).click();
  await page.getByRole("button", { name: c.mode.PICKUP as string }).click();
  await page.getByRole("button", { name: c.getQuote }).click();
  await page.getByLabel(c.terms).check();
  await page.getByRole("button", { name: c.placeOrder }).click();
  await expect(page.getByRole("heading", { level: 2 }).getByText(c.state.PENDING_ACCEPTANCE as string)).toBeVisible();
  await page.getByRole("link", { name: c.nav.orders }).click();
  await expect(page.getByRole("link", { name: /^MB-/ })).toHaveCount(2);

  // Favorite the meal, see it in the account page, and see the visible review and rating on the meal page.
  await page.goto(`${CUSTOMER}/#/`);
  await page.getByLabel(c.search).fill("كشري");
  await page.getByRole("search").getByRole("button", { name: c.search }).click();
  await page.getByRole("link", { name: "كشري تجريبي" }).click();
  await page.getByRole("button", { name: c.favorite }).click();
  await expect(page.getByRole("button", { name: c.unfavorite })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText(/طعم ممتاز - مراجعة تجريبية/)).toBeVisible();
  await expect(page.getByText(/من 5/)).toBeVisible();
  await page.getByRole("link", { name: c.nav.account }).click();
  await expect(page.getByRole("region", { name: c.favorites }).getByRole("link", { name: "كشري تجريبي" })).toBeVisible();
  await page.goBack();
  await page.getByLabel(c.report).selectOption("SPAM");
  await expect(page.getByText(c.reported)).toBeVisible();

  // A moderator hides the reported review with a reason; it leaves the public list.
  const opsContext = await browser.newContext({ baseURL: env("E2E_OPS_URL") });
  const ops = await opsContext.newPage();
  await ops.goto("/");
  await ops.getByRole("link", { name: t.signIn }).click();
  await idpLogin(ops, "ops-a");
  await ops.getByRole("button", { name: t.ops.catalogue }).click();
  const reviewCard = ops.getByRole("article", { name: /طعم ممتاز - مراجعة تجريبية/ });
  await expect(reviewCard.getByText(t.ops.reportCount(1))).toBeVisible();
  await reviewCard.getByLabel(t.reason).fill("e2e synthetic moderation");
  await reviewCard.getByRole("button", { name: t.ops.hide }).click();
  await expect(reviewCard).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("button", { name: c.unfavorite })).toBeVisible();
  await expect(page.getByText(/طعم ممتاز - مراجعة تجريبية/)).toHaveCount(0);
  await expect(page.getByText(c.noReviews)).toBeVisible();
  expect(sql(`SELECT count(*) FROM audit_event WHERE action = 'review.moderation'`)).toBe("1");
  await opsContext.close();

  // One kitchen and one slot per basket: replacing needs explicit confirmation; cancelling keeps the basket.
  sql(`WITH s AS (SELECT * FROM cook_slot WHERE kitchen_id = (SELECT kitchen_id FROM meal WHERE title_ar = 'كشري تجريبي') AND state = 'OPEN'
         ORDER BY starts_at LIMIT 1),
       n AS (INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
         SELECT gen_random_uuid(), kitchen_id, local_date + 1, starts_at + interval '1 day', ends_at + interval '1 day', lead_minutes,
                cutoff_at + interval '1 day', max_portions, 'OPEN' FROM s RETURNING id)
       INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity)
         SELECT gen_random_uuid(), n.id, ms.meal_id, ms.capacity FROM n, meal_slot ms JOIN s ON ms.cook_slot_id = s.id`);
  const cartSlot = () => page.evaluate(() => (JSON.parse(window.localStorage.getItem("matbakhy.cart") ?? "null") as { slotId: string } | null)?.slotId);
  await page.reload();
  await expect(page.getByRole("radio")).toHaveCount(2);
  await page.getByRole("radio").first().check();
  await page.getByRole("button", { name: c.addToCart }).click();
  await expect(page.getByText(c.added)).toBeVisible();
  const firstSlot = await cartSlot();
  await page.getByRole("radio").nth(1).check();
  await page.getByRole("button", { name: c.addToCart }).click();
  const prompt = page.getByRole("alertdialog", { name: c.replaceTitle });
  await expect(prompt).toBeVisible();
  await prompt.getByRole("button", { name: c.keepBasket }).click();
  await expect(page.getByText(c.basketKept)).toBeVisible();
  expect(await cartSlot(), "cancelling keeps the previous basket").toBe(firstSlot);
  await page.getByRole("button", { name: c.addToCart }).click();
  await page.getByRole("alertdialog", { name: c.replaceTitle }).getByRole("button", { name: c.replaceBasket }).click();
  await expect(page.getByText(c.added)).toBeVisible();
  const replaced = await cartSlot();
  expect(replaced, "confirming replaces the basket with the other slot").not.toBe(firstSlot);
  expect(replaced).toBeTruthy();

  await cookContext.close();
  await customerContext.close();
});
