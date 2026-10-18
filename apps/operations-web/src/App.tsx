import { brand, direction, type Locale } from "@matbakhy/design-tokens";
import { useEffect, useMemo, useState } from "react";
import { API_BASE, createApi, errorCode, loginUrl, stepUpUrl, type Api } from "./api";
import { Dashboard } from "./cook/Dashboard";
import { builtForDemo, EnvironmentNotice } from "./EnvironmentNotice";
import { Meals } from "./cook/Meals";
import { Onboarding } from "./cook/Onboarding";
import { Orders } from "./cook/Orders";
import { SettingsPanel } from "./cook/Settings";
import { Slots } from "./cook/Slots";
import { Insights } from "./cook/Insights";
import { Statement } from "./cook/Statement";
import { Cases } from "./ops/Cases";
import { Catalogue } from "./ops/Catalogue";
import { Configuration } from "./ops/Configuration";
import { Finance } from "./ops/Finance";
import { OpsOrders } from "./ops/OpsOrders";
import { Reviews } from "./ops/Reviews";
import { SecurityTab } from "./ops/Security";
import { Staff } from "./ops/Staff";
import type { Ctx } from "./cook/shared";
import { strings } from "./i18n";

const COOK_TABS = ["dashboard", "orders", "meals", "slots", "statement", "insights", "settings"] as const;
type Tab = (typeof COOK_TABS)[number] | "reviews" | "catalogue" | "cases" | "opsOrders" | "finance" | "configuration" | "staff" | "security";

export function title(locale: Locale): string {
  return `${brand.name[locale]} — ${strings[locale].section}`;
}

/** Which sections the server allows: a kitchen for the cook workspace, staff permissions for review and moderation. */
type Session =
  | { kind: "checking" }
  | { kind: "signedOut" }
  | { kind: "error" }
  | { kind: "stepUp" }
  | { kind: "in"; kitchen: boolean; serverOffsetMs: number; reviews: boolean; catalogue: boolean; cases: boolean; opsOrders: boolean; finance: boolean; configuration: boolean; staff: boolean; mfaPolicy: boolean; mfaReset: boolean };

function allowedTabs(s: Extract<Session, { kind: "in" }>): Tab[] {
  const sections: Tab[] = [
    ...(s.kitchen ? COOK_TABS : []),
    ...(s.reviews ? (["reviews"] as const) : []),
    ...(s.catalogue ? (["catalogue"] as const) : []),
    ...(s.cases ? (["cases"] as const) : []),
    ...(s.opsOrders ? (["opsOrders"] as const) : []),
    ...(s.finance ? (["finance"] as const) : []),
    ...(s.configuration ? (["configuration"] as const) : []),
    ...(s.staff ? (["staff"] as const) : []),
    ...(s.mfaPolicy || s.mfaReset ? (["security"] as const) : []),
  ];
  // Account security (D18) is shown beside the other sections; an applicant with none keeps the onboarding screen.
  return sections.length > 0 && !sections.includes("security") ? [...sections, "security"] : sections;
}

/** Signed in? Approved kitchen? The server decides; 403 on the dashboard means no approved kitchen. */
async function checkSession(api: Api): Promise<Session> {
  try {
    const me = await api.GET("/api/v1/me");
    if (me.response.status === 401) {
      return { kind: "signedOut" };
    }
    const [d, reviews, catalogue, cases, opsOrders, finance, configuration, staff, security] = await Promise.all([
      api.GET("/api/v1/cook/dashboard"),
      api.GET("/api/v1/ops/cooks", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/catalogue", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/cases", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/orders", { params: { query: { limit: 1 } } }),
      // An unknown kitchen answers 404 to finance staff and 403 to everyone else.
      api.GET("/api/v1/ops/finance/statements", { params: { query: { kitchenId: "00000000-0000-0000-0000-000000000000" } } }),
      api.GET("/api/v1/ops/configuration"),
      api.GET("/api/v1/ops/staff"),
      // Which security administration this account may do (from its own grants); every account may use its own settings.
      api.GET("/api/v1/me/security"),
    ]);
    // The administrator's policy requires a second factor this session has not passed: nothing else will load, so say so.
    if ([d, reviews, catalogue, cases, opsOrders, configuration, staff].some((r) => errorCode(r.error) === "STEP_UP_REQUIRED")) {
      return { kind: "stepUp" };
    }
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
      mfaPolicy: security.data?.canManagePolicy === true,
      mfaReset: security.data?.canResetMfa === true,
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
  const [demoByApi, setDemoByApi] = useState(false);

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

  // Demo/test environments are labelled on every screen (owner decision 2026-10-14); a failed read keeps the build-time flag only.
  useEffect(() => {
    let active = true;
    api
      .GET("/api/v1/configuration/public")
      .then((r) => {
        if (active && r.data) {
          setDemoByApi(r.data.environment?.demo === true);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [api]);

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
      <EnvironmentNotice show={demoByApi || builtForDemo()} title={t.demoNoticeTitle} text={t.demoNotice} />
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
        {session.kind === "stepUp" ? (
          <section className="card" role="alert">
            <p>{t.sec.requiredScreen}</p>
            <a className="button" href={stepUpUrl(window.location.href)}>
              {t.sec.verifyNow}
            </a>
          </section>
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
                                : name === "security"
                                  ? t.sec.tab
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
            {current === "insights" ? <Insights ctx={ctx} /> : null}
            {current === "settings" ? <SettingsPanel ctx={ctx} /> : null}
            {current === "reviews" ? <Reviews ctx={ctx} /> : null}
            {current === "catalogue" ? <Catalogue ctx={ctx} /> : null}
            {current === "cases" ? <Cases ctx={ctx} /> : null}
            {current === "opsOrders" ? <OpsOrders ctx={ctx} /> : null}
            {current === "finance" ? <Finance ctx={ctx} /> : null}
            {current === "configuration" ? <Configuration ctx={ctx} /> : null}
            {current === "staff" ? <Staff ctx={ctx} /> : null}
            {current === "security" ? <SecurityTab ctx={ctx} policyAdmin={session.mfaPolicy} resetAllowed={session.mfaReset} /> : null}
          </>
        ) : null}
      </main>
      <footer className="muted footer">API: {API_BASE}</footer>
    </div>
  );
}
