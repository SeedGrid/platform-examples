CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    company_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(40),
    price NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_products_company_id ON products (company_id);
