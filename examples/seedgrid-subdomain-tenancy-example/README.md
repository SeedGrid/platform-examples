# seedgrid-subdomain-tenancy — exemplo

Multi-tenant com **um banco de dados por tenant**, resolvido pelo subdomínio da
requisição: `cliente1.crm.exemplo.com.br` fala com o banco do `cliente1`.

Um segundo banco, o **master**, guarda o que é comum: cadastro dos tenants, planos,
usuários da plataforma. É nele que o Flyway roda as migrations no boot.

> Se o que você quer é multi-tenant com **banco único** e separação lógica por empresa
> — o caso da maioria dos SaaS —, veja o
> [`seedgrid-login-tenancy-example`](../seedgrid-login-tenancy-example). As duas
> extensões publicam o mesmo contrato; muda onde os dados moram.

---

## Rodando

Estes passos foram executados do zero — banco recém-criado e vazio — e a aplicação
subiu em 18 segundos.

### 1. Infraestrutura

Da raiz do repositório:

```bash
podman compose -f infra/compose.yml up -d
```

Sobe Postgres em `5439`, MongoDB em `27019` (trilha de auditoria), pgAdmin em `5051`
e MinIO em `9010`. Este exemplo não precisa do MinIO — o avatar vai para disco — mas
ele fica disponível caso você queira trocar o backend; veja o bloco comentado em
`dev.env`.

### 2. Chaves JWT

```bash
./scripts/gerar-chaves-jwt.sh seedgrid-subdomain-tenancy-example
```

### 3. Crie o banco master — **este passo é obrigatório**

A aplicação **não cria o banco**; ela roda as migrations dentro de um banco que já
existe. Sem este passo o boot falha ao conectar.

```bash
podman exec -i seedgrid-examples-postgres \
  psql -U postgres -c "CREATE DATABASE crm_master OWNER postgres ENCODING 'UTF8';"
```

<details>
<summary>Preferindo o pgAdmin</summary>

Abra <http://localhost:5051> (login `admin@exemplo.com.br` / senha `admin`), conecte
em um servidor novo com host `postgres`, porta `5432`, usuário `postgres`, senha
`postgres` — host e porta são os da rede interna do compose, não os do seu micro — e
crie um banco chamado `crm_master`.
</details>

O nome vem de `PRODUCT_PREFIX=crm` no `dev.env`: o master é sempre
`<PRODUCT_PREFIX>_master`. Se mudar o prefixo, mude o nome do banco junto.

### 4. Suba

```bash
cd examples/seedgrid-subdomain-tenancy-example
set -a && source dev.env && set +a && mvn quarkus:dev
```

No Windows PowerShell, use `.\run-dev.ps1` (o script carrega o `dev.env` e chama o
Maven).

No primeiro boot o Flyway cria o schema do zero: 56 migrations em 6 grupos, um por
módulo (security, backup, billing, apikeys, tenancy…), resultando em 31 tabelas.

```
Successfully applied 32 migrations to schema "public", now at version v231
Successfully applied  3 migrations to schema "public", now at version v402
Successfully applied  4 migrations to schema "public", now at version v503
...
seedgrid-test-quarkus started in 18.191s. Listening on: http://localhost:8085
```

Nos boots seguintes o Flyway não tem o que aplicar e a subida cai para ~10s.

### 5. Confira

| endereço | o que é |
|---|---|
| <http://localhost:8085/swagger-ui/> | Swagger UI — a porta de entrada |
| <http://localhost:8085/q/openapi> | contrato OpenAPI em YAML |
| <http://localhost:5051> | pgAdmin, para ver as 31 tabelas criadas no `crm_master` |

`GET /api/payment-plans` responde **401** — e está certo: rota pública aqui significa
"dispensa a resolução de tenant", não "dispensa autenticação". Para exercitar os
endpoints, autentique-se pelo Swagger.

---

## O que observar para avaliar a extensão

