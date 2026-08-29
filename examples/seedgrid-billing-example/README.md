# seedgrid-billing-example

App de exemplo, rodável, do `seedgrid-billing-core`: checkout, webhook e assinatura por company,
usando um `FakeBillingProvider` próprio (sem Stripe de verdade — o `seedgrid-billing-stripe`
existe pronto para produção, mas não é o foco didático aqui).

`seedgrid-billing-core` **não roda standalone** — depende hard-code de `seedgrid-security` (os
próprios endpoints `/billing/checkout` e `/billing/portal` são protegidos por
`@PermissionsAllowed`, RBAC do security).

## O que o `seedgrid-billing-core` faz (e o que não faz)

- Faz: expõe `POST /billing/checkout`, `POST /billing/portal` e `POST /public/billing/webhook`
  prontos; persiste `BillingEventEntity` (tabela `billing_events`) para idempotência de webhook
  (`unique(provider, event_id)`); define a SPI `BillingProvider` (que você implementa) e
  `BillingEventHandler` (que a app host implementa para reagir aos eventos).
- Não faz: não modela Plano nem Assinatura — isso é responsabilidade da app host. Este exemplo
  cria as entidades `Plan` e `Subscription` você mesmo.

## O princípio central: o provider é um adaptador, o handler é o dono do domínio

- `FakeBillingProvider` (`BillingProvider`) — adaptador do "gateway" fake: cria checkout/portal
  (URLs locais não navegáveis de verdade) e verifica/parseia o webhook. Não sabe nada de
  `Plan`/`Subscription`.
- `SubscriptionBillingEventHandler` (`BillingEventHandler`) — dono do domínio: ao receber um
  evento canônico (`CHECKOUT_COMPLETED`/`SUBSCRIPTION_CREATED`/`SUBSCRIPTION_CANCELED`), cria/ativa
  ou cancela a `Subscription` da `Company` referenciada.

## Formato do webhook fake

Como não existe Stripe de verdade aqui, o formato do payload é **definido por este exemplo**
(`FakeWebhookPayload`), assinado por um HMAC-SHA256 simples no header `x-fake-signature`
(segredo: `seedgrid.billing.fake.webhook-secret`):

```json
{
  "eventId": "evt_123",
  "type": "CHECKOUT_COMPLETED",
  "companyId": 1,
  "planCode": "PRO",
  "subscriptionId": "sub_fake_abc",
  "customerId": "cus_fake_abc",
  "checkoutSessionId": "cs_fake_abc"
}
```

`type` é o nome exato de um `BillingEventType` (`CHECKOUT_COMPLETED`, `SUBSCRIPTION_CREATED`,
`SUBSCRIPTION_CANCELED`, ...). `companyId` vira `BillingReference.externalReference()`;
`planCode` vira `metadata.get("planCode")` — é assim que o `SubscriptionBillingEventHandler` sabe
qual company/plano ativar, já que o "provider" fake não guarda estado nenhum entre o checkout e o
webhook (quem simula o pagamento monta o payload à mão, ecoando os dados que quiser).

## Fluxo

1. `POST /public/auth/login` — humano loga com JWT (admin do bootstrap do `seedgrid-security`).
2. `POST /billing/checkout` (JWT, precisa `BILLING_CHECKOUT_CREATE`) `{"planCode": "PRO",
   "customerEmail": "...", "successUrl": "...", "cancelUrl": "..."}` — o `FakeBillingProvider` gera
   um `checkoutSessionId` fake e uma URL local fake.
3. `POST /public/billing/webhook` (público, header `x-fake-signature` — HMAC do body, não JWT) com
   um payload sintético `CHECKOUT_COMPLETED` (ou `SUBSCRIPTION_CREATED`) — simula o "pagamento
   confirmado". O `SubscriptionBillingEventHandler` cria/ativa a `Subscription` da company
   informada.
