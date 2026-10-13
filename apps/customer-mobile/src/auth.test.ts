import * as SecureStore from "expo-secure-store";
import { ConfigError, loadConfig } from "./config";
import { AuthRedirectError, checkRedirect } from "./redirect";
import { RefreshRejected, Session } from "./session";
import { fakeJwt } from "./testing";
import { loadSession, saveSession, subjectOf } from "./tokenStore";

// In-memory stand-in for the OS secure mockStore (test double only).
const mockStore = new Map<string, string>();
const mockOptions: unknown[] = [];
jest.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: "WHEN_UNLOCKED_THIS_DEVICE_ONLY",
  setItemAsync: jest.fn(async (k: string, v: string, o: unknown) => {
    mockOptions.push(o);
    mockStore.set(k, v);
  }),
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  deleteItemAsync: jest.fn(async (k: string) => {
    mockStore.delete(k);
  }),
}));

beforeEach(() => {
  mockStore.clear();
  mockOptions.length = 0;
});

const REDIRECT = "matbakhy-dev://auth/callback";

describe("authorization redirect (TC-131: invalid redirect rejected)", () => {
  it("accepts only our exact redirect with the expected state and a code", () => {
    expect(checkRedirect(`${REDIRECT}?code=abc&state=s1`, REDIRECT, "s1")).toBe("abc");
    const reject = (url: string, code: string) => {
      try {
        checkRedirect(url, REDIRECT, "s1");
        throw new Error("accepted");
      } catch (e) {
        expect(e).toBeInstanceOf(AuthRedirectError);
        expect((e as AuthRedirectError).code).toBe(code);
      }
    };
    reject("matbakhy-dev://auth/callback.evil?code=abc&state=s1", "INVALID_REDIRECT");
    reject("https://evil.example/auth/callback?code=abc&state=s1", "INVALID_REDIRECT");
    reject("other-app://auth/callback?code=abc&state=s1", "INVALID_REDIRECT");
    reject(`${REDIRECT}?code=abc&state=forged`, "STATE_MISMATCH");
    reject(`${REDIRECT}?code=abc`, "STATE_MISMATCH");
    reject(`${REDIRECT}?error=access_denied&state=s1`, "PROVIDER_ERROR");
    reject(`${REDIRECT}?state=s1`, "MISSING_CODE");
  });
});

describe("secure token storage (TC-048, TC-131: invalid token state rejected)", () => {
  it("stores the session only in the secure mockStore, device-only", async () => {
    await saveSession({ accessToken: "a", refreshToken: "r", idToken: null, expiresAt: Date.now() + 60_000, subject: "u1" });
    expect(SecureStore.setItemAsync).toHaveBeenCalled();
    expect(mockOptions[0]).toEqual({ keychainAccessible: "WHEN_UNLOCKED_THIS_DEVICE_ONLY" });
    expect((await loadSession())?.subject).toBe("u1");
  });

  it("deletes corrupt, incomplete or expired-unrefreshable sessions and reports signed out", async () => {
    for (const raw of ["{not json", JSON.stringify({ accessToken: "a" }), JSON.stringify({ accessToken: "", refreshToken: null, idToken: null, expiresAt: 1, subject: "u" })]) {
      mockStore.set("matbakhy.session.v1", raw);
      expect(await loadSession()).toBeNull();
      expect(mockStore.size).toBe(0);
    }
    mockStore.set("matbakhy.session.v1", JSON.stringify({ accessToken: "a", refreshToken: null, idToken: null, expiresAt: 1000, subject: "u" }));
    expect(await loadSession(2000)).toBeNull();
    expect(mockStore.size).toBe(0);
    await expect(saveSession({ accessToken: "", refreshToken: null, idToken: null, expiresAt: 1, subject: "u" })).rejects.toThrow();
  });

  it("reads the subject from a token without trusting anything else", () => {
    expect(subjectOf(fakeJwt("user-a"))).toBe("user-a");
    expect(subjectOf("garbage")).toBeNull();
  });
});

