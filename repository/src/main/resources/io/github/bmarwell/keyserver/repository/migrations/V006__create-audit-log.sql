CREATE TABLE audit_log
(
    id              BIGSERIAL                NOT NULL PRIMARY KEY,
    command_type    TEXT                     NOT NULL,
    -- NULL when the fingerprint could not be extracted from the input.
    fingerprint     TEXT,
    request_ip      TEXT,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    result          TEXT                     NOT NULL,
    failure_type    TEXT,
    failure_message TEXT,
    CONSTRAINT audit_log_result_check
        CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX audit_log_fingerprint ON audit_log (fingerprint);
CREATE INDEX audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX audit_log_result      ON audit_log (result);
