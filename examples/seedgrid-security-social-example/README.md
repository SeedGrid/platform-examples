# seedgrid-security-social-example

App de exemplo, rodável, do `seedgrid-security-social`: login social (Google, Facebook, Apple) sobre a
`seedgrid-security`. Ao contrário dos outros exemplos de extension, **este usa a `seedgrid-security`** — o login social é
um módulo opcional *dela* (a política de vínculo, e-mail verificado, termos e 2FA é da security; o módulo só faz OIDC).

O exemplo sobe com a **lista de provedores vazia**: o módulo fica **inerte** (nenhum provedor, nenhum tenant OIDC) e
não há credencial real em lugar nenhum.

## O que o exemplo mostra (sem provedor ligado)

1. `GET /public/auth/social/providers` — `[]`: nenhum provedor.
2. `GET /public/auth/social/google/login?returnTo=...` — `404` com `code: SOCIAL_PROVIDER_DISABLED`.
3. `POST /public/auth/login` — o login por senha segue valendo (admin do bootstrap).

## Como ligar um provedor (exemplo: Google)

Crie um cliente OAuth no console do Google e cadastre, como URI de redirecionamento, o callback da sua API. Depois:

```properties
seedgrid.security.social.providers=google
seedgrid.security.social.allowed-return-hosts=app.exemplo.com     # padrão: host de seedgrid.security.frontend.base-url
seedgrid.security.social.google.client-id=<do console do Google>
seedgrid.security.social.google.client-secret=<do console do Google>
seedgrid.security.social.google.state-secret=<>=32 caracteres, igual em todas as instâncias>
seedgrid.security.social.google.encryption-secret=<>=32 caracteres, igual em todas as instâncias>
seedgrid.security.social.google.redirect-uri=https://api.exemplo.com/public/auth/social/google/callback
```

No exemplo, `SOCIAL_PROVIDERS=google` (variável de ambiente) liga a lista; as demais chaves também vêm do ambiente
(`SEEDGRID_SECURITY_SOCIAL_GOOGLE_CLIENT_ID` etc.). **Nunca versione credenciais**: use um `dev.local.env` (ignorado pelo
git) ou o cofre do seu ambiente. O boot **falha com mensagem clara** se um provedor listado estiver sem credencial ou
com segredo curto — por isso o exemplo vem com a lista vazia. O fluxo HTTP completo (login, callback, troca do ticket) e
os cuidados de segurança estão no README da extension.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (Postgres em localhost:5439, porta 8097)
mvn verify            # sobe Postgres via Testcontainers automaticamente
```

Swagger em `http://localhost:8097/q/swagger-ui`. Login do `dev.env`: `admin@example.com` / `Admin@123`.

## Testes

`SocialInertTest` — nenhum provedor listado; o login de qualquer provedor é `404 SOCIAL_PROVIDER_DISABLED`; o login por
senha segue funcionando. As migrations da security rodam sozinhas, no histórico próprio do Flyway.
