import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import source from "../tokens.json";
import { brand, contrastRatio, renderCss, tokens } from "../src";

const ROOT = new URL("../../../", import.meta.url).pathname;
const pairs = source.contrastPairs as [string, string, number][];

describe("TC-103 contrast", () => {
  for (const theme of ["light", "dark"] as const) {
    it.each(pairs)(`${theme}: %s on %s meets %s:1`, (fg, bg, min) => {
      const palette = tokens.color[theme];
      expect(palette[fg], fg).toBeDefined();
      expect(palette[bg], bg).toBeDefined();
      expect(contrastRatio(palette[fg] as string, palette[bg] as string)).toBeGreaterThanOrEqual(min);
    });
  }

  it("computes known WCAG reference ratios", () => {
    expect(contrastRatio("#000000", "#FFFFFF")).toBeCloseTo(21, 5);
    expect(contrastRatio("#777777", "#FFFFFF")).toBeCloseTo(4.48, 2);
  });
});

describe("token completeness", () => {
  it("light and dark palettes define the same roles with valid colors", () => {
    expect(Object.keys(tokens.color.dark).sort()).toEqual(Object.keys(tokens.color.light).sort());
    for (const value of [...Object.values(tokens.color.light), ...Object.values(tokens.color.dark)]) {
      expect(value).toMatch(/^#[0-9A-F]{6}$/);
    }
  });

  it("brand names exist for both locales and remain marked as draft", () => {
    expect(brand.name.ar).not.toBe("");
    expect(brand.name.en).not.toBe("");
    expect(brand.status).toBe("DRAFT_PENDING_OWNER_APPROVAL");
    expect(source.brand.trademarkClearance).toMatch(/^OPEN/);
  });

  it("generated CSS is current", () => {
    const committed = readFileSync(new URL("../src/generated/tokens.css", import.meta.url), "utf8");
    expect(committed).toBe(renderCss(tokens));
  });

  it("touch targets meet the 44px minimum", () => {
    expect(tokens.touchTarget.min).toBeGreaterThanOrEqual(44);
  });
});

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return ["node_modules", "dist", "generated"].includes(name) ? [] : sourceFiles(path);
    }
    return /\.(ts|tsx|css|html)$/.test(name) && !/\.test\./.test(name) ? [path] : [];
  });
}

describe("TC-103 name, logo and colors are editable without business-code changes", () => {
  const appSources = ["apps/customer-web/src", "apps/operations-web/src", "apps/customer-mobile/src"]
    .flatMap((dir) => sourceFiles(join(ROOT, dir)));

  it("apps do not hard-code the brand name", () => {
    expect(appSources.length).toBeGreaterThan(0);
    for (const file of appSources) {
      const text = readFileSync(file, "utf8");
      expect(text.includes(brand.name.ar) || text.includes(brand.name.en), file).toBe(false);
    }
  });

  it("apps do not hard-code hex colors", () => {
    for (const file of appSources) {
      expect(readFileSync(file, "utf8"), file).not.toMatch(/#[0-9a-fA-F]{6}\b/);
    }
  });
});
