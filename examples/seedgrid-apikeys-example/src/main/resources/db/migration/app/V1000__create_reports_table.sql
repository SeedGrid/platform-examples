CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000)
);

-- =========================
-- PERMISSIONS do dominio do exemplo
-- =========================
INSERT INTO sg_permissions (name, description, public_id) VALUES
    ('REPORT_READ', 'Ler relatorios', gen_random_uuid()::text),
    ('REPORT_CREATE', 'Criar relatorios', gen_random_uuid()::text)
ON CONFLICT (name) DO NOTHING;

-- ADMIN (humano) recebe as duas
INSERT INTO sg_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sg_roles r
CROSS JOIN sg_permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('REPORT_READ', 'REPORT_CREATE')
ON CONFLICT DO NOTHING;

-- =========================
-- ROLE DE INTEGRACAO: so pode LER (principio do menor privilegio pra API keys M2M)
-- =========================
-- sg_roles.name so tem unique constraint PLANO ate a V223 do security; depois disso vira indice
-- unico PARCIAL (uk_sg_roles_name_global, WHERE company_id IS NULL) para permitir roles por conta.
-- O ON CONFLICT target precisa repetir essa mesma condicao pra Postgres achar o arbiter.
INSERT INTO sg_roles (name, description, public_id, for_integration) VALUES
    ('REPORT_READER', 'Integracao M2M com acesso somente leitura a relatorios', gen_random_uuid()::text, TRUE)
ON CONFLICT (name) WHERE company_id IS NULL DO NOTHING;

INSERT INTO sg_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sg_roles r
CROSS JOIN sg_permissions p
WHERE r.name = 'REPORT_READER'
  AND p.name = 'REPORT_READ'
ON CONFLICT DO NOTHING;
