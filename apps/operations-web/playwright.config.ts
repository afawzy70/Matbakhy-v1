import { defineConfig } from "@playwright/test";

// Real-API browser workflows (P05-CHECK-1). Run through `scripts/dev.sh e2e`, which starts a throwaway local stack
// (PostgreSQL, Keycloak, S3 emulator, API) and this app, then sets the variables below. Never against shared or
// production environments.
export default defineConfig({
  testDir: "e2e",
  testMatch: "*.e2e.ts",
  timeout: 180_000,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_OPS_URL ?? "http://localhost:5174",
    launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {},
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  outputDir: process.env.E2E_OUTPUT_DIR ?? "test-results",
});
