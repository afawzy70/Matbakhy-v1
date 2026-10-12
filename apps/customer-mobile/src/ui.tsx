import { direction, tokens, type Locale } from "@matbakhy/design-tokens";
import type { ReactNode } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type TextStyle } from "react-native";
import { errorText, type Strings } from "./i18n";
import type { Loaded } from "./useServer";

/** Small native component set on the approved light tokens; every control is at least 44 pt and labelled. */
export const colors = tokens.color.light;

export function Screen({ locale, children, testID }: { locale: Locale; children: ReactNode; testID?: string }) {
  return (
    <ScrollView testID={testID} style={{ backgroundColor: colors["background"] }} contentContainerStyle={[styles.screen, { direction: direction(locale) }]}>
      {children}
    </ScrollView>
  );
}

function align(locale: Locale): TextStyle {
  return { textAlign: direction(locale) === "rtl" ? "right" : "left", writingDirection: direction(locale) };
}

export function Title({ locale, children }: { locale: Locale; children: ReactNode }) {
  return (
    <Text accessibilityRole="header" style={[styles.title, align(locale)]}>
      {children}
    </Text>
  );
}

export function Body({ locale, children, muted, strong }: { locale: Locale; children: ReactNode; muted?: boolean; strong?: boolean }) {
  return <Text style={[styles.body, muted ? styles.muted : null, strong ? styles.strong : null, align(locale)]}>{children}</Text>;
}

export function Card({ children, testID }: { children: ReactNode; testID?: string }) {
  return (
    <View testID={testID} style={styles.card}>
      {children}
    </View>
  );
}

export function Button({
  label,
  onPress,
  disabled,
  variant = "primary",
  testID,
  accessibilityLabel,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "danger";
  testID?: string;
  accessibilityLabel?: string;
}) {
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: Boolean(disabled) }}
      disabled={disabled}
      onPress={onPress}
      style={[styles.button, variant === "secondary" ? styles.secondary : variant === "danger" ? styles.danger : null, disabled ? styles.disabled : null]}
    >
      <Text style={variant === "secondary" ? styles.secondaryText : variant === "danger" ? styles.dangerText : styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

/** A selectable option (radio semantics) such as a slot, an address or a fulfillment mode. */
export function Choice({ label, selected, onPress, testID }: { label: string; selected: boolean; onPress: () => void; testID?: string }) {
  return (
    <Pressable
      testID={testID}
      accessibilityRole="radio"
      accessibilityLabel={label}
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.choice, selected ? styles.choiceSelected : null]}
    >
      <Text style={styles.body}>{label}</Text>
    </Pressable>
  );
}

export function Field({
  label,
  value,
  onChangeText,
  keyboardType,
  testID,
  ltr,
  multiline,
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  keyboardType?: "default" | "phone-pad" | "number-pad";
  testID?: string;
  ltr?: boolean;
  multiline?: boolean;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        testID={testID}
        accessibilityLabel={label}
        value={value}
        onChangeText={onChangeText}
        keyboardType={keyboardType ?? "default"}
        multiline={multiline}
        style={[styles.input, ltr ? { writingDirection: "ltr", textAlign: "left" } : null]}
      />
    </View>
  );
}

export function ErrorLine({ t, code }: { t: Strings; code: string | null }) {
  return code ? (
    <Text accessibilityRole="alert" style={styles.error}>
      {errorText(t, code)}
    </Text>
  ) : null;
}

/** Loading, explicit offline and error states for server data; data is shown only from a successful response. */
export function StatusView<T>({ t, locale, state, onRetry, children }: { t: Strings; locale: Locale; state: Loaded<T>; onRetry: () => void; children: (data: T) => ReactNode }) {
  if (state.status === "loading") {
    return <ActivityIndicator accessibilityLabel={t.loading} color={colors["primary"]} />;
  }
  if (state.status === "error") {
    return (
      <View accessibilityRole="alert" style={styles.notice} testID={state.code === "NETWORK" ? "offline" : "load-error"}>
        <Body locale={locale}>{state.code === "NETWORK" ? t.offline : errorText(t, state.code)}</Body>
        <Button label={t.retry} variant="secondary" onPress={onRetry} />
      </View>
    );
  }
  return <>{children(state.data)}</>;
}

export function Notice({ locale, children, testID }: { locale: Locale; children: ReactNode; testID?: string }) {
  return (
    <View accessibilityRole="alert" style={styles.notice} testID={testID}>
      <Body locale={locale}>{children}</Body>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { padding: tokens.space["lg"], gap: tokens.space["md"] },
  title: { fontSize: tokens.font.size["xl"], fontWeight: "700", color: colors["foreground"] },
  body: { fontSize: tokens.font.size["md"], color: colors["foreground"] },
  muted: { color: colors["foregroundMuted"] },
  strong: { fontWeight: "700" },
  card: { backgroundColor: colors["surface"], borderRadius: tokens.radius["lg"], borderWidth: 1, borderColor: colors["border"], padding: tokens.space["md"], gap: tokens.space["sm"] },
  button: {
    minHeight: tokens.touchTarget.min,
    minWidth: tokens.touchTarget.min,
    paddingHorizontal: tokens.space["lg"],
    justifyContent: "center",
    alignItems: "center",
    borderRadius: tokens.radius["md"],
    backgroundColor: colors["primary"],
  },
  secondary: { backgroundColor: colors["surfaceMuted"], borderWidth: 1, borderColor: colors["border"] },
  danger: { backgroundColor: colors["danger"] },
  disabled: { opacity: 0.55 },
  buttonText: { color: colors["onPrimary"], fontWeight: "700" },
  dangerText: { color: colors["onDanger"], fontWeight: "700" },
  secondaryText: { color: colors["foreground"] },
  choice: { minHeight: tokens.touchTarget.min, justifyContent: "center", padding: tokens.space["sm"], borderRadius: tokens.radius["md"], borderWidth: 1, borderColor: colors["border"] },
  choiceSelected: { borderColor: colors["primary"], borderWidth: 2 },
  field: { gap: tokens.space["xs"] },
  label: { fontSize: tokens.font.size["sm"], color: colors["foregroundMuted"] },
  input: { minHeight: tokens.touchTarget.min, borderWidth: 1, borderColor: colors["border"], borderRadius: tokens.radius["md"], paddingHorizontal: tokens.space["sm"], color: colors["foreground"], backgroundColor: colors["surface"] },
  error: { color: colors["danger"], fontSize: tokens.font.size["md"] },
  notice: { backgroundColor: colors["surfaceMuted"], borderRadius: tokens.radius["md"], padding: tokens.space["md"], gap: tokens.space["sm"] },
});
