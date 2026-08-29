# Autenticação JWT em Ambiente Multitenancy

## 🔐 Como Funciona

### Claim `subdomain` no Token JWT

O token JWT inclui um claim customizado `subdomain` que identifica o tenant ao qual o usuário pertence:

```json
{
  "sub": "voce@exemplo.com.br",
  "uid": 1,
  "upn": "voce@exemplo.com.br",
  "iss": "https://seedgrid.com.br",
  "groups": ["ADMIN"],
  "subdomain": "lckconsultoria",  ← CLAIM CUSTOMIZADO
  "exp": 1766442406,
  "iat": 1766413606,
  "jti": "32355911-d231-4b59-93f5-75e0614e2a94"
}
```

### Geração do Claim

O claim `subdomain` é adicionado pelo `MultitenancyJwtTokenCustomizer`:

```java
@Override
public Map<String, Object> customizeClaims(User user, List<String> roles, String hostHeader) {
    Map<String, Object> customClaims = new HashMap<>();

    if (hostHeader != null && !hostHeader.isBlank()) {
        String subdomain = tenantHostExtractor.extractTenantFromHost(hostHeader);

        if (subdomain != null && !subdomain.isBlank()) {
            customClaims.put("subdomain", subdomain);
        }
    }

    return customClaims;
}
```

### Validação do Token

O `MultitenancyJwtTokenValidator` valida que o subdomain do token corresponde ao Host da requisição:

```java
@Override
public void validateCustomClaims(JWTClaimsSet claims, String hostHeader) {
    String tokenSubdomain = claims.getStringClaim("subdomain");
    String requestSubdomain = tenantHostExtractor.extractTenantFromHost(hostHeader);

    // Token sem subdomain não pode ser usado em tenant
    if (tokenSubdomain == null && requestSubdomain != null) {
        throw new TokenValidationException("Token sem subdomain não pode ser usado em tenant");
    }

    // Token de tenant não pode ser usado fora de tenant
    if (tokenSubdomain != null && requestSubdomain == null) {
        throw new TokenValidationException("Token de tenant não pode ser usado fora de tenant");
    }

    // Subdomains devem ser iguais
    if (tokenSubdomain != null && !Objects.equals(tokenSubdomain, requestSubdomain)) {
        throw new TokenValidationException(
            String.format("Token emitido para tenant '%s' não pode ser usado em tenant '%s'",
                tokenSubdomain, requestSubdomain));
    }
}
```

## ⚠️ Problema Comum: Token Inválido

### Cenário do Erro

```bash
# 1️⃣ Login no domínio master (SEM tenant)
POST http://kb.seedgrid.com.br:8080/public/auth/login
{
  "login": "voce@exemplo.com.br",
  "password": "senha"
}

# Token gerado:
{
  "subdomain": null  ← SEM SUBDOMAIN!
}

# 2️⃣ Tentativa de usar o token em um tenant
GET http://lckconsultoria.kb.seedgrid.com.br:8080/api/persons
Authorization: Bearer <token_sem_subdomain>

# ❌ Erro:
{
  "message": "Token inválido: Assinatura inválida"
}
```

### Por Que Acontece?

1. **Login no domínio master**: `kb.seedgrid.com.br`
   - `TenantHostExtractor.extractTenantFromHost("kb.seedgrid.com.br")` retorna `null`
   - Token gerado **SEM** claim `subdomain`

2. **Requisição no domínio do tenant**: `lckconsultoria.kb.seedgrid.com.br`
   - `TenantHostExtractor.extractTenantFromHost("lckconsultoria.kb.seedgrid.com.br")` retorna `"lckconsultoria"`
   - `MultitenancyJwtTokenValidator` compara:
     - Token subdomain: `null`
     - Request subdomain: `"lckconsultoria"`
   - ❌ **Rejeita**: "Token sem subdomain não pode ser usado em tenant"

## ✅ Solução

### Sempre Fazer Login no Domínio do Tenant

```bash
# ✅ CORRETO
POST http://lckconsultoria.kb.seedgrid.com.br:8080/public/auth/login
{
  "login": "voce@exemplo.com.br",
  "password": "senha"
}

# Token gerado:
{
  "subdomain": "lckconsultoria"  ← COM SUBDOMAIN!
}

# Agora pode usar:
GET http://lckconsultoria.kb.seedgrid.com.br:8080/api/persons
Authorization: Bearer <token_com_subdomain>
```

## 📋 Matriz de Compatibilidade

