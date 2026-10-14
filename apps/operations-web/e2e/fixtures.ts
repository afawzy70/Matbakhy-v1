import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { crc32, deflateSync } from "node:zlib";

export function env(name: string): string {
  const v = process.env[name];
  if (!v) {
    throw new Error(`${name} is not set; run through scripts/dev.sh e2e`);
  }
  return v;
}

/**
 * Test setup and read-only checks through the staging-only fixture channel. The runner has no database access and never
 * sends SQL: it sends the NAME of a checked-in fixture (deploy/web-preview/e2e-fixtures/<name>.sql) and its parameters on
 * the standard input of E2E_FIXTURE_CMD, a JSON array. Locally that is the server-side script itself
 * (deploy/web-preview/e2e-fixture.sh); on a staging host it is an SSH command restricted to that script.
 */
export function fixture(name: string, params: Record<string, string> = {}): string {
  const [cmd, ...args] = JSON.parse(env("E2E_FIXTURE_CMD")) as string[];
  const lines = Object.entries(params).map(([key, value]) => {
    if (/[\r\n]/.test(value)) {
      throw new Error(`fixture parameter ${key} contains a line break`);
    }
    return `${key}=${value}`;
  });
  return execFileSync(cmd as string, args, { encoding: "utf8", input: `${[name, ...lines].join("\n")}\n` }).trim();
}

/** The synthetic identities of the suite: the login name at the identity provider and the subject the API stores for it. */
export type AccountName = "cook-a" | "customer-a" | "customer-b" | "ops-a";
const LOCAL_ACCOUNTS: Record<AccountName, { username: string; subject: string }> = {
  "cook-a": { username: "cook-a", subject: "00000000-0000-4000-8000-0000000000c1" },
  "customer-a": { username: "customer-a", subject: "00000000-0000-4000-8000-0000000000a1" },
  "customer-b": { username: "customer-b", subject: "00000000-0000-4000-8000-0000000000b1" },
  "ops-a": { username: "ops-a", subject: "00000000-0000-4000-8000-0000000000d2" },
};

/** E2E_USERS (JSON, optional) maps these names to the accounts of another realm, e.g. the preview realm's users. */
export function account(name: AccountName): { username: string; subject: string } {
  const override = process.env.E2E_USERS ? (JSON.parse(process.env.E2E_USERS) as Partial<Record<AccountName, { username: string; subject: string }>>) : {};
  return override[name] ?? LOCAL_ACCOUNTS[name];
}

/** Fills the identity provider's login form (the page is already there). */
export async function idpLogin(page: Page, name: AccountName) {
  await page.locator("#username").fill(account(name).username);
  await page.locator("#password").fill(env("E2E_USER_PASSWORD"));
  await page.locator("#kc-login").click();
}

/** A synthetic solid-colour PNG (no metadata) large enough for the meal-photo policy. */
export function png(width: number, height: number): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.writeUInt8(8, 8); // bit depth
  header.writeUInt8(2, 9); // RGB
  const row = Buffer.alloc(1 + width * 3);
  for (let x = 0; x < width; x++) {
    row.writeUInt8(200, 1 + x * 3);
    row.writeUInt8(120, 2 + x * 3);
    row.writeUInt8(60, 3 + x * 3);
  }
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/** Cairo calendar date `days` from now. */
export function cairoDate(days: number): string {
  const d = new Date(Date.now() + days * 86_400_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Cairo" }).format(d);
}

/** WCAG 2.2 A/AA automated rules (axe-core); serious and critical findings fail. Automated rules are not a full audit. */
export async function axe(page: Page, name: string) {
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
  const blocking = result.violations
    .filter((v) => v.impact === "serious" || v.impact === "critical")
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(" | ")}`);
  expect.soft(blocking, `axe ${name}`).toEqual([]);
}

/** Elements that stick out of the viewport horizontally (empty when the page does not overflow); tables scroll inside .table-wrap. */
export async function overflowing(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    document.documentElement.scrollWidth <= window.innerWidth
      ? []
      : [...document.querySelectorAll<HTMLElement>("body *")]
          .filter((e) => {
            const r = e.getBoundingClientRect();
            return r.width > 0 && (r.right > window.innerWidth + 1 || r.left < -1) && !e.closest(".table-wrap");
          })
          .slice(0, 5)
          .map((e) => `${e.tagName}.${e.className}:${(e.textContent ?? "").trim().slice(0, 25)}`));
}
