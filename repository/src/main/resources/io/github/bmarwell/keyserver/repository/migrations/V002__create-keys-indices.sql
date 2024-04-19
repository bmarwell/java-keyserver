CREATE INDEX IF NOT EXISTS keys_rfp ON keys(rfingerprint text_pattern_ops);
CREATE INDEX IF NOT EXISTS keys_ctime ON keys(ctime);
CREATE INDEX IF NOT EXISTS keys_mtime ON keys(mtime);
