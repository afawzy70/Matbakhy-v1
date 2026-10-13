import { expect, test, type Page } from "@playwright/test";
import { strings as customerStrings } from "../../customer-web/src/i18n";
import { strings } from "../src/i18n";
import { env } from "./fixtures";

/**
 * MB-029 / TC-047 keyboard-only journeys on the real stack (after the purchase journey, which leaves two open slots of
 * the synthetic kitchen). Customer: search, open a meal, choose a slot, add to the cart, meet the replacement prompt
 * for another slot (focus moves into it, Escape closes it and focus returns to the add button), open the cart, sign in,
 * choose pickup, get the server quote, accept the terms and place the order. Cook:
 * sign in and switch sections. Every element reached with Tab must show a visible focus indicator. No pointer input.
 */
const c = customerStrings.ar;
const t = strings.ar;
const CUSTOMER = env("E2E_CUSTOMER_URL");
const PASSWORD = env("E2E_USER_PASSWORD");

/** Presses Tab until the focused element matches, asserting a visible focus indicator at every stop. */
async function tabTo(page: Page, matches: (el: { tag: string; text: string; label: string; type: string }) => boolean, what: string) {
  for (let i = 0; i < 80; i++) {
    await page.keyboard.press("Tab");
    const focused = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body) {
        return null;
      }
      const style = getComputedStyle(el);
      const visible = (style.outlineStyle !== "none" && parseFloat(style.outlineWidth) > 0) || style.boxShadow !== "none";
      return {
        tag: el.tagName,
        text: (el.textContent ?? "").trim(),
        label: el.getAttribute("aria-label") ?? "",
        type: el.getAttribute("type") ?? "",
        visible,
      };
    });
    if (!focused) {
      continue;
    }
    expect(focused.visible, `visible focus on ${focused.tag} "${focused.text.slice(0, 30)}"`).toBe(true);
    if (matches(focused)) {
      return;
    }
  }
  throw new Error(`could not reach ${what} with Tab`);
}

test("keyboard-only customer and cook journeys with visible focus and focus restoration", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${CUSTOMER}/#/`);
  await tabTo(page, (e) => e.tag === "INPUT" && e.label === c.search, "search");
  await page.keyboard.type("كشري");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("link", { name: "كشري تجريبي", exact: true })).toBeVisible();
  await tabTo(page, (e) => e.tag === "A" && e.text === "كشري تجريبي", "meal title link");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { level: 2, name: "كشري تجريبي" })).toBeVisible();

  await tabTo(page, (e) => e.tag === "INPUT" && e.type === "radio", "first slot");
  await page.keyboard.press("Space");
  await expect(page.getByRole("radio").first()).toBeChecked();
  await tabTo(page, (e) => e.tag === "BUTTON" && e.text.startsWith(c.addToCart), "add to cart");
  await page.keyboard.press("Enter");
  await expect(page.getByText(c.added)).toBeVisible();

  // Another slot of the same kitchen: the prompt takes focus; Escape keeps the basket and returns focus.
  await page.getByRole("radio").first().focus();
  await page.keyboard.press("ArrowDown");
  await expect(page.getByRole("radio").nth(1)).toBeChecked();
  await tabTo(page, (e) => e.tag === "BUTTON" && e.text.startsWith(c.addToCart), "add to cart");
  await page.keyboard.press("Enter");
  const prompt = page.getByRole("alertdialog", { name: c.replaceTitle });
  await expect(prompt).toBeVisible();
  await expect(prompt.getByRole("button", { name: c.keepBasket })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(prompt).toHaveCount(0);
  await expect(page.getByRole("button", { name: new RegExp(`^${c.addToCart}`) })).toBeFocused();
  await expect(page.getByText(c.basketKept)).toBeVisible();

  await page.keyboard.press("Shift+Tab");
  await tabTo(page, (e) => e.tag === "A" && e.text === c.nav.cart, "cart link");
  await page.keyboard.press("Enter");
  // CW05 basket line: the quantity can be changed from the keyboard and the estimate follows it.
  const quantity = page.getByRole("group", { name: c.quantityOf("كشري تجريبي") }).locator("output");
  await expect(quantity).toHaveText("1");
  await tabTo(page, (e) => e.tag === "BUTTON" && e.label === c.increaseOf("كشري تجريبي"), "increase quantity");
  await page.keyboard.press("Enter");
  await expect(quantity).toHaveText("2");
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Enter");
  await expect(quantity).toHaveText("1");
  await expect(page.getByText(c.estimatedSubtotal)).toBeVisible();

  // Checkout without a pointer: sign in, pickup, server quote, terms, place the order.
  await tabTo(page, (e) => e.tag === "A" && e.text === c.signIn, "sign in");
  await page.keyboard.press("Enter");
  await page.locator("#username").focus();
  await page.keyboard.type("customer-b");
  await page.keyboard.press("Tab");
  await page.keyboard.type(PASSWORD);
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: c.checkout })).toBeVisible();
  await tabTo(page, (e) => e.tag === "BUTTON" && e.text === c.mode.PICKUP, "pickup");
  await page.keyboard.press("Enter");
  await tabTo(page, (e) => e.tag === "BUTTON" && e.text === c.getQuote, "get quote");
  await page.keyboard.press("Enter");
  await expect(page.getByText(c.total, { exact: true })).toBeVisible();
  await tabTo(page, (e) => e.tag === "INPUT" && e.type === "checkbox", "terms");
  await page.keyboard.press("Space");
  await expect(page.getByLabel(c.terms)).toBeChecked();
  await tabTo(page, (e) => e.tag === "BUTTON" && e.text === c.placeOrder, "place order");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { level: 2 }).getByText(c.state.PENDING_ACCEPTANCE as string)).toBeVisible();
  await context.close();

  // Cook: sign in with the keyboard and move between sections.
  const cookContext = await browser.newContext();
  const cook = await cookContext.newPage();
  await cook.goto("/");
  await tabTo(cook, (e) => e.tag === "A" && e.text === t.signIn, "sign in");
  await cook.keyboard.press("Enter");
  await cook.locator("#username").focus();
  await cook.keyboard.type("cook-a");
  await cook.keyboard.press("Tab");
  await cook.keyboard.type(PASSWORD);
  await cook.keyboard.press("Enter");
  await expect(cook.getByRole("navigation", { name: t.section })).toBeVisible();
  await tabTo(cook, (e) => e.tag === "BUTTON" && e.text === t.tabs.statement, "statement tab");
  await cook.keyboard.press("Enter");
  await expect(cook.getByRole("button", { name: t.tabs.statement })).toHaveAttribute("aria-current", "page");
  // MB-050 (D10): the launch policy has no platform commission, so the statement says so instead of the commission note.
  await expect(cook.getByText(t.statementNoteNoCommission)).toBeVisible();
  await expect(cook.getByText(t.noCommission)).toBeVisible();
  await expect(cook.getByText(t.statementNote)).toHaveCount(0);
  await cookContext.close();
});
