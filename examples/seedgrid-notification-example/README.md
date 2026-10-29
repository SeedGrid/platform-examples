# seedgrid-notification-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-notification`, num host **sem `seedgrid-security`**: a extension é
agnóstica. Sem `quarkus-security` a REST administrativa dela (`/admin/notifications`) não existe (responde `404`,
nunca uma rota desprotegida); o host conduz o fluxo chamando os serviços da extension no `NotificationDemoResource`.

O host só diz **o que aconteceu** (publica um evento de negócio); a extension decide quem recebe, em quais canais,
enfileira, tenta de novo se falhar e registra cada entrega.

## Fluxo demonstrado

1. O host registra o evento `order.created` no catálogo (`OrderEvents`, um `NotificationCatalogContributor`).
2. `PUT /demo/notification/rule` `{"emails":["cliente@example.com"]}` — liga o evento no canal e-mail.
3. `POST /demo/notification/orders` `{"customer":"Maria"}` — o host publica o evento; a entrega nasce `PENDING`
   (`GET /demo/notification/deliveries`, assunto `Novo pedido de Maria`).
4. `POST /demo/notification/dispatch` — roda o envio agora (em produção é o agendamento da extension); a entrega
   vira `SENT`.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8096)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

O e-mail sai pelo mailer nomeado `security` (nome histórico do mailer da plataforma): em dev aponta para o Mailpit
(`localhost:1025`, UI em <http://localhost:8025>); nos testes é simulado (`mock=true`). Swagger em
`http://localhost:8096/q/swagger-ui`.

## Testes

`NotificationFlowTest` — a REST da extension é `404` sem security; o evento publicado vira entrega e é enviado por
e-mail. A extension migra sozinha, com o Flyway próprio dela.

## Configuração

`quarkus.hibernate-orm.packages` lista as entidades do commons (as da notification entram sozinhas). O Flyway do host
fica sem migrations. Para ter a REST da extension (com RBAC), acrescente o `seedgrid-security` ao host.
