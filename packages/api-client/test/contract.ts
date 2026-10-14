import { readFileSync } from "node:fs";
import { Ajv2020 } from "ajv/dist/2020.js";
import addFormatsModule from "ajv-formats";
import { parse } from "yaml";

export const ROOT = new URL("../../../", import.meta.url);

export interface Operation {
  operationId: string;
  "x-matbakhy-id": string;
  "x-permission": string;
  "x-implemented": boolean;
  responses: Record<string, unknown>;
  security?: unknown[];
}

export interface OpenApi {
  openapi: string;
  paths: Record<string, Record<string, Operation>>;
  components: { schemas: Record<string, Record<string, unknown>> };
}

export const openapi = parse(readFileSync(new URL("contracts/openapi.yaml", ROOT), "utf8")) as OpenApi;

const addFormats = addFormatsModule as unknown as (ajv: Ajv2020) => void;
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addFormat("int64", { type: "number", validate: (n: number) => Number.isSafeInteger(n) });
const ID = "https://contracts.matbakhy.invalid/openapi";
ajv.addSchema({ $id: ID, components: openapi.components });

export function validatorFor(schema: string) {
  const validate = ajv.getSchema(`${ID}#/components/schemas/${schema}`);
  if (!validate) {
    throw new Error(`Unknown schema ${schema}`);
  }
  return (value: unknown) => validate(value) as boolean;
}
