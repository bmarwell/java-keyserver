-- Redo the keys table with a complete schema.
-- V001/V002 are superseded by this migration.
DROP TABLE IF EXISTS keys CASCADE;

CREATE TABLE keys
(
    -- Full hex fingerprint: 40 chars (v4) or 64 chars (v5).
    fingerprint     TEXT                     NOT NULL PRIMARY KEY,
    -- Reversed fingerprint for suffix-based index scans (SKS-style).
    rfingerprint    TEXT                     NOT NULL GENERATED ALWAYS AS (reverse(fingerprint)) STORED,
    -- Last 16 hex chars of fingerprint; covers both short (8) and long (16) key IDs.
    keyid_long      TEXT                     NOT NULL GENERATED ALWAYS AS (right(fingerprint, 16)) STORED,
    version         INTEGER                  NOT NULL,
    algorithm       INTEGER                  NOT NULL,
    -- NULL for fixed-length ECC algorithms (Ed25519, X25519, …).
    bit_strength    INTEGER,
    creation_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    expiration_time TIMESTAMP WITH TIME ZONE,
    revoked         BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- Full ASCII-armored public key block, returned verbatim on op=get.
    armored_key     TEXT                     NOT NULL,
    md5             TEXT                     NOT NULL,
    ctime           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    mtime           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX keys_md5          ON keys (md5);
CREATE INDEX        keys_rfingerprint ON keys (rfingerprint text_pattern_ops);
CREATE INDEX        keys_keyid_long   ON keys (keyid_long);
CREATE INDEX        keys_ctime        ON keys (ctime);
CREATE INDEX        keys_mtime        ON keys (mtime);
