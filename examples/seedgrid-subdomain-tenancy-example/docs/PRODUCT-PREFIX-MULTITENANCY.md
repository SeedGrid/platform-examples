# Product Prefix para Multitenancy

## 🎯 Objetivo

Permitir múltiplos **produtos** rodarem no mesmo servidor PostgreSQL, isolando os bancos de dados dos tenants por produto.

## 🏗️ Arquitetura

### Antes (Sem Product Prefix)

```
PostgreSQL Server (localhost:5439)
├── seedgrid_kb_master          (master para KB)
├── tenant_lckconsultoria       (tenant do KB)
├── tenant_acmecorp             (tenant do KB)
└── tenant_xpto                 (tenant do KB)
```

**Problema:** Se você tiver outro produto (CRM, ERP, etc.), não pode usar os mesmos tenant IDs.

### Depois (Com Product Prefix)

```
PostgreSQL Server (localhost:5439)
├── kb_seedgrid_master          (master para KB)
├── kb_tenant_lckconsultoria    (tenant do KB)
├── kb_tenant_acmecorp          (tenant do KB)
├── kb_tenant_xpto              (tenant do KB)
│
├── crm_seedgrid_master         (master para CRM)
├── crm_tenant_lckconsultoria   (tenant do CRM)
├── crm_tenant_acmecorp         (tenant do CRM)
│
├── erp_seedgrid_master         (master para ERP)
├── erp_tenant_lckconsultoria   (tenant do ERP)
└── erp_tenant_acmecorp         (tenant do ERP)
```

**Vantagens:**
✅ Mesmo tenant ID (`lckconsultoria`) pode existir em múltiplos produtos
✅ Isolamento completo por produto
✅ Um único servidor PostgreSQL para todos os produtos
✅ Backup e restore mais fácil (por produto)

## ⚙️ Configuração

### application.properties

```properties
# Product prefix (identificador único do produto)
seedgrid.subdomain-tenancy.product-prefix=kb

# URL pattern (o {tenant} será substituído por "kb_tenant_lckconsultoria")
seedgrid.subdomain-tenancy.tenant.jdbc.url-pattern=jdbc:postgresql://localhost:5439/{tenant}

# Master database (também usa o prefix)
database.master.subdomain=kb
database.master.name=${database.master.subdomain}_seedgrid_master
# Resultado: kb_seedgrid_master
```

### Exemplos de Configuração por Produto

#### Knowledge Base (KB)
```properties
seedgrid.subdomain-tenancy.product-prefix=kb
seedgrid.subdomain-tenancy.domain-suffix=kb.seedgrid.com.br
database.master.subdomain=kb
database.master.name=kb_seedgrid_master
```

**Bancos gerados:**
- Master: `kb_seedgrid_master`
- Tenants: `kb_tenant_{subdomain}`

#### CRM
```properties
seedgrid.subdomain-tenancy.product-prefix=crm
seedgrid.subdomain-tenancy.domain-suffix=crm.seedgrid.com.br
database.master.subdomain=crm
database.master.name=crm_seedgrid_master
```

**Bancos gerados:**
- Master: `crm_seedgrid_master`
- Tenants: `crm_tenant_{subdomain}`

#### ERP
```properties
seedgrid.subdomain-tenancy.product-prefix=erp
seedgrid.subdomain-tenancy.domain-suffix=erp.seedgrid.com.br
database.master.subdomain=erp
database.master.name=erp_seedgrid_master
```

**Bancos gerados:**
- Master: `erp_seedgrid_master`
- Tenants: `erp_tenant_{subdomain}`

## 🔄 Fluxo de Criação de Tenant

### 1. Registro via API

```bash
POST http://kb.seedgrid.com.br:8080/public/tenants
{
  "subdomain": "lckconsultoria",
  "corporateName": "LCK Consultoria LTDA",
  ...
}
```

### 2. Processamento Interno

```java
// TenantProvisioningService.buildTenantDatabaseName()
String tenantId = "lckconsultoria";
String productPrefix = "kb";  // do application.properties

String dbName = productPrefix + "_tenant_" + tenantId;
// Resultado: "kb_tenant_lckconsultoria"
```

### 3. Criação do Banco

```sql
CREATE DATABASE kb_tenant_lckconsultoria;
```

### 4. Registro na Tabela Master

```sql
-- Banco: kb_seedgrid_master
-- Tabela: tenants
INSERT INTO sg_tenants (subdomain, ...) VALUES ('lckconsultoria', ...);
```

## 📊 Matriz de Nomenclatura

| Product Prefix | Domain Suffix | Tenant ID | Master DB | Tenant DB |
|----------------|---------------|-----------|-----------|-----------|
| `kb` | `kb.seedgrid.com.br` | `lckconsultoria` | `kb_seedgrid_master` | `kb_tenant_lckconsultoria` |
| `kb` | `kb.seedgrid.com.br` | `acmecorp` | `kb_seedgrid_master` | `kb_tenant_acmecorp` |
| `crm` | `crm.seedgrid.com.br` | `lckconsultoria` | `crm_seedgrid_master` | `crm_tenant_lckconsultoria` |
| `crm` | `crm.seedgrid.com.br` | `xpto` | `crm_seedgrid_master` | `crm_tenant_xpto` |
| `erp` | `erp.seedgrid.com.br` | `lckconsultoria` | `erp_seedgrid_master` | `erp_tenant_lckconsultoria` |

## 🔍 Como Funciona Internamente

### TenantConnectionResolverImpl

