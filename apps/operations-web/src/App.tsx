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
import { Cases } from "./ops/Cases";
import { Catalogue } from "./ops/Catalogue";
import { Configuration } from "./ops/Configuration";
import { Finance } from "./ops/Finance";
import { OpsOrders } from "./ops/OpsOrders";
import { Reviews } from "./ops/Reviews";
import { Staff } from "./ops/Staff";
import type { Ctx } from "./cook/shared";
import { strings } from "./i18n";

const COOK_TABS = ["dashboard", "orders", "meals", "slots", "statement", "settings"] as const;
type Tab = (typeof COOK_TABS)[number] | "reviews" | "catalogue" | "cases" | "opsOrders" | "finance" | "configuration" | "staff";

export function title(locale: Locale): string {
  return `${brand.name[locale]} — ${strings[locale].section}`;
}

/** Which sections the server allows: a kitchen for the cook workspace, staff permissions for review and moderation. */
type Session =
  | { kind: "checking" }
  | { kind: "signedOut" }
  | { kind: "error" }
  | { kind: "in"; kitchen: boolean; serverOffsetMs: number; reviews: boolean; catalogue: boolean; cases: boolean; opsOrders: boolean; finance: boolean; configuration: boolean; staff: boolean };

function allowedTabs(s: Extract<Session, { kind: "in" }>): Tab[] {
  return [
    ...(s.kitchen ? COOK_TABS : []),
    ...(s.reviews ? (["reviews"] as const) : []),
    ...(s.catalogue ? (["catalogue"] as const) : []),
    ...(s.cases ? (["cases"] as const) : []),
    ...(s.opsOrders ? (["opsOrders"] as const) : []),
    ...(s.finance ? (["finance"] as const) : []),
    ...(s.configuration ? (["configuration"] as const) : []),
    ...(s.staff ? (["staff"] as const) : []),
  ];
}

/** Signed in? Approved kitchen? The server decides; 403 on the dashboard means no approved kitchen. */
async function checkSession(api: Api): Promise<Session> {
  try {
    const me = await api.GET("/api/v1/me");
    if (me.response.status === 401) {
      return { kind: "signedOut" };
    }
    const [d, reviews, catalogue, cases, opsOrders, finance, configuration, staff] = await Promise.all([
      api.GET("/api/v1/cook/dashboard"),
      api.GET("/api/v1/ops/cooks", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/catalogue", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/cases", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/orders", { params: { query: { limit: 1 } } }),
      // An unknown kitchen answers 404 to finance staff and 403 to everyone else.
      api.GET("/api/v1/ops/finance/statements", { params: { query: { kitchenId: "00000000-0000-0000-0000-000000000000" } } }),
      api.GET("/api/v1/ops/configuration"),
      api.GET("/api/v1/ops/staff"),
    ]);
    if (!d.data && d.response.status !== 403) {
      return { kind: "error" };
    }
    return {
      kind: "in",
      kitchen: d.data !== undefined,
      serverOffsetMs: d.data ? Date.parse(d.data.serverTime) - Date.now() : 0,
      reviews: reviews.response.ok,
      catalogue: catalogue.response.ok,
      cases: cases.response.ok,
      opsOrders: opsOrders.response.ok,
      finance: finance.response.status === 404,
      configuration: configuration.response.ok,
      staff: staff.response.ok,
    };
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

  // The first allowed section is shown until the user picks another one.
  const current: Tab | null =
    session.kind !== "in"
      ? null
      : allowedTabs(session).includes(tab)
        ? tab
        : (allowedTabs(session)[0] ?? null);

  const ctx: Ctx = {
    api,
    t,
    locale,
    onSignedOut: () => setSession({ kind: "signedOut" }),
    serverOffsetMs: session.kind === "in" ? session.serverOffsetMs : 0,
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
          <button type="button" onClick={() => {
              const next: Locale = locale === "ar" ? "en" : "ar";
              // Requests switch language in the same event as the state, before any section reloads for the new language.
              api.setLanguage(next);
              setLocale(next);
            }}>
            {t.language}
          </button>
          {session.kind === "in" ? (
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
        {session.kind === "in" ? (
          <>
            {current !== null ? (
            <nav aria-label={t.section} className="tabs">
              {allowedTabs(session).map((name) => (
                <button key={name} type="button" aria-current={current === name ? "page" : undefined} onClick={() => setTab(name)}>
                  {name === "reviews"
                    ? t.ops.reviews
                    : name === "catalogue"
                      ? t.ops.catalogue
                      : name === "cases"
                        ? t.ops.cases
                        : name === "opsOrders"
                          ? t.ops.orders
                          : name === "finance"
                            ? t.ops.finance
                            : name === "configuration"
                              ? t.ops.configuration
                              : name === "staff"
                                ? t.ops.staff
                                : t.tabs[name]}
                </button>
              ))}
            </nav>
            ) : null}
            {current === null ? <Onboarding ctx={ctx} /> : null}
            {current === "dashboard" ? <Dashboard ctx={ctx} onOpenOrders={() => setTab("orders")} /> : null}
            {current === "orders" ? <Orders ctx={ctx} /> : null}
            {current === "meals" ? <Meals ctx={ctx} /> : null}
            {current === "slots" ? <Slots ctx={ctx} /> : null}
            {current === "statement" ? <Statement ctx={ctx} /> : null}
            {current === "settings" ? <SettingsPanel ctx={ctx} /> : null}
            {current === "reviews" ? <Reviews ctx={ctx} /> : null}
            {current === "catalogue" ? <Catalogue ctx={ctx} /> : null}
            {current === "cases" ? <Cases ctx={ctx} /> : null}
            {current === "opsOrders" ? <OpsOrders ctx={ctx} /> : null}
            {current === "finance" ? <Finance ctx={ctx} /> : null}
            {current === "configuration" ? <Configuration ctx={ctx} /> : null}
            {current === "staff" ? <Staff ctx={ctx} /> : null}
          </>
        ) : null}
      </main>
      <footer className="muted footer">API: {API_BASE}</footer>
    </div>
  );
}
