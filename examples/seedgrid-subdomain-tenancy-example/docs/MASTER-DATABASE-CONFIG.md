# Configuração Dinâmica do Banco de Dados Master

## 📋 Visão Geral

O banco de dados master é configurado dinamicamente usando **property expansion** do MicroProfile Config, permitindo que você altere o ambiente apenas modificando uma única propriedade.

## 🔧 Como Funciona

### Propriedades no `application.properties`

```properties
# 1. Defina o subdomínio do ambiente
database.master.subdomain=kb

# 2. O nome do banco é gerado automaticamente usando property expansion
database.master.name=seedgrid_${database.master.subdomain}_master

# 3. A URL do datasource usa a property gerada
quarkus.datasource.master.jdbc.url=jdbc:postgresql://localhost:5439/${database.master.name}
```

### Expansão de Propriedades

O Quarkus resolve as propriedades nesta ordem:

1. `database.master.subdomain=kb`
2. `database.master.name=seedgrid_${database.master.subdomain}_master` → `seedgrid_kb_master`
3. `quarkus.datasource.master.jdbc.url=jdbc:postgresql://localhost:5439/${database.master.name}` → `jdbc:postgresql://localhost:5439/seedgrid_kb_master`

## 🚀 Como Mudar de Ambiente

### ✅ Agora (Automático)

Para mudar de ambiente, **basta alterar UMA propriedade**:

```properties
# Para KB (atual)
database.master.subdomain=kb

# Para Staging
database.master.subdomain=staging

# Para Produção
database.master.subdomain=prod
```

O nome do banco será gerado automaticamente:
- `kb` → `seedgrid_kb_master`
- `staging` → `seedgrid_staging_master`
- `prod` → `seedgrid_prod_master`

### ❌ Antes (Manual)

Você precisava alterar duas propriedades manualmente e garantir consistência:

```properties
seedgrid.subdomain-tenancy.domain-suffix=kb.seedgrid.com.br
quarkus.datasource.master.jdbc.url=jdbc:postgresql://localhost:5439/seedgrid_kb_master
```

## 📊 Exemplos de Configuração

### Ambiente KB (Knowledge Base)

```properties
database.master.subdomain=kb
seedgrid.subdomain-tenancy.domain-suffix=kb.seedgrid.com.br
```

**Resultado:**
- Banco Master: `seedgrid_kb_master`
- URL: `jdbc:postgresql://localhost:5439/seedgrid_kb_master`
- Domain: `kb.seedgrid.com.br`

### Ambiente Staging

```properties
database.master.subdomain=staging
seedgrid.subdomain-tenancy.domain-suffix=staging.seedgrid.com.br
```

**Resultado:**
- Banco Master: `seedgrid_staging_master`
- URL: `jdbc:postgresql://localhost:5439/seedgrid_staging_master`
- Domain: `staging.seedgrid.com.br`

### Ambiente Produção

```properties
database.master.subdomain=prod
seedgrid.subdomain-tenancy.domain-suffix=seedgrid.com.br
```

**Resultado:**
- Banco Master: `seedgrid_prod_master`
- URL: `jdbc:postgresql://localhost:5439/seedgrid_prod_master`
- Domain: `seedgrid.com.br`

## 🛠️ Criação do Banco de Dados

### 1. Via Script SQL

Use o script fornecido em [`create-master-database.sql`](create-master-database.sql):

```bash
psql -U postgres -h localhost -p 5439 -f docs/create-master-database.sql
```

### 2. Via psql Manual

```sql
-- Conectar
psql -U postgres -h localhost -p 5439

-- Criar banco (substitua 'kb' pelo ambiente desejado)
CREATE DATABASE seedgrid_kb_master
    WITH
    OWNER = postgres
    ENCODING = 'UTF8';
```

### 3. Verificar Criação

```sql
\l seedgrid_kb_master
```

## 🔍 Validação no Startup

O bean `MasterDatabaseConfig` valida a configuração no startup da aplicação:

```
=================================================
📊 Master Database Configuration
=================================================
Domain Suffix: kb.seedgrid.com.br
Expected DB Name: seedgrid_kb_master
Actual DB URL: jdbc:postgresql://localhost:5439/seedgrid_kb_master
=================================================
```

Se houver inconsistência, você verá um **warning**:

```
⚠️  Database name mismatch! Expected 'seedgrid_kb_master' but URL is 'seedgrid_staging_master'
```

## ⚙️ Configuração Completa

```properties
# ========================================
# MASTER DATABASE
# ========================================

# Subdomínio do ambiente (ALTERE APENAS ESTA LINHA para mudar de ambiente)
database.master.subdomain=kb

# Nome do banco (gerado automaticamente - NÃO ALTERE)
database.master.name=seedgrid_${database.master.subdomain}_master

# Datasource Master
quarkus.datasource.master.db-kind=postgresql
quarkus.datasource.master.jdbc.url=jdbc:postgresql://localhost:5439/${database.master.name}
quarkus.datasource.master.username=postgres
quarkus.datasource.master.password=postgres

# ========================================
# MULTITENANCY
# ========================================

# Domain suffix (altere conforme o ambiente)
seedgrid.subdomain-tenancy.domain-suffix=kb.seedgrid.com.br
```

## 🎯 Vantagens

✅ **Uma única alteração:** Mude `database.master.subdomain` e pronto
✅ **Sem inconsistências:** Expansão automática garante consistência
✅ **Validação no startup:** Bean valida se tudo está correto
✅ **Documentação clara:** Comentários explicam a regra
✅ **Property expansion nativa:** Usa recurso nativo do MicroProfile Config

## ❓ Precisa Reinstalar a Lib de Multitenancy?

**NÃO!** Estas alterações são apenas no projeto `seedgrid-test-quarkus`. A biblioteca de multitenancy não foi alterada para esta funcionalidade.

## 🔄 Migração de Ambientes

Para migrar entre ambientes:

1. **Pare a aplicação**
2. **Altere** `database.master.subdomain` em `application.properties`
3. **Crie o novo banco** no PostgreSQL (se ainda não existe)
4. **Inicie a aplicação** - Flyway criará as tabelas automaticamente
5. **Verifique os logs** para confirmar o banco correto

## 📝 Notas Importantes

- O bean `MasterDatabaseConfig` apenas **valida** a configuração, não a gera
- A geração é feita pelo **property expansion** do MicroProfile Config
- As migrations Flyway serão executadas automaticamente no banco correto
- Cada ambiente deve ter seu próprio banco master no PostgreSQL
