import * as SecureStore from "expo-secure-store";

/**
 * Tokens live only in the OS secure store (Keychain / Android Keystore-backed storage), never in plain storage or
 * logs. A stored value that is not a well-formed session is deleted and treated as signed out (TC-131 "invalid token
 * state rejected").
 */
export interface StoredSession {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  expiresAt: number;
  subject: string;
}

const KEY = "matbakhy.session.v1";
const OPTIONS: SecureStore.SecureStoreOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };

function isSession(value: unknown): value is StoredSession {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const v = value as Record<string, unknown>;
  return (
    typeof v.accessToken === "string" &&
    v.accessToken.length > 0 &&
    (v.refreshToken === null || typeof v.refreshToken === "string") &&
    (v.idToken === null || typeof v.idToken === "string") &&
    typeof v.expiresAt === "number" &&
    Number.isFinite(v.expiresAt) &&
    typeof v.subject === "string" &&
    v.subject.length > 0
  );
}

export async function saveSession(session: StoredSession): Promise<void> {
  if (!isSession(session)) {
    throw new Error("refusing to store an invalid session");
  }
  await SecureStore.setItemAsync(KEY, JSON.stringify(session), OPTIONS);
}

export async function loadSession(now: number = Date.now()): Promise<StoredSession | null> {
  // One retry of a failed read (as for the basket): a keystore that is not ready yet must not look like a sign-out.
  let raw: string | null;
  try {
    raw = await SecureStore.getItemAsync(KEY, OPTIONS);
  } catch (e) {
    console.warn(`secure storage read failed for the session (${e instanceof Error ? e.name : typeof e}); retrying`);
    await new Promise((resolve) => setTimeout(resolve, 300));
    raw = await SecureStore.getItemAsync(KEY, OPTIONS);
  }
  if (raw === null) {
    return null;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    parsed = null;
  }
  if (!isSession(parsed) || (parsed.expiresAt <= now && parsed.refreshToken === null)) {
    await SecureStore.deleteItemAsync(KEY, OPTIONS);
    return null;
  }
  return parsed;
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY, OPTIONS);
}

/** The token's subject (`sub`) without verifying it; the server verifies every token. Null when unreadable. */
export function subjectOf(jwt: string): string | null {
  const payload = jwt.split(".")[1];
  if (!payload) {
    return null;
  }
  try {
    const json = JSON.parse(globalThis.atob(payload.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(payload.length / 4) * 4, "=")));
    return typeof json.sub === "string" && json.sub.length > 0 ? json.sub : null;
  } catch {
    return null;
  }
}
