-- rollback-compat: tightening the replaced grant check accepts a strict superset of the previous permission codes, so no existing row or older API write can violate it
-- D18 (owner decision 2026-10-18): configurable MFA. The authentication itself stays in Keycloak (OIDC step-up);
-- these tables hold only the administrator's policy per account category and each user's own preference.
-- Additive: new tables, plus two new permission codes in the grant check (relaxing, so the previous API is unaffected).
CREATE TABLE mfa_policy (
    category    text        PRIMARY KEY CHECK (category IN ('CUSTOMER', 'KITCHEN', 'STAFF')),
    mode        text        NOT NULL CHECK (mode IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    reason      text        CHECK (reason IS NULL OR length(reason) BETWEEN 3 AND 500),
    updated_by  uuid        REFERENCES app_user (id),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0
);
-- PREVIEW default (owner decision): every category OPTIONAL; REQUIRED is an explicit later administrator action.
INSERT INTO mfa_policy (category, mode) VALUES ('CUSTOMER', 'OPTIONAL'), ('KITCHEN', 'OPTIONAL'), ('STAFF', 'OPTIONAL');

CREATE TABLE user_mfa_preference (
    user_id    uuid        PRIMARY KEY REFERENCES app_user (id),
    enabled    boolean     NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE user_grant DROP CONSTRAINT user_grant_permission_check;
ALTER TABLE user_grant ADD CONSTRAINT user_grant_permission_check CHECK (permission IN (
    'staff.grant', 'audit.read', 'cook.review', 'content.moderate', 'order.ops', 'order.exception_cancel',
    'order.handover_override', 'support.case', 'finance.read', 'finance.verify', 'finance.refund_approve',
    'finance.settlement', 'config.read', 'config.write', 'integration.ops', 'security.mfa_policy', 'security.mfa_reset'));
