ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(64);
UPDATE refresh_tokens SET session_id = CONCAT('legacy-', id) WHERE session_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(512);
ALTER TABLE refresh_tokens ADD COLUMN ip_address VARCHAR(64);

ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP;
UPDATE refresh_tokens SET last_used_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE last_used_at IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN last_used_at SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN last_used_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE refresh_tokens ADD COLUMN revoked_at TIMESTAMP;

CREATE INDEX idx_refresh_tokens_user_id_revoked ON refresh_tokens(user_id, revoked);
CREATE INDEX idx_refresh_tokens_user_session_id ON refresh_tokens(user_id, session_id);
CREATE INDEX idx_refresh_tokens_last_used_at ON refresh_tokens(last_used_at DESC);
