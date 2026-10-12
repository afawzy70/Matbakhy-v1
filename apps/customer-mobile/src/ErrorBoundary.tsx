import { tokens } from "@matbakhy/design-tokens";
import { Component, type ReactNode } from "react";
import { Pressable, Text, View } from "react-native";
import type { Strings } from "./i18n";

/** Crash boundary: a rendering error shows a recoverable screen without stack traces or private data. */
export class ErrorBoundary extends Component<{ t: Strings; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    const colors = tokens.color.light;
    return (
      <View accessibilityRole="alert" style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: tokens.space["md"], backgroundColor: colors["background"] }}>
        <Text style={{ color: colors["foreground"], fontSize: tokens.font.size["lg"] }}>{this.props.t.crashTitle}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={() => this.setState({ failed: false })}
          style={{ minHeight: tokens.touchTarget.min, minWidth: tokens.touchTarget.min, padding: tokens.space["md"], borderRadius: tokens.radius["md"], backgroundColor: colors["primary"] }}
        >
          <Text style={{ color: colors["onPrimary"] }}>{this.props.t.crashRetry}</Text>
        </Pressable>
      </View>
    );
  }
}
