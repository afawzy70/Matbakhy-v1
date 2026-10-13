import { Linking } from "react-native";

/**
 * MB-033: the phone's notification permission, independent of any push provider (O07 keeps push delivery and device
 * token registration open). The app asks only when the customer chooses to, never on launch; a refusal changes nothing
 * else: every order update still appears in Notifications inside the app.
 */
export type PermissionState = "granted" | "denied" | "undetermined" | "unavailable";

export interface NotificationPermissions {
  get(): Promise<PermissionState>;
  request(): Promise<PermissionState>;
  openSettings(): Promise<void>;
}

type Status = { status: string; granted: boolean; canAskAgain: boolean };

export function stateOf(s: Status): PermissionState {
  if (s.granted || s.status === "granted") {
    return "granted";
  }
  // Android after two refusals and iOS after one: the system will not ask again, so this is a refusal.
  if (s.status === "denied" || !s.canAskAgain) {
    return "denied";
  }
  return "undetermined";
}

/** The device implementation (expo-notifications permissions only; no token is requested or registered). */
export function systemNotificationPermissions(): NotificationPermissions {
  const load = () => import("expo-notifications");
  const safely = async (read: () => Promise<Status>): Promise<PermissionState> => {
    try {
      return stateOf(await read());
    } catch {
      return "unavailable";
    }
  };
  return {
    get: () => safely(async () => (await load()).getPermissionsAsync()),
    request: () => safely(async () => (await load()).requestPermissionsAsync({ ios: { allowAlert: true, allowBadge: false, allowSound: true } })),
    openSettings: () => Linking.openSettings(),
  };
}
