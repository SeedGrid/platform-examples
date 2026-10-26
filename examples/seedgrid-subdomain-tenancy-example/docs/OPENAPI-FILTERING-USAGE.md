# OpenAPI Filtering - Guia de Uso

Este projeto implementa filtragem automática de endpoints no Swagger UI baseado no contexto de acesso (master ou tenant).

## 🎯 Como Funciona

Quando você acessa o Swagger UI, os endpoints são filtrados automaticamente:

### Acesso Master (`http://kb.seedgrid.com.br:8080/q/swagger-ui`)

**Exibe:**
- ✅ Endpoints públicos (sem anotação)
- ✅ Endpoints `@SgMasterOnly`

**Oculta:**
- ❌ Endpoints `@SgTenantOnly`

### Acesso Tenant (`http://lckconsultoria.kb.seedgrid.com.br:8080/q/swagger-ui`)

**Exibe:**
- ✅ Endpoints públicos (sem anotação)
- ✅ Endpoints `@SgTenantOnly`

**Oculta:**
- ❌ Endpoints `@SgMasterOnly`

## 📝 Exemplos Implementados

### `@SgTenantOnly` - PersonResource

```java
@Path("/api/persons")
@SgTenantOnly
public class PersonResource {
    // Endpoints de gestão de pessoas
    // Só aparecem no Swagger do tenant
}
```

**Teste:**
- ✅ Visível em: `http://lckconsultoria.kb.seedgrid.com.br:8080/q/swagger-ui`
- ❌ Invisível em: `http://kb.seedgrid.com.br:8080/q/swagger-ui`

### `@SgMasterOnly` - PaymentPlanResource

```java
@Path("/api/payment-plans")
@SgMasterOnly
public class PaymentPlanResource {
    // Endpoints de gestão de planos de pagamento
    // Só aparecem no Swagger master
}
```

**Teste:**
- ✅ Visível em: `http://kb.seedgrid.com.br:8080/q/swagger-ui`
- ❌ Invisível em: `http://lckconsultoria.kb.seedgrid.com.br:8080/q/swagger-ui`

### Sem anotação - Endpoints Públicos

```java
@Path("/public/auth")
public class AuthResource {
    // Endpoints públicos de autenticação
    // Aparecem em ambos os Swaggers
}
```

**Teste:**
- ✅ Visível em: `http://kb.seedgrid.com.br:8080/q/swagger-ui`
- ✅ Visível em: `http://lckconsultoria.kb.seedgrid.com.br:8080/q/swagger-ui`

## 🔧 Configuração

A filtragem está configurada em `application.properties`:

```properties
# OpenAPI Filter para separar endpoints master/tenant
mp.openapi.filter=br.com.seedgrid.subdomaintenancyexample.config.TenantAwareOpenApiFilter
```

## 🧪 Como Testar

### 1. Inicie a aplicação

```bash
cd seedgrid-test-quarkus
mvn quarkus:dev
```

### 2. Acesse o Swagger Master

```
http://kb.seedgrid.com.br:8080/q/swagger-ui
```

**Você deve ver:**
- Payment Plans (MASTER) - `@SgMasterOnly`
- Public endpoints (auth, tenants)

**Você NÃO deve ver:**
- Persons (TENANT) - `@SgTenantOnly`

### 3. Acesse o Swagger de um Tenant

Primeiro, registre um tenant e ative-o:

```bash
# 1. Registrar tenant
curl -X POST http://kb.seedgrid.com.br:8080/public/tenants \
  -H 'Content-Type: application/json' \
  -d '{
    "subdomain": "teste",
    "cnpj": "12.345.678/0001-90",
    "corporateName": "Teste LTDA",
    ...
  }'

# 2. Ativar o tenant (pegar o código do email ou do banco)
curl http://kb.seedgrid.com.br:8080/public/tenants/validate-email/{CODE}
```

Depois acesse:

```
http://teste.kb.seedgrid.com.br:8080/q/swagger-ui
```

**Você deve ver:**
- Persons (TENANT) - `@SgTenantOnly`
- Public endpoints (auth, tenants)

**Você NÃO deve ver:**
- Payment Plans (MASTER) - `@SgMasterOnly`

## 📊 Matriz de Visibilidade

| Endpoint | Anotação | Swagger Master | Swagger Tenant |
|----------|----------|----------------|----------------|
| `/api/persons` | `@SgTenantOnly` | ❌ | ✅ |
| `/api/payment-plans` | `@SgMasterOnly` | ✅ | ❌ |
| `/public/auth/login` | (nenhuma) | ✅ | ✅ |
| `/public/tenants` | (nenhuma) | ✅ | ✅ |

## ⚠️ Importante

### Isso NÃO é segurança!

As anotações são **apenas para documentação**. A segurança real é garantida por:

1. **TenantConnectionResolverImpl** - Valida se o tenant existe e está ativo
2. **JWT com claim subdomain** - Tokens incluem o subdomain
3. **JwtAuthenticationFilter** - Valida tokens em cada requisição

### Exemplo de endpoint protegido

Se você quiser segurança adicional além da filtragem do Swagger:

```java
@Path("/admin/something")
@SgMasterOnly  // Apenas documentação
public class AdminResource {

    @Inject
    TenantContext context;

    @GET
    public Response doSomething() {
        // Validação real de segurança
        if (!context.isMaster()) {
            throw new ForbiddenException("Acesso apenas no master");
        }
        // ...
    }
}
```

## 🔗 Documentação Completa

Para mais detalhes sobre como usar as anotações `@SgTenantOnly` e `@SgMasterOnly`, consulte:

- [OPENAPI-FILTERING.md](../../seedgrid-subdomain-tenancy/docs/OPENAPI-FILTERING.md) - Documentação completa na biblioteca
- [TenantAwareOpenApiFilter.java](../src/main/java/br/com/seedgrid/subdomaintenancyexample/config/TenantAwareOpenApiFilter.java) - Implementação do filtro
- [SgTenantOnly.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/annotations/SgTenantOnly.java) - Anotação para tenants
- [SgMasterOnly.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/annotations/SgMasterOnly.java) - Anotação para master

## 🎓 Resumo

1. Use `@SgTenantOnly` em endpoints que operam em dados do tenant
2. Use `@SgMasterOnly` em endpoints administrativos globais
3. Não use anotação em endpoints públicos
4. Teste acessando ambos os Swaggers (master e tenant)
5. Lembre-se: isso é apenas documentação, implemente segurança real nos endpoints sensíveis
