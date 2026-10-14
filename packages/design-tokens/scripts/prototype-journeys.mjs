/* global document, window, location -- used only inside page.evaluate callbacks that run in Chromium */
// MB-004: walks every critical journey of docs/design/prototype in Arabic and English at phone and
// desktop widths, asserts observable outcomes and captures screenshots for review.
// Usage: node scripts/prototype-journeys.mjs <output-dir>
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { chromium } from "playwright-core";

const out = resolve(process.argv[2] ?? "ux");
mkdirSync(out, { recursive: true });
const proto = new URL("../../../docs/design/prototype/index.html", import.meta.url).href;
const executablePath = process.env.CHROMIUM_PATH
  ?? [join(process.env.PLAYWRIGHT_BROWSERS_PATH ?? "/opt/pw-browsers", "chromium-1194/chrome-linux/chrome")].find(existsSync);
const results = [];
const assert = (cond, msg) => { if (!cond) throw new Error("Assertion failed: " + msg); };

async function run(lang, viewport) {
  const browser = await chromium.launch({ executablePath });
  const page = await browser.newPage({ viewport: { width: viewport.width, height: 900 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  const shot = async (name) => {
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    assert(overflow <= 0, `${name}: horizontal overflow ${overflow}px at ${viewport.width}px`);
    const path = join(out, `${lang}-${viewport.name}-${name}.png`);
    await page.screenshot({ path, fullPage: true });
    return path;
  };
  const id = (t) => page.getByTestId(t);
  const seen = (t, msg) => id(t).waitFor({ state: "visible", timeout: 5000 }).catch(() => { throw new Error("Assertion failed: " + msg); });
  const open = async (hash) => { await page.goto(`${proto}?lang=${lang}${hash}`); await page.waitForFunction((h) => location.hash === h, hash); };
  const steps = [];
  const step = async (name, fn) => { await fn(); steps.push(name); };
  try {
    await open("#/discover");
    await page.evaluate(() => document.fonts.ready);
    const dir = await page.evaluate(() => document.documentElement.dir);
    assert(dir === (lang === "ar" ? "rtl" : "ltr"), `direction ${dir}`);

    await step("discovery", async () => { await seen("meal-card-m1", "meal card"); await shot("01-discovery"); });
    await step("empty zone", async () => {
      await id("zone-select").selectOption("zone-b"); await seen("empty-state", "empty state");
      await shot("02-empty-zone"); await id("zone-select").selectOption("zone-a");
    });
    await step("sold out meal cannot be added", async () => {
      await open("#/meal/m3"); await seen("allergens", "meal m3 rendered"); assert(await id("add-to-basket").isDisabled(), "sold out disabled");
    });
    await step("delivery checkout and order tracking", async () => {
      await open("#/discover"); await id("open-m1").click(); await seen("allergens", "meal m1 rendered");
      await shot("03-meal-detail");
      await id("slot-s2").click(); await id("quantity").fill("2");
      await id("add-to-basket").click();
      await seen("totals", `checkout after add-to-basket (url ${page.url()})`);
      const total = await id("total").innerText();
      assert(/270|٢٧٠/.test(total), `delivery total shows 270: ${total}`);
      await shot("04-checkout-delivery");
      await id("place-order").click();
      await page.waitForTimeout(200);
      assert(await id("checking").isHidden(), "policy acceptance required before submit");
      assert((await id("accept-policy").getAttribute("aria-invalid")) === "true", "unaccepted policy flagged");
      await id("accept-policy").check(); await id("place-order").click();
      await id("checking").waitFor({ state: "visible" }); await shot("05-checking-order");
      await id("order-state").waitFor(); await shot("06-order-pending");
      for (let i = 0; i < 3; i++) await id("advance").click();
      await seen("handover-code", "handover code visible when READY"); await shot("07-order-ready-code");
      await id("advance").click(); await id("advance").click();
      assert(await id("advance").count() === 0, "completed order has no further steps"); await shot("08-order-completed");
    });
    await step("pickup checkout has no delivery fee", async () => {
      await open("#/meal/m2"); await seen("allergens", "meal m2 rendered"); await id("add-to-basket").click(); await seen("totals", "checkout"); await id("mode-pickup").click();
      const total = await id("total").innerText(); assert(/150|١٥٠/.test(total), `pickup total 150: ${total}`);
      await shot("09-checkout-pickup");
    });
    await step("stock changed blocks submission", async () => {
      await open("#/meal/m2"); await seen("allergens", "meal m2 rendered"); await id("quantity").fill("3"); await id("add-to-basket").click();
      await seen("stock-changed", "stock warning"); assert(await id("place-order").isDisabled(), "submit disabled");
      await shot("10-stock-changed");
    });
    await step("cook accepts and rejects within deadline", async () => {
      await id("area-cook").click(); await id("queue-MB-T-1042").waitFor(); await shot("11-cook-queue");
      await id("accept-MB-T-1042").click(); await id("reject-MB-T-1043").click();
      await seen("queue-result-MB-T-1042", "accepted result"); await seen("queue-result-MB-T-1043", "rejected result");
      await shot("12-cook-queue-actioned");
    });
    await step("operations approves cook and opens cash discrepancy case", async () => {
      await id("area-ops").click(); await shot("13-ops-queues");
      await id("approve-app-1").click(); await id("open-case-MB-T-0991").click();
      await seen("application-result", "approval result"); await seen("case-opened", "finance case");
      await shot("14-ops-actioned");
    });
    assert(errors.length === 0, "page errors: " + errors.join("; "));
    results.push({ lang, viewport: viewport.name, width: viewport.width, status: "PASS", steps });
  } catch (e) {
    const context = { pageErrors: errors, url: page.url(), screen: (await page.locator("#screen").innerText().catch(() => "")).slice(0, 400) };
    await page.screenshot({ path: join(out, `${lang}-${viewport.name}-FAILURE.png`), fullPage: true }).catch(() => {});
    results.push({ lang, viewport: viewport.name, width: viewport.width, status: "FAIL", steps, error: String(e), context });
  } finally {
    await browser.close();
  }
}

const only = (process.env.JOURNEY_ONLY ?? "").split(",").filter(Boolean); // e.g. "en-desktop" for debugging
for (const lang of ["ar", "en"]) {
  for (const viewport of [{ name: "phone", width: 390 }, { name: "desktop", width: 1280 }]) {
    if (only.length === 0 || only.includes(`${lang}-${viewport.name}`)) await run(lang, viewport);
  }
}
writeFileSync(join(out, "journey-results.json"), JSON.stringify(results, null, 2) + "\n");
const failed = results.filter((r) => r.status !== "PASS");
console.log(failed.length ? `FAIL: ${JSON.stringify(failed, null, 2)}` : `PASS: ${results.length} locale/viewport runs, ${results[0].steps.length} journeys each`);
process.exit(failed.length ? 1 : 0);
