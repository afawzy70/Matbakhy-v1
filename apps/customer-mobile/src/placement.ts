import { loadPending, savePending, saveBasket, type PendingPlacement } from "./basket";
import { errorCode, type MobileApi } from "./mobileApi";

export type PlacementOutcome =
  | { kind: "placed"; orderId: string }
  /** No definitive answer (offline, unreachable, server error): the keys are kept; retrying cannot duplicate. */
  | { kind: "uncertain"; offline: boolean }
  /** The server answered definitively; these keys can never succeed. */
  | { kind: "rejected"; code: string }
  /** The server wants a second factor first (D18). Nothing was ordered and the stored keys are kept; the customer retries explicitly after verifying. */
  | { kind: "stepUp" };

async function settle(orderId: string): Promise<PlacementOutcome> {
  await savePending(null);
  await saveBasket(null);
  return { kind: "placed", orderId };
}

/** Sends the stored placement with its fixed idempotency key and client request id. */
export async function place(api: MobileApi, p: PendingPlacement): Promise<PlacementOutcome> {
  try {
    const r = await api.POST("/api/v1/orders", {
      params: { header: { "Idempotency-Key": p.idempotencyKey } },
      body: { quoteId: p.quoteId, clientRequestId: p.clientRequestId, acceptedPolicyVersions: { terms: p.terms, cancellation: p.cancellation } },
    });
    if (r.data) {
      return settle(r.data.id);
    }
    if (r.response.status >= 500 || r.response.status === 401) {
      return { kind: "uncertain", offline: false };
    }
    if (r.response.status === 403 && errorCode(r.error) === "STEP_UP_REQUIRED") {
      await savePending({ ...p, awaitingExplicitRetry: true });
      return { kind: "stepUp" };
    }
    await savePending(null);
    return { kind: "rejected", code: errorCode(r.error) };
  } catch {
    return { kind: "uncertain", offline: true };
  }
}

/**
 * After a restart or a lost response: asks the server whether the pending order exists (by client request id). Found
 * means placed; a definite "not found" re-sends with the same keys; anything else stays uncertain and nothing is
 * claimed about the order or about stock.
 */
export async function recover(api: MobileApi, explicit = false): Promise<PlacementOutcome | null> {
  const p = await loadPending();
  if (!p) {
    return null;
  }
  if (p.awaitingExplicitRetry && !explicit) {
    return { kind: "stepUp" };
  }
  try {
    const r = await api.GET("/api/v1/orders/by-request/{clientRequestId}", { params: { path: { clientRequestId: p.clientRequestId } } });
    if (r.data) {
      return settle(r.data.id);
    }
    if (r.response.status === 404) {
      return place(api, { clientRequestId: p.clientRequestId, idempotencyKey: p.idempotencyKey, quoteId: p.quoteId, terms: p.terms, cancellation: p.cancellation });
    }
    return { kind: "uncertain", offline: false };
  } catch {
    return { kind: "uncertain", offline: true };
  }
}
