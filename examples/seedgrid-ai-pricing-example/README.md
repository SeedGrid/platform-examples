# seedgrid-ai-pricing-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-ai-pricing`, num host **sem `seedgrid-security`**: a extension é
agnóstica. Sem `quarkus-security` a REST administrativa dela (`/admin/ai/**`) não existe (responde `404`, nunca uma
rota desprotegida); o host conduz o fluxo chamando os serviços e repositórios da extension no `PriceFlowDemoResource`.

O robô lê preços de modelos de IA numa página de preços, **propõe** a mudança e só depois da aprovação de uma pessoa o
preço passa a valer. A página do fornecedor é simulada pelo próprio exemplo (`PricingPageResource`) e o extrator de
IA é um `FakePriceExtractor`.

## Fluxo demonstrado

1. `POST /demo/ai-pricing/models` `{"provider":"acme","model":"acme-small"}` — cadastra o modelo (sem preço).
2. `PUT /demo/ai-pricing/source` `{"sourceUrl":"<url da página de preços>"}` — de onde o robô lê.
3. `POST /demo/ai-pricing/check` — o robô lê a página e propõe (`status: CHANGES`); o preço vigente continua vazio.
4. `GET /demo/ai-pricing/proposals` — a proposta está `PENDING`.
5. `POST /demo/ai-pricing/proposals/{id}/approve` — a pessoa aprova; `GET /demo/ai-pricing/models` já mostra o preço.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8094)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8094/q/swagger-ui`.

## Testes

`PriceApprovalFlowTest` — a REST da extension é `404` sem security; o preço detectado só vale depois da aprovação.
A extension migra sozinha, no histórico próprio do Flyway.

## Configuração

`quarkus.hibernate-orm.packages` lista `br.com.seedgrid.aipricing.entities` e as do commons. O Flyway do host fica
sem migrations. Para ter a REST da extension (com RBAC), acrescente o `seedgrid-security` ao host.
