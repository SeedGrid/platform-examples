# Changelog

## [Nao publicado]

### Alterado (migracao para a API atual do billing, Fase 5 / T5.9)
- Depende de `seedgrid-billing-core` 2026.10.14-1, `seedgrid-security` 2026.10.14 e `seedgrid-audit-postgresql` 2026.09.25.
- `FakeBillingProvider` reescrito para a API atual: pacotes `billing.spis` (`BillingProvider`), `billing.dtos`
  (`BillingEvent`, `BillingReference`, `CheckoutRequest`...), `billing.exceptions`; assinaturas com
  `List<String> newMeteredPriceIds`; passa a implementar tambem `BillingCatalogProvider` (publicacao dos precos do term)
  e lembra o metadata de cada sessao de checkout.
- `FakeWebhookPayload`: `companyId`/`planCode` saem; entram `accountPublicId`, `planTermPublicId`, `invoiceId`,
  `amountPaid` (a conta e o term vem do metadata do checkout).
- `application.properties`: Flyway com `security`, `audit` e `billing` (sem `app`); `hibernate-orm.packages` com as
  entidades do audit; URLs de checkout/portal; chaves do ciclo de inadimplencia (`seedgrid.billing.past-due.*`,
  desligado por padrao).
- README reescrito (fluxo `/admin/plans` -> `/me/billing/checkout` -> webhook -> `/me/subscription`).

### Removido
- Dominio proprio de plano/assinatura (`entity/Plan`, `entity/Subscription`, `SubscriptionStatus`, repositorios,
  `SubscriptionService`, `SubscriptionResource` `/api/subscriptions/me`, `SubscriptionDTO`, migration `V1000`) e o
  `SubscriptionBillingEventHandler` proprio: o core agora e dono desse dominio (as entidades colidiam: `Plan` duplicado)
  e traz o handler padrao.

### Adicionado
- `CompanyAccessNotifier`: observa `CompanyAccessEvent` (exemplo de aviso ao responsavel; so' registra no log).
- Testes: `CompanyLimitsExampleTest` (os quatro campos novos do `PlanTerm`/trial e a cota de empresas via
  `PlanCompanyQuotaValidator`), `ExampleClients` (conta cliente de teste); `BillingSchemaBootstrapTest` confere as
  colunas da V515.