describe("account switch and logout clear private data (TC-048)", () => {
  it("drops A's cached data when B signs in, and everything on sign-out", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "ra", expiresIn: 300 });
    session.cache("orders", ["A-order"]);
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "ra2", expiresIn: 300 });
    expect(session.cache("orders")).toEqual(["A-order"]);
    await session.signedIn({ accessToken: fakeJwt("customer-b"), idToken: fakeJwt("customer-b"), refreshToken: "rb", expiresIn: 300 });
    expect(session.cache("orders")).toBeUndefined();
    expect(session.subject).toBe("customer-b");
    session.cache("me", { displayName: "B" });
    await session.signOut();
    expect(session.cachedKeys).toEqual([]);
    expect(mockStore.size).toBe(0);
    expect(() => session.cache("me", { displayName: "x" })).toThrow();
  });

  it("rejects a token without a subject and stays signed out", async () => {
    const session = new Session();
    await expect(session.signedIn({ accessToken: "not-a-jwt" })).rejects.toThrow();
    expect(session.accessToken).toBeNull();
    expect(mockStore.size).toBe(0);
  });
});

describe("environment configuration", () => {
  it("uses development defaults and refuses plain HTTP outside development", () => {
    expect(loadConfig({}, { environment: "development" }, "matbakhy-dev").redirectUri).toBe(REDIRECT);
    expect(() => loadConfig({ EXPO_PUBLIC_ENVIRONMENT: "staging", EXPO_PUBLIC_API_URL: "http://api" }, {}, "x")).toThrow(ConfigError);
    expect(loadConfig({ EXPO_PUBLIC_ENVIRONMENT: "staging", EXPO_PUBLIC_API_URL: "https://api", EXPO_PUBLIC_OIDC_ISSUER: "https://id" }, {}, "x").issuer).toBe("https://id");
  });
});

describe("token refresh keeps the customer signed in past the access-token lifetime (native run 36 finding)", () => {
  const fresh = (sub: string, n: number) => ({ accessToken: fakeJwt(sub) + `.${n}`, refreshToken: `r${n}`, idToken: fakeJwt(sub), expiresIn: 300 });

  it("returns the current token while it is valid and refreshes once, for concurrent callers, when it has expired", async () => {
    const session = new Session();
    await session.signedIn({ ...fresh("customer-a", 1), expiresIn: 300 }, 0);
    const refresher = jest.fn(async () => fresh("customer-a", 2));
    session.useRefresher(refresher);
    expect(await session.freshAccessToken(1_000)).toBe(fakeJwt("customer-a") + ".1");
    expect(refresher).not.toHaveBeenCalled();
    const [a, b] = await Promise.all([session.freshAccessToken(301_000), session.freshAccessToken(301_000)]);
    expect(a).toBe(fakeJwt("customer-a") + ".2");
    expect(b).toBe(a);
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(refresher).toHaveBeenCalledWith("r1");
    expect(JSON.parse(mockStore.get("matbakhy.session.v1") ?? "{}").refreshToken).toBe("r2");
  });

  it("ends the session when the provider refuses the refresh token", async () => {
    const session = new Session();
    await session.signedIn(fresh("customer-a", 1), 0);
    session.cache("me", { name: "A" });
    session.useRefresher(async () => {
      throw new RefreshRejected("invalid_grant");
    });
    expect(await session.freshAccessToken(400_000)).toBeNull();
    expect(session.accessToken).toBeNull();
    expect(session.cachedKeys).toEqual([]);
    expect(mockStore.size).toBe(0);
  });

  it("keeps the session when the provider cannot be reached, and refuses tokens for another subject", async () => {
    const session = new Session();
    await session.signedIn(fresh("customer-a", 1), 0);
    session.useRefresher(async () => {
      throw new TypeError("Network request failed");
    });
    expect(await session.freshAccessToken(400_000)).toBe(fakeJwt("customer-a") + ".1");
    expect(session.subject).toBe("customer-a");

    session.useRefresher(async () => fresh("customer-b", 3));
    expect(await session.freshAccessToken(400_000)).toBeNull();
    expect(session.accessToken).toBeNull();
  });
});
