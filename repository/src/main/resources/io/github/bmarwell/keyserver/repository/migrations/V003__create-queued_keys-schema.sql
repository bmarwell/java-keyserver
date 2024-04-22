CREATE TABLE queued_keys
(
    rfingerprint        TEXT NOT NULL PRIMARY KEY
);

CREATE INDEX IF NOT EXISTS qkeys_rfp ON queued_keys(rfingerprint text_pattern_ops);
