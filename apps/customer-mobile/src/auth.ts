import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import type { AppConfig } from "./config";
import { checkRedirect } from "./redirect";
import type { Session } from "./session";

WebBrowser.maybeCompleteAuthSession();

export class SignInCancelled extends Error {}

/**
 * Authorization code + PKCE in the system browser (ASWebAuthenticationSession / Custom Tabs), never an embedded web
 * view (BR-048). The response is checked strictly before the code is exchanged.
 */
export async function signIn(config: AppConfig, session: Session) {
  const discovery = await AuthSession.fetchDiscoveryAsync(config.issuer);
  const request = new AuthSession.AuthRequest({
    clientId: config.clientId,
    redirectUri: config.redirectUri,
    scopes: ["openid", "profile", "email"],
    usePKCE: true,
    responseType: AuthSession.ResponseType.Code,
  });
  await request.makeAuthUrlAsync(discovery);
  const result = await request.promptAsync(discovery);
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
