import type { Palette } from "./css";

export interface WordmarkOptions {
  name: string;
  locale: "ar" | "en";
  palette: Palette;
  fontFamily: string;
}

const escapeXml = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/**
 * DRAFT wordmark: a cooking-pot mark with steam plus the brand name, rendered from tokens so the
 * name and colors can change without editing app code. Not an approved or cleared logo (O05).
 */
export function wordmarkSvg({ name, locale, palette, fontFamily }: WordmarkOptions): string {
  const rtl = locale === "ar";
  const width = 320;
  const markX = rtl ? width - 72 : 8;
  const textX = rtl ? width - 84 : 84;
  const primary = palette["primary"] ?? "#000000";
  const accent = palette["accent"] ?? "#000000";
  const fg = palette["foreground"] ?? "#000000";
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="72" viewBox="0 0 ${width} 72" role="img" aria-label="${escapeXml(name)}">
  <g transform="translate(${markX} 4)">
    <path d="M10 30 h44 v8 a22 22 0 0 1 -44 0 z" fill="${primary}"/>
    <rect x="4" y="26" width="56" height="6" rx="3" fill="${primary}"/>
    <path d="M22 20 c-6 -6 6 -10 0 -16 M32 20 c-6 -6 6 -10 0 -16 M42 20 c-6 -6 6 -10 0 -16" stroke="${accent}" stroke-width="3" fill="none" stroke-linecap="round"/>
  </g>
  <text x="${textX}" y="46" font-family="${escapeXml(fontFamily)}" font-size="34" font-weight="700" fill="${fg}" direction="${rtl ? "rtl" : "ltr"}" text-anchor="start">${escapeXml(name)}</text>
</svg>`;
}
