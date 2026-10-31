# SeedGrid Platform — Exemplos

Aplicações Quarkus mínimas, uma por extensão da plataforma SeedGrid. Cada uma existe
para você **rodar em poucos minutos e avaliar a extensão** antes de decidir usá-la —
não para servir de template de produção.

As extensões são publicadas no Maven Central sob o groupId `br.com.seedgrid`.

---

## Antes de começar

| requisito | versão | como conferir |
|---|---|---|
| JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Podman ou Docker | qualquer recente | `podman --version` |

É só isso — nada de OpenSSL nem de criar banco na mão.

## Os três passos comuns a todos os exemplos

**1. Suba a infraestrutura** (uma vez só, serve para todos):

```bash
podman compose -f infra/compose.yml up -d
# ou: docker compose -f infra/compose.yml up -d
```

Isso sobe:

| serviço | porta | para que |
|---|---|---|
| PostgreSQL | `5439` | todos os exemplos |
| MongoDB | `27019` | trilha de auditoria com estratégia `MONGODB` |
| pgAdmin | `5051` | inspecionar o banco pelo navegador |
| MinIO | `9010` (API) / `9011` (console) | storage S3, com os buckets já criados |
| Mailpit | `8025` | caixa de entrada local: todo e-mail enviado pelos exemplos cai aqui |

As portas são deslocadas de propósito para não conflitar com serviços que você já
tenha rodando.

O Postgres já sobe com o banco de todos os exemplos criado, e o Mailpit captura os
e-mails — é ele que te dá o link de confirmação do primeiro login sem precisar de um
SMTP de verdade. Um container `seedgrid-examples-minio-init` cria os buckets e
encerra; vê-lo como `Exited (0)` é o esperado.

**2. Rode o exemplo que quiser:**

```bash
cd examples/seedgrid-security-example
source dev.env && mvn quarkus:dev
# Windows PowerShell: .un-dev.ps1
```

É isso. O banco já foi criado pelo compose, e as chaves JWT são geradas no build
(Java puro, sem OpenSSL). As chaves **não são versionadas** de propósito — chave
privada em repositório público é um convite a copiá-la para produção.

**3. Entre.** Cada exemplo com autenticação cria um admin no primeiro boot:

| | |
|---|---|
| usuário | `admin@exemplo.com.br` |
| senha | `Admin@123` |

No primeiro login o e-mail precisa ser confirmado. O link chega na caixa de entrada
local, em **<http://localhost:8025>** — nada sai para a internet. Abra, clique no
link do e-mail "Bem-vindo! Confirme seu e-mail" e pronto.

---

## As extensões

### Fundação

| extensão | o que resolve | exemplo |
|---|---|---|
| **seedgrid-security** | Usuários, empresas, JWT, RBAC com permissões por papel, 2FA (TOTP e e-mail), política de senha, auto-cadastro com aprovação | [`seedgrid-security-example`](examples/seedgrid-security-example) ✅ |
| **seedgrid-privacy** | Criptografia de dados pessoais em repouso e mascaramento em logs e auditoria | [`seedgrid-privacy-example`](examples/seedgrid-privacy-example) |
| **seedgrid-audit** | Trilha de auditoria por anotação `@SgAudit`, com backend PostgreSQL ou MongoDB | [`seedgrid-audit-example`](examples/seedgrid-audit-example) ✅ |
| **seedgrid-apikeys** | Chaves de API com prefixo, hash e escopo, como alternativa ao JWT | [`seedgrid-apikeys-example`](examples/seedgrid-apikeys-example) |

### Multi-tenant — escolha **um** modelo

As duas extensões publicam o **mesmo contrato**; o que muda é onde os dados moram.

| extensão | modelo | quando faz sentido | exemplo |
|---|---|---|---|
| **seedgrid-login-tenancy** | Banco único, separação lógica por empresa | A maioria dos SaaS. Mais simples de operar, migrations em um lugar só | [`seedgrid-login-tenancy-example`](examples/seedgrid-login-tenancy-example) ✅ |
| **seedgrid-subdomain-tenancy** | Um banco por tenant, resolvido pelo subdomínio | Isolamento forte por cliente, exigência regulatória, backup e restore por cliente | [`seedgrid-subdomain-tenancy-example`](examples/seedgrid-subdomain-tenancy-example) ✅ |

