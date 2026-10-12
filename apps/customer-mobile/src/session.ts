import { clearSession, loadSession, saveSession, subjectOf, type StoredSession } from "./tokenStore";

/**
 * Account switch and logout (BR-048, TC-048): private data cached in memory belongs to one subject. Signing in as a
 * different subject, signing out, or an invalid stored session clears every private cache before anything is shown,
 * so customer B never sees customer A's orders, addresses or profile.
 */
export class Session {
  private current: StoredSession | null = null;
  private readonly privateCache = new Map<string, unknown>();
  private lastSubject: string | null = null;

  async restore(now: number = Date.now()): Promise<StoredSession | null> {
    this.current = await loadSession(now);
    if (!this.current) {
      this.clearPrivate();
    }
    this.lastSubject = this.current?.subject ?? null;
    return this.current;
  }

  async signedIn(tokens: { accessToken: string; refreshToken?: string | null; idToken?: string | null; expiresIn?: number }, now: number = Date.now()) {
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
