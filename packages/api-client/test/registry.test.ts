import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { openapi, ROOT, validatorFor } from "./contract";

interface RegistryEntry {
  id: string;
  method: string;
  path: string;
  permission: string;
  response_type: string;
  implemented: boolean;
}

const registry = (JSON.parse(readFileSync(new URL("contracts/endpoints.json", ROOT), "utf8")) as { endpoints: RegistryEntry[] }).endpoints;
const operations = Object.entries(openapi.paths).flatMap(([path, methods]) =>
  Object.entries(methods).map(([method, op]) => ({ path, method: method.toUpperCase(), op })));

describe("contract matches the endpoint registry", () => {
  it("uses OpenAPI 3.1", () => {
    expect(openapi.openapi).toMatch(/^3\.1\./);
  });

  it("has exactly the registry operations with matching permission and implementation state", () => {
    expect(operations).toHaveLength(registry.length);
    for (const entry of registry) {
      const match = operations.find((o) => o.path === entry.path && o.method === entry.method);
      expect(match, `${entry.id} ${entry.method} ${entry.path}`).toBeDefined();
      expect(match?.op["x-matbakhy-id"]).toBe(entry.id);
      expect(match?.op["x-permission"]).toBe(entry.permission);
      expect(match?.op["x-implemented"]).toBe(entry.implemented);
    }
  });

  it("public operations are unauthenticated and every other operation requires a session or token", () => {
    for (const { op } of operations) {
      if (op["x-permission"] === "PUBLIC") {
        expect(op.security).toEqual([]);
      } else {
        expect(op.security).toEqual([{ sessionCookie: [] }, { bearerAuth: [] }]);
      }
    }
  });

  it("every operation documents the standard error body", () => {
    for (const { op } of operations) {
      expect(op.responses["4XX"]).toEqual({ $ref: "#/components/responses/Error" });
      expect(op.responses["5XX"]).toEqual({ $ref: "#/components/responses/Error" });
    }
  });

  it("every schema compiles, and typed schemas forbid unknown properties", () => {
    for (const [name, schema] of Object.entries(openapi.components.schemas)) {
      expect(() => validatorFor(name)).not.toThrow();
      if (schema["type"] === "object" && schema["x-schema-status"] !== "PENDING") {
        expect(schema["additionalProperties"], name).toBe(false);
      }
    }
  });

  it("error body schema matches the backend error codes", () => {
    const backend = readFileSync(new URL("backend/shared/src/main/java/com/matbakhy/shared/error/ErrorCode.java", ROOT), "utf8");
    const codes = [...backend.matchAll(/^\s{4}([A-Z_]+)\(\d{3}, (?:true|false)\)/gm)].map((m) => m[1]);
    const schema = openapi.components.schemas["ApiError"] as { properties: { code: { enum: string[] } } };
    expect(schema.properties.code.enum.slice().sort()).toEqual(codes.slice().sort());
  });
});
