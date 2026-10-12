import { brand } from "@matbakhy/design-tokens";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { App } from "./App";
import type { AppConfig } from "./config";
import { ErrorBoundary } from "./ErrorBoundary";
import { strings } from "./i18n";
import { Session } from "./session";
import { fakeJwt } from "./testing";

const mockStore = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: "x",
  setItemAsync: jest.fn(async (k: string, v: string) => {
    mockStore.set(k, v);
  }),
  getItemAsync: jest.fn(async (k: string) => mockStore.get(k) ?? null),
  deleteItemAsync: jest.fn(async (k: string) => {
    mockStore.delete(k);
  }),
}));
jest.mock("expo-localization", () => ({ getLocales: () => [{ languageCode: "ar" }] }));
jest.mock("./auth", () => ({ signIn: jest.fn(), signOut: jest.fn(), SignInCancelled: class extends Error {} }));

beforeEach(() => mockStore.clear());

const config: AppConfig = { environment: "development", apiBaseUrl: "http://api", issuer: "http://id", clientId: "matbakhy-mobile", redirectUri: "matbakhy-dev://auth/callback" };

function fakeFetch(status: number, body: unknown) {
  return jest.fn(async () => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } })) as unknown as typeof fetch;
}

describe("customer-mobile shell (MB-031)", () => {
  it("starts in Arabic, signs in through the injected PKCE flow, shows the profile and switches language", async () => {
    const session = new Session();
    const signIn = jest.fn(async (_c: AppConfig, s: Session) => s.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 }));
    const fetchImpl = fakeFetch(200, { displayName: "Mona" });
    await render(<App config={config} session={session} signIn={signIn} signOut={jest.fn()} fetchImpl={fetchImpl} />);
    expect(screen.getByRole("header")).toHaveTextContent(brand.name.ar);
    await fireEvent.press(await screen.findByRole("button", { name: strings.ar.signIn }));
    expect(await screen.findByText(strings.ar.signedInAs("Mona"))).toBeTruthy();
    expect(fetchImpl).toHaveBeenCalledWith("http://api/api/v1/me", expect.objectContaining({ headers: expect.objectContaining({ Authorization: `Bearer ${fakeJwt("customer-a")}` }) }));
    await fireEvent.press(screen.getByRole("button", { name: strings.ar.language }));
    expect(screen.getByRole("header")).toHaveTextContent(brand.name.en);
  });

  it("ends the session and clears private data when the server says 401", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={jest.fn()} fetchImpl={fakeFetch(401, {})} />);
    expect(await screen.findByText(strings.ar.sessionEnded)).toBeTruthy();
    expect(mockStore.size).toBe(0);
    expect(session.cachedKeys).toEqual([]);
  });

  it("signs out through the provider hook and returns to the signed-out screen", async () => {
    const session = new Session();
    await session.signedIn({ accessToken: fakeJwt("customer-a"), idToken: fakeJwt("customer-a"), refreshToken: "r", expiresIn: 300 });
    const signOut = jest.fn(async (_c: AppConfig, s: Session) => s.signOut());
    await render(<App config={config} session={session} signIn={jest.fn()} signOut={signOut} fetchImpl={fakeFetch(200, { displayName: "Mona" })} />);
    await fireEvent.press(await screen.findByRole("button", { name: strings.ar.signOut }));
    expect(await screen.findByRole("button", { name: strings.ar.signIn })).toBeTruthy();
    expect(signOut).toHaveBeenCalledWith(config, session, "r");
    expect(mockStore.size).toBe(0);
  });

  it("shows a recoverable crash screen without technical details", async () => {
    const Boom = () => {
      throw new Error("secret stack detail");
    };
    jest.spyOn(console, "error").mockImplementation(() => undefined);
    await render(
      <ErrorBoundary t={strings.en}>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText(strings.en.crashTitle)).toBeTruthy();
    expect(screen.queryByText(/secret stack detail/)).toBeNull();
  });
});
