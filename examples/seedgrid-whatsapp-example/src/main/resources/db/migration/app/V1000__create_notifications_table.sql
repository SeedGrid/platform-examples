CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    phone_number VARCHAR(32) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(200),
    error_message VARCHAR(1000)
);
