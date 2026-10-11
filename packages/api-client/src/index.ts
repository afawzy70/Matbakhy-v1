import createClient, { type ClientOptions } from "openapi-fetch";
import type { components, operations, paths } from "./generated/schema";

export type { components, operations, paths };
export type ApiError = components["schemas"]["ApiError"];
export { MalformedMoneyError, parseMoney, type Money } from "./money";

/**
 * Typed client for the Matbakhy API. Paths, parameters and bodies are checked against
 * contracts/openapi.yaml at compile time. Authentication (cookie session + CSRF on web, bearer token
 * on native) is configured by the caller in MB-009 and later stories.
 */
export function createMatbakhyClient(options: ClientOptions) {
  return createClient<paths>(options);
}
