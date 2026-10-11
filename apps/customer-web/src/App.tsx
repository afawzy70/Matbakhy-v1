import { brand, direction, type Locale } from "@matbakhy/design-tokens";
import { useEffect, useState } from "react";

// Minimal P01 shell. Brand name, colors and fonts come from @matbakhy/design-tokens (MB-003);
// product screens arrive in later stories.
const SWITCH_LABEL: Record<Locale, string> = { ar: "English", en: "العربية" };

export function title(locale: Locale): string {
  return brand.name[locale];
}

export function App() {
  const [locale, setLocale] = useState<Locale>("ar");

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = direction(locale);
    document.title = title(locale);
  }, [locale]);

  return (
    <main className="shell">
      <h1>{title(locale)}</h1>
      <p>{brand.slogan[locale]}</p>
      <button type="button" onClick={() => setLocale(locale === "ar" ? "en" : "ar")}>
        {SWITCH_LABEL[locale]}
      </button>
    </main>
  );
}
