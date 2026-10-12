-- MB-039 (TC-056): carry the originating request ID with each outbox event so asynchronous dispatch and provider
-- failures can be traced back to the request in logs. The value is the validated X-Request-Id (never user content).
ALTER TABLE outbox_event ADD COLUMN request_id text CHECK (request_id IS NULL OR request_id ~ '^[A-Za-z0-9-]{8,64}$');
