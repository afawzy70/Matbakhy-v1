/**
 * Strict checks on the authorization response delivered to the app's redirect URI (BR-048, TC-131 "invalid redirect
 * rejected"). Anything that is not exactly our redirect with the state we generated and a code is refused before a
 * token exchange is attempted.
 */
export type RedirectError = "INVALID_REDIRECT" | "STATE_MISMATCH" | "PROVIDER_ERROR" | "MISSING_CODE";

export class AuthRedirectError extends Error {
  constructor(readonly code: RedirectError) {
    super(code);
  }
}

export function checkRedirect(url: string, expectedRedirect: string, expectedState: string): string {
  const [base, query = ""] = url.split("?", 2);
  if (base !== expectedRedirect) {
    throw new AuthRedirectError("INVALID_REDIRECT");
  }
  const params = new URLSearchParams(query.split("#", 1)[0]);
  if (params.get("error")) {
    throw new AuthRedirectError("PROVIDER_ERROR");
  }
  const state = params.get("state");
  if (!state || state !== expectedState) {
    throw new AuthRedirectError("STATE_MISMATCH");
  }
  const code = params.get("code");
  if (!code) {
    throw new AuthRedirectError("MISSING_CODE");
  }
  return code;
}
