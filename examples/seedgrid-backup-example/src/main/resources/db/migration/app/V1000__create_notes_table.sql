CREATE TABLE notes (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    create_user_public_id VARCHAR(36),
    update_user_public_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    content VARCHAR(4000)
);

-- =========================
-- PERMISSIONS do dominio do exemplo
-- =========================
INSERT INTO sg_permissions (name, description, public_id) VALUES
    ('NOTE_READ', 'Ler anotacoes', gen_random_uuid()::text),
    ('NOTE_CREATE', 'Criar anotacoes', gen_random_uuid()::text)
ON CONFLICT (name) DO NOTHING;

-- ADMIN (humano, bootstrap do seedgrid-security) recebe as duas
INSERT INTO sg_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sg_roles r
CROSS JOIN sg_permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('NOTE_READ', 'NOTE_CREATE')
ON CONFLICT DO NOTHING;
