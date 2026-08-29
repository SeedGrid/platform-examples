-- Seed permissions for payment_plans endpoints

-- Insert permissions for payment_plans
INSERT INTO permissions (public_id, name, description) VALUES
    (gen_random_uuid()::text, 'PAYMENT_PLANS_READ', 'Visualizar planos de pagamento'),
    (gen_random_uuid()::text, 'PAYMENT_PLANS_CREATE', 'Criar planos de pagamento'),
    (gen_random_uuid()::text, 'PAYMENT_PLANS_UPDATE', 'Atualizar planos de pagamento'),
    (gen_random_uuid()::text, 'PAYMENT_PLANS_DELETE', 'Deletar planos de pagamento')
ON CONFLICT (name) DO NOTHING;

-- Link permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('PAYMENT_PLANS_READ', 'PAYMENT_PLANS_CREATE', 'PAYMENT_PLANS_UPDATE', 'PAYMENT_PLANS_DELETE')
ON CONFLICT DO NOTHING;
