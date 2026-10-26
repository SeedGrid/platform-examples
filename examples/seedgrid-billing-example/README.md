# seedgrid-billing-example

App de exemplo, rodável, do `seedgrid-billing-core`: um "produto" mínimo (gerar relatórios) cobrado por
assinatura, com trial, plano pago, checkout, webhook, franquia por **contador de uso** e funcionalidade por
plano. O gateway é um `FakeBillingProvider` próprio, para rodar sem Stripe. Em produção, use o
`seedgrid-billing-stripe`, que implementa as mesmas interfaces sem nenhum código no host.

É a **referência mínima**. A demonstração completa, com telas, planos semeados e **dois contadores** (um
contado pela anotação e outro pelo código), está nos boilerplates `login-tenancy` e `subdomain-tenancy`.

`seedgrid-billing-core` **não roda sozinho**: depende do `seedgrid-security` (usuários, empresas, RBAC dos
próprios endpoints).

## O que é da extension e o que é do produto

| | `seedgrid-billing-core` | Este produto |
|---|---|---|
| Plano, term (preço, vigência, vagas, permissões), trial, assinatura, ciclo | tudo | nada |
| Checkout, portal, webhook, faturas, troca de plano | tudo | nada |
| **Quais contadores de uso existem** | junta e valida | declara (`ReportUsageMeters`) |
| **O que é 1 unidade e quando contar** | — | `@SgConsumesUsage(meter = "reports")` |
| Franquia e preço do excedente de cada contador, por plano | guarda e cobra | só semeia a cota do trial |
| Bloqueio 402 (sem assinatura, trial vencido, fora do plano, franquia esgotada) | tudo | nada |
| Funcionalidade por plano | `@SgRequiresPlanFeature` | a permissão `REPORT_EXPORT` |
| Gateway | interfaces | `FakeBillingProvider` (só no exemplo) |

O código do produto é pequeno de propósito:

- `product/ReportUsageMeters.java`: declara o contador `reports`.
- `product/ReportResource.java`: `POST /api/reports` consome 1 relatório; `GET /api/reports/export` só abre
  se o plano liberar `REPORT_EXPORT`.
- `db/migration/app/V1000__product_billing_seed.sql`: a permissão `REPORT_EXPORT` e a cota do trial
  (3 relatórios).

## Como estender: contadores de uso

Um **contador** é o que o produto cobra por uso: relatórios, requisições, MB armazenados, notas emitidas. O
produto diz quais existem e quando contar. O billing guarda, por plano, a **franquia** (quanto está
incluído) e o **preço do excedente** (quanto custa cada unidade além dela), e faz o resto.

### 1. Declare os contadores

```java
@ApplicationScoped
public class ReportUsageMeters implements UsageMeterContributor {
    public static final String REPORTS = "reports";

    @Override
    public List<UsageMeterDefinition> meters() {
        return List.of(new UsageMeterDefinition(REPORTS, "Relatorios"));
        // outro contador = mais uma linha:
        // new UsageMeterDefinition("storage-mb", "MB armazenados")
    }
}
```

- O **código** (`[a-z0-9-]`, até 40 caracteres) é a identidade do contador **para sempre**: vai para o banco,
  para o `@SgConsumesUsage` e para o nome do meter no provider. Não reaproveite o código de um contador removido
  para outro significado, porque misturaria o histórico de cobrança dos dois.
- O **label** é o que aparece nas telas e nas mensagens de erro.
- Código inválido ou repetido derruba o startup.

### 2. Conte

Quando **uma chamada vale uma quantidade fixa**, anote o método:

```java
@POST
@SgConsumesUsage(meter = ReportUsageMeters.REPORTS)        // units = 1 por padrão
public Map<String, Object> generate() { ... }
```

O `meter` é obrigatório: esquecer não compila. Um código que nenhum `UsageMeterContributor` declara (ex.:
`"reprots"`) **derruba o startup**, com o método na mensagem.

Quando **a quantidade só se conhece em runtime** (ex.: os MB de um upload), conte pelo código:

```java
Account account = currentAccountResolver.requireCurrentAccount();
usageService.assertCanConsume(account, "storage-mb", mb);          // 402 se não puder
// ... faz o trabalho ...
usageService.consume(account, "storage-mb", mb, "upload-" + fileId); // chave idempotente
```

Nos dois casos: checa **antes** (o trabalho nem roda se a conta não pode consumir), contabiliza **depois**,
só se deu certo, e dentro da transação do método (um rollback desfaz o consumo). O excedente é reportado ao
provider **depois do commit**.

### 3. Defina a franquia e o excedente por plano

Pela API de admin do billing (ou pelas telas dos boilerplates). Cada plano lista os contadores que inclui:

