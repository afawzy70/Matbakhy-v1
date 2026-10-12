import { expect, test, type Page } from "@playwright/test";
import { strings } from "../src/i18n";
import { axe, env, sql } from "./fixtures";

/**
 * MB-029 on the operations web, after the cook and customer workflows have created real data: the cook (cook-a) and a
 * staff user (ops-a, grants from the cook workflow) open every section they are allowed, in Arabic RTL and English LTR
 * at 360, 390, 768 and 1440 px. Every section must have no page-level horizontal overflow, every visible control at
 * least 44 px, and tables either fitting or scrolling inside their own container (no clipped totals). axe-core WCAG
 * 2.2 A/AA rules report no serious or critical finding at 390 and 1440 px. One meal gets a very long synthetic title
 * (test-only SQL) to exercise long values in lists and tables. Automated rules are not a full audit.
 */
const PASSWORD = env("E2E_USER_PASSWORD");
const LONG_TITLE =
  "كشري تجريبي بعنوان طويل جدًا لاختبار التفاف النص في الجداول والقوائم ومراجعة الأطباق VeryLongUnbrokenSyntheticTokenWithoutSpaces1234567890";

async function idpLogin(page: Page, user: string) {
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

async function checkSection(page: Page, where: string, scan: boolean) {
  await page.waitForLoadState("networkidle");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `overflow ${where}`).toBe(true);
  const undersized = await page.locator("button:visible, a:visible, input:visible, select:visible, textarea:visible").evaluateAll((els) =>
    els.filter((e) => {
      const r = e.getBoundingClientRect();
      return r.width > 0 && (r.width < 43.9 || r.height < 43.9);
    }).map((e) => `${e.tagName}:${(e.textContent ?? (e as HTMLInputElement).name ?? "").trim().slice(0, 30)}`));
  expect(undersized, `touch targets ${where}`).toEqual([]);
  const clippedTables = await page.locator("table:visible").evaluateAll((tables) =>
    tables.filter((table) => {
      let el: HTMLElement | null = table.parentElement;
      while (el && el !== document.body) {
        const style = getComputedStyle(el);
        if (style.overflowX === "auto" || style.overflowX === "scroll") {
          return false;
        }
        if (el.clientWidth < table.scrollWidth - 1 && (style.overflowX === "hidden" || style.overflowX === "clip")) {
          return true;
        }
        el = el.parentElement;
      }
      return table.scrollWidth > window.innerWidth;
    }).length);
  expect(clippedTables, `tables without their own scroll container ${where}`).toBe(0);
  if (scan) {
    await axe(page, where);
  }
}

async function everySection(page: Page, who: string) {
  for (const locale of ["ar", "en"] as const) {
    const t = strings[locale];
    if (locale === "en") {
      await page.getByRole("button", { name: strings.ar.language }).click();
    }
    await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
    for (const width of [360, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const tabs = page.getByRole("navigation", { name: t.section }).getByRole("button");
      const count = await tabs.count();
      expect(count, `${who} sees sections`).toBeGreaterThan(0);
      for (let i = 0; i < count; i++) {
        const tab = tabs.nth(i);
        const name = (await tab.textContent())?.trim() ?? String(i);
        await tab.click();
        await expect(tab).toHaveAttribute("aria-current", "page");
        await checkSection(page, `${who} ${locale} ${width} ${name}`, width === 390 || width === 1440);
        if (process.env.E2E_SCREENSHOT_DIR && (width === 390 || width === 1440)) {
          await page.screenshot({ path: `${process.env.E2E_SCREENSHOT_DIR}/ops-${who}-${locale}-${i}-${width}.png`, fullPage: true });
        }
      }
    }
  }
}

test("operations sections keep layout and accessibility across widths and both directions", async ({ browser }) => {
  test.setTimeout(240_000);
  sql(`UPDATE meal SET title_ar = '${LONG_TITLE}' WHERE id = (SELECT id FROM meal WHERE title_ar = 'كشري تجريبي')`);
  for (const user of ["cook-a", "ops-a"]) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("/");
    await page.getByRole("link", { name: strings.ar.signIn }).click();
    await idpLogin(page, user);
    await expect(page.getByRole("navigation", { name: strings.ar.section })).toBeVisible();
    await everySection(page, user);
    await context.close();
  }
});
