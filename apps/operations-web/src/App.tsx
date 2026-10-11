import { brand, direction, type Locale } from "@matbakhy/design-tokens";
import { useEffect, useMemo, useState } from "react";
import { API_BASE, createApi, loginUrl, type Api } from "./api";
import { Dashboard } from "./cook/Dashboard";
import { Meals } from "./cook/Meals";
import { Onboarding } from "./cook/Onboarding";
import { Orders } from "./cook/Orders";
import { SettingsPanel } from "./cook/Settings";
import { Slots } from "./cook/Slots";
import { Statement } from "./cook/Statement";
import type { Ctx } from "./cook/shared";
import { strings } from "./i18n";

const TABS = ["dashboard", "orders", "meals", "slots", "statement", "settings"] as const;
type Tab = (typeof TABS)[number];

export function title(locale: Locale): string {
  return `${brand.name[locale]} — ${strings[locale].section}`;
}

type Session = { kind: "checking" } | { kind: "signedOut" } | { kind: "noKitchen" } | { kind: "cook"; serverOffsetMs: number } | { kind: "error" };

/** Signed in? Approved kitchen? The server decides; 403 on the dashboard means no approved kitchen. */
async function checkSession(api: Api): Promise<Session> {
  try {
    const me = await api.GET("/api/v1/me");
    if (me.response.status === 401) {
      return { kind: "signedOut" };
    }
    const d = await api.GET("/api/v1/cook/dashboard");
    if (d.data) {
      return { kind: "cook", serverOffsetMs: Date.parse(d.data.serverTime) - Date.now() };
    }
    return d.response.status === 403 ? { kind: "noKitchen" } : { kind: "error" };
  } catch {
    return { kind: "error" };
  }
}

export function App({ fetchImpl }: { fetchImpl?: typeof fetch } = {}) {
  const [locale, setLocale] = useState<Locale>("ar");
  const [session, setSession] = useState<Session>({ kind: "checking" });
  const [tab, setTab] = useState<Tab>("dashboard");
  const t = strings[locale];
  const api = useMemo(() => createApi(fetchImpl), [fetchImpl]);
  const [probe, setProbe] = useState(0);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = direction(locale);
    document.title = title(locale);
  }, [locale]);

  useEffect(() => {
    let active = true;
    checkSession(api).then((s) => {
      if (active) {
        setSession(s);
      }
    });
    return () => {
      active = false;
    };
  }, [api, probe]);

  const ctx: Ctx = {
    api,
    t,
    locale,
    onSignedOut: () => setSession({ kind: "signedOut" }),
    serverOffsetMs: session.kind === "cook" ? session.serverOffsetMs : 0,
  };

  async function signOut() {
    const r = await api.POST("/api/v1/auth/logout" as "/api/v1/me/deletion-requests", {} as never).catch(() => null);
    const body = (r?.data ?? null) as { idpLogoutUrl?: string | null } | null;
    setSession({ kind: "signedOut" });
    if (body?.idpLogoutUrl) {
      window.location.assign(body.idpLogoutUrl);
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <h1>{title(locale)}</h1>
        <div className="topbar-actions">
          <button type="button" onClick={() => setLocale(locale === "ar" ? "en" : "ar")}>
            {t.language}
          </button>
          {session.kind === "cook" || session.kind === "noKitchen" ? (
            <button type="button" className="secondary" onClick={() => void signOut()}>
              {t.signOut}
            </button>
          ) : null}
        </div>
      </header>
      <main className="shell">
        {session.kind === "checking" ? <p role="status">{t.loading}</p> : null}
        {session.kind === "error" ? (
          <div role="alert" className="notice notice-danger">
            <p>{t.errors.NETWORK}</p>
            <button type="button" onClick={() => setProbe((n) => n + 1)}>
              {t.retry}
            </button>
          </div>
        ) : null}
        {session.kind === "signedOut" ? (
          <section className="card">
            <p>{brand.slogan[locale]}</p>
            <p>{t.signedOut}</p>
            <a className="button" href={loginUrl(window.location.href)}>
              {t.signIn}
            </a>
          </section>
        ) : null}
        {session.kind === "noKitchen" ? <Onboarding ctx={ctx} /> : null}
        {session.kind === "cook" ? (
          <>
            <nav aria-label={t.section} className="tabs">
              {TABS.map((name) => (
                <button key={name} type="button" aria-current={tab === name ? "page" : undefined} onClick={() => setTab(name)}>
                  {t.tabs[name]}
                </button>
              ))}
            </nav>
            {tab === "dashboard" ? <Dashboard ctx={ctx} onOpenOrders={() => setTab("orders")} /> : null}
            {tab === "orders" ? <Orders ctx={ctx} /> : null}
            {tab === "meals" ? <Meals ctx={ctx} /> : null}
            {tab === "slots" ? <Slots ctx={ctx} /> : null}
            {tab === "statement" ? <Statement ctx={ctx} /> : null}
            {tab === "settings" ? <SettingsPanel ctx={ctx} /> : null}
          </>
        ) : null}
      </main>
      <footer className="muted footer">API: {API_BASE}</footer>
    </div>
  );
}
