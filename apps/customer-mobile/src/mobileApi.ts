import * as Crypto from "expo-crypto";
import { createApiClient, type ApiError, type components } from "@matbakhy/api-client";
import type { AppConfig } from "./config";
import type { Session } from "./session";

export type Schemas = components["schemas"];

/**
 * Typed client for the native app (generated from contracts/openapi.yaml). Requests carry the bearer token from the
 * OS secure store when signed in and the UI language. A 401 ends the local session and clears private data; the
 * listener moves the UI to the signed-out state. The server owns prices, stock, states and permissions.
 */
/** The UI language for Accept-Language and what to do when the server ends the session. */
export interface RequestContext {
  locale: string;
  onSignedOut: () => void;
  /** The server wants a second factor (MFA policy, D18): the UI offers a step-up sign-in. */
  onStepUpRequired?: () => void;
}

export function createMobileApi(config: AppConfig, session: Session, context: RequestContext, fetchImpl?: typeof fetch) {
  const client = createApiClient({ baseUrl: config.apiBaseUrl, ...(fetchImpl ? { fetch: fetchImpl } : {}) });
  // The middleware only sets headers and observes responses; it returns nothing, so openapi-fetch keeps the original
  // objects. Returning them fails its `instanceof Response` check when the platform fetch (React Native/Expo) yields a
  // Response class other than the global one, which turned every API answer into an error on iOS (native run 21).
  client.use({
    async onRequest({ request }) {
      request.headers.set("Accept-Language", context.locale);
      const hadSession = session.accessToken !== null;
      // Refreshes an expired access token first (native run 36); a refused refresh ends the session here.
      const token = await session.freshAccessToken();
      if (token) {
        request.headers.set("Authorization", `Bearer ${token}`);
      } else if (hadSession) {
        context.onSignedOut();
      }
    },
    async onResponse({ request, response }) {
      if (response.status === 401 && request.headers.has("Authorization")) {
        await session.signOut();
        context.onSignedOut();
      } else if (response.status === 403 && request.headers.has("Authorization")) {
        // The body is read from a clone so the caller still receives the untouched response.
        try {
          const body = (await response.clone().json()) as { code?: unknown };
          if (body.code === "STEP_UP_REQUIRED") {
            context.onStepUpRequired?.();
          }
        } catch {
          // Not a JSON error body: nothing to learn from it.
        }
      }
    },
  });
  return client;
}

export type MobileApi = ReturnType<typeof createMobileApi>;

/** Error code from the standard error body; transport failures (offline, unreachable) are "NETWORK". */
export function errorCode(error: unknown): string {
  const e = error as Partial<ApiError> | undefined;
  return typeof e?.code === "string" ? e.code : "NETWORK";
}

/** Random request keys from the platform's secure generator (expo-crypto; Hermes has no Web Crypto). */
export function newKey(prefix: string): string {
  const uuid = Crypto.randomUUID();
  return prefix ? `${prefix}-${uuid}` : uuid;
}

export function formatMoney(amountMinor: number, locale: string): string {
  return new Intl.NumberFormat(locale === "ar" ? "ar-EG" : "en-EG", { style: "currency", currency: "EGP" }).format(amountMinor / 100);
}

export function formatTime(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-EG" : "en-GB", { timeZone: "Africa/Cairo", dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}
