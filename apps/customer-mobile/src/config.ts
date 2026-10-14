import Constants from "expo-constants";

/**
 * Environment configuration for the customer app. Values come from EXPO_PUBLIC_* variables (inlined at build time)
 * with development defaults from app.json. No secret ever lives here: the mobile client is a public OIDC client
 * that relies on PKCE (BR-002, BR-048).
 */
export interface AppConfig {
  environment: "development" | "staging" | "production";
  apiBaseUrl: string;
  issuer: string;
  clientId: string;
  redirectUri: string;
  /**
   * MB-033: offer to ask for the phone's notification permission. Push delivery is not decided (O07), so only
   * development builds ask; other builds say that updates appear in Notifications inside the app.
   */
  notificationPrompt?: boolean;
}

export class ConfigError extends Error {}

export function loadConfig(
  env: Record<string, string | undefined> = process.env,
  extra: Record<string, unknown> = (Constants.expoConfig?.extra ?? {}) as Record<string, unknown>,
  scheme: string = String(Constants.expoConfig?.scheme ?? "matbakhy-dev"),
): AppConfig {
  const environment = (env.EXPO_PUBLIC_ENVIRONMENT ?? String(extra.environment ?? "development")) as AppConfig["environment"];
  if (!["development", "staging", "production"].includes(environment)) {
    throw new ConfigError(`unknown environment ${environment}`);
  }
  const config: AppConfig = {
    environment,
    apiBaseUrl: env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
    issuer: env.EXPO_PUBLIC_OIDC_ISSUER ?? "http://localhost:8180/realms/matbakhy",
    clientId: env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? "matbakhy-mobile",
    redirectUri: `${scheme}://auth/callback`,
    notificationPrompt: (env.EXPO_PUBLIC_NOTIFICATION_PROMPT ?? String(extra.notificationPrompt ?? environment === "development")) === "true",
  };
  // Only local development may use plain HTTP; deployed builds must talk to TLS endpoints.
  if (environment !== "development") {
    for (const [name, url] of [["API", config.apiBaseUrl], ["issuer", config.issuer]] as const) {
      if (!url.startsWith("https://")) {
        throw new ConfigError(`${name} must use https outside development`);
      }
    }
  }
  return config;
}
