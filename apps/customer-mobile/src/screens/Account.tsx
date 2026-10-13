import { brand } from "@matbakhy/design-tokens";
import { useEffect, useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import { useApp } from "../AppContext";
import type { PermissionState } from "../notificationPermission";
import { Body, Button, Card, Screen, Title, colors } from "../ui";
import type { TabProps } from "./navigation";

/** MB-031 shell content: brand, sign-in, greeting from the server profile, account switch, sign-out and language. */
export function AccountScreen({ navigation }: TabProps<"Account">) {
  const { t, locale, auth, busy, startSignIn, endSession, switchAccount, toggleLocale } = useApp();
  return (
    <Screen locale={locale} testID="account">
      <Title locale={locale}>{brand.name[locale]}</Title>
      <Body locale={locale} muted>
        {brand.slogan[locale]}
      </Body>
      {auth.kind === "loading" ? <ActivityIndicator accessibilityLabel={t.loading} color={colors["primary"]} /> : null}
      {auth.kind === "signedOut" ? (
        <>
          {auth.message ? <Text accessibilityRole="alert">{auth.message}</Text> : null}
          <Button label={t.signIn} disabled={busy} onPress={() => void startSignIn()} />
        </>
      ) : null}
      {auth.kind === "signedIn" ? (
        <>
          <Body locale={locale}>{t.signedInAs(auth.profile?.displayName ?? "")}</Body>
          <Button label={t.notifications} variant="secondary" onPress={() => navigation.navigate("Notifications")} />
          <PhoneAlerts />
          <Button label={t.support} variant="secondary" onPress={() => navigation.navigate("Support", {})} />
          <Button label={t.switchAccount} disabled={busy} variant="secondary" onPress={() => void switchAccount()} />
          <Button label={t.signOut} disabled={busy} variant="secondary" onPress={() => void endSession()} />
        </>
      ) : null}
      <Button label={t.language} variant="secondary" onPress={toggleLocale} />
    </Screen>
  );
}

/**
 * MB-033: the phone's notification permission. Asked only when the customer presses the button (never on launch);
 * a refusal is final for the system dialog, so the app then points to Settings. Order updates always stay in
 * Notifications inside the app. Push delivery itself is O07; no device token is requested here.
 */
function PhoneAlerts() {
  const { t, locale, config, permissions } = useApp();
  const [state, setState] = useState<PermissionState | null>(null);
  const [asking, setAsking] = useState(false);
  const prompt = config.notificationPrompt === true;
  useEffect(() => {
    if (!prompt) {
      return;
    }
    let active = true;
    void permissions.get().then((s) => {
      if (active) {
        setState(s);
      }
    });
    return () => {
      active = false;
    };
  }, [permissions, prompt]);
  const text = !prompt
    ? t.alertsInApp
    : state === "granted"
      ? t.alertsGranted
      : state === "denied"
        ? t.alertsDenied
        : state === "unavailable"
          ? t.alertsUnavailable
          : state === "undetermined"
            ? t.alertsAsk
            : null;
  return (
    <Card testID="phone-alerts">
      <View style={{ gap: 8 }}>
        <Body locale={locale} strong>
          {t.phoneAlerts}
        </Body>
        {text ? (
          <Body locale={locale} testID={`phone-alerts-${prompt ? (state ?? "loading") : "in-app"}`}>
            {text}
          </Body>
        ) : (
          <ActivityIndicator accessibilityLabel={t.loading} color={colors["primary"]} />
        )}
        {prompt && state === "undetermined" ? (
          <Button
            testID="allow-alerts"
            label={t.alertsAllow}
            disabled={asking}
            onPress={() => {
              setAsking(true);
              void permissions.request().then(
                (s) => {
                  setState(s);
                  setAsking(false);
                },
                () => setAsking(false),
              );
            }}
          />
        ) : null}
        {prompt && state === "denied" ? (
          <Button testID="open-alert-settings" label={t.alertsSettings} variant="secondary" onPress={() => void permissions.openSettings()} />
        ) : null}
      </View>
    </Card>
  );
}
