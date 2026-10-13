import { clearSession, loadSession, saveSession, subjectOf, type StoredSession } from "./tokenStore";

/** The provider refused the refresh token (an OAuth error such as invalid_grant): the session is over. */
export class RefreshRejected extends Error {}

export type Tokens = { accessToken: string; refreshToken?: string | null; idToken?: string | null; expiresIn?: number };
export type Refresher = (refreshToken: string) => Promise<Tokens>;

/** Refresh a little before the provider's expiry so a request never leaves with a token about to lapse. */
const EARLY_MS = 30_000;

/**
 * Account switch and logout (BR-048, TC-048): private data cached in memory belongs to one subject. Signing in as a
 * different subject, signing out, or an invalid stored session clears every private cache before anything is shown,
 * so customer B never sees customer A's orders, addresses or profile.
 */
export class Session {
  private current: StoredSession | null = null;
  private readonly privateCache = new Map<string, unknown>();
  private lastSubject: string | null = null;
  private refresher: Refresher | null = null;
  private refreshing: Promise<string | null> | null = null;

  /** How to exchange the refresh token (the provider's token endpoint in the app; a fake in tests). */
  useRefresher(refresher: Refresher) {
    this.refresher = refresher;
  }

  /**
   * The access token to send now (native run 36: without this the app signed customers out once the 5-minute access
   * token lapsed). Expired or about to: one refresh, shared by concurrent callers, stored like a sign-in. The provider
   * refusing the refresh token, or returning another person's token, ends the session (null). An unreachable
   * provider keeps the session and returns the old token; the server then decides.
   */
  async freshAccessToken(now: number = Date.now()): Promise<string | null> {
    const current = this.current;
    if (!current) {
      return null;
    }
    if (current.expiresAt - EARLY_MS > now || !current.refreshToken || !this.refresher) {
      return current.accessToken;
    }
    if (!this.refreshing) {
      const refreshToken = current.refreshToken;
      const refresher = this.refresher;
      this.refreshing = (async () => {
        try {
          const tokens = await refresher(refreshToken);
          if (subjectOf(tokens.idToken ?? tokens.accessToken) !== current.subject) {
            await this.signOut();
            return null;
          }
          const next = await this.signedIn({ ...tokens, refreshToken: tokens.refreshToken ?? refreshToken, idToken: tokens.idToken ?? current.idToken }, now);
          return next.accessToken;
        } catch (e) {
          if (e instanceof RefreshRejected) {
            await this.signOut();
            return null;
          }
          return this.current?.accessToken ?? null;
        } finally {
          this.refreshing = null;
        }
      })();
    }
    return this.refreshing;
  }

  async restore(now: number = Date.now()): Promise<StoredSession | null> {
    this.current = await loadSession(now);
    if (!this.current) {
      this.clearPrivate();
    }
    this.lastSubject = this.current?.subject ?? null;
    return this.current;
  }

  async signedIn(tokens: Tokens, now: number = Date.now()) {
    const subject = subjectOf(tokens.idToken ?? tokens.accessToken);
    if (!subject) {
      await this.signOut();
      throw new Error("token without subject rejected");
    }
    if (this.lastSubject !== null && this.lastSubject !== subject) {
      this.clearPrivate();
    }
    const session: StoredSession = {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken ?? null,
      idToken: tokens.idToken ?? null,
      expiresAt: now + (tokens.expiresIn ?? 300) * 1000,
      subject,
    };
    await saveSession(session);
    this.current = session;
    this.lastSubject = subject;
    return session;
  }

  async signOut(): Promise<void> {
    this.current = null;
    this.clearPrivate();
    await clearSession();
  }

  get accessToken(): string | null {
    return this.current?.accessToken ?? null;
  }

  get subject(): string | null {
    return this.current?.subject ?? null;
  }

  cache<T>(key: string, value?: T): T | undefined {
    if (value !== undefined) {
      if (!this.current) {
        throw new Error("no private cache without a session");
      }
      this.privateCache.set(key, value);
    }
    return this.privateCache.get(key) as T | undefined;
  }

  get cachedKeys(): string[] {
    return [...this.privateCache.keys()];
  }

  private clearPrivate() {
    this.privateCache.clear();
  }
}
