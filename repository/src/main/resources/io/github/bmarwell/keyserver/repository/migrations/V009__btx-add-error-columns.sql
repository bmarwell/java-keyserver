-- Add error detail columns to business_transactions.
-- Populated only when state = 'FAILED'; NULL for STARTED and COMPLETED rows.
ALTER TABLE business_transactions
    ADD COLUMN error_type    TEXT,
    ADD COLUMN error_message TEXT;
