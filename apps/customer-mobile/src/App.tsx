import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

type Locale = "ar" | "en";

// Minimal P01 native shell only. Navigation, authentication and branding arrive in P07 stories.
const TITLES: Record<Locale, string> = { ar: "مطبخي", en: "Matbakhy" };
const SWITCH_LABEL: Record<Locale, string> = { ar: "English", en: "العربية" };

export function App() {
  const [locale, setLocale] = useState<Locale>("ar");
  const rtl = locale === "ar";

  return (
    <View style={[styles.container, { direction: rtl ? "rtl" : "ltr" }]}>
      <Text accessibilityRole="header" style={styles.title}>
        {TITLES[locale]}
      </Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => setLocale(rtl ? "en" : "ar")}
        style={styles.button}
      >
        <Text>{SWITCH_LABEL[locale]}</Text>
      </Pressable>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 16 },
  title: { fontSize: 28 },
  button: { minHeight: 44, minWidth: 44, padding: 12 },
});
