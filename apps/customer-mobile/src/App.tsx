import { type Locale } from "@matbakhy/design-tokens";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { DefaultTheme, NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { getLocales } from "expo-localization";
import { StatusBar } from "expo-status-bar";
import { useMemo, useState } from "react";
import { Text, View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { AppProvider, useApp } from "./AppContext";
import { refresh as systemRefresh, signIn as systemSignIn, signOut as systemSignOut } from "./auth";
import { ConfigError, loadConfig, type AppConfig } from "./config";
import { ErrorBoundary } from "./ErrorBoundary";
import { strings } from "./i18n";
import { AccountScreen } from "./screens/Account";
import { CartScreen } from "./screens/Cart";
import { DiscoverScreen } from "./screens/Discover";
import { FavoritesScreen } from "./screens/Favorites";
import { MealScreen } from "./screens/Meal";
import { linkingFor } from "./linking";
import type { RootParams, TabParams } from "./screens/navigation";
import { NotificationsScreen } from "./screens/Notifications";
import { OrderScreen, OrdersScreen } from "./screens/Orders";
import { SupportScreen } from "./screens/Support";
import { Session } from "./session";
import { Button, colors, Notice } from "./ui";
import type { NotificationPermissions } from "./notificationPermission";

/**
 * Customer app (MB-031 shell, MB-032 discovery and ordering). Tabs: Discover, Favorites, Orders, Account; stack
 * screens for meal, cart/checkout, order, notifications and support keep the platform's back behaviour (Android back,
 * iOS swipe). Arabic is the default when the device is not English.
 */
export interface AppDeps {
  config?: AppConfig;
  session?: Session;
  signIn?: typeof systemSignIn;
  signOut?: typeof systemSignOut;
  fetchImpl?: typeof fetch;
  permissions?: NotificationPermissions;
  refresh?: typeof systemRefresh;
}

function initialLocale(): Locale {
  try {
    return getLocales()[0]?.languageCode === "en" ? "en" : "ar";
  } catch {
    return "ar";
  }
}

const Stack = createNativeStackNavigator<RootParams>();
const Tab = createBottomTabNavigator<TabParams>();
const c = DefaultTheme.colors;
const theme = {
  ...DefaultTheme,
  colors: { ...c, primary: colors["primary"] ?? c.primary, background: colors["background"] ?? c.background, card: colors["surface"] ?? c.card, text: colors["foreground"] ?? c.text, border: colors["border"] ?? c.border },
};

function Tabs() {
  const { t } = useApp();
  return (
    <Tab.Navigator screenOptions={{ tabBarActiveTintColor: colors["primary"], tabBarIcon: () => null, tabBarIconStyle: { display: "none" } }}>
      <Tab.Screen name="Discover" component={DiscoverScreen} options={{ title: t.tabs.discover, tabBarButtonTestID: "tab-discover" }} />
      <Tab.Screen name="Favorites" component={FavoritesScreen} options={{ title: t.tabs.favorites, tabBarButtonTestID: "tab-favorites" }} />
      <Tab.Screen name="Orders" component={OrdersScreen} options={{ title: t.tabs.orders, tabBarButtonTestID: "tab-orders" }} />
      <Tab.Screen name="Account" component={AccountScreen} options={{ title: t.tabs.account, tabBarButtonTestID: "tab-account" }} />
    </Tab.Navigator>
  );
}

/** D18: the server asked for a second factor (the user turned MFA on, or the administrator requires it). */
function StepUpBanner() {
  const { t, locale, stepUpRequired, startStepUp, busy } = useApp();
  if (!stepUpRequired) {
    return null;
  }
  return (
    <View testID="step-up-banner">
      <Notice locale={locale}>{t.stepUp.message}</Notice>
      <Button testID="step-up-verify" label={t.stepUp.action} onPress={() => void startStepUp()} disabled={busy} />
    </View>
  );
}

function Navigation() {
  const { t, config, accountEpoch } = useApp();
  const linking = useMemo(() => linkingFor(config.redirectUri), [config.redirectUri]);
  return (
    <View style={{ flex: 1 }}>
      <StepUpBanner />
      <View style={{ flex: 1 }}>
        <NavigationContainer key={accountEpoch} theme={theme} linking={linking}>
          <Stack.Navigator screenOptions={{ headerBackTitle: t.back }}>
            <Stack.Screen name="Tabs" component={Tabs} options={{ headerShown: false }} />
            <Stack.Screen name="Meal" component={MealScreen} options={{ title: "" }} />
            <Stack.Screen name="Cart" component={CartScreen} options={{ title: t.cart }} />
            <Stack.Screen name="Order" component={OrderScreen} options={{ title: t.tabs.orders }} />
            <Stack.Screen name="Notifications" component={NotificationsScreen} options={{ title: t.notifications }} />
            <Stack.Screen name="Support" component={SupportScreen} options={{ title: t.support }} />
          </Stack.Navigator>
          <StatusBar style="auto" />
        </NavigationContainer>
      </View>
    </View>
  );
}

export function App(deps: AppDeps = {}) {
  const [locale] = useState<Locale>(initialLocale);
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
  if (!config) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors["background"] }}>
        <Text accessibilityRole="alert">{strings[locale].configError}</Text>
      </View>
    );
  }
  return (
    <ErrorBoundary t={strings[locale]}>
      <SafeAreaProvider>
        <AppProvider config={config} session={session} initialLocale={locale} signIn={deps.signIn} signOut={deps.signOut} fetchImpl={deps.fetchImpl} {...(deps.permissions ? { permissions: deps.permissions } : {})} {...(deps.refresh ? { refresh: deps.refresh } : {})}>
          <Navigation />
        </AppProvider>
      </SafeAreaProvider>
    </ErrorBoundary>
  );
}
