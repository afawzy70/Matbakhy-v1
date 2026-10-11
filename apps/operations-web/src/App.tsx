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
import { Catalogue } from "./ops/Catalogue";
import { Reviews } from "./ops/Reviews";
import type { Ctx } from "./cook/shared";
import { strings } from "./i18n";

const COOK_TABS = ["dashboard", "orders", "meals", "slots", "statement", "settings"] as const;
type Tab = (typeof COOK_TABS)[number] | "reviews" | "catalogue";

export function title(locale: Locale): string {
  return `${brand.name[locale]} — ${strings[locale].section}`;
}

/** Which sections the server allows: a kitchen for the cook workspace, staff permissions for review and moderation. */
type Session =
  | { kind: "checking" }
  | { kind: "signedOut" }
  | { kind: "error" }
  | { kind: "in"; kitchen: boolean; serverOffsetMs: number; reviews: boolean; catalogue: boolean };

/** Signed in? Approved kitchen? The server decides; 403 on the dashboard means no approved kitchen. */
async function checkSession(api: Api): Promise<Session> {
  try {
    const me = await api.GET("/api/v1/me");
    if (me.response.status === 401) {
      return { kind: "signedOut" };
    }
    const [d, reviews, catalogue] = await Promise.all([
      api.GET("/api/v1/cook/dashboard"),
      api.GET("/api/v1/ops/cooks", { params: { query: { limit: 1 } } }),
      api.GET("/api/v1/ops/catalogue", { params: { query: { limit: 1 } } }),
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
      : (session.kitchen && (COOK_TABS as readonly string[]).includes(tab)) || (tab === "reviews" && session.reviews) || (tab === "catalogue" && session.catalogue)
        ? tab
        : session.kitchen
          ? "dashboard"
          : session.reviews
            ? "reviews"
            : session.catalogue
              ? "catalogue"
              : null;

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
          <button type="button" onClick={() => setLocale(locale === "ar" ? "en" : "ar")}>
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
              {[...(session.kitchen ? COOK_TABS : []), ...(session.reviews ? (["reviews"] as const) : []), ...(session.catalogue ? (["catalogue"] as const) : [])].map(
                (name) => (
                  <button key={name} type="button" aria-current={current === name ? "page" : undefined} onClick={() => setTab(name)}>
                    {name === "reviews" ? t.ops.reviews : name === "catalogue" ? t.ops.catalogue : t.tabs[name]}
                  </button>
                ),
              )}
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
          </>
        ) : null}
      </main>
      <footer className="muted footer">API: {API_BASE}</footer>
    </div>
  );
}
