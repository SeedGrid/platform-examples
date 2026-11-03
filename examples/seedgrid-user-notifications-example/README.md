# seedgrid-user-notifications-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-user-notifications`: a caixa de notificações do usuário. O host **só
coloca a dependência** `seedgrid-user-notifications`; a migration, as rotas `/user-notifications` e o bean `UserNotifier`
vêm juntos, sem nenhuma propriedade obrigatória. Este host não tem `seedgrid-security`: o usuário é o padrão
`no-security` do commons e o `@Authenticated` da rota é inerte (quem nega, sem usuário, é o próprio serviço).

## Fluxo demonstrado

1. `POST /demo/notify?title=Primeira` — o host chama `UserNotifier.notify(...)` (o único código do exemplo).
2. `GET /user-notifications/unread-count` — `{"count":1}`.
3. `GET /user-notifications` — lista paginada (`items`, `page`, `size`, `total`).
4. `POST /user-notifications/{id}/read` — marca como lida (204, idempotente).
5. `DELETE /user-notifications/{id}` — exclui (204). `POST /user-notifications/read-all` e `DELETE /user-notifications`
   marcam todas / limpam.

O aviso em tempo real sai por `SgUserEvent` (canal `user-notifications`); com a extension `realtime` no host ele chega
ao Pulse, sem a user-notifications conhecer o Pulse.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8098)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8098/q/swagger-ui`.

## Configuração

`quarkus.hibernate-orm.packages` lista `br.com.seedgrid.usernotifications.entities` e as do commons. Opcionais:
`seedgrid.user-notifications.retention` (P30D), `max-per-user` (200), `max-payload-bytes` (4096), `enabled` (true).
