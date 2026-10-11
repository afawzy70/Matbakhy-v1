import { brand, direction, tokens, type Locale } from "@matbakhy/design-tokens";
import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

// Minimal P01 native shell. Brand values come from @matbakhy/design-tokens (MB-003); navigation,
// authentication and product screens arrive in P07 stories.
const colors = tokens.color.light;
const SWITCH_LABEL: Record<Locale, string> = { ar: "English", en: "العربية" };

export function App() {
  const [locale, setLocale] = useState<Locale>("ar");
  const rtl = direction(locale) === "rtl";

  return (
    <View style={[styles.container, { direction: rtl ? "rtl" : "ltr" }]}>
      <Text accessibilityRole="header" style={styles.title}>
        {brand.name[locale]}
      </Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => setLocale(rtl ? "en" : "ar")}
        style={styles.button}
      >
        <Text style={styles.buttonText}>{SWITCH_LABEL[locale]}</Text>
      </Pressable>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: tokens.space["lg"], backgroundColor: colors["background"] },
  title: { fontSize: tokens.font.size["xxl"], color: colors["foreground"] },
  button: {
    minHeight: tokens.touchTarget.min, minWidth: tokens.touchTarget.min, padding: tokens.space["md"],
    borderRadius: tokens.radius["md"], backgroundColor: colors["primary"],
  },
  buttonText: { color: colors["onPrimary"] },
});
