-- QA-002: a durable canonical fingerprint of the placement request, stored with the order's permanent request reference
-- (customer_id, client_request_id). Recovery after the temporary idempotency record is gone compares it, so a reused
-- reference with a materially different request is a deterministic conflict. Nullable: orders placed before this
-- migration keep the previous quote-only recovery check. Additive and backward compatible.
ALTER TABLE customer_order ADD COLUMN request_fingerprint text;
COMMENT ON COLUMN customer_order.request_fingerprint IS 'sha256 of the canonical placement request; null for orders placed before V29';
