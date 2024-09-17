CREATE TABLE queued_keys
(
    rfingerprint        TEXT NOT NULL PRIMARY KEY,
    itime               TIMESTAMP WITH TIME ZONE NOT NULL,
    secret              TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS qkeys_rfp ON queued_keys(rfingerprint text_pattern_ops);
CREATE INDEX IF NOT EXISTS qkeys_itime ON queued_keys(itime);
