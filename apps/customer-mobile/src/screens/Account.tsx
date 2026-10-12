import { brand } from "@matbakhy/design-tokens";
import { ActivityIndicator, Text } from "react-native";
import { useApp } from "../AppContext";
import { Body, Button, Screen, Title, colors } from "../ui";
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
          <Button label={t.support} variant="secondary" onPress={() => navigation.navigate("Support", {})} />
          <Button label={t.switchAccount} disabled={busy} variant="secondary" onPress={() => void switchAccount()} />
          <Button label={t.signOut} disabled={busy} variant="secondary" onPress={() => void endSession()} />
        </>
      ) : null}
      <Button label={t.language} variant="secondary" onPress={toggleLocale} />
    </Screen>
  );
}
