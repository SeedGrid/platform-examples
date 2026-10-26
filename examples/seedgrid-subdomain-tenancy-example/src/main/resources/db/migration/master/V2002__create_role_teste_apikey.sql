-- Create role for API key integration (PAYMENT_PLANS_READ)

BEGIN;

INSERT INTO sg_roles (public_id, name, description, for_integration)
VALUES (gen_random_uuid()::text, 'TESTE_APIKEY', 'Role de integração (somente leitura de planos de pagamento)', true)
ON CONFLICT (name) WHERE company_id IS NULL DO NOTHING;

INSERT INTO sg_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sg_roles r
JOIN sg_permissions p ON p.name = 'PAYMENT_PLANS_READ'
WHERE r.name = 'TESTE_APIKEY'
ON CONFLICT DO NOTHING;

COMMIT;
