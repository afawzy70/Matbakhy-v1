import source from "../tokens.json";
import type { TokenSource } from "./css";

export { contrastRatio, luminance, renderCss, type Palette, type TokenSource } from "./css";
export { wordmarkSvg, type WordmarkOptions } from "./logo";

export type Locale = "ar" | "en";

/** Draft brand values. Apps read the name and slogan from here; never hard-code them. */
export const brand: {
  name: Record<Locale, string>;
  slogan: Record<Locale, string>;
  status: string;
} = { name: source.brand.name, slogan: source.brand.slogan, status: source.status };

export const tokens: TokenSource = source;

export const direction = (locale: Locale): "rtl" | "ltr" => (locale === "ar" ? "rtl" : "ltr");
