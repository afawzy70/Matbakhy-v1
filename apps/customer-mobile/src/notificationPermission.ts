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

export function stateOf(s: Status, answered = false): PermissionState {
  if (s.granted || s.status === "granted") {
    return "granted";
  }
  // The system will not show its dialog again (iOS after one refusal, Android after two), or the customer has just
  // answered it with a refusal: this is a refusal.
  if (!s.canAskAgain || (answered && s.status === "denied")) {
    return "denied";
  }
  // Android 13+ reports a never-asked permission as "denied" with canAskAgain: it can still be asked.
  return "undetermined";
}

/** The device implementation (expo-notifications permissions only; no token is requested or registered). */
export function systemNotificationPermissions(): NotificationPermissions {
  const load = () => import("expo-notifications");
  const safely = async (read: () => Promise<Status>, answered = false): Promise<PermissionState> => {
    try {
      return stateOf(await read(), answered);
    } catch {
      return "unavailable";
    }
  };
  return {
    get: () => safely(async () => (await load()).getPermissionsAsync()),
    request: () => safely(async () => (await load()).requestPermissionsAsync({ ios: { allowAlert: true, allowBadge: false, allowSound: true } }), true),
    openSettings: () => Linking.openSettings(),
  };
}
