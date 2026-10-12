import { brand, direction, tokens, type Locale } from "@matbakhy/design-tokens";
import { getLocales } from "expo-localization";
import { StatusBar } from "expo-status-bar";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { apiGet, SignedOut } from "./api";
import { signIn as systemSignIn, signOut as systemSignOut, SignInCancelled } from "./auth";
import { ConfigError, loadConfig, type AppConfig } from "./config";
import { ErrorBoundary } from "./ErrorBoundary";
import { strings } from "./i18n";
import { Session } from "./session";

/**
 * MB-031 native shell: environment configuration, Arabic (default) / English with direction, sign-in through the
 * system browser with PKCE, tokens in the OS secure store, account switch and logout that clear private data, and a
 * crash boundary. Product screens arrive in MB-032.
 */
const colors = tokens.color.light;

interface Profile {
  displayName: string | null;
}

export interface AppDeps {
  config?: AppConfig;
  session?: Session;
  signIn?: typeof systemSignIn;
  signOut?: typeof systemSignOut;
  fetchImpl?: typeof fetch;
}

type Phase = { kind: "loading" } | { kind: "signedOut"; message?: string } | { kind: "signedIn"; profile: Profile | null } | { kind: "configError" };

function initialLocale(): Locale {
  try {
    return getLocales()[0]?.languageCode === "en" ? "en" : "ar";
  } catch {
    return "ar";
  }
}

export function App(deps: AppDeps = {}) {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  const t = strings[locale];
  return (
    <ErrorBoundary t={t}>
      <Shell deps={deps} locale={locale} onToggleLocale={() => setLocale(locale === "ar" ? "en" : "ar")} />
    </ErrorBoundary>
  );
}

function Shell({ deps, locale, onToggleLocale }: { deps: AppDeps; locale: Locale; onToggleLocale: () => void }) {
  const t = strings[locale];
  const rtl = direction(locale) === "rtl";
  const config = useMemo(() => {
    try {
      return deps.config ?? loadConfig();
    } catch (e) {
      if (e instanceof ConfigError) {
        return null;
      }
      throw e;
    }
  }, [deps.config]);
  const session = useMemo(() => deps.session ?? new Session(), [deps.session]);
  const doSignIn = deps.signIn ?? systemSignIn;
  const doSignOut = deps.signOut ?? systemSignOut;
  const [phase, setPhase] = useState<Phase>(config ? { kind: "loading" } : { kind: "configError" });
  const [busy, setBusy] = useState(false);

  const loadProfile = useCallback(async () => {
    if (!config) {
      return;
    }
    try {
      const cached = session.cache<Profile>("me");
      const profile = cached ?? (await apiGet<Profile>(config, session, "/api/v1/me", locale, deps.fetchImpl));
      session.cache("me", profile);
      setPhase({ kind: "signedIn", profile });
    } catch (e) {
      if (e instanceof SignedOut) {
        setPhase({ kind: "signedOut", message: t.sessionEnded });
      } else {
        setPhase({ kind: "signedIn", profile: null });
      }
    }
  }, [config, session, locale, deps.fetchImpl, t.sessionEnded]);

  // The restore effect runs once per session object and calls the latest loadProfile through a ref.
  const loadProfileRef = useRef(loadProfile);
  useEffect(() => {
    loadProfileRef.current = loadProfile;
  });
  useEffect(() => {
    if (!config) {
      return;
    }
    let active = true;
    session
      .restore()
      .then((restored) => {
        if (!active) {
          return;
        }
        if (restored) {
          void loadProfileRef.current();
        } else {
          setPhase({ kind: "signedOut" });
        }
      })
      .catch(() => active && setPhase({ kind: "signedOut" }));
    return () => {
      active = false;
    };
  }, [config, session]);

  const startSignIn = async () => {
    if (!config) {
      return;
    }
    setBusy(true);
    try {
      await doSignIn(config, session);
      await loadProfile();
    } catch (e) {
      setPhase({ kind: "signedOut", message: e instanceof SignInCancelled ? undefined : t.signInFailed });
    } finally {
      setBusy(false);
    }
  };

  const endSession = async () => {
    if (!config) {
      return;
    }
    setBusy(true);
    try {
      const stored = await session.restore();
      await doSignOut(config, session, stored?.refreshToken ?? null);
    } finally {
      setPhase({ kind: "signedOut" });
      setBusy(false);
    }
  };

  return (
    <View style={[styles.container, { direction: rtl ? "rtl" : "ltr" }]}>
      <Text accessibilityRole="header" style={styles.title}>
        {brand.name[locale]}
      </Text>
      <Text style={styles.slogan}>{brand.slogan[locale]}</Text>
      {phase.kind === "loading" ? <ActivityIndicator accessibilityLabel={t.loading} color={colors["primary"]} /> : null}
      {phase.kind === "configError" ? <Text accessibilityRole="alert">{t.configError}</Text> : null}
      {phase.kind === "signedOut" ? (
        <>
          {phase.message ? <Text accessibilityRole="alert">{phase.message}</Text> : null}
          <Button label={t.signIn} disabled={busy} onPress={() => void startSignIn()} />
        </>
      ) : null}
      {phase.kind === "signedIn" ? (
        <>
          <Text style={styles.body}>{t.signedInAs(phase.profile?.displayName ?? "")}</Text>
          <Button label={t.switchAccount} disabled={busy} secondary onPress={() => void endSession().then(startSignIn)} />
          <Button label={t.signOut} disabled={busy} secondary onPress={() => void endSession()} />
        </>
      ) : null}
      <Button label={t.language} secondary onPress={onToggleLocale} />
      <StatusBar style="auto" />
    </View>
  );
}

function Button({ label, onPress, disabled, secondary }: { label: string; onPress: () => void; disabled?: boolean; secondary?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: Boolean(disabled) }}
      disabled={disabled}
      onPress={onPress}
      style={[styles.button, secondary ? styles.secondary : null, disabled ? styles.disabled : null]}
    >
      <Text style={secondary ? styles.secondaryText : styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: tokens.space["lg"], padding: tokens.space["lg"], backgroundColor: colors["background"] },
  title: { fontSize: tokens.font.size["xxl"], fontWeight: "700", color: colors["foreground"] },
  slogan: { fontSize: tokens.font.size["md"], color: colors["foregroundMuted"], textAlign: "center" },
  body: { fontSize: tokens.font.size["md"], color: colors["foreground"] },
  button: {
    minHeight: tokens.touchTarget.min,
    minWidth: tokens.touchTarget.min,
    paddingHorizontal: tokens.space["lg"],
    justifyContent: "center",
    borderRadius: tokens.radius["md"],
    backgroundColor: colors["primary"],
  },
  secondary: { backgroundColor: colors["surfaceMuted"], borderWidth: 1, borderColor: colors["border"] },
  disabled: { opacity: 0.55 },
  buttonText: { color: colors["onPrimary"], fontWeight: "700" },
  secondaryText: { color: colors["foreground"] },
});