Cada uma publica um **BOM**: o host declara *uma* versão — a da tenancy — importa o
BOM e lista as demais libs sem `<version>`. Isso evita o problema clássico de subir a
versão da tenancy e esquecer a do security, que só aparece muito depois e longe da causa.

### Operação

| extensão | o que resolve | exemplo |
|---|---|---|
| **seedgrid-billing** | Planos, assinaturas, período de teste e webhooks; implementação Stripe ou fake para desenvolvimento | [`seedgrid-billing-example`](examples/seedgrid-billing-example) |
| **seedgrid-backup** | Exportação agendada com retenção e notificação; destino em filesystem, S3, MinIO ou nuvem | [`seedgrid-backup-example`](examples/seedgrid-backup-example) |
| **seedgrid-notification** | O host publica eventos de negócio; a extension decide destinatários e canais (e-mail, WhatsApp), enfileira, tenta de novo e registra cada entrega | [`seedgrid-notification-example`](examples/seedgrid-notification-example) |
| **seedgrid-security-social** | Login social (Google, Facebook, Apple) sobre a security; sobe inerte, com a lista de provedores vazia | [`seedgrid-security-social-example`](examples/seedgrid-security-social-example) |
| **seedgrid-filemanagement** | Upload e download com backend intercambiável (filesystem, S3, MinIO, Drive, OneDrive, Dropbox), tabela de anexos opcional e armazenamento no espaço do próprio usuário (consentimento; usa o PostgreSQL deste repositório) | [`seedgrid-filemanagement-example`](examples/seedgrid-filemanagement-example) |
| **seedgrid-cache** | Cache com invalidação por evento e escopo por tenant | [`seedgrid-cache-example`](examples/seedgrid-cache-example) |
| **seedgrid-business-days** | Feriados, perfil da semana e cálculo de dias úteis por empresa | [`seedgrid-business-days-example`](examples/seedgrid-business-days-example) |
| **seedgrid-ai-pricing** | Robô que lê preços de modelos de IA, propõe a mudança e só vale com aprovação humana | [`seedgrid-ai-pricing-example`](examples/seedgrid-ai-pricing-example) |
| **seedgrid-resilience** | Circuit breaker, retry e timeout com defaults sensatos | [`seedgrid-resilience-example`](examples/seedgrid-resilience-example) |
| **seedgrid-whatsapp** | Envio por WhatsApp, agnóstico de provedor (Evolution API ou Cloud API) | [`seedgrid-whatsapp-example`](examples/seedgrid-whatsapp-example) |
| **seedgrid-realtime** | Avisos em tempo real ao usuário logado pelo SeedGrid Pulse: token de conexão da sessão e publicação a partir do evento neutro `SgUserEvent` (o exemplo traz um Pulse falso) | [`seedgrid-realtime-example`](examples/seedgrid-realtime-example) |

### Dados de referência

| extensão | o que resolve | exemplo |
|---|---|---|
| **seedgrid-exchange** | Cotação de moedas buscada por robô numa fonte configurável, com histórico, retentativa e série | [`seedgrid-exchange-example`](examples/seedgrid-exchange-example) |
| **seedgrid-cities** | Países, estados e cidades do Brasil (5 / 27 / 5.570) semeados por migration, com métodos auxiliares (`BrazilGeo`) e REST opcional | [`seedgrid-cities-example`](examples/seedgrid-cities-example) |

✅ = verificado: os passos acima foram executados a partir de banco vazio e a
aplicação subiu. Os quatro marcados passaram por isso; os demais trazem o README que
já tinham e entram na próxima rodada. Se esbarrar em algo antes disso, abra uma issue.

---

## Sobre as credenciais deste repositório

Este repositório é público e **não contém segredo nenhum**. O que você encontra:

- senhas de containers locais (`postgres`, `admin`) — valem só na sua máquina;
- uma chave de criptografia de demonstração que decodifica para
  `exemplo-nao-use-em-producao-1234`, escolhida para ser óbvia;
- as credenciais do MinIO local (`minioadmin`/`minioadmin`), que só existem
  dentro do seu compose;
- placeholders `COLOQUE_SUA_..._AQUI` onde seria preciso uma credencial real
  (SMTP, WhatsApp). Os exemplos sobem sem elas; só a funcionalidade
  correspondente fica inerte.

As chaves JWT você gera localmente no passo 2, e o `.gitignore` impede que sejam
commitadas.

## Licença

Ver [LICENSE](LICENSE).
