-- MB-011: server-side staff grants and append-only audit log.

CREATE TABLE user_grant (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES app_user (id),
    permission    text        NOT NULL CHECK (permission IN (
                      'staff.grant', 'audit.read', 'cook.review', 'content.moderate', 'order.ops', 'order.exception_cancel',
                      'order.handover_override', 'support.case', 'finance.read', 'finance.verify', 'finance.refund_approve',
                      'finance.settlement', 'config.read', 'config.write', 'integration.ops')),
    granted_by    uuid        REFERENCES app_user (id),
    grant_reason  text        NOT NULL CHECK (length(grant_reason) BETWEEN 3 AND 500),
    granted_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz,
    revoked_at    timestamptz,
    revoked_by    uuid        REFERENCES app_user (id),
    revoke_reason text        CHECK (revoke_reason IS NULL OR length(revoke_reason) BETWEEN 3 AND 500),
    CONSTRAINT user_grant_revocation_ck CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    CONSTRAINT user_grant_expiry_ck CHECK (expires_at IS NULL OR expires_at > granted_at)
);

-- One active grant per user and permission; history rows (revoked) remain.
CREATE UNIQUE INDEX user_grant_active_uk ON user_grant (user_id, permission) WHERE revoked_at IS NULL;

CREATE TABLE audit_event (
    id          uuid        PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    actor_id    uuid,
    action      text        NOT NULL CHECK (length(action) BETWEEN 1 AND 120),
    permission  text,
    object_type text,
    object_id   text,
    outcome     text        NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED')),
    reason      text,
    request_id  text,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX audit_event_time_idx ON audit_event (occurred_at DESC, id DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor_id, occurred_at DESC);
CREATE INDEX audit_event_object_idx ON audit_event (object_type, object_id, occurred_at DESC);

-- Append-only: rows can never be changed or removed through the application database role.
CREATE FUNCTION audit_event_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$;

CREATE TRIGGER audit_event_no_update BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_immutable();
