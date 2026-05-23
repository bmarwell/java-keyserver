-- Add non-nullable business_transaction reference to audit_log.
-- Every audit entry must belong to exactly one business transaction.
-- The table is freshly created (dev-only), so this is safe as NOT NULL.
ALTER TABLE audit_log
    ADD COLUMN btx_id BIGINT NOT NULL
        REFERENCES business_transactions (id) ON DELETE RESTRICT;

CREATE INDEX audit_log_btx_id ON audit_log (btx_id);
