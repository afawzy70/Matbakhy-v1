import { signIn, SignInCancelled } from "./auth";
import { Session } from "./session";
import { fakeJwt } from "./testing";

// Test doubles for the provider round trip; the real system-browser flow is exercised on the emulator in CI.
const mockRequests: { config: Record<string, unknown>; promptOptions?: Record<string, unknown> }[] = [];
let mockResult: { type: string; url?: string } = { type: "success" };
jest.mock("expo-web-browser", () => ({ maybeCompleteAuthSession: jest.fn() }));
jest.mock("expo-secure-store", () => {
  const store = new Map<string, string>();
  return {
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: "WHEN_UNLOCKED_THIS_DEVICE_ONLY",
    setItemAsync: jest.fn(async (k: string, v: string) => void store.set(k, v)),
    getItemAsync: jest.fn(async (k: string) => store.get(k) ?? null),
    deleteItemAsync: jest.fn(async (k: string) => void store.delete(k)),
  };
});
jest.mock("expo-auth-session", () => ({
  ResponseType: { Code: "code" },
  Prompt: { Login: "login" },
  fetchDiscoveryAsync: jest.fn(async () => ({ authorizationEndpoint: "https://idp.test/auth", tokenEndpoint: "https://idp.test/token" })),
  exchangeCodeAsync: jest.fn(async () => ({ accessToken: mockToken, refreshToken: "r", idToken: null, expiresIn: 300 })),
  AuthRequest: class {
    state = "state-1";
    codeVerifier = "verifier";
    private entry: (typeof mockRequests)[number];
    constructor(config: Record<string, unknown>) {
      this.entry = { config };
      mockRequests.push(this.entry);
    }
    async makeAuthUrlAsync() {
      return "https://idp.test/auth";
    }
    async promptAsync(_discovery: unknown, options: Record<string, unknown>) {
      this.entry.promptOptions = options;
      return mockResult.type === "success" ? { type: "success", url: `matbakhy-dev://auth/callback?code=c&state=state-1` } : mockResult;
    }
  },
}));
let mockToken = "";

const config = { environment: "development" as const, apiBaseUrl: "http://localhost:8080", issuer: "https://idp.test", clientId: "matbakhy-mobile", redirectUri: "matbakhy-dev://auth/callback" };

beforeEach(async () => {
  await new Session().signOut();
  mockRequests.length = 0;
  mockResult = { type: "success" };
  mockToken = fakeJwt("user-b");
});

describe("interactive sign-in never reuses the previous person's browser login (TC-048)", () => {
  it("asks for a fresh login with PKCE in an ephemeral system-browser session", async () => {
    await signIn(config, new Session());
    expect(mockRequests).toHaveLength(1);
    expect(mockRequests[0]?.config).toMatchObject({ prompt: "login", usePKCE: true, responseType: "code", redirectUri: config.redirectUri });
    expect(mockRequests[0]?.promptOptions).toEqual({ preferEphemeralSession: true });
  });

  it("asks the provider for a second factor only when a step-up is requested (D18)", async () => {
    await signIn(config, new Session());
    expect(mockRequests[0]?.config).not.toHaveProperty("extraParams");
    mockRequests.length = 0;
    await signIn(config, new Session(), { stepUp: true });
    expect(mockRequests[0]?.config).toMatchObject({ prompt: "login", usePKCE: true, extraParams: { acr_values: "mfa 2" } });
  });

  it("reports a dismissed browser as cancelled and stores nothing", async () => {
    mockResult = { type: "dismiss" };
    const session = new Session();
    await expect(signIn(config, session)).rejects.toBeInstanceOf(SignInCancelled);
    expect(await session.restore()).toBeNull();
  });
});
