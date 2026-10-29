# seedgrid-business-days-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-business-days`: feriados, perfil da semana e cálculo de
dias úteis por REST, protegido por RBAC (`seedgrid-security`, que também informa a empresa e a UF).

O exemplo não tem domínio próprio: tudo vem da extension.

## Fluxo demonstrado

1. `POST /public/auth/login` — admin do bootstrap da security.
2. `GET /business-days/check?date=2026-04-21` — Tiradentes: feriado nacional semeado, não é dia útil.
3. `GET /business-days/count?from=2026-04-20&to=2026-04-24` — 4 dias úteis (a terça é feriado).
4. `GET /business-days/add?from=2026-04-20&days=2` — anda 2 dias úteis pulando o feriado (quinta 23).
5. `POST /business-days/holidays` `{"name":"Feriado da empresa","month":4,"day":22,"year":2026}` — feriado só da
   empresa; a contagem do passo 3 cai para 3. Repetir a data devolve `409`.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8093)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8093/q/swagger-ui`. Login do `dev.env`: `admin@example.com` / `Admin@123`.

## Testes

`BusinessDaysFlowTest` — sem token é `401`; feriado nacional semeado não é dia útil; feriado da empresa muda
a contagem e o deslocamento; data repetida é `409`. As migrations da security e da business-days rodam sozinhas,
cada uma no seu histórico do Flyway.

## Configuração

`quarkus.hibernate-orm.packages` precisa listar `br.com.seedgrid.businessdays.entities` junto das entidades da
security, do commons e da cities. O Flyway do host fica sem migrations: a extension migra sozinha.
