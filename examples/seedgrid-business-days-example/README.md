# seedgrid-business-days-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-business-days`, num host **sem `seedgrid-security`**: a extension é
agnóstica. Sem `quarkus-security` a REST própria dela (`/business-days/**`) não existe (responde `404`, nunca uma rota
desprotegida); o host expõe o que quer chamando os serviços da extension (`BusinessDayService`, `HolidayService`)
no `CalendarDemoResource`. O calendário é global (sem escopo de empresa).

## Fluxo demonstrado

1. `GET /demo/business-days/check?date=2026-04-21` — Tiradentes: feriado nacional semeado, não é dia útil.
2. `GET /demo/business-days/count?from=2026-04-20&to=2026-04-24` — 4 dias úteis (a terça é feriado).
3. `GET /demo/business-days/add?from=2026-04-20&days=2` — anda 2 dias úteis pulando o feriado (quinta 23).
4. `POST /demo/business-days/holidays` `{"name":"Feriado da empresa","month":4,"day":22,"year":2026}` — feriado
   próprio; a contagem do passo 2 cai para 3.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8093)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8093/q/swagger-ui`.

## Testes

`BusinessDaysFlowTest` — a REST da extension é `404` sem security; feriado nacional semeado não é dia útil; feriado
próprio muda a contagem e o deslocamento. A extension migra sozinha, no histórico próprio do Flyway.

## Configuração

`quarkus.hibernate-orm.packages` lista `br.com.seedgrid.businessdays.entities` e as do commons. O Flyway do host fica
sem migrations. Para ter a REST da extension (com RBAC), acrescente o `seedgrid-security` ao host.