4. `GET /api/subscriptions/me` (qualquer usuário autenticado) — mostra o plano/assinatura ativa da
   **company do usuário logado** (resolvida via `CompanyRequestContext`, claim `companyId` do
   token — não precisa de permissão especial além de estar autenticado).
5. `POST /public/billing/webhook` com um payload `SUBSCRIPTION_CANCELED` referenciando a mesma
   `subscriptionId` — a assinatura fica `CANCELED`. `GET /api/subscriptions/me` reflete a mudança.

## Endpoints da extension (já prontos, você não precisa reimplementar)

| Método | Rota | Permissão | Auth |
|---|---|---|---|
| `POST` | `/billing/checkout` | `BILLING_CHECKOUT_CREATE` | JWT |
| `POST` | `/billing/portal` | `BILLING_PORTAL_CREATE` | JWT |
| `POST` | `/public/billing/webhook` | — | assinatura HMAC (`x-fake-signature`) |

## Endpoint próprio deste exemplo

| Método | Rota | Permissão | Auth |
|---|---|---|---|
| `GET` | `/api/subscriptions/me` | — (só autenticado) | JWT |

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env
mvn test              # sobe Postgres via Testcontainers automaticamente
```

## Testando via curl

```bash
# 1. Login (admin do bootstrap — email/senha vem do dev.env / dos logs de boot)
TOKEN=$(curl -s -X POST http://localhost:8088/public/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin@example.com","password":"<senha do log de boot>"}' | jq -r .token)

# 2. Cria um checkout para o plano PRO
curl -s -X POST http://localhost:8088/billing/checkout -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"PRO","customerEmail":"admin@example.com","successUrl":"http://localhost/ok","cancelUrl":"http://localhost/cancel"}'

# 3. Descubra o companyId da company default (ou consulte GET /me/companies com o TOKEN)
#    Neste exemplo com 1 unica company, normalmente e o id 1.

# 4. Monta o payload e assina com HMAC-SHA256 (segredo: dev-fake-webhook-secret)
BODY='{"eventId":"evt_1","type":"CHECKOUT_COMPLETED","companyId":1,"planCode":"PRO","subscriptionId":"sub_fake_1","customerId":"cus_fake_1"}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "dev-fake-webhook-secret" | sed 's/^.* //')

curl -i -X POST http://localhost:8088/public/billing/webhook \
  -H "Content-Type: application/json" -H "x-fake-signature: $SIG" -d "$BODY"

# 5. Ve a assinatura ativa
curl http://localhost:8088/api/subscriptions/me -H "Authorization: Bearer $TOKEN"

# 6. Cancela (novo payload, SUBSCRIPTION_CANCELED, mesma subscriptionId, assinatura recalculada)
BODY2='{"eventId":"evt_2","type":"SUBSCRIPTION_CANCELED","companyId":1,"planCode":"PRO","subscriptionId":"sub_fake_1","customerId":"cus_fake_1"}'
SIG2=$(printf '%s' "$BODY2" | openssl dgst -sha256 -hmac "dev-fake-webhook-secret" | sed 's/^.* //')
curl -i -X POST http://localhost:8088/public/billing/webhook \
  -H "Content-Type: application/json" -H "x-fake-signature: $SIG2" -d "$BODY2"

curl http://localhost:8088/api/subscriptions/me -H "Authorization: Bearer $TOKEN"
```

Swagger UI: http://localhost:8088/q/swagger-ui.

## Testes

- `BillingSchemaBootstrapTest` — prova que as migrations de security + billing (V500/V501) + app
  (plans/subscriptions, V1000) rodam no mesmo banco, incluindo a semente de permissions do billing
  vinculadas a `ADMIN` e dos planos `BASIC`/`PRO`.
- `BillingCheckoutWebhookFlowTest` — fluxo completo: login admin → checkout → webhook
  `CHECKOUT_COMPLETED` assinado → `GET /api/subscriptions/me` mostra `PRO`/`ACTIVE` → webhook
  `SUBSCRIPTION_CANCELED` → `GET /api/subscriptions/me` mostra `CANCELED`; mais um teste de
  assinatura HMAC inválida → `400`.

## Pegadinhas encontradas montando este exemplo

- **BUG na extension (#14, CORRIGIDO no `seedgrid-billing-core:2026.07.08`)**: até a versão
  `2026.07.02`, o arquivo empacotado `db/migration/billing/V501_add_permissions.sql` usava
  **underscore simples** entre a versão e a descrição, e o Flyway o **ignorava silenciosamente**
  (só um `WARN` — `validateMigrationNaming` é `false` por padrão), então as permissions
  `BILLING_CHECKOUT_CREATE`/`BILLING_PORTAL_CREATE` nunca eram semeadas e o admin recebia `403`
  em `POST /billing/checkout` e `/billing/portal`. A partir de `2026.07.08` o arquivo foi
  renomeado para `V501__add_permissions.sql` (underscore duplo), roda de verdade e é defensivo
  (bloco `DO $$`: só semeia se a tabela `permissions` existir — em hosts com `seedgrid-security`,
  como este, semeia normalmente). Nota histórica: este exemplo contornava o bug com uma migration
  própria `V1001__workaround_billing_permissions_naming_bug.sql` (mesmo SQL do V501, idempotente),
  removida após o fix — agora as permissions vêm do V501 oficial da extension.
- `seedgrid-billing-core` empacota seu **próprio** `META-INF/microprofile-config.properties` com
  `quarkus.flyway.locations=db/migration/billing` e
  `quarkus.flyway.table=flyway_schema_history_billing` como *defaults* — diferente de
  security/apikeys, que não setam essas properties. Se você não sobrescrever
  `quarkus.flyway.locations` incluindo `classpath:db/migration/billing` (junto com `security` e
  `app`) e uma `quarkus.flyway.table` única, a tabela `billing_events` nunca é criada e o app só
  quebra em runtime na primeira chamada de checkout/webhook — não no boot.
- Migrations da app host (`db/migration/app`) sempre `V1000+`: o Flyway aplica tudo em ordem de
  versão **global**, não por pasta. Convenção do ecossistema: `subdomain-tenancy`=V100s,
  `security`=V200s, `audit`=V300s, `backup`=V400s, `billing`=V500-501, `apikeys`=V600s,
  `login-tenancy`=V700s.
- `BillingProvider` é uma SPI **completa** (15 métodos) — mesmo implementando só o fluxo de
  checkout/webhook/cancelamento neste exemplo, os demais métodos (troca de plano, portal,
  invoices, proration...) precisam de um corpo (ainda que no-op) para compilar.
- Headers HTTP no `RawWebhookRequest.headers()` chegam com o *case* original do cliente (o
  `BillingWebhookController` monta o `Map<String, List<String>>` direto de
  `HttpHeaders.getRequestHeaders()`, sem normalizar) — comparar o nome do header
  (`x-fake-signature`) com `equalsIgnoreCase`, não com `.get(chave-exata)`.
- Para resolver a *company* do usuário logado num endpoint próprio (`GET /api/subscriptions/me`),
  basta injetar `br.com.seedgrid.security.services.CompanyRequestContext` e ler
  `getCurrentCompanyId()` — já vem populado pelo `CompanyContextFilter` do `seedgrid-security` a
  partir da claim `companyId` do JWT (não precisa resolver `BaseUser` → `Company` manualmente).
- `seedgrid-billing-core` traz `seedgrid-resilience-core` como dependência transitiva (usa
  `@SeedGridRateLimit` nos próprios endpoints de checkout/portal/webhook) — não precisou adicionar
  nada no `pom.xml` deste exemplo além do `seedgrid-billing-core`, o Maven resolveu offline.
- O admin criado pelo bootstrap (`FirstAdminBootstrapService`) nasce com e-mail **não confirmado**
  e `changePasswordRequired=true` — os testes precisam confirmar o e-mail e limpar essa flag direto
  no banco antes de logar.