```java
private String buildTenantDatabaseName(String tenantId) {
    if (productPrefix.isPresent() && !productPrefix.get().isBlank()) {
        return productPrefix.get() + "_tenant_" + tenantId;
    }
    return "tenant_" + tenantId;
}
```

### TenantProvisioningService

```java
public String buildTenantJdbcUrl(String tenantId) {
    String dbName = buildTenantDatabaseName(tenantId);
    return urlPattern.replace("{tenant}", dbName);
}

// Exemplo:
// tenantId = "lckconsultoria"
// productPrefix = "kb"
// dbName = "kb_tenant_lckconsultoria"
// urlPattern = "jdbc:postgresql://localhost:5439/{tenant}"
// Resultado: "jdbc:postgresql://localhost:5439/kb_tenant_lckconsultoria"
```

## 🧪 Testando

### 1. Verificar Configuração no Startup

```
🗄️  Verificando/criando banco de dados: kb_tenant_lckconsultoria
Criando DataSource para tenant='lckconsultoria' dbName='kb_tenant_lckconsultoria' url='jdbc:postgresql://localhost:5439/kb_tenant_lckconsultoria'
```

### 2. Listar Bancos no PostgreSQL

```bash
psql -U postgres -h localhost -p 5439 -c "\l" | grep tenant
```

**Saída esperada:**
```
kb_seedgrid_master
kb_tenant_lckconsultoria
kb_tenant_acmecorp
```

### 3. Registrar Novo Tenant

```bash
curl -X POST http://kb.seedgrid.com.br:8080/public/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "subdomain": "novotenante",
    "corporateName": "Novo Tenante LTDA",
    "tradeName": "Novo Tenante",
    "cnpj": "12345678000190",
    ...
  }'
```

**Banco criado:** `kb_tenant_novotenante`

## 🔧 Migração de Sistema Existente

Se você já tem bancos sem prefix (`tenant_lckconsultoria`), há duas opções:

### Opção 1: Renomear Bancos Existentes

```sql
-- No PostgreSQL
ALTER DATABASE tenant_lckconsultoria RENAME TO kb_tenant_lckconsultoria;
ALTER DATABASE tenant_acmecorp RENAME TO kb_tenant_acmecorp;
```

### Opção 2: Não Usar Product Prefix (Retrocompatibilidade)

```properties
# Remover ou comentar a property
# seedgrid.subdomain-tenancy.product-prefix=kb
```

Sem o `product-prefix`, o comportamento é o padrão: `tenant_{subdomain}`

## 📝 Boas Práticas

### Escolha do Product Prefix

✅ **Bom:**
- `kb` (Knowledge Base)
- `crm` (CRM)
- `erp` (ERP)
- `hrm` (Human Resources Management)

❌ **Evite:**
- Prefixos muito longos (`knowledge_base`)
- Caracteres especiais (`kb-app`, `kb.app`)
- Números no início (`1kb`)

### Convenção de Nomenclatura

```
{product}_{type}_{identifier}

Exemplos:
kb_seedgrid_master          (master do KB)
kb_tenant_lckconsultoria    (tenant do KB)
crm_seedgrid_master         (master do CRM)
crm_tenant_lckconsultoria   (tenant do CRM)
```

## 🚀 Vantagens

### 1. Isolamento por Produto
Cada produto tem seus próprios bancos, evitando conflitos e facilitando manutenção.

### 2. Escalabilidade
Adicione novos produtos sem impactar os existentes.

### 3. Backup e Restore Simplificados
```bash
# Backup de todos os bancos do KB
pg_dump -U postgres -h localhost -p 5439 kb_seedgrid_master > kb_master.sql
pg_dump -U postgres -h localhost -p 5439 kb_tenant_lckconsultoria > kb_tenant_lckconsultoria.sql

# Backup de todos os bancos do CRM
pg_dump -U postgres -h localhost -p 5439 crm_seedgrid_master > crm_master.sql
pg_dump -U postgres -h localhost -p 5439 crm_tenant_lckconsultoria > crm_tenant_lckconsultoria.sql
```

### 4. Reutilização de Tenant IDs
O mesmo tenant (`lckconsultoria`) pode usar múltiplos produtos:
- KB: `http://lckconsultoria.kb.seedgrid.com.br`
- CRM: `http://lckconsultoria.crm.seedgrid.com.br`
- ERP: `http://lckconsultoria.erp.seedgrid.com.br`

## 🔒 Segurança

O product prefix NÃO afeta a segurança multitenancy:
- ✅ Isolamento por banco de dados continua garantido
- ✅ Tokens JWT com claim `subdomain` continuam funcionando
- ✅ Validação de tenant ativo continua sendo feita

## 📚 Arquivos Modificados na Biblioteca

- [MultitenancyConfig.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/config/MultitenancyConfig.java) - Adicionado `productPrefix` property
- [TenantConnectionResolverImpl.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/resolver/TenantConnectionResolverImpl.java) - Método `buildTenantDatabaseName()`
- [TenantProvisioningService.java](../../seedgrid-subdomain-tenancy/src/main/java/br/com/seedgrid/multitenancy/service/TenantProvisioningService.java) - Métodos `buildTenantJdbcUrl()` e `buildTenantDatabaseName()`

## 🎓 Resumo

O **product prefix** é uma propriedade **opcional** que adiciona um prefixo aos nomes dos bancos de dados dos tenants, permitindo múltiplos produtos compartilharem o mesmo servidor PostgreSQL sem conflitos de nomenclatura.

**Configuração mínima:**
```properties
seedgrid.subdomain-tenancy.product-prefix=kb
```

**Resultado:**
- Sem prefix: `tenant_lckconsultoria`
- Com prefix "kb": `kb_tenant_lckconsultoria`
