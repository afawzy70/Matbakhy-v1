-- MB-039 runtime database role (Flyway afterMigrate callback, run by the migration owner after every migrate).
-- Migrations run as the owner; the API connects as the least-privilege runtime role named by the Flyway placeholder
-- runtimeRole (DATABASE_RUNTIME_ROLE). Without that setting (local single-user development) this callback does nothing.
-- The runtime role gets data access only: no ownership, no DDL, no TRUNCATE, and no UPDATE or DELETE on append-only
-- tables, so it cannot rewrite or delete audit, ledger, revision or order history, nor disable the protecting triggers.
DO $$
DECLARE
    runtime text := '${runtimeRole}';
    t text;
BEGIN
    IF runtime = '' OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime) THEN
        RETURN;
    END IF;
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', runtime);
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', runtime);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', runtime);
    EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', runtime);
    EXECUTE format('REVOKE INSERT, UPDATE, DELETE ON TABLE flyway_schema_history FROM %I', runtime);
    -- Fully append-only (unconditional triggers): no UPDATE or DELETE privilege at all.
    FOREACH t IN ARRAY ARRAY['audit_event', 'case_message', 'config_change', 'ledger_entry', 'ledger_group', 'meal_revision',
                             'order_event', 'order_item', 'payment_event'] LOOP
        EXECUTE format('REVOKE UPDATE, DELETE ON TABLE %I FROM %I', t, runtime);
    END LOOP;
    -- Guarded tables (some updates are legitimate, deletion never is).
    FOREACH t IN ARRAY ARRAY['customer_order', 'cook_application_revision', 'settlement_line', 'order_payment'] LOOP
        EXECUTE format('REVOKE DELETE ON TABLE %I FROM %I', t, runtime);
    END LOOP;
END $$;