| Login Feito em | Token `subdomain` | Pode Usar em | Resultado |
|----------------|-------------------|--------------|-----------|
| `kb.seedgrid.com.br` | `null` | `kb.seedgrid.com.br` | ✅ Master endpoints |
| `kb.seedgrid.com.br` | `null` | `lckconsultoria.kb.seedgrid.com.br` | ❌ Token inválido |
| `lckconsultoria.kb.seedgrid.com.br` | `"lckconsultoria"` | `lckconsultoria.kb.seedgrid.com.br` | ✅ Funciona |
| `lckconsultoria.kb.seedgrid.com.br` | `"lckconsultoria"` | `acmecorp.kb.seedgrid.com.br` | ❌ Token inválido |
| `lckconsultoria.kb.seedgrid.com.br` | `"lckconsultoria"` | `kb.seedgrid.com.br` | ❌ Token inválido |

## 🎯 Regras de Validação

### Regra 1: Token sem subdomain → Só pode usar em master

```
Token: { subdomain: null }
✅ Válido em: kb.seedgrid.com.br
❌ Inválido em: {tenant}.kb.seedgrid.com.br
```

### Regra 2: Token com subdomain → Só pode usar no mesmo tenant

```
Token: { subdomain: "lckconsultoria" }
✅ Válido em: lckconsultoria.kb.seedgrid.com.br
❌ Inválido em: acmecorp.kb.seedgrid.com.br
❌ Inválido em: kb.seedgrid.com.br
```

## 🔍 Como Verificar o Subdomain do Token

### Via JWT.io

1. Copie o token
2. Cole em https://jwt.io
3. Verifique o payload:

```json
{
  "sub": "voce@exemplo.com.br",
  "subdomain": "lckconsultoria",  ← AQUI
  ...
}
```

### Via Log da Aplicação

O `MultitenancyJwtTokenCustomizer` loga quando adiciona o subdomain:

```
🏢 Adding subdomain 'lckconsultoria' to JWT token for user voce@exemplo.com.br
```

O `MultitenancyJwtTokenValidator` loga a validação:

```
🔍 Validating subdomain - Token: 'lckconsultoria', Request: 'lckconsultoria'
✅ Subdomain validation passed for tenant 'lckconsultoria'
```

## 🛡️ Benefícios de Segurança

### Isolamento de Tenants

✅ **Token não pode vazar entre tenants**
- Um usuário do tenant A não pode acessar dados do tenant B mesmo que tenha o token

### Validação no Nível HTTP

✅ **Validação antes de chegar no banco de dados**
- Token inválido é rejeitado antes de qualquer consulta

### Auditoria Clara

✅ **Logs identificam qual tenant está acessando**
- Facilita troubleshooting e auditoria de segurança

## 📝 Checklist para Desenvolvimento

Ao testar a API com Swagger ou Postman:

- [ ] Verificar qual domínio estou usando para login
- [ ] Garantir que o domínio de login tem o tenant (`{tenant}.kb.seedgrid.com.br`)
- [ ] Copiar o token gerado no login
- [ ] Usar o token **no mesmo domínio** onde foi gerado
- [ ] Se precisar acessar outro tenant, fazer novo login nesse tenant

## 🐛 Troubleshooting

### Erro: "Token inválido: Assinatura inválida"

**Causa:** Token gerado no domínio master sendo usado em tenant (ou vice-versa)

**Solução:** Fazer login no mesmo domínio onde vai usar o token

### Erro: "Token emitido para tenant X não pode ser usado em tenant Y"

**Causa:** Tentando usar token de um tenant em outro tenant

**Solução:** Fazer novo login no tenant Y

### Token funcionou uma vez, depois parou de funcionar

**Causa:** Provavelmente fez login em outro domínio e o token foi sobrescrito

**Solução:** Verificar qual domínio está usando e refazer o login se necessário

## 📚 Arquivos Relacionados

- [MultitenancyJwtTokenCustomizer.java](../src/main/java/br/com/seedgrid/subdomaintenancyexample/security/MultitenancyJwtTokenCustomizer.java) - Adiciona claim `subdomain`
- [MultitenancyJwtTokenValidator.java](../src/main/java/br/com/seedgrid/subdomaintenancyexample/security/MultitenancyJwtTokenValidator.java) - Valida claim `subdomain`
- [TenantHostExtractor.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/resolver/TenantHostExtractor.java) - Extrai tenant do Host
- [OpenApiConfig.java](../src/main/java/br/com/seedgrid/subdomaintenancyexample/config/OpenApiConfig.java) - Documentação Swagger
