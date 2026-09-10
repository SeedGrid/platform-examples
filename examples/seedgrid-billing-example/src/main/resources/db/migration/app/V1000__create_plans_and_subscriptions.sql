-- ATENCAO -- ate o billing 2026.07.08 esta migration criava a tabela `plans` do proprio exemplo,
-- porque o seedgrid-billing-core so modelava BillingEventEntity. Desde o billing 2026.09.10 o
-- CORE e' o dono da familia plan (`plans`, `plan_terms`, `plan_term_permissions`, criadas na V502 e
-- na V504). Manter o CREATE TABLE aqui quebrava o boot com `relation "plans" already exists` -- as
-- migrations do billing rodam antes (V50x < V1000, e o Flyway ordena por versao, nao por location).
--
-- O que sobrou para a app: `subscriptions`. O vinculo assinatura->plano continua sendo plan_id, so
-- que agora aponta para a tabela do billing.

-- Assinatura de uma company (seedgrid-security) a um plano (seedgrid-billing-core). Uma linha por
-- company: o SubscriptionBillingEventHandler faz upsert nesta tabela conforme os webhooks chegam.
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
-- SEED: planos de exemplo, agora nas tabelas do billing-core
-- =========================
-- O preco nao fica mais no plano: mora na VIGENCIA (plan_terms.monthly_price), que e' o que permite
-- mudar de preco sem reescrever o historico de quem ja assinou. `effective_from` no passado deixa a
-- vigencia valendo desde o primeiro boot.
INSERT INTO plans (public_id, code, name, description, active) VALUES
    (gen_random_uuid()::text, 'BASIC', 'Plano Basico', 'Plano de entrada do exemplo', true),
    (gen_random_uuid()::text, 'PRO', 'Plano Pro', 'Plano completo do exemplo', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO plan_terms (public_id, plan_id, monthly_price, overage_price, effective_from)
SELECT gen_random_uuid()::text, p.id, v.monthly_price, 0.00, now() - interval '1 day'
FROM plans p
JOIN (VALUES ('BASIC', 49.90), ('PRO', 149.90)) AS v(code, monthly_price) ON v.code = p.code
WHERE NOT EXISTS (SELECT 1 FROM plan_terms pt WHERE pt.plan_id = p.id);