**A resolução de tenant pelo host.** É o coração da extensão. Suba o app, chame
qualquer endpoint e acompanhe o log — a cada requisição você vê qual tenant foi
resolvido a partir do `Host`:

```
🔍 Host header: 'localhost'
⚠️ No tenant resolved, using default tenant: 'master'
```

Como `localhost` não termina no `DOMAIN_SUFFIX` configurado, a requisição cai no
tenant `master`. Para ver a resolução acontecendo de verdade, aponte um subdomínio
para `127.0.0.1` no seu arquivo de hosts:

```
127.0.0.1  cliente1.crm.exemplo.com.br
```

e chame `http://cliente1.crm.exemplo.com.br:8085/...`. O log passa a mostrar
`✅ Resolved tenant 'cliente1'`, e a partir dali toda consulta vai para o banco
daquele cliente.

**O provisionamento.** Criar um tenant cria um banco novo e roda as migrations dele.
Veja `create-tenant.ps1` e as migrations em `src/main/resources/db/migration/tenant/`.

**A separação.** Um token emitido no `cliente1` não funciona no `cliente2`: os usuários
vivem na tabela do banco de cada tenant, então o `publicId` do token simplesmente não
existe no outro banco e a requisição termina em 401.

---

## Coisas que vão aparecer no log e são esperadas

**Erro de envio de e-mail.** O `dev.env` vem com placeholder no SMTP, então o envio de
boas-vindas falha:

```
ERROR [...AdminUserBootstrapService] Failed to send welcome email for admin user.
  ... AUTH PLAIN failed: 535-5.7.8 Username and Password not accepted
```

Isso não impede nada de funcionar. Para testar e-mail de verdade, preencha
`SEEDGRID_SUBDOMAIN_TENANCY_MAILER_*` no `dev.env` (com Gmail, use uma senha de app,
não a senha da conta).

**`/q/health` responde 404.** Este exemplo não inclui `quarkus-smallrye-health`. Use
`/q/openapi` para verificar se subiu.

---

## Configuração

O `dev.env` traz tudo comentado. Na prática, só estes importam para rodar local:

| variável | default | para que serve |
|---|---|---|
| `PRODUCT_PREFIX` | `crm` | prefixo do produto; define o nome do banco master |
| `SEEDGRID_SUBDOMAIN_TENANCY_DOMAIN_SUFFIX` | `crm.exemplo.com.br` | sufixo a partir do qual o subdomínio vira tenant |
| `SEEDGRID_SUBDOMAIN_TENANCY_MASTER_DB_URL` | `...5439/crm_master` | banco master |
| `SEEDGRID_SUBDOMAIN_TENANCY_TENANT_DB_URL` | `...5439` | servidor onde os bancos de tenant são criados (sem nome de banco) |
| `HTTP_PORT` | `8085` | porta da aplicação |

Todo nome de variável aqui é o **nome canônico** que a extensão lê. Se precisar
descobrir outro, ele está no `microprofile-config.properties` publicado dentro do jar:

```bash
unzip -p ~/.m2/repository/br/com/seedgrid/seedgrid-subdomain-tenancy/2026.08.29/\
seedgrid-subdomain-tenancy-2026.08.29.jar META-INF/microprofile-config.properties | grep <chave>
```

### Uma nota sobre o `seedgrid-security`

Este exemplo define:

```properties
seedgrid.security.bootstrap.enabled=false
```

O bootstrap de startup do security cria a empresa default e o primeiro admin, e para
isso toca o banco durante o `StartupEvent`. Num modelo de banco por tenant não existe,
naquele momento, requisição de onde resolver qual tenant usar — então o bootstrap
precisa ficar desligado. Aqui empresa e admin nascem no provisionamento de cada tenant.

Exige `seedgrid-security` 2026.08.29 ou superior.

---

## Recomeçando do zero

```bash
podman compose -f infra/compose.yml down -v   # apaga os volumes
podman compose -f infra/compose.yml up -d
# repita os passos 3 e 4
```
