# seedgrid-login-tenancy-example

App de exemplo, rodável, do modelo **login-tenancy**: isolamento **lógico** de dados por
`company_id`, num único banco/schema — diferente do `seedgrid-subdomain-tenancy-example`, que isola
fisicamente por schema/banco (master vs tenant). As duas extensions são **mutuamente exclusivas**:
não misture os dois modelos numa mesma aplicação.

Testa, contra Postgres real (Testcontainers): `seedgrid-login-tenancy` + `seedgrid-security` +
`seedgrid-audit` + `seedgrid-backup` + `seedgrid-billing-core` + `seedgrid-apikeys` (todos vêm
transitivamente ao declarar só a dependência `seedgrid-login-tenancy`).

## Arquitetura: uma base, várias companies

Ao contrário do subdomain-tenancy (2 Persistence Units, banco separado por tenant), aqui existe
**uma única Persistence Unit** e **um único datasource**. Cada linha das tabelas de domínio carrega
um `company_id`, e o isolamento é **de aplicação**: todo acesso passa pelo `CompanyRequestContext`
(bean `@RequestScoped` do `seedgrid-security`), preenchido a partir do claim `companyId` do JWT.

```
Cliente ──JWT(companyId=42)──▶ CompanyContextFilter ──▶ CompanyRequestContext.currentCompanyId=42
                                                              │
                                                              ▼
                                          ProductService.listAll() → WHERE company_id = 42
```

## Fluxo completo (signup → login → domínio)

1. **`POST /public/signup`** — cadastro institucional rico (empresa + representante legal +
   e-mail/senha do root). Cria uma `signup_request` PENDENTE e dispara e-mail com o token de
   confirmação.
2. **`POST /public/signup/confirm-email`** `{ "token": "..." }` — confirma o e-mail: cria a
   `Company` (ou vincula, se o e-mail já existir) e o `BaseUser` **ROOT DA CONTA** (membership
   aditivo: `user_companies.is_root=true`, não root global).
3. **`POST /public/auth/login`** — autentica. Com **exatamente 1** company vinculada, o token já
   nasce **escopado** (`claim companyId`). Com mais de uma, o login retorna `companyId=null` e o
   cliente escolhe via `POST /me/select-company` (mesmo endpoint do `seedgrid-security`).
4. **`/api/products`** (exemplo de domínio deste projeto) — CRUD simples, sempre filtrado pela
   company do token corrente.

## Exemplo de domínio: `Product`

`Product extends BaseEntity` (id, `public_id`, `active`, `created_at`/`updated_at`, auditoria de
usuário) + `company_id`, `name`, `sku`, `price`. `ProductRepository` só expõe consultas
`findByCompany`/`findByIdAndCompany`; `ProductService` sempre resolve a company atual via
`CompanyRequestContext` antes de qualquer leitura/escrita — não há como um endpoint "esquecer" de
filtrar, porque a company nunca vem do request, só do token.

## Como rodar

```bash
# 1. Suba um Postgres local (ex.: docker/podman) na porta 5432, ou ajuste SEEDGRID_DB_URL no dev.env
# 2. Rode em modo dev (recarrega dev.env automaticamente)
./run-dev.sh
```

Testes (sobem Postgres via Testcontainers automaticamente):

```bash
mvn test
```

## Testando o fluxo via curl

```bash
# 1. Signup
curl -X POST http://localhost:8086/public/signup -H 'Content-Type: application/json' -d '{
  "cnpj": "11222333000181",
  "corporateName": "Empresa Exemplo LTDA",
  "tradeName": "Empresa Exemplo",
  "address": {"street":"Rua A","number":"100","postalCode":"01000-000","city":"Sao Paulo","state":"SP","district":"Centro","phone":"11999999999"},
  "legalRepresentative": {"firstName":"Joao","lastName":"Silva","cpf":"52998224725","birthDate":"1990-01-01","phone":"11988888888"},
  "rootEmail":"joao@exemplo.com",
  "plainRootPassword":"Sup3r#Secret!"
}'

# 2. Pegue o token de confirmacao no log/e-mail (mock em dev) e confirme
curl -X POST http://localhost:8086/public/signup/confirm-email -H 'Content-Type: application/json' -d '{"token":"<TOKEN>"}'

# 3. Login (token ja vem escopado: 1 unica company)
curl -X POST http://localhost:8086/public/auth/login -H 'Content-Type: application/json' -d '{"login":"joao@exemplo.com","password":"Sup3r#Secret!"}'

# 4. Criar e listar produtos (troque <TOKEN> pelo token retornado no login)
curl -X POST http://localhost:8086/api/products -H "Authorization: Bearer <TOKEN>" -H 'Content-Type: application/json' -d '{"name":"Produto 1","sku":"SKU-1","price":10.00}'
curl http://localhost:8086/api/products -H "Authorization: Bearer <TOKEN>"
```

Swagger UI: http://localhost:8086/q/swagger-ui.

## Billing

O `seedgrid-billing-core` é *provider-agnostic*: exige um bean `BillingProvider` via CDI. Este
exemplo fornece um `NoopBillingProvider` (`billing/NoopBillingProvider.java`) só para satisfazer o
boot — troque por `seedgrid-billing-stripe` (ou outro provider) num projeto real.

## Testes

- `LoginTenancySchemaBootstrapTest` — prova que o `LoginTenancyMigrationService` roda, na ordem
  certa, as migrations de todas as extensions do baseline (security, audit, backup, billing,
  apikeys, login-tenancy) e da app host (`products`), no mesmo banco/schema.
- `ProductCompanyIsolationTest` — semeia 2 signups, confirma, loga como cada root e prova, via HTTP
  real, que cada company só vê e só acessa os seus próprios `/api/products` (o cerne do modelo
  login-tenancy).
