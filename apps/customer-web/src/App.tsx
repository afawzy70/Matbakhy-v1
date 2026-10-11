import { brand, direction, type Locale } from "@matbakhy/design-tokens";
import { useEffect, useMemo, useState } from "react";
import { createApi, loginUrl, type Schemas } from "./api";
import { Account } from "./Account";
import { Browse } from "./Browse";
import { Checkout } from "./Checkout";
import { strings } from "./i18n";
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
export function App({ fetchImpl }: { fetchImpl?: typeof fetch } = {}) {
  const [locale, setLocale] = useState<Locale>("ar");
  const [route, setRoute] = useState(currentRoute);
  const [me, setMe] = useState<Schemas["CustomerProfile"] | null>(null);
  const [meVersion, setMeVersion] = useState(0);
  const [online, setOnline] = useState(() => typeof navigator === "undefined" || navigator.onLine);
  const api = useMemo(() => createApi(fetchImpl), [fetchImpl]);
  const t = strings[locale];

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = direction(locale);
    document.title = title(locale);
  }, [locale]);

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

  const ctx: Ctx = {
    api,
    t,
    locale,
    onSignedOut: () => setMe(null),
    go: (to: string) => {
      window.location.hash = to;
    },
  };

  const mealMatch = /^\/meal\/([0-9a-f-]{36})$/.exec(route);
  const orderMatch = /^\/orders\/([0-9a-f-]{36})$/.exec(route);
  return (
    <div className="app">
      <header className="topbar">
        <h1>{title(locale)}</h1>
        <nav aria-label={title(locale)} className="tabs">
          <a className="button secondary" href="#/" aria-current={route === "/" ? "page" : undefined}>
            {t.nav.browse}
          </a>
          <a className="button secondary" href="#/checkout" aria-current={route === "/checkout" ? "page" : undefined}>
            {t.nav.cart}
          </a>
          {me ? (
            <a className="button secondary" href="#/orders" aria-current={route.startsWith("/orders") ? "page" : undefined}>
              {t.nav.orders}
            </a>
          ) : null}
          {me ? (
            <a className="button secondary" href="#/account" aria-current={route === "/account" ? "page" : undefined}>
              {t.nav.account}
            </a>
          ) : null}
        </nav>
        <div className="topbar-actions">
          <button type="button" onClick={() => setLocale(locale === "ar" ? "en" : "ar")}>
            {t.language}
          </button>
          {me ? null : (
            <a className="button secondary" href={loginUrl(window.location.href)}>
              {t.signIn}
            </a>
          )}
        </div>
      </header>
      {!online ? (
        <p role="alert" className="notice">
          {t.offline}
        </p>
      ) : null}
      <main className="shell">
        {mealMatch?.[1] ? <Meal ctx={ctx} id={mealMatch[1]} signedIn={me !== null} /> : null}
        {orderMatch?.[1] ? <OrderDetail ctx={ctx} id={orderMatch[1]} /> : null}
        {route === "/orders" ? <OrderList ctx={ctx} /> : null}
        {route === "/account" && me ? <Account ctx={ctx} me={me} onMeChanged={() => setMeVersion((v) => v + 1)} /> : null}
        {route === "/checkout" ? <Checkout ctx={ctx} me={me} onMeChanged={() => setMeVersion((v) => v + 1)} /> : null}
        {route === "/" ? (
          <>
            <p>{brand.slogan[locale]}</p>
            <Browse ctx={ctx} />
          </>
        ) : null}
      </main>
    </div>
  );
}
