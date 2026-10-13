import { expect, test, type Page } from "@playwright/test";
import { strings } from "../src/i18n";
import { env, sql } from "./fixtures";

/**
 * QA repair MBQA-006 (W-MONEY-REFLOW): on the cook Statement and the finance view, every money amount stays one intact
 * token (currency and digits on one line, never a vertical column of glyphs) at 320, 390 and 1440 px in Arabic RTL and
 * English LTR: 2 roles × 3 widths × 2 locales = 12 variations. Runs after the cook workflow, which completed real
 * orders for kitchen d1. The statement table either fits or scrolls inside its own focusable region, and keyboard
 * scrolling reaches the end. A 200% root text size at 390 px is checked as well. ops-a gets finance.read by test-only
 * SQL (synthetic grant).
 */
const PASSWORD = env("E2E_USER_PASSWORD");
const KITCHEN = "00000000-0000-4000-8000-0000000000d1";
const OPS_SUBJECT = "00000000-0000-4000-8000-0000000000d2";

async function idpLogin(page: Page, user: string) {
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

/** Each formatted amount (EGP + no-break space + digits, or Arabic digits + no-break space + ج.م.) and the lines it spans. */
async function moneyTokens(page: Page): Promise<{ text: string; lines: number }[]> {
  return page.evaluate(() => {
    const re = /EGP [\d,]+\.\d{2}|[٠-٩٬]+٫[٠-٩]{2} ج\.م\./g;
    const out: { text: string; lines: number }[] = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      const value = n.nodeValue ?? "";
      for (const m of value.matchAll(re)) {
        const range = document.createRange();
        range.setStart(n, m.index ?? 0);
        range.setEnd(n, (m.index ?? 0) + m[0].length);
        const tops = new Set([...range.getClientRects()].filter((r) => r.width > 0).map((r) => Math.round(r.top)));
        out.push({ text: m[0], lines: tops.size });
      }
    }
    return out;
  });
}

async function checkVariation(page: Page, where: string) {
  await page.waitForLoadState("networkidle");
  const tokens = await moneyTokens(page);
  expect(tokens.length, `${where}: amounts shown`).toBeGreaterThan(2);
  expect(tokens.filter((t) => t.lines !== 1), `${where}: amounts split across lines`).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${where}: page scrolls sideways`).toBe(true);
  const wrap = page.locator(".table-wrap");
  if ((await wrap.count()) > 0) {
    const scrollable = await wrap.first().evaluate((el) => el.scrollWidth > el.clientWidth + 1);
    if (scrollable) {
      await wrap.first().focus();
      await expect(wrap.first(), `${where}: table region focusable`).toBeFocused();
      const rtl = (await page.locator("html").getAttribute("dir")) === "rtl";
      for (let i = 0; i < 40; i++) {
        await page.keyboard.press(rtl ? "ArrowLeft" : "ArrowRight");
      }
      const atEnd = await wrap.first().evaluate((el) => Math.abs(el.scrollLeft) + el.clientWidth >= el.scrollWidth - 2);
      expect(atEnd, `${where}: keyboard scrolls the table to its end`).toBe(true);
    }
  }
}

async function open(browser: import("@playwright/test").Browser, user: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/");
  await page.getByRole("link", { name: strings.ar.signIn }).click();
  await idpLogin(page, user);
  await expect(page.getByRole("navigation", { name: strings.ar.section })).toBeVisible();
  return { context, page };
}

async function variations(page: Page, who: string, show: (locale: "ar" | "en") => Promise<void>) {
  for (const locale of ["ar", "en"] as const) {
    if (locale === "en") {
      await page.getByRole("button", { name: strings.ar.language }).click();
    }
    await show(locale);
    for (const width of [320, 390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await checkVariation(page, `${who} ${locale} ${width}`);
      if (process.env.E2E_SCREENSHOT_DIR) {
        await page.screenshot({ path: `${process.env.E2E_SCREENSHOT_DIR}/money-${who}-${locale}-${width}.png`, fullPage: true });
      }
    }
    await page.setViewportSize({ width: 390, height: 1000 });
    await page.evaluate(() => (document.documentElement.style.fontSize = "200%"));
    await checkVariation(page, `${who} ${locale} 390 text 200%`);
    await page.evaluate(() => (document.documentElement.style.fontSize = ""));
  }
}

test("MBQA-006: money amounts stay intact on the cook statement and the finance view (12 variations)", async ({ browser }) => {
  test.setTimeout(240_000);
  expect(Number(sql(`SELECT count(*) FROM customer_order WHERE kitchen_id = '${KITCHEN}' AND state = 'COMPLETED'`))).toBeGreaterThan(0);

  const cook = await open(browser, "cook-a");
  await variations(cook.page, "cook", async (locale) => {
    await cook.page.getByRole("button", { name: strings[locale].tabs.statement }).click();
    await expect(cook.page.locator("dl.summary")).toBeVisible();
  });
  await cook.context.close();

  sql(`INSERT INTO user_grant (id, user_id, permission, grant_reason)
       SELECT gen_random_uuid(), id, 'finance.read', 'e2e synthetic grant (MBQA-006)' FROM app_user WHERE subject = '${OPS_SUBJECT}'
       AND NOT EXISTS (SELECT 1 FROM user_grant g WHERE g.user_id = app_user.id AND g.permission = 'finance.read' AND g.revoked_at IS NULL)`);
  const finance = await open(browser, "ops-a");
  await variations(finance.page, "finance", async (locale) => {
    const t = strings[locale];
    await finance.page.getByRole("button", { name: t.ops.finance }).click();
    await finance.page.getByLabel(t.ops.kitchenId).fill(KITCHEN);
    await finance.page.getByRole("button", { name: t.show }).click();
    await expect(finance.page.locator("dl.summary").first()).toBeVisible();
  });
  await finance.context.close();
});