```json
POST /admin/plans/{planId}/terms
{
  "monthlyPrice": 99.90,
  "meters": [
    { "meter": "reports", "includedUnits": 100, "overagePrice": 0.50 }
  ],
  "permissionPublicIds": ["<publicId de REPORT_EXPORT>"]
}
```

- `overagePrice: 0` significa **sem excedente**: esgotou a franquia, bloqueia (402 `QUOTA_EXCEEDED`).
- `overagePrice > 0` significa **com excedente**: não bloqueia por franquia. Cada unidade a mais é cobrada
  pelo provider, numa linha própria da fatura por contador.
- Um contador que **não está** no plano dá 402 `METER_NOT_IN_PLAN` ao consumir ("seu plano não inclui
  Relatórios").
- Um contador que o produto não declara é recusado com 400 `INVALID_PLAN_TERM_METER`.
- A cota do trial é ajustada com `PATCH /admin/plans/trial` `{"meters": [...]}`. O trial não aceita
  excedente.
- `GET /admin/billing/meters` lista os contadores declarados, para as telas montarem uma linha por contador.

### 4. Mostre o consumo ao cliente

`GET /me/usage` traz o saldo de cada contador do ciclo (`billingCycle.meters[]`: incluído, consumido,
excedente, restante) e, em `meterAccess.<contador>`, se 1 unidade passaria agora, com o mesmo `code` do 402
que o consumo daria. Assim o client explica o bloqueio sem tentar. `access` diz se a **assinatura** permite
consumir (vale para todos os contadores).

### Códigos 402 de consumo

| `code` | Quando |
|---|---|
| `NO_ACTIVE_SUBSCRIPTION` / `TRIAL_EXPIRED` | a conta não tem assinatura vigente |
| `PAYMENT_PAST_DUE` | pagamento pendente |
| `METER_NOT_IN_PLAN` | o plano não inclui o contador (`meter`, `label` no corpo) |
| `QUOTA_EXCEEDED` / `TRIAL_QUOTA_EXCEEDED` | franquia do contador esgotada num plano sem excedente dele |

## Fluxo de ponta a ponta

É o que o `BillingCheckoutWebhookFlowTest` exercita:

1. A conta nasce no **trial**, com 3 relatórios (`V1000`).
2. `POST /api/reports` 3 vezes; na 4ª, 402 `TRIAL_QUOTA_EXCEEDED`. `GET /api/reports/export` dá 402
   `FEATURE_NOT_IN_PLAN`.
3. O admin cria o plano **Pro** e um term com `reports`: 100 incluídos, R$ 0,50 o excedente, liberando
   `REPORT_EXPORT`.
4. `POST /me/billing/checkout {"planId": "..."}`: o client manda só o plano; a extension monta o resto e
   devolve a URL do gateway.
5. Abrir a URL (`GET /public/fake-gateway/checkout/{id}`) "paga": o gateway fake entrega o
   `CHECKOUT_COMPLETED` assinado ao webhook, e a assinatura Pro fica ativa com a franquia nova.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (porta 8088)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger UI: http://localhost:8088/q/swagger-ui.

## Testes

- `BillingSchemaBootstrapTest`: as migrations de security, audit, billing e app rodam no mesmo banco; a
  permissão do produto e a cota do trial (contador `reports`) estão lá.
- `BillingCheckoutWebhookFlowTest`: o fluxo acima, mais um term com contador não declarado (400) e um
  webhook com assinatura inválida (400).

## Pegadinhas

- **Versão do `seedgrid-commons`:** o `pom.xml` fixa o commons que a billing exige. Sem isso, o Maven
  escolhe a versão mais antiga, que chega pela security, e a billing quebra em runtime
  (`NoClassDefFoundError`) ao gravar um term.
- `seedgrid-billing-core` empacota o próprio `META-INF/microprofile-config.properties` com
  `quarkus.flyway.locations=db/migration/billing`. Ao sobrescrever `quarkus.flyway.locations`, inclua
  `classpath:db/migration/billing` (junto com `security`, `audit` e `app`). Sem isso, o app sobe e só quebra
  na primeira chamada de billing.
- As migrations da app são sempre `V1000+`: o Flyway ordena por versão **global**, não por pasta
  (`security`=2xx, `audit`=3xx, `billing`=5xx).
- Ao trocar migrations de lugar ou de nome, rode `mvn clean`: um arquivo antigo em `target/classes` gera
  "Found more than one migration with version ...".
- O admin criado pelo bootstrap (`FirstAdminBootstrapService`) nasce com e-mail **não confirmado** e
  `changePasswordRequired=true`. Os testes confirmam o e-mail e limpam essa flag direto no banco antes de
  logar.
