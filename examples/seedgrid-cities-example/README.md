# seedgrid-cities-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-cities` (artefato `seedgrid-cities`), num host **sem `seedgrid-security`**: a extension é agnóstica. Países, estados e cidades do Brasil semeados por migration, a consulta pública (`/public/cities`) e o uso da fachada `BrazilGeo` em código do host.

A extension **migra sozinha** (histórico `sg_flyway_schema_history_cities`) e semeia 5 países, 27 estados e 5.570 cidades — o exemplo não tem migration nem entidade própria.

> **Versão de trabalho.** A `seedgrid-cities` `2026.10.0` ainda não foi publicada no Maven Central. Antes de rodar, instale-a no repositório local do Maven, a partir do repositório da extension: `mvn install -DskipTests -Dgpg.skip=true`.

## O que o exemplo mostra

| Rota | Quem responde | Para que |
|---|---|---|
| `GET /public/cities?uf=SP&name=campinas`, `/public/cities/by-ibge-code/3509502` | consulta pública da extension (`seedgrid.cities.public-rest.enabled=true`) | autocompletar cidade; devolve só `id`, `name`, `uf`, `ibgeCode` |
| `GET /api/geo/capital/sp` | `GeoLookupResource` (código do host) | `BrazilGeo.capitalOf` |
| `GET /api/geo/search?q=sao paulo&uf=SP` | `GeoLookupResource` | `BrazilGeo.searchByPrefix` (sem acento) |
| `GET /cities-api/states` | — | **404**: a REST administrativa da extension (`/cities-api`) exige `quarkus-security`; sem ele ela não existe, nunca fica desprotegida |

**A extension não tem autenticação própria.** Para ter a REST administrativa (com a permissão `CITY_READ`), acrescente o `seedgrid-security` ao host.

## Como rodar

1. Suba a infraestrutura comum dos exemplos (PostgreSQL na porta `5439`), conforme o README do repositório:

   ```bash
   podman compose -f infra/compose.yml up -d
   ```

2. Crie o banco do exemplo, se ainda não existir (o compose não foi alterado para este exemplo):

   ```bash
   podman exec <container-postgres> psql -U postgres -c "create database cities_example"
   ```

3. Rode:

   ```bash
   cd examples/seedgrid-cities-example
   ./run-dev.sh
   ```

4. Consulte (sem token):

   ```bash
   curl "http://localhost:8099/public/cities?uf=SP&name=campinas"
   curl "http://localhost:8099/api/geo/capital/sp"
   curl -i "http://localhost:8099/cities-api/states"   # 404: REST administrativa exige security
   ```

   **Swagger:** <http://localhost:8099/q/swagger-ui>.

## Serviços usados (do `infra/compose.yml`)

| Serviço | Porta no host | URL | Finalidade |
|---|---|---|---|
| PostgreSQL | `5439` | `jdbc:postgresql://localhost:5439/cities_example` | banco do exemplo (a migration + seed da extension) |
| Mockoon (perfil `mock`, só sobe sob demanda) | `3100` | <http://localhost:3100/cities-api/states> | back falso da REST da cities, sem o back real (ver abaixo); sem UI web, a edição é pelo aplicativo desktop |
| Aplicação | `8099` | <http://localhost:8099> | o exemplo |

Mailpit, Redis e MinIO não são usados.

## Back falso (Mockoon) para o front

Quem faz o front sem subir o back usa o Mockoon: `mocks/cities.mockoon.json` (na extension) tem as 13 rotas de `/cities-api` com dados fictícios em pt-BR (5 países, estados e capitais conhecidas, ids estáveis).

```bash
# da pasta infra/ deste repositório (podman compose ou docker compose)
podman compose --profile mock up -d mockoon
curl http://localhost:3100/cities-api/states
```

O serviço `mockoon` (`mockoon/cli:9.9.0`, perfil `mock`, porta `3100`, sem UI web) está no `infra/compose.yml` e não sobe com o `up -d` comum: só com `--profile mock`. Ele serve `mocks/cities.mockoon.json` deste repositório, que é uma **cópia**: a **fonte** é `mocks/cities.mockoon.json` do repositório da extension (`seedgrid-quarkus-ext-cities`); ao mudar o mock, edite lá e recopie. O mock **não exige token**; é o contrato da REST administrativa, que só existe num host com `seedgrid-security` (a REST real exige token).

## Testes

```bash
mvn test                                                     # sobe PostgreSQL via Testcontainers
mvn test -Dcities.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/cities_test   # ou use um PostgreSQL já de pé (usuário/senha `test`; o schema public é recriado)
```

`CitiesExampleTest` prova: a consulta pública responde sem token; o código do host usa o `BrazilGeo`; a REST administrativa da extension é `404` sem security.

## Observações de configuração

- **Hibernate no dev mode:** o exemplo define `quarkus.hibernate-orm.validate-in-dev-mode=false`. O Quarkus valida o schema numa thread logo após subir o Hibernate, e a cities migra no `StartupEvent` (depois), então num banco vazio o log mostraria dois `ERROR ... missing table [sg_cities]` apesar de a API responder.
- **`quarkus.hibernate-orm.packages`** lista as entidades da cities e do commons.
- **Flyway do host:** nenhum; a `cities` migra sozinha, no histórico `sg_flyway_schema_history_cities`.
