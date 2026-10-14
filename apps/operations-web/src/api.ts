import { createApiClient, type ApiError, type components } from "@matbakhy/api-client";

export type Schemas = components["schemas"];

/** API origin. The session cookie and the readable XSRF-TOKEN cookie are set by the API. */
export const API_BASE: string = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8080";

function cookie(name: string): string | undefined {
  return document.cookie
    .split("; ")
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

/**
 * Typed client for the cook workspace: credentials are the HttpOnly session cookie; mutations echo the CSRF cookie.
 * Every request carries the language the user chose, set through {@code setLanguage} in the same event that changes
 * it, not the document language, which an effect updates after children have already reloaded (QA finding MBQA-001
 * on the customer web; same pattern here). The server owns state, prices, deadlines and permissions; the UI only renders them.
 */
export function createApi(fetchImpl?: typeof fetch) {
  let language = "ar";
  const client = createApiClient({ baseUrl: API_BASE, credentials: "include", ...(fetchImpl ? { fetch: fetchImpl } : {}) });
  client.use({
    onRequest({ request }) {
      request.headers.set("Accept-Language", language);
      if (request.method !== "GET") {
        const token = cookie("XSRF-TOKEN");
        if (token) {
          request.headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
        }
      }
      return request;
    },
  });
  return Object.assign(client, {
    setLanguage(next: string) {
      language = next;
    },
  });
}

export type Api = ReturnType<typeof createApi>;

export function loginUrl(returnTo: string): string {
  return `${API_BASE}/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(returnTo)}`;
}

export function newIdempotencyKey(): string {
  return `ops-${crypto.randomUUID()}`;
}

/** Error code from the standard error body, or a transport marker. */
export function errorCode(error: unknown): string {
  const e = error as Partial<ApiError> | undefined;
  return typeof e?.code === "string" ? e.code : "NETWORK";
}

export function formatMoney(amountMinor: number, locale: string): string {
  return new Intl.NumberFormat(locale === "ar" ? "ar-EG" : "en-EG", { style: "currency", currency: "EGP" }).format(amountMinor / 100);
}

export function formatTime(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-EG" : "en-GB", {
    timeZone: "Africa/Cairo",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));
}

/**
 * Owner decision D10 (2026-10-13): no platform commission at launch. Commission rows are shown only when a statement
 * actually carries a commission (the dormant capability); otherwise the screen says there is none.
 */
export function hasCommission(statement: {
  summary: { commissionPosted: { amountMinor: number }; commissionReversed: { amountMinor: number }; commissionSettled: { amountMinor: number }; commissionOutstanding: { amountMinor: number } };
  items: { commission?: { amountMinor: number } | null }[];
}): boolean {
  const s = statement.summary;
  return [s.commissionPosted, s.commissionReversed, s.commissionSettled, s.commissionOutstanding].some((m) => m.amountMinor !== 0)
    || statement.items.some((l) => (l.commission?.amountMinor ?? 0) !== 0);
}
