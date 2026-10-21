# seedgrid-cities-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-cities` (artefato `seedgrid-cities`): países, estados e cidades do Brasil semeados por migration, a REST opcional ligada e o uso da fachada `BrazilGeo` em código do host.

A extension **migra sozinha** (histórico `flyway_schema_history_cities`) e semeia 5 países, 27 estados e 5.570 cidades — o exemplo não tem migration nem entidade própria.

> **Versão de trabalho.** A `seedgrid-cities` `2026.10.0` ainda não foi publicada no Maven Central. Antes de rodar, instale-a no repositório local do Maven, a partir do repositório da extension: `mvn install -DskipTests -Dgpg.skip=true`.

## O que o exemplo mostra

| Rota | Quem responde | Para que |
|---|---|---|
| `GET /cities-api/states`, `/states/by-uf/SP`, `/states/35/cities`, `/cities`, `/cities/by-ibge-code/3550308`, `/regions`, … | REST pronta da extension (`seedgrid.cities.rest.enabled=true`) | consultar o dado de referência |
| `GET /api/geo/capital/sp` | `GeoLookupResource` (código do host) | `BrazilGeo.capitalOf` |
| `GET /api/geo/search?q=sao paulo&uf=SP` | `GeoLookupResource` | `BrazilGeo.searchByPrefix` (sem acento) |

A REST da extension exige a permissão `CITY_READ`. **A extension não tem autenticação própria:** quem autentica é o host, e aqui é a `seedgrid-security` (login, JWT, roles/permissions), como nos exemplos de backup, billing e apikeys. A migration `V1203` da cities liga `CITY_READ` à role `ADMIN` quando as tabelas da security existem; o admin criado no primeiro boot já entra com a permissão.

## Como rodar

1. Suba a infraestrutura comum dos exemplos (PostgreSQL na porta `5439`, Mailpit), conforme o README do repositório:

   ```bash
   podman compose -f infra/compose.yml up -d
   ```

2. Crie o banco do exemplo, se ainda não existir (o compose não foi alterado para este exemplo):

   ```bash
   podman exec <container-postgres> psql -U postgres -c "create database cities_example"
   ```

3. Rode (as chaves JWT são geradas no primeiro build, fora do git):

   ```bash
   cd examples/seedgrid-cities-example
   ./run-dev.sh
   ```

4. Entre com o admin do primeiro boot (`dev.env`: `admin@example.com` / `Admin@123`; se a security pedir confirmação de e-mail, o link chega no Mailpit, <http://localhost:8025>):

   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8099/public/auth/login -H 'Content-Type: application/json' \
     -d '{"login":"admin@example.com","password":"Admin@123"}' | jq -r .token)
   curl -H "Authorization: Bearer $TOKEN" "http://localhost:8099/cities-api/states?size=3&sort=uf,asc"
   curl -H "Authorization: Bearer $TOKEN" "http://localhost:8099/cities-api/states/35/cities?size=2"
   curl -H "Authorization: Bearer $TOKEN" "http://localhost:8099/api/geo/capital/sp"
   curl -i "http://localhost:8099/cities-api/states"   # sem token: 401
   ```

   **Swagger:** <http://localhost:8099/q/swagger-ui>. Chame `POST /public/auth/login`, copie o `token` da resposta, clique em *Authorize* e cole o token; as rotas de `/cities-api` passam a responder 200.

## Serviços usados (do `infra/compose.yml`)

| Serviço | Porta no host | URL | Finalidade |
|---|---|---|---|
| PostgreSQL | `5439` | `jdbc:postgresql://localhost:5439/cities_example` | banco do exemplo (security, auditoria e a migration + seed da extension) |
| Mailpit | `8025` (UI), `1025` (SMTP) | <http://localhost:8025> | e-mails da security (link de confirmação do primeiro login) |
| Mockoon (ainda fora do compose) | `3100` | <http://localhost:3100/cities-api/states> | back falso da REST da cities, sem o back real (ver abaixo); sem UI web, a edição é pelo aplicativo desktop |
| Aplicação | `8099` | <http://localhost:8099> | o exemplo |

Redis e MinIO não são usados.

## Back falso (Mockoon) para o front

Quem faz o front sem subir o back usa o Mockoon: `mocks/cities.mockoon.json` (na extension) tem as 13 rotas de `/cities-api` com dados fictícios em pt-BR (5 países, estados e capitais conhecidas, ids estáveis).

```bash
# da pasta do repositório da extension (seedgrid-quarkus-ext-cities); no Git Bash use // no início do caminho do contêiner
podman run -d --name cities-mock -p 127.0.0.1:3100:3100 \
  -v "$PWD/mocks/cities.mockoon.json:/data/cities.mockoon.json:ro" \
  mockoon/cli:9.9.0 --data /data/cities.mockoon.json --port 3100 --disable-admin-api
curl http://localhost:3100/cities-api/states
```

O serviço `mockoon` (perfil `mock`, `docker compose --profile mock up -d mockoon`) **ainda não está no `infra/compose.yml`** deste repositório de exemplos: a edição do compose fica com o dono (pendência registrada na T1.10 do plano). O mock **não exige token** (o contrato de autenticação é do host; a REST real devolve 401 sem o token).

## Testes

```bash
mvn test                                                     # sobe PostgreSQL via Testcontainers
mvn test -Dcities.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/cities_test   # ou use um PostgreSQL já de pé (usuário/senha `test`; o schema public é recriado)
```

`CitiesExampleTest` prova: o login real da security devolve o token; a migration semeou 27 estados e 645 cidades de SP; a REST responde com o token do admin (que tem `CITY_READ` pela `V1203`) e nega o anônimo (401); o código do host usa o `BrazilGeo`.

## Observações de configuração

- **Hibernate no dev mode:** o exemplo define `quarkus.hibernate-orm.validate-in-dev-mode=false`. O Quarkus valida o schema numa thread logo após subir o Hibernate, e a cities migra no `StartupEvent` (depois), então num banco vazio o log mostraria dois `ERROR ... missing table [geo_cities]` apesar de a API responder.
- **`quarkus.hibernate-orm.packages`** lista as entidades da cities, da security, do commons e da auditoria (com várias extensions contribuindo entidades a lista é explícita).
- **Flyway do host:** só `security` e `audit`; a cities migra sozinha, com histórico `flyway_schema_history_cities`.
- **Versões:** a `seedgrid-security` 2026.10.14 traz o commons compatível com a cities (a 2026.09.25 trazia um commons anterior e a subida falhava com `ClassNotFoundException: ...spis.ApiExceptionExtension`).
