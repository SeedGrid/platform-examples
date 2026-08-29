-- Migrations da app host comecam em V1000 (ordem de versao global do Flyway + baseline das libs).
-- As colunas name/email/cpf/phone guardam o texto CIFRADO (v1:iv:ciphertext, AES-256-GCM) — por
-- isso sao VARCHAR largos, nao o tamanho do dado cru. As colunas *_hash guardam o hash de busca
-- HMAC-SHA256 em hex (64 chars) e sao indexadas para a busca por igualdade sem descriptografar.
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    email VARCHAR(512) NOT NULL,
    email_hash VARCHAR(64),
    cpf VARCHAR(512) NOT NULL,
    cpf_hash VARCHAR(64),
    phone VARCHAR(512)
);

CREATE INDEX idx_customers_email_hash ON customers (email_hash);
CREATE INDEX idx_customers_cpf_hash ON customers (cpf_hash);
