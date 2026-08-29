# seedgrid-apikeys-example

App de exemplo, rodável, do `seedgrid-apikeys`: autenticação **machine-to-machine** via header
`x-api-key`, para integrações (ERPs, cronjobs, parceiros) que não têm um usuário humano pra logar
via JWT.

`seedgrid-apikeys` **não roda standalone** — depende hard-code de `seedgrid-security` (a chave é
sempre atrelada a um `BaseUser` dono e a uma `Role`; as permissões efetivas da chave vêm 100% da
role, não há "scopes" próprios por chave).

## O princípio central: API key = Role, não dono

Uma API key não carrega permissões próprias. Ela herda as permissões da `Role` atribuída a ela no
momento da criação. Por isso o padrão recomendado é criar **roles de integração dedicadas e
enxutas** (`for_integration = true`), em vez de emitir chaves com a role `ADMIN` inteira.

Este exemplo demonstra isso com o domínio `Report`:

- `REPORT_READ` e `REPORT_CREATE` — permissões do domínio.
- `ADMIN` (humano, via JWT) tem as duas.
- `REPORT_READER` (role de integração, `for_integration=true`) tem **só** `REPORT_READ`.

## Fluxo

1. `POST /public/auth/login` — humano loga com JWT (usa o admin do bootstrap do `seedgrid-security`).
2. `POST /api/reports` (JWT, precisa `REPORT_CREATE`) — cria um relatório.
3. `POST /apikeys/role/REPORT_READER` (JWT, precisa `APIKEY_CREATE` — endpoint do próprio
   `seedgrid-apikeys`) `{"name": "job-noturno", "expiresDays": 90}` — emite uma API key atrelada à
   role `REPORT_READER`. A chave crua só aparece **nesta resposta** (campo `apiKey`); depois só o
   hash fica salvo.
4. `GET /api/reports` com header `x-api-key: <chave>` — a integração lê os relatórios, **sem JWT
   nenhum**.
5. `POST /api/reports` com a **mesma** API key → **403**: a role `REPORT_READER` não tem
   `REPORT_CREATE`.

`@PermissionsAllowed("REPORT_READ")` (Quarkus Security nativo) protege `ReportResource` igual pra
JWT humano e API key — o mechanism da extension injeta as permissions da role da chave direto no
`SecurityIdentity`, sem código extra no app host.

## Gestão de chaves (endpoints prontos, vêm da extension)

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/apikeys/role/{roleName}` | `APIKEY_CREATE` |
| `GET` | `/apikeys/role/{roleName}` | `APIKEY_READ` |
| `GET` | `/apikeys` (do usuário atual, paginação opcional `?page=&size=`) | `APIKEY_READ` |
| `POST` | `/apikeys/{publicId}/revoke` | `APIKEY_REVOKE` |

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env
mvn test              # sobe Postgres via Testcontainers automaticamente
```

## Testando via curl

```bash
# 1. Login (admin do bootstrap — email/senha vem do dev.env / dos logs de boot)
TOKEN=$(curl -s -X POST http://localhost:8087/public/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin@example.com","password":"<senha do log de boot>"}' | jq -r .token)

# 2. Cria um report
curl -X POST http://localhost:8087/api/reports -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Relatorio mensal","content":"..."}'

# 3. Emite uma API key pra role REPORT_READER
APIKEY=$(curl -s -X POST http://localhost:8087/apikeys/role/REPORT_READER -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"job-noturno","expiresDays":90}' | jq -r .apiKey)

# 4. Le com a api key (sem JWT)
curl http://localhost:8087/api/reports -H "x-api-key: $APIKEY"

# 5. Tenta criar com a api key -> 403 (role so tem REPORT_READ)
curl -i -X POST http://localhost:8087/api/reports -H "x-api-key: $APIKEY" \
  -H 'Content-Type: application/json' -d '{"title":"x","content":"x"}'
```

Swagger UI: http://localhost:8087/q/swagger-ui.

## Testes

- `ApiKeysSchemaBootstrapTest` — prova que as migrations de security + apikeys + app (reports)
  rodam no mesmo banco, incluindo a semente de permissions/role de integração do domínio do
  exemplo.
- `ApiKeyReportAccessTest` — fluxo completo: login admin → cria report → emite API key pra
  `REPORT_READER` → lê via `x-api-key` (sem JWT) → tenta criar com a mesma chave e recebe 403.

## Pegadinhas encontradas montando este exemplo (úteis se for reaproveitar o padrão)

- Migrations da app host que rodam **junto** com as da extension (`quarkus.flyway.locations`
  listando várias pastas) precisam de versão **maior que a maior versão de qualquer módulo**
  (aqui, `apikeys` vai até V603) — Flyway aplica em ordem de versão global, não por pasta. Por
  isso a migration deste exemplo é `V1000`, não `V1`.
- `roles.name` só tem unique constraint "normal" até a V223 do `seedgrid-security`; depois disso
  vira um **índice único parcial** (`WHERE company_id IS NULL`). Um `INSERT ... ON CONFLICT (name)
  DO NOTHING` simples falha com "no unique or exclusion constraint matching" — é preciso repetir a
  mesma condição: `ON CONFLICT (name) WHERE company_id IS NULL DO NOTHING`.
- O admin criado pelo bootstrap (`FirstAdminBootstrapService`) nasce com e-mail **não confirmado**
  e `changePasswordRequired=true` — os testes de integração precisam confirmar o e-mail e limpar
  essa flag direto no banco antes de logar (não dá pra logar com o admin "cru" do bootstrap).
