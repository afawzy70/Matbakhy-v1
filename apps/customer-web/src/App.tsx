import { brand, direction, type Locale } from "@matbakhy/design-tokens";
import { useEffect, useMemo, useState } from "react";
import { createApi, loginUrl, type Schemas } from "./api";
import { Account, SupportCaseDetail } from "./Account";
import { loadPending, saveCart, savePending } from "./cart";
import { Browse } from "./Browse";
import { Checkout } from "./Checkout";
import { builtForDemo, EnvironmentNotice } from "./EnvironmentNotice";
import { strings } from "./i18n";
import { Kitchen } from "./Kitchen";
import { Meal } from "./Meal";
import { OrderDetail, OrderList } from "./Orders";
import type { Ctx } from "./shared";

export function title(locale: Locale): string {
  return brand.name[locale];
}

function currentRoute(): string {
  return window.location.hash.replace(/^#/, "") || "/";
}

/** Customer web (MB-027, MB-028): browsing is public; ordering needs a session and a verified phone. The server owns all prices. */
export function App({
  fetchImpl,
  assign = (url: string) => window.location.assign(url),
}: { fetchImpl?: typeof fetch; /** Full-page navigation (the identity provider's logout); replaceable in tests. */ assign?: (url: string) => void } = {}) {
  const [locale, setLocale] = useState<Locale>("ar");
  const [route, setRoute] = useState(currentRoute);
  const [me, setMe] = useState<Schemas["CustomerProfile"] | null>(null);
  const [meVersion, setMeVersion] = useState(0);
  const [maxQuantityPerLine, setMaxQuantityPerLine] = useState<number | null>(null);
  const [demoByApi, setDemoByApi] = useState(false);
  const [online, setOnline] = useState(() => typeof navigator === "undefined" || navigator.onLine);
  const api = useMemo(() => createApi(fetchImpl), [fetchImpl]);
  const [signOutState, setSignOutState] = useState<"idle" | "confirm" | "busy" | "failed">("idle");

  const t = strings[locale];

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = direction(locale);
    document.title = title(locale);
  }, [locale]);

  // MBQA-003: a page restored from the back-forward cache after sign-out must not show the previous account's screens;
  // reload it so it asks the server again.
  useEffect(() => {
    const onShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        window.location.reload();
      }
    };
    window.addEventListener("pageshow", onShow);
    return () => window.removeEventListener("pageshow", onShow);
  }, []);

  useEffect(() => {
    const onHash = () => setRoute(currentRoute());
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener("hashchange", onHash);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("hashchange", onHash);
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  useEffect(() => {
    let active = true;
    api
      .GET("/api/v1/me")
      .then((r) => {
        if (active) {
          setMe(r.data ?? null);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [api, meVersion]);

  // Q1 option A: quantity controls follow the server's published limit; without it they fall back to 20 and the slot.
  // Re-read whenever the cart or a meal page opens, so a policy lowered after the page loaded applies before any quote.
  const limitKey = route === "/checkout" || route.startsWith("/meal/") ? route : "";
  useEffect(() => {
    let active = true;
    api
      .GET("/api/v1/configuration/public")
      .then((r) => {
        if (active) {
          // A failed re-read keeps the last published limit rather than loosening the controls.
          if (r.data) {
            setMaxQuantityPerLine(r.data.orderLimits?.maxQuantityPerLine ?? null);
            setDemoByApi(r.data.environment?.demo === true);
          }
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [api, limitKey]);

  /**
   * MBQA-003: ends the web session on the server (Spring Security logout: session invalidated, cookie deleted; CSRF as for
   * every mutation), clears this browser's private state (basket, pending order keys, profile), then ends the identity
   * provider's session so the next sign-in asks for credentials instead of silently restoring this account. With an
   * unconfirmed order in this browser, the customer is told first. If the server cannot be reached, nothing is cleared
   * and the customer stays signed in.
   */
  async function signOut(confirmed: boolean) {
    if (!confirmed && loadPending()) {
      setSignOutState("confirm");
      return;
    }
    setSignOutState("busy");
    const r = await api.POST("/api/v1/auth/logout" as "/api/v1/me/deletion-requests", {} as never).catch(() => null);
    if (!r || (r.response.status !== 200 && r.response.status !== 204 && r.response.status !== 401)) {
      setSignOutState("failed");
      return;
    }
    saveCart(null);
    savePending(null);
    setMe(null);
    setSignOutState("idle");
    const body = (r.data ?? null) as { idpLogoutUrl?: string | null } | null;
    if (body?.idpLogoutUrl) {
      assign(body.idpLogoutUrl);
    } else {
      window.location.hash = "/";
    }
  }

  const ctx: Ctx = {
    api,
    t,
    locale,
    maxQuantityPerLine,
    onSignedOut: () => setMe(null),
    go: (to: string) => {
      window.location.hash = to;
    },
  };

  const mealMatch = /^\/meal\/([0-9a-f-]{36})$/.exec(route);
  const orderMatch = /^\/orders\/([0-9a-f-]{36})$/.exec(route);
  const kitchenMatch = /^\/kitchen\/([a-z0-9-]{3,80})$/.exec(route);
  const caseMatch = /^\/support\/([0-9a-f-]{36})$/.exec(route);
  return (
    <div className="app">
      <EnvironmentNotice show={demoByApi || builtForDemo()} title={t.demoNoticeTitle} text={t.demoNotice} />
      <header className="topbar">
        <h1>{title(locale)}</h1>
        <div className="topbar-actions">
          <a className="button secondary" href="#/checkout" aria-current={route === "/checkout" ? "page" : undefined}>
            {t.nav.cart}
          </a>
          <button type="button" className="secondary" onClick={() => {
              const next: Locale = locale === "ar" ? "en" : "ar";
              // Requests switch language in the same event as the state, before any child loads for the new language.
              api.setLanguage(next);
              setLocale(next);
            }}>
            {t.language}
          </button>
          {me ? (
            <button type="button" className="secondary" disabled={signOutState === "busy"} onClick={() => void signOut(false)}>
              {t.signOut}
            </button>
          ) : (
            <a className="button secondary" href={loginUrl(window.location.href)}>
              {t.signIn}
            </a>
          )}
        </div>
      </header>
      {signOutState === "confirm" ? (
        <div role="alertdialog" aria-labelledby="sign-out-pending" className="notice">
          <p id="sign-out-pending">{t.signOutPending}</p>
          <div className="actions">
            <a className="button" href="#/checkout" onClick={() => setSignOutState("idle")}>
              {t.nav.cart}
            </a>
            <button type="button" className="secondary" onClick={() => void signOut(true)}>
              {t.signOutAnyway}
            </button>
            <button type="button" className="secondary" onClick={() => setSignOutState("idle")}>
              {t.staySignedIn}
            </button>
          </div>
        </div>
      ) : null}
      {signOutState === "failed" ? (
        <p role="alert" className="notice notice-danger">
          {t.signOutFailed}
        </p>
      ) : null}
      {!online ? (
        <p role="alert" className="notice">
          {t.offline}
        </p>
      ) : null}
      <main className="shell">
        {/* Keyed by meal so a slot or quantity chosen for one meal never carries over to another. */}
        {mealMatch?.[1] ? <Meal key={mealMatch[1]} ctx={ctx} id={mealMatch[1]} signedIn={me !== null} /> : null}
        {kitchenMatch?.[1] ? <Kitchen ctx={ctx} slug={kitchenMatch[1]} /> : null}
        {orderMatch?.[1] ? <OrderDetail ctx={ctx} id={orderMatch[1]} /> : null}
        {route === "/orders" ? <OrderList ctx={ctx} /> : null}
        {route === "/account" && me ? <Account ctx={ctx} me={me} onMeChanged={() => setMeVersion((v) => v + 1)} /> : null}
        {caseMatch?.[1] && me ? <SupportCaseDetail key={caseMatch[1]} ctx={ctx} id={caseMatch[1]} /> : null}
        {(route === "/account" || caseMatch) && !me ? (
          <p className="notice">
            <a href={loginUrl(window.location.href)}>{t.signIn}</a>
          </p>
        ) : null}
        {route === "/checkout" ? <Checkout ctx={ctx} me={me} onMeChanged={() => setMeVersion((v) => v + 1)} /> : null}
        {route === "/" ? <Browse ctx={ctx} /> : null}
      </main>
      <nav aria-label={title(locale)} className="bottom-nav">
        <a href="#/" aria-current={route === "/" ? "page" : undefined}>
          {t.nav.browse}
        </a>
        {me ? (
          <a href="#/orders" aria-current={route.startsWith("/orders") ? "page" : undefined}>
            {t.nav.orders}
          </a>
        ) : null}
        {me ? (
          <a href="#/account" aria-current={route === "/account" ? "page" : undefined}>
            {t.nav.account}
          </a>
        ) : null}
      </nav>
    </div>
  );
}
