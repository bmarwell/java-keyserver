CREATE TABLE uids
(
    id              BIGSERIAL                NOT NULL PRIMARY KEY,
    fingerprint     TEXT                     NOT NULL REFERENCES keys (fingerprint) ON DELETE CASCADE,
    uid_raw         TEXT                     NOT NULL,
    uid_name        TEXT,
    uid_email       TEXT,
    creation_time   TIMESTAMP WITH TIME ZONE,
    expiration_time TIMESTAMP WITH TIME ZONE,
    revoked         BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- TRUE after the email verification link has been clicked.
    verified        BOOLEAN                  NOT NULL DEFAULT FALSE,
    UNIQUE (fingerprint, uid_raw)
);

CREATE INDEX uids_fingerprint ON uids (fingerprint);
CREATE INDEX uids_uid_email   ON uids (uid_email);
