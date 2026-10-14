// Fails when src/generated/schema.ts differs from a fresh generation from contracts/openapi.yaml.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const dir = mkdtempSync(join(tmpdir(), "matbakhy-api-"));
try {
  const fresh = join(dir, "schema.ts");
  execFileSync("pnpm", ["exec", "openapi-typescript", "../../contracts/openapi.yaml", "--output", fresh], { stdio: "inherit" });
  if (readFileSync(fresh, "utf8") !== readFileSync("src/generated/schema.ts", "utf8")) {
    console.error("FAIL: src/generated/schema.ts is stale. Run `pnpm --filter @matbakhy/api-client generate` and commit.");
    process.exit(1);
  }
  console.log("PASS: generated client types match contracts/openapi.yaml");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
