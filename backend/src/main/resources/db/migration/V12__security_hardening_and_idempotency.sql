ALTER TABLE app_users
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret VARCHAR(128);

ALTER TABLE sales_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE purchase_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_tokens_user_expires
    ON password_reset_tokens(user_id, expires_at DESC);
CREATE INDEX idx_password_reset_tokens_expires
    ON password_reset_tokens(expires_at);

CREATE TABLE api_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    actor_username VARCHAR(100) NOT NULL,
    endpoint_key VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_api_idempotency_key UNIQUE (actor_username, endpoint_key, idempotency_key)
);

CREATE INDEX idx_api_idempotency_keys_expires_at
    ON api_idempotency_keys(expires_at);
