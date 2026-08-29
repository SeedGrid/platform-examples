# seedgrid-whatsapp-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-whatsapp`: envio de mensagens de texto via
WhatsApp através de uma interface comum (`WhatsAppApi`), com o provider trocável por config
(`Evolution API` self-hosted, ou `WhatsApp Cloud API` oficial da Meta).

Diferente do `seedgrid-apikeys-example`, a extension `seedgrid-quarkus-ext-whatsapp` é
**standalone** — não depende de `seedgrid-security` (nem de JWT, RBAC, tenancy, etc). Este
exemplo não tem autenticação nenhuma; o foco é demonstrar o uso **direto** da extension para
enviar mensagens, não a integração com 2FA do `seedgrid-security` (que também usa esta lib por
baixo, mas é um caso de uso diferente).

## O que a extension oferece

- `WhatsAppApi` — interface comum de mensageria (`sendTextMessage`, `sendMediaFile`,
  `sendMediaUrl`, `sendAudioFile`, `sendAudioUrl`), injetável via CDI.
- O provider ativo é escolhido pela config `seedgrid.whatsapp.api` (`evolution` | `whatsapp-cloud`
  | `default`). Se nenhum provider válido estiver configurado, um `DefaultWhatsAppApi` interno
  apenas loga a chamada — **não falha o boot** mesmo sem credenciais.
- Este exemplo usa o provider **Evolution API** (self-hosted, `seedgrid-whatsapp-evolution`), que
  normaliza automaticamente números brasileiros para `+55DDNNNNNNNNN` antes de enviar.
- A extension **não persiste nada** — histórico de envios é responsabilidade do app host, que é
  exatamente o que este exemplo demonstra.

## Domínio do exemplo

- `POST /api/notifications` `{"phoneNumber": "11999999999", "message": "Ola!"}` — chama
  `WhatsAppApi.sendTextMessage(channelId, ...)` (o `channelId` é o nome da instância Evolution,
  configurado em `app.notifications.whatsapp-channel-id`) e grava uma `Notification` local com o
  resultado:
  - sucesso → `201`, status `SENT`, `providerMessageId` preenchido.
  - falha do provider (`WhatsAppException`, ex: instância offline, número inválido) → `502`,
    status `FAILED`, `errorMessage` preenchido — a notificação é registrada mesmo assim, só que
    marcada como falha (não é perdida silenciosamente).
- `GET /api/notifications` — lista o histórico completo (sucesso e falha).

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env
mvn test              # sobe Postgres via Testcontainers automaticamente
```

Por padrão o `dev.env` aponta `seedgrid.whatsapp.rest-client.evolution.url` para
`http://localhost:8095` (nenhum servidor real ali) — rodando em modo dev sem uma Evolution API de
verdade no ar, os envios vão falhar com erro de conexão e ficar `FAILED` no histórico (o app
continua funcionando normalmente, só o envio externo que não acontece). Para enviar mensagens de
verdade, aponte `SEEDGRID_WHATSAPP_REST_CLIENT_EVOLUTION_URL` e
`SEEDGRID_WHATSAPP_EVOLUTION_API_KEY` para uma instância real da Evolution API.

## Testando via curl

```bash
# Envia uma notificacao (sucesso ou falha depende de haver uma Evolution API real configurada)
curl -i -X POST http://localhost:8091/api/notifications \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"11999999999","message":"Ola! Seu pedido foi confirmado."}'

# Lista o historico
curl http://localhost:8091/api/notifications
```

Swagger UI: http://localhost:8091/q/swagger-ui.

## Testes

A extension é um REST client para um serviço externo (Evolution API / WhatsApp Cloud) — não dá
para bater num servidor real num teste automatizado. Em vez de WireMock, os testes substituem o
bean `WhatsAppApi` inteiro por `StubWhatsAppApi`, um CDI `@Alternative @Priority(1)` colocado em
`src/test/java` (padrão CDI puro — um alternative com `@Priority` direto na classe é
automaticamente habilitado globalmente, sem precisar de `beans.xml`). Isso evita adicionar
Mockito/`quarkus-junit5-mockito` só para isso, e exercita o `NotificationService`/
`NotificationResource` reais de ponta a ponta.

- `NotificationResourceTest` — fluxo HTTP completo: envio com sucesso (verifica o `WhatsAppApi`
  foi chamado com os parâmetros certos), envio com falha simulada do provider, validação de
  campo obrigatório (400 antes mesmo de chamar o WhatsAppApi) e listagem do histórico.
- `NotificationServiceSchemaTest` — prova que a migration `V1000` (tabela `notifications`) roda e
  que o mapeamento JPA (incluindo os campos herdados de `BaseEntity`: `publicId`, `active`,
  `createdAt`) funciona fim-a-fim contra um Postgres real, chamando o `NotificationService`
  diretamente (sem HTTP).

## Pegadinhas encontradas montando este exemplo

- A extension `seedgrid-whatsapp-core` **não depende de `seedgrid-security`** (só de
  `seedgrid-commons` + `quarkus-arc` + `smallrye-config`) — diferente do `seedgrid-apikeys`, este
  exemplo não precisa de `keys/*.pem`, `seedgrid.security.encryption.key`, bootstrap de admin,
  nem login. É o exemplo mais simples de montar do programa até agora.
- Nenhum módulo da extension traz `quarkus-flyway` transitivamente — precisou declarar
  `quarkus-flyway` explicitamente no pom (nos outros exemplos isso vinha de tabela via
  `seedgrid-security`/`seedgrid-apikeys`).
- `WhatsAppApi` é produzido via um `@Produces` method (`WhatsAppApiProducer`), não é uma classe
  concreta `@ApplicationScoped` direta — funciona normalmente com CDI `@Alternative` (o
  alternative globalmente habilitado vence o bean produzido, como esperado pela spec CDI), mas
  vale registrar que **`@InjectMock` (Mockito) não foi usado** porque `quarkus-junit5-mockito` só
  estava cacheado localmente na versão `3.30.6`, enquanto o `quarkus-bom` desta versão
  (`3.34.6`) fixa `quarkus-junit5-mockito` em `3.34.6` — isso quebraria o build `mvn -o`
  (offline). O padrão `@Alternative` evita essa dependência inteira.
