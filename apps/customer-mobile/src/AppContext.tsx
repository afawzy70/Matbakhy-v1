import type { Locale } from "@matbakhy/design-tokens";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { refresh as systemRefresh, signIn as systemSignIn, signOut as systemSignOut, SignInCancelled } from "./auth";
import { clearShopping } from "./basket";
import type { AppConfig } from "./config";
import { strings, type Strings } from "./i18n";
import { createMobileApi, type MobileApi, type Schemas } from "./mobileApi";
import type { Session } from "./session";
import { type NotificationPermissions, systemNotificationPermissions } from "./notificationPermission";

const devicePermissions = systemNotificationPermissions();

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
  /** The server asked for a second factor (D18) and the user has not answered yet. */
  stepUpRequired: boolean;
  /** Verifies again with a second factor (same account, fresh login); clears the request when it succeeds. */
  startStepUp: () => Promise<void>;
  /** Changes when the signed-in person changes: everything below it is remounted, so no screen state of the previous account survives. */
  accountEpoch: number;
  /** Changes after a successful step-up for the same person: server reads that were refused are loaded again (writes are never replayed). */
  dataEpoch: number;
  endSession: () => Promise<void>;
  switchAccount: () => Promise<void>;
  reloadProfile: () => Promise<void>;
  /** The active policy's per-line limit from API-001 (Q1 option A); null until known or when unavailable. */
  maxQuantityPerLine: number | null;
  /** Re-reads the published limit (the cart calls it when it gains focus). */
  reloadLimits: () => void;
  /** The phone's notification permission (MB-033), provider-independent. */
  permissions: NotificationPermissions;
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
  permissions = devicePermissions,
  refresh = systemRefresh,
  children,
}: {
  config: AppConfig;
  session: Session;
  initialLocale: Locale;
  signIn?: typeof systemSignIn;
  signOut?: typeof systemSignOut;
  fetchImpl?: typeof fetch;
  permissions?: NotificationPermissions;
  refresh?: typeof systemRefresh;
  children: ReactNode;
}) {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  const [auth, setAuth] = useState<AuthPhase>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [stepUpRequired, setStepUpRequired] = useState(false);
  const [accountEpoch, setAccountEpoch] = useState(0);
  const [dataEpoch, setDataEpoch] = useState(0);
  const t = strings[locale];
  // A new client per language: requests carry the current Accept-Language; a 401 ends the session everywhere.
  const api = useMemo(() => {
    session.useRefresher((refreshToken) => refresh(config, refreshToken));
    return createMobileApi(
        config,
        session,
        {
          locale,
          onStepUpRequired: () => setStepUpRequired(true),
          onSignedOut: () => {
            void clearShopping();
            setAuth({ kind: "signedOut", message: strings[locale].sessionEnded });
          },
        },
        fetchImpl,
      );
  }, [config, session, fetchImpl, locale, refresh]);

  const reloadProfile = useCallback(async () => {
    // An answer that arrives after the signed-in person changed belongs to the previous person and is dropped.
    const subject = session.subject;
    try {
      const cached = session.cache<Profile>("me");
      if (cached) {
        setAuth({ kind: "signedIn", profile: cached });
        return;
      }
      const r = await api.GET("/api/v1/me");
      if (session.subject !== subject) {
        return;
      }
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

  const startStepUp = useCallback(async () => {
    setBusy(true);
    try {
      const before = (await session.restore())?.subject ?? null;
      await signIn(config, session, { stepUp: true });
      const after = (await session.restore())?.subject ?? null;
      if (before !== after) {
        // The person signed in as somebody else in the browser: this is an account switch, so nothing of the previous account may remain (TC-048).
        // Order matters: hide the old profile, drop the persisted basket and pending placement, then remount every screen (navigation and
        // screen state) before the new person's profile is read; the session already cleared its private caches when the subject changed.
        // Hide and unmount first, so the previous account's screens go away even if the storage cleanup below fails.
        setAuth({ kind: "loading" });
        setStepUpRequired(false);
        setAccountEpoch((n) => n + 1);
        try {
          await clearShopping();
        } catch {
          // The previous account's basket could not be removed: do not continue as the new person on top of it; end this session.
          await session.signOut();
          setAuth({ kind: "signedOut", message: t.signInFailed });
          return;
        }
        await reloadProfile();
      } else {
        setStepUpRequired(false);
        setDataEpoch((n) => n + 1);
      }
    } catch (e) {
      // Cancelling keeps the account signed in with the notice still showing; a real failure says so.
      if (!(e instanceof SignInCancelled)) {
        setStepUpRequired(true);
      }
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
      setStepUpRequired(false);
      setAuth({ kind: "signedOut" });
      setBusy(false);
    }
  }, [config, session, signOut]);

  const switchAccount = useCallback(async () => {
    await endSession();
    await startSignIn();
  }, [endSession, startSignIn]);

  // Q1 option A: quantity controls follow the server's published limit; without it they fall back to 20 and the slot.
  // Re-read when the cart gains focus (reloadLimits), so a policy lowered after launch applies before any quote; a failed
  // re-read keeps the last published limit rather than loosening the controls.
  const [maxQuantityPerLine, setMaxQuantityPerLine] = useState<number | null>(null);
  const [limitsNonce, setLimitsNonce] = useState(0);
  const reloadLimits = useCallback(() => setLimitsNonce((n) => n + 1), []);
  useEffect(() => {
    let active = true;
    api
      .GET("/api/v1/configuration/public")
      .then((r) => {
        if (active && r.data) {
          setMaxQuantityPerLine(r.data.orderLimits?.maxQuantityPerLine ?? null);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [api, limitsNonce]);

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
    stepUpRequired,
    startStepUp,
    accountEpoch,
    dataEpoch,
    endSession,
    switchAccount,
    reloadProfile,
    maxQuantityPerLine,
    reloadLimits,
    permissions,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
