import { useEffect, useState } from "react";

type Locale = "ar" | "en";

// Minimal P01 shell only. Final copy, branding and layout come from MB-003/MB-004 and later stories.
const TITLES: Record<Locale, string> = { ar: "مطبخي — التشغيل", en: "Operations web shell" };
const SWITCH_LABEL: Record<Locale, string> = { ar: "English", en: "العربية" };

export function App() {
  const [locale, setLocale] = useState<Locale>("ar");

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = locale === "ar" ? "rtl" : "ltr";
  }, [locale]);

  return (
    <main>
      <h1>{TITLES[locale]}</h1>
      <button type="button" onClick={() => setLocale(locale === "ar" ? "en" : "ar")}>
        {SWITCH_LABEL[locale]}
      </button>
    </main>
  );
}
