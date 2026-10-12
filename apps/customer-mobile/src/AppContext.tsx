import type { Locale } from "@matbakhy/design-tokens";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { signIn as systemSignIn, signOut as systemSignOut, SignInCancelled } from "./auth";
import { clearShopping } from "./basket";
import type { AppConfig } from "./config";
import { strings, type Strings } from "./i18n";
import { createMobileApi, type MobileApi, type Schemas } from "./mobileApi";
import type { Session } from "./session";

export type Profile = Schemas["CustomerProfile"];

export type AuthPhase =
  | { kind: "loading" }
  | { kind: "signedOut"; message?: string }
  | { kind: "signedIn"; profile: Profile | null };

export interface AppContextValue {
  config: AppConfig;
  session: Session;
  api: MobileApi;
  locale: Locale;
  t: Strings;
  toggleLocale: () => void;
  auth: AuthPhase;
  busy: boolean;
  startSignIn: () => Promise<void>;
  endSession: () => Promise<void>;
  switchAccount: () => Promise<void>;
  reloadProfile: () => Promise<void>;
  /** The active policy's per-line limit from API-001 (Q1 option A); null until known or when unavailable. */
  maxQuantityPerLine: number | null;
}

const Ctx = createContext<AppContextValue | null>(null);

export function useApp(): AppContextValue {
  const value = useContext(Ctx);
  if (!value) {
    throw new Error("useApp outside AppProvider");
  }
  return value;
}

/**
 * Session, language and API for every screen (MB-031 behaviour kept): sign-in through the system browser with a fresh
 * login, tokens in the OS secure store, and sign-out or account switch that clear private caches, the basket and any
 * pending placement before anything of the next person is shown (TC-048).
 */
export function AppProvider({
  config,
  session,
  initialLocale,
  signIn = systemSignIn,
  signOut = systemSignOut,
  fetchImpl,
  children,
}: {
  config: AppConfig;
  session: Session;
  initialLocale: Locale;
  signIn?: typeof systemSignIn;
  signOut?: typeof systemSignOut;
  fetchImpl?: typeof fetch;
  children: ReactNode;
}) {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  const [auth, setAuth] = useState<AuthPhase>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const t = strings[locale];
  // A new client per language: requests carry the current Accept-Language; a 401 ends the session everywhere.
  const api = useMemo(
    () =>
      createMobileApi(
        config,
        session,
        {
          locale,
          onSignedOut: () => {
            void clearShopping();
            setAuth({ kind: "signedOut", message: strings[locale].sessionEnded });
          },
        },
        fetchImpl,
      ),
    [config, session, fetchImpl, locale],
  );

  const reloadProfile = useCallback(async () => {
    try {
      const cached = session.cache<Profile>("me");
      if (cached) {
        setAuth({ kind: "signedIn", profile: cached });
        return;
      }
      const r = await api.GET("/api/v1/me");
      if (r.data) {
        session.cache("me", r.data);
        setAuth({ kind: "signedIn", profile: r.data });
      } else if (r.response.status !== 401) {
        setAuth({ kind: "signedIn", profile: null });
      }
    } catch {
      if (session.accessToken) {
        setAuth({ kind: "signedIn", profile: null });
      }
    }
  }, [api, session]);

  const reloadRef = useRef(reloadProfile);
  useEffect(() => {
    reloadRef.current = reloadProfile;
  });
  useEffect(() => {
    let active = true;
    session
      .restore()
      .then((restored) => {
        if (!active) {
          return;
        }
        if (restored) {
          void reloadRef.current();
        } else {
          setAuth({ kind: "signedOut" });
        }
      })
      .catch(() => active && setAuth({ kind: "signedOut" }));
    return () => {
      active = false;
    };
  }, [session]);

  const startSignIn = useCallback(async () => {
    setBusy(true);
    try {
      await signIn(config, session);
      await reloadProfile();
    } catch (e) {
      setAuth({ kind: "signedOut", message: e instanceof SignInCancelled ? undefined : t.signInFailed });
    } finally {
      setBusy(false);
    }
  }, [config, session, signIn, reloadProfile, t]);

  const endSession = useCallback(async () => {
    setBusy(true);
    try {
      const stored = await session.restore();
      await clearShopping();
      await signOut(config, session, stored?.refreshToken ?? null);
    } finally {
      setAuth({ kind: "signedOut" });
      setBusy(false);
    }
  }, [config, session, signOut]);

  const switchAccount = useCallback(async () => {
    await endSession();
    await startSignIn();
  }, [endSession, startSignIn]);

  // Q1 option A: quantity controls follow the server's published limit; without it they fall back to 20 and the slot.
  const [maxQuantityPerLine, setMaxQuantityPerLine] = useState<number | null>(null);
  useEffect(() => {
    let active = true;
    api
      .GET("/api/v1/configuration/public")
      .then((r) => {
        if (active) {
          setMaxQuantityPerLine(r.data?.orderLimits?.maxQuantityPerLine ?? null);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [api]);

  const value: AppContextValue = {
    config,
    session,
    api,
    locale,
    t,
    toggleLocale: () => setLocale((l) => (l === "ar" ? "en" : "ar")),
    auth,
    busy,
    startSignIn,
    endSession,
    switchAccount,
    reloadProfile,
    maxQuantityPerLine,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
