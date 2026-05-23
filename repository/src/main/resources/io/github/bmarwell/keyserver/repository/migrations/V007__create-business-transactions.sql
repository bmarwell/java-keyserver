-- Business transaction tracking table.
-- Each row records one command invocation from STARTED through COMPLETED or FAILED.
-- Written in REQUIRES_NEW JTA transactions so rows survive a command-level rollback.
-- id is a TSID (time-sorted, node-aware) assigned by the application via @PrePersist.
CREATE TABLE business_transactions
(
    id           BIGINT                   NOT NULL PRIMARY KEY,
    command_type TEXT                     NOT NULL,
    -- NULL when the fingerprint cannot be extracted before dispatch.
    fingerprint  TEXT,
    caller_ip    TEXT,
    state        TEXT                     NOT NULL DEFAULT 'STARTED',
    started_at   TIMESTAMPTZ              NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT btx_state_check CHECK (state IN ('STARTED', 'COMPLETED', 'FAILED'))
);

CREATE INDEX btx_command_type  ON business_transactions (command_type);
CREATE INDEX btx_fingerprint   ON business_transactions (fingerprint);
CREATE INDEX btx_state         ON business_transactions (state);
CREATE INDEX btx_started_at    ON business_transactions (started_at);
