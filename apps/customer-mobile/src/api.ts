import type { AppConfig } from "./config";
import type { Session } from "./session";

export class SignedOut extends Error {}

/** Authenticated JSON GET with the bearer token; a 401 ends the local session and clears private data. */
export async function apiGet<T>(config: AppConfig, session: Session, path: string, locale: string, fetchImpl: typeof fetch = fetch): Promise<T> {
  const token = await session.freshAccessToken();
  if (!token) {
    throw new SignedOut();
  }
  const response = await fetchImpl(`${config.apiBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Accept-Language": locale },
  });
  if (response.status === 401) {
    await session.signOut();
    throw new SignedOut();
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}
