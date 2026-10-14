import { defineConfig } from "@playwright/test";

// Staging smoke (browser part): sign-in through the identity provider and a simulated deposit order against a DEPLOYED preview.
// It needs no database access and no fixtures: it uses the seeded preview accounts and catalogue (deploy.sh seed). It creates one
// demo order per run. Variables: STAGING_APP_URL, STAGING_OPS_URL, STAGING_API_URL, STAGING_USER_PASSWORD (the preview users'
// password from the server's .env), optionally STAGING_IGNORE_HTTPS_ERRORS=1 (local rehearsal only) and E2E_CHROMIUM.
// The server-side boundary of the browser E2E suite is a different thing: that one resets the database (docs/operations/11).
export default defineConfig({
  testDir: "e2e-staging",
  testMatch: "*.staging.ts",
  timeout: 240_000,
  workers: 1,
  reporter: [["list"]],
  use: {
    ignoreHTTPSErrors: process.env.STAGING_IGNORE_HTTPS_ERRORS === "1",
    launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {},
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  outputDir: process.env.E2E_OUTPUT_DIR ?? "test-results-staging",
});
