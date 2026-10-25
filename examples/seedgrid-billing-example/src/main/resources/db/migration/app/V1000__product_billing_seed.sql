-- O que o billing precisa saber DESTE produto. Plano, term, trial, assinatura e ciclo sao tabelas do
-- seedgrid-billing (db/migration/billing): este produto nao cria nenhuma.

-- 1. A funcionalidade que o plano pode liberar (@RequiresPlanFeature("REPORT_EXPORT")). E' uma permissao
--    comum da security: o term do plano lista quais libera; o usuario ainda precisa te-la pelo perfil.
insert into sg_permissions (name, description, public_id, audience)
values ('REPORT_EXPORT', 'Exportar relatorios', gen_random_uuid()::text, 'client')
on conflict (name) do nothing;

insert into sg_role_permissions (role_id, permission_id)
select r.id, p.id
from sg_roles r
cross join sg_permissions p
where r.name = 'ADMIN'
  and p.name = 'REPORT_EXPORT'
on conflict do nothing;

-- 2. A cota do trial: o term TRIAL e' semeado pelo billing (billing/V505); aqui so' entra a franquia do
--    contador DESTE produto (`reports`, declarado em ReportUsageMeters). O trial nao tem excedente.
--    Depois do primeiro boot, a cota se ajusta pela API: PATCH /admin/plans/trial com `meters`.
insert into sg_plan_term_meters (public_id, plan_term_id, meter, included_units, overage_price)
select gen_random_uuid()::text, pt.id, 'reports', 3, 0
from sg_plan_terms pt
where pt.public_id = '00000000-0000-0000-0000-0000000000a2'
on conflict (plan_term_id, meter) do nothing;
