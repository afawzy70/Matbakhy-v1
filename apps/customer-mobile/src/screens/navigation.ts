import type { BottomTabScreenProps } from "@react-navigation/bottom-tabs";
import type { CompositeScreenProps, NavigatorScreenParams } from "@react-navigation/native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";

export type TabParams = {
  Discover: undefined;
  Favorites: undefined;
  Orders: undefined;
  Account: undefined;
};

export type RootParams = {
  Tabs: NavigatorScreenParams<TabParams>;
  Meal: { mealId: string };
  Cart: undefined;
  Order: { orderId: string };
  Notifications: undefined;
  Support: { orderId?: string };
};

export type RootProps<K extends keyof RootParams> = NativeStackScreenProps<RootParams, K>;
export type TabProps<K extends keyof TabParams> = CompositeScreenProps<BottomTabScreenProps<TabParams, K>, NativeStackScreenProps<RootParams>>;
