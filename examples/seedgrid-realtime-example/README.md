# seedgrid-realtime-example

App de exemplo, rodável, do `seedgrid-realtime`: **avisos em tempo real para o usuário logado**, pelo
SeedGrid Pulse. O exemplo é só backend (como os demais): quem conecta no Pulse seria o front, com o token que este
backend entrega.

O realtime pega o usuário **da sessão** (o `sub` do JWT do `seedgrid-security`), por isso este host traz o security
(login JWT, admin do primeiro boot) e o `seedgrid-audit-postgresql`, que o security usa.

## O que o exemplo mostra

| Peça | Classe | Papel |
|---|---|---|
| Token de conexão | `POST /realtime/connect` (vem da extension) | autenticado; devolve o token curto com que o front conecta no Pulse. O usuário é o da sessão, nunca o do request |
| Um "processo" que avisa | `NoticeResource` (`POST /notices/report-finished`) | dispara o evento neutro `SgUserEvent` para o usuário logado. **Não conhece o realtime nem o Pulse** |
| Pulse **falso** | `FakePulseResource` (`/fake-pulse/...`) | emite o token e guarda o que recebeu; `GET /fake-pulse/received` mostra o aviso que saiu. Num produto a URL aponta para o Pulse de verdade |

A extension escuta o `SgUserEvent` (depois do commit) e publica no Pulse para aquele usuário. Pulse fora do ar não
derruba quem chama: o token responde `503` (a tela volta ao polling) e o processo termina normalmente.

## Configuração

Em `src/main/resources/application.properties` (valores de desenvolvimento em `dev.env`):

| Env | Padrão do exemplo | O quê |
|---|---|---|
| `SEEDGRID_REALTIME_ENABLED` | `true` | liga o realtime (a extension, sozinha, vem desligada) |
| `SEEDGRID_REALTIME_PULSE_BASE_URL` | `http://localhost:8090/fake-pulse` | o Pulse visto pelo backend; aqui, o falso do próprio app |
| `SEEDGRID_REALTIME_PULSE_PROJECT_KEY` | valor de exemplo, sem segredo | a chave `project` da conta no Pulse. **Segredo**: num produto, só no env do backend, nunca no repositório |

## Passos para rodar

```bash
# 1. Postgres do repositório (porta 5439)
podman compose -f infra/compose.yml up -d postgres       # ou: docker compose ...
#    Se o volume do Postgres já existia antes deste exemplo, crie o banco uma vez:
podman exec seedgrid-examples-postgres psql -U postgres -c "CREATE DATABASE realtime_example"

# 2. O app (porta 8090). O par de chaves JWT é gerado no build.
cd examples/seedgrid-realtime-example
./run-dev.sh
```

```bash
B=http://localhost:8090

# 1. Login do admin do primeiro boot (a senha está em dev.env)
TOKEN=$(curl -s -X POST $B/public/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin@example.com","password":"Admin@123"}' | jq -r .token)

# 2. Sem login não há token (401); logado, o token é do usuário da sessão
curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/realtime/connect
curl -s -X POST $B/realtime/connect -H "Authorization: Bearer $TOKEN"
# -> {"enabled":true,"token":"pct_...","expiresAt":...,"connectUrl":"wss://..."}

# 3. O "processo" avisa o usuário; o aviso chega ao Pulse (falso) para ele
curl -s -X POST $B/notices/report-finished -H "Authorization: Bearer $TOKEN"
curl -s $B/fake-pulse/received -H "Authorization: Bearer $TOKEN"
# -> [{"channel":"report","event":"report.finished.success","user":"<publicId do admin>","data":{...}}]
```

Se o admin do primeiro boot ainda não confirmou o e-mail, o login recusa; os testes confirmam direto no banco
(`RealtimeFlowTest`).

## Testes

`mvn verify` (precisa de Podman/Docker: o Postgres dos testes é Testcontainers). Cobrem: sem login não há token;
logado, o token é do usuário da sessão; o aviso disparado pelo processo chega ao Pulse para o usuário logado; com o
Pulse fora do ar o token responde `503` e o processo ainda termina (`PulseDownTest`).

O guia da extension, com a tabela completa de status e a configuração, está no README do `seedgrid-realtime`.
