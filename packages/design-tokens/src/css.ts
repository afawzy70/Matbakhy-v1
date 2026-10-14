// Pure functions shared by the app runtime, the CSS generator and the tests. No imports, so Node can
// run this file directly with type stripping.

export type ColorRole = string;
export type Palette = Record<ColorRole, string>;

export interface TokenSource {
  color: { light: Palette; dark: Palette };
  font: { family: string; size: Record<string, number>; weight: Record<string, number>; lineHeight: Record<string, number> };
  space: Record<string, number>;
  radius: Record<string, number>;
  elevation: Record<string, string>;
  touchTarget: { min: number };
}

const kebab = (s: string) => s.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase());

function block(selector: string, vars: [string, string][]): string {
  return `${selector} {\n${vars.map(([k, v]) => `  --mb-${k}: ${v};`).join("\n")}\n}\n`;
}

/** CSS custom properties for web apps. Light is the default; dark applies with the OS preference. */
export function renderCss(t: TokenSource): string {
  const colors = (p: Palette) => Object.entries(p).map(([k, v]) => [`color-${kebab(k)}`, v] as [string, string]);
  const scale = (prefix: string, rec: Record<string, number>, unit: string) =>
    Object.entries(rec).map(([k, v]) => [`${prefix}-${kebab(k)}`, `${v}${unit}`] as [string, string]);
  const statics: [string, string][] = [
    ["font-family", t.font.family],
    ...scale("font-size", t.font.size, "px"),
    ...scale("font-weight", t.font.weight, ""),
    ...scale("line-height", t.font.lineHeight, ""),
    ...scale("space", t.space, "px"),
    ...scale("radius", t.radius, "px"),
    ...Object.entries(t.elevation).map(([k, v]) => [`elevation-${kebab(k)}`, v] as [string, string]),
    ["touch-target-min", `${t.touchTarget.min}px`],
  ];
  return [
    "/* Generated from packages/design-tokens/tokens.json by `pnpm --filter @matbakhy/design-tokens generate`. Do not edit. */\n",
    block(":root", [...statics, ...colors(t.color.light)]),
    "@media (prefers-color-scheme: dark) {\n" + block(":root", colors(t.color.dark)).replace(/^/gm, "  ").trimEnd() + "\n}\n",
  ].join("\n");
}

function channel(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

/** WCAG 2.x relative luminance of a #RRGGBB color. */
export function luminance(hex: string): number {
  const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex);
  if (!m) {
    throw new Error(`Not a #RRGGBB color: ${hex}`);
  }
  const [r, g, b] = [m[1], m[2], m[3]].map((x) => channel(parseInt(x as string, 16)));
  return 0.2126 * (r as number) + 0.7152 * (g as number) + 0.0722 * (b as number);
}

/** WCAG contrast ratio between two #RRGGBB colors (1 to 21). */
export function contrastRatio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (hi + 0.05) / (lo + 0.05);
}
