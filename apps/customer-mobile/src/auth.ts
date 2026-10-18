import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import type { AppConfig } from "./config";
import { checkRedirect } from "./redirect";
import { RefreshRejected, type Session, type Tokens } from "./session";

WebBrowser.maybeCompleteAuthSession();

export class SignInCancelled extends Error {}

/**
 * Authorization code + PKCE in the system browser (ASWebAuthenticationSession / Custom Tabs), never an embedded web
 * view (BR-048). The response is checked strictly before the code is exchanged.
 *
 * The app keeps its own session, so an interactive sign-in only happens when the user is signed out or switching
 * account. It always asks the provider for a fresh login (`prompt=login`) and, on iOS, uses an ephemeral browser
 * session: otherwise the provider's browser cookie would silently sign the previous person back in (TC-048).
 * With {@code stepUp} the same account verifies again with a second factor (or enrols one); the tokens are replaced and,
 * being the same subject, the basket and private caches are kept.
 */
export async function signIn(config: AppConfig, session: Session, options: { stepUp?: boolean } = {}) {
  const discovery = await AuthSession.fetchDiscoveryAsync(config.issuer);
  const request = new AuthSession.AuthRequest({
    clientId: config.clientId,
    redirectUri: config.redirectUri,
    scopes: ["openid", "profile", "email"],
    usePKCE: true,
    responseType: AuthSession.ResponseType.Code,
    prompt: AuthSession.Prompt.Login,
    // D18 step-up: ask the provider for a second factor (acr "mfa", also accepted as level 2). The API decides from the token.
    ...(options.stepUp ? { extraParams: { acr_values: "mfa 2" } } : {}),
  });
  await request.makeAuthUrlAsync(discovery);
  const result = await request.promptAsync(discovery, { preferEphemeralSession: true });
  if (result.type !== "success") {
    throw new SignInCancelled(result.type);
  }
  const code = checkRedirect(result.url, config.redirectUri, request.state);
  if (!request.codeVerifier) {
    throw new Error("missing PKCE verifier");
  }
  const tokens = await AuthSession.exchangeCodeAsync(
    { clientId: config.clientId, code, redirectUri: config.redirectUri, extraParams: { code_verifier: request.codeVerifier } },
    discovery,
  );
  return session.signedIn({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken ?? null, idToken: tokens.idToken ?? null, expiresIn: tokens.expiresIn });
}

/** Revokes the refresh token when the provider supports it (best effort), then clears all local private state. */
export async function signOut(config: AppConfig, session: Session, refreshToken: string | null) {
  try {
    if (refreshToken) {
      const discovery = await AuthSession.fetchDiscoveryAsync(config.issuer);
      if (discovery.revocationEndpoint) {
        await AuthSession.revokeAsync({ clientId: config.clientId, token: refreshToken, tokenTypeHint: AuthSession.TokenTypeHint.RefreshToken }, discovery);
      }
    }
  } finally {
    await session.signOut();
  }
}

/**
 * Exchanges the refresh token at the provider (public client, no secret). An OAuth error response (for example
 * invalid_grant after the session expired or was revoked) ends the session; a transport failure does not.
 */
export async function refresh(config: AppConfig, refreshToken: string): Promise<Tokens> {
  const discovery = await AuthSession.fetchDiscoveryAsync(config.issuer);
  try {
    const tokens = await AuthSession.refreshAsync({ clientId: config.clientId, refreshToken }, discovery);
    return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken ?? null, idToken: tokens.idToken ?? null, ...(tokens.expiresIn ? { expiresIn: tokens.expiresIn } : {}) };
  } catch (e) {
    if (e instanceof AuthSession.TokenError) {
      throw new RefreshRejected(e.code);
    }
    throw e;
  }
}
