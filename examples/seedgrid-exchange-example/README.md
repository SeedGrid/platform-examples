# seedgrid-exchange-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-exchange`, num host **sem `seedgrid-security`**: a extension é
agnóstica. Sem `quarkus-security` a REST administrativa dela (`/admin/currencies`, `/admin/exchange-rate`, ...) não
existe (responde `404`, nunca uma rota desprotegida); o host conduz o fluxo chamando os serviços e repositórios da
extension no `ExchangeDemoResource`.

A fonte da cotação (um Banco Central, uma API de câmbio) é simulada pelo próprio exemplo (`RateSourceResource`): o robô
da extension baixa o JSON e lê o valor pelo caminho configurado na moeda.

## Fluxo demonstrado

1. `POST /demo/exchange/currencies` — cadastra a moeda `EUR-BRL` com a URL da fonte, o cron do robô e os caminhos
   `value[0].cotacaoVenda` / `value[0].dataHoraCotacao`.
2. `GET /demo/exchange/currencies/EUR-BRL/series?days=7` — ainda vazia.
3. `POST /demo/exchange/currencies/EUR-BRL/fetch` — o robô busca a cotação de hoje e grava a linha do dia.
4. `GET /demo/exchange/currencies/EUR-BRL/series?days=7` — agora traz `5.1234`.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8095)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8095/q/swagger-ui`. A migration da extension semeia o `USD-BRL` ativo (PTAX do Banco
Central): com internet o robô o busca na subida; sem internet o aviso vai para o log e a aplicação sobe normalmente.

## Testes

`ExchangeFlowTest` — a REST da extension é `404` sem security; a cotação buscada aparece na série. A extension migra
sozinha, no histórico próprio do Flyway.

## Configuração

`quarkus.hibernate-orm.packages` lista `br.com.seedgrid.exchange.entities` e as do commons. O Flyway do host fica sem
migrations. Para ter a REST da extension (com RBAC), acrescente o `seedgrid-security` ao host.
