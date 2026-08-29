-- Plano de assinatura da app host (seedgrid-billing-core NAO modela plano/assinatura, so
-- BillingEventEntity para idempotencia/auditoria de webhook).
CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL'
);

-- Assinatura de uma company (seedgrid-security) a um plano. Uma linha por company: o
-- SubscriptionBillingEventHandler faz upsert nesta tabela conforme os webhooks de billing chegam.
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    company_id BIGINT NOT NULL UNIQUE REFERENCES companies(id),
    plan_id BIGINT NOT NULL REFERENCES plans(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_subscription_id VARCHAR(255),
    provider_customer_id VARCHAR(255),
    provider_checkout_session_id VARCHAR(255),
    current_period_end TIMESTAMP,
    canceled_at TIMESTAMP
);

CREATE INDEX idx_subscriptions_provider_subscription_id ON subscriptions(provider_subscription_id);

-- =========================
-- SEED: planos de exemplo
-- =========================
INSERT INTO plans (public_id, code, name, price_cents, currency) VALUES
    (gen_random_uuid()::text, 'BASIC', 'Plano Basico', 4990, 'BRL'),
    (gen_random_uuid()::text, 'PRO', 'Plano Pro', 14990, 'BRL')
ON CONFLICT (code) DO NOTHING;
