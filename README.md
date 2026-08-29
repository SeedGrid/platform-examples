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
| OpenSSL | qualquer | `openssl version` (no Windows, use o Git Bash) |

## Os três passos comuns a todos os exemplos

**1. Suba a infraestrutura** (uma vez só, serve para todos):

```bash
podman compose -f infra/compose.yml up -d
# ou: docker compose -f infra/compose.yml up -d
```

Isso sobe Postgres em `5439`, MongoDB em `27019` e pgAdmin em `5051`. As portas são
deslocadas de propósito para não conflitar com um banco que você já tenha rodando.

**2. Gere as chaves JWT do exemplo que quer rodar:**

```bash
./scripts/gerar-chaves-jwt.sh seedgrid-security-example
# Windows PowerShell: .\scripts\gerar-chaves-jwt.ps1 seedgrid-security-example
```

As chaves **não são versionadas** aqui de propósito — chave privada em repositório
público é um convite a copiá-la para produção.

**3. Siga o README do exemplo.** Cada um diz exatamente o que falta: quase sempre
criar um banco e rodar `mvn quarkus:dev`.

> Um detalhe que vale saber antes: os exemplos **não criam o banco sozinhos**. O
> Flyway roda as migrations dentro de um banco que já existe, então criá-lo é sempre
> um passo explícito no README. Nos exemplos multi-tenant isso importa mais, porque
> o banco *master* precisa existir antes do primeiro boot.

---

## As extensões

### Fundação

| extensão | o que resolve | exemplo |
|---|---|---|
| **seedgrid-security** | Usuários, empresas, JWT, RBAC com permissões por papel, 2FA (TOTP e e-mail), política de senha, auto-cadastro com aprovação | [`seedgrid-security-example`](examples/seedgrid-security-example) ✅ |
| **seedgrid-privacy** | Criptografia de dados pessoais em repouso e mascaramento em logs e auditoria | [`seedgrid-privacy-example`](examples/seedgrid-privacy-example) |
| **seedgrid-audit** | Trilha de auditoria por anotação `@Audit`, com backend PostgreSQL ou MongoDB | [`seedgrid-audit-example`](examples/seedgrid-audit-example) ✅ |
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
| **seedgrid-filemanagement** | Upload e download com backend intercambiável (filesystem, S3, MinIO) | [`seedgrid-filemanagement-example`](examples/seedgrid-filemanagement-example) |
| **seedgrid-cache** | Cache com invalidação por evento e escopo por tenant | [`seedgrid-cache-example`](examples/seedgrid-cache-example) |
| **seedgrid-resilience** | Circuit breaker, retry e timeout com defaults sensatos | [`seedgrid-resilience-example`](examples/seedgrid-resilience-example) |
| **seedgrid-whatsapp** | Envio por WhatsApp, agnóstico de provedor (Evolution API ou Cloud API) | [`seedgrid-whatsapp-example`](examples/seedgrid-whatsapp-example) |

✅ = README verificado ponta a ponta: alguém seguiu os passos numa máquina limpa e a
aplicação subiu. Os demais têm README, mas ainda não passaram por essa verificação —
se esbarrar em algo, abra uma issue.

---

## Sobre as credenciais deste repositório

Este repositório é público e **não contém segredo nenhum**. O que você encontra:

- senhas de containers locais (`postgres`, `admin`) — valem só na sua máquina;
- uma chave de criptografia de demonstração que decodifica para
  `exemplo-nao-use-em-producao-1234`, escolhida para ser óbvia;
- placeholders `COLOQUE_SUA_..._AQUI` onde seria preciso uma credencial real
  (SMTP, WhatsApp, MinIO). Os exemplos sobem sem elas; só a funcionalidade
  correspondente fica inerte.

As chaves JWT você gera localmente no passo 2, e o `.gitignore` impede que sejam
commitadas.

## Licença

Ver [LICENSE](LICENSE).
