/* global document -- used only inside page.evaluate callbacks that run in Chromium */
// Renders the draft brand specimen (wordmarks, Arabic/Latin glyphs, mixed bidi content, contrast
// swatches) for light and dark themes at phone and desktop widths, and fails unless the Arabic font
// actually loaded. Usage: node scripts/render-specimen.mjs <output-dir>
// Uses the preinstalled Chromium (PLAYWRIGHT_BROWSERS_PATH or CHROMIUM_PATH).
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import { chromium } from "playwright-core";
import { contrastRatio, renderCss } from "../src/css.ts";
import { wordmarkSvg } from "../src/logo.ts";

const out = resolve(process.argv[2] ?? "specimen");
mkdirSync(out, { recursive: true });
const tokens = JSON.parse(readFileSync(new URL("../tokens.json", import.meta.url), "utf8"));
const require = createRequire(import.meta.url);
const fontDir = dirname(require.resolve("@fontsource/ibm-plex-sans-arabic/package.json"));
const fontCss = ["arabic-400", "arabic-700", "latin-400", "latin-700"]
  .map((f) => `<link rel="stylesheet" href="file://${join(fontDir, f + ".css")}">`).join("\n");
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");

const ARABIC = "ا ب ت ث ج ح خ د ذ ر ز س ش ص ض ط ظ ع غ ف ق ك ل م ن ه و ي ء ة ى ئ ؤ أ إ آ";
// Segments: plain strings are escaped; [text] arrays are embedded opposite-direction runs wrapped in <bdi>.
const SAMPLES = {
  ar: [["لسه مفيش وجبات متاحة في منطقتك. جرّب منطقة أو ميعاد تاني."], ["الكمية المتاحة اتغيّرت. راجع السلة قبل تأكيد الطلب."],
    ["الإجمالي: ", ["٢٧٠٫٠٠ ج.م"], " (", ["EGP 270.00"], ")"], ["العنوان: ١٢ شارع التحرير، الدقي — ", ["Building 4, Floor 2"]]],
  en: [["No meals are available here yet. Try another area or time."], ["Availability changed. Review your basket before confirming."],
    ["Total: ", ["EGP 270.00"], " (", ["٢٧٠٫٠٠ ج.م"], ")"], ["Address: 12 Tahrir St, Dokki — ", ["مبنى ٤، الدور ٢"]]],
};
const segments = (parts) => parts.map((p) => (Array.isArray(p) ? `<bdi>${esc(p[0])}</bdi>` : esc(p))).join("");

function page(theme, locale) {
  const palette = tokens.color[theme];
  const pairs = tokens.contrastPairs.map(([fg, bg, min]) => {
    const ratio = contrastRatio(palette[fg], palette[bg]);
    return `<div class="sw" style="background:${palette[bg]};color:${palette[fg]}"><b>${fg} / ${bg}</b><span>${ratio.toFixed(2)}:1 (min ${min})</span></div>`;
  }).join("");
  const name = tokens.brand.name[locale];
  const dir = locale === "ar" ? "rtl" : "ltr";
  return `<!doctype html><html lang="${locale}" dir="${dir}"><head><meta charset="utf-8">${fontCss}
<style>${renderCss(tokens)}
:root { color-scheme: ${theme}; }
body { margin:0; padding:24px; background:${palette.background}; color:${palette.foreground}; font-family: var(--mb-font-family); }
h1,h2 { margin: 16px 0 8px; } .note { color:${palette.foregroundMuted}; font-size:14px; }
.glyphs { font-size: 28px; line-height: 1.8; } .sample { font-size: 18px; margin: 4px 0; }
.bidi bdi { unicode-bidi: isolate; }
.grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.sw { padding: 12px; border-radius: 8px; border: 1px solid ${palette.border}; display:flex; flex-direction:column; gap:4px; font-size:14px; }
.btn { display:inline-flex; align-items:center; min-height:44px; padding: 0 16px; border-radius: 8px; background:${palette.primary}; color:${palette.onPrimary}; font-weight:700; }
</style></head><body>
<p class="note">DRAFT brand specimen — ${tokens.status}. Trademark clearance: ${esc(tokens.brand.trademarkClearance)}. Theme: ${theme}.</p>
${wordmarkSvg({ name, locale, palette, fontFamily: tokens.font.family })}
<p class="sample">${esc(tokens.brand.slogan[locale])}</p>
<h2>${locale === "ar" ? "الحروف" : "Arabic glyphs"}</h2><p class="glyphs" lang="ar" dir="rtl">${ARABIC}</p>
<p class="glyphs">٠١٢٣٤٥٦٧٨٩ · 0123456789 · Aa Bb Gg Qq</p>
<h2>${locale === "ar" ? "نصوص" : "Content"}</h2>${SAMPLES[locale].map((s) => `<p class="sample bidi">${segments(s)}</p>`).join("")}
<p><label style="display:inline-flex;flex-direction:column;gap:4px">${locale === "ar" ? "رقم الموبايل" : "Mobile number"}<input value="+20 100 000 0000" dir="ltr" style="min-height:44px;padding:0 12px;border:1px solid ${palette.borderStrong};border-radius:8px;background:${palette.surface};color:${palette.foreground};font:inherit"></label></p>
<p><span class="btn">${locale === "ar" ? "أكّد الطلب" : "Confirm order"}</span></p>
<h2>${locale === "ar" ? "التباين" : "Contrast"}</h2><div class="grid">${pairs}</div>
</body></html>`;
}

const executablePath = process.env.CHROMIUM_PATH
  ?? [join(process.env.PLAYWRIGHT_BROWSERS_PATH ?? "/opt/pw-browsers", "chromium-1194/chrome-linux/chrome")].find(existsSync);
const browser = await chromium.launch({ executablePath });
const results = [];
try {
  for (const theme of ["light", "dark"]) {
    for (const locale of ["ar", "en"]) {
      const file = join(out, `specimen-${theme}-${locale}.html`);
      writeFileSync(file, page(theme, locale));
      for (const [label, width] of [["phone", 390], ["desktop", 1280]]) {
        const p = await browser.newPage({ viewport: { width, height: 900 } });
        await p.goto("file://" + file);
        await p.evaluate(() => document.fonts.ready);
        const fonts = await p.evaluate(async () => {
          await document.fonts.load('400 16px "IBM Plex Sans Arabic"', "مطبخي");
          await document.fonts.load('700 16px "IBM Plex Sans Arabic"', "Matbakhy");
          return {
            arabic: document.fonts.check('16px "IBM Plex Sans Arabic"', "مطبخي ج.م"),
            loaded: [...document.fonts].filter((f) => f.family.includes("IBM Plex Sans Arabic") && f.status === "loaded").length,
          };
        });
        if (!fonts.arabic || fonts.loaded < 2) {
          throw new Error(`Arabic font not loaded for ${theme}/${locale}/${label}: ${JSON.stringify(fonts)}`);
        }
        const png = join(out, `specimen-${theme}-${locale}-${label}.png`);
        await p.screenshot({ path: png, fullPage: true });
        results.push({ theme, locale, viewport: label, width, png, fontFacesLoaded: fonts.loaded });
        await p.close();
      }
    }
  }
} finally {
  await browser.close();
}
writeFileSync(join(out, "specimen-results.json"), JSON.stringify(results, null, 2) + "\n");
console.log(`PASS: rendered ${results.length} specimens with IBM Plex Sans Arabic loaded into ${out}`);
