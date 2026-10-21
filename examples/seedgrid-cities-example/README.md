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

A REST da extension exige a permissão `CITY_READ`. Sem `security` no exemplo, o usuário `reader` (senha `reader`, basic auth em memória) ganha a permissão pelo `CityReaderAugmentor`. Num host real a permissão vem do RBAC: a migration `V1203` da cities a liga à role `ADMIN` quando a `security` existe.

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
   source dev.env && mvn quarkus:dev
   ```

4. Experimente (basic auth `reader:reader`):

   ```bash
   curl -u reader:reader "http://localhost:8099/cities-api/states?size=3&sort=uf,asc"
   curl -u reader:reader "http://localhost:8099/cities-api/states/35/cities?size=2"
   curl -u reader:reader "http://localhost:8099/api/geo/capital/sp"
   ```

   Swagger: <http://localhost:8099/q/swagger-ui>.

## Serviços usados (do `infra/compose.yml`)

| Serviço | Porta no host | URL | Finalidade |
|---|---|---|---|
| PostgreSQL | `5439` | `jdbc:postgresql://localhost:5439/cities_example` | banco do exemplo (migration + seed da extension) |
| Aplicação | `8099` | <http://localhost:8099> | o exemplo |

Redis, MinIO, Mailpit e Mockoon não são usados. **Sem mock Mockoon:** a REST da extension é opcional (desligada por padrão) e não tem uso no front.

## Testes

```bash
mvn test                                                     # sobe PostgreSQL via Testcontainers
mvn test -Dcities.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/cities_test   # ou use um PostgreSQL já de pé (usuário/senha `test`; o schema public é recriado)
```

`CitiesExampleTest` prova: a migration semeou 27 estados e 645 cidades de SP; a REST responde com basic auth e nega o anônimo (401); o código do host usa o `BrazilGeo`.
