-- ================================================================
-- Script para criar o banco de dados master
-- ================================================================
--
-- O nome do banco master segue o padrão: seedgrid_{subdomain}_master
-- Baseado no database.master.subdomain em application.properties
--
-- Configuração em application.properties:
--   database.master.subdomain=kb
--   database.master.name=seedgrid_${database.master.subdomain}_master
--   quarkus.datasource.master.jdbc.url=jdbc:postgresql://localhost:5439/${database.master.name}
--
-- Exemplos:
--   subdomain: kb       -> banco: seedgrid_kb_master
--   subdomain: staging  -> banco: seedgrid_staging_master
--   subdomain: (vazio)  -> banco: seedgrid__master (não use vazio, use "prod")
--
-- ================================================================

-- 1. Conecte-se ao PostgreSQL como superusuário (postgres)
-- psql -U postgres -h localhost -p 5439

-- 2. Execute este comando para criar o banco master:

CREATE DATABASE seedgrid_kb_master
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE seedgrid_kb_master
    IS 'Master database for KB SeedGrid - contains tenant configurations and global data';

-- 3. Verifique a criação:
-- \l seedgrid_kb_master

-- ================================================================
-- Para outros ambientes, substitua 'kb' pelo nome apropriado:
-- ================================================================

-- STAGING:
-- CREATE DATABASE seedgrid_staging_master ...;

-- PRODUÇÃO:
-- CREATE DATABASE seedgrid_prod_master ...;

-- ================================================================
-- As tabelas serão criadas automaticamente pelo Flyway na primeira
-- inicialização da aplicação.
-- ================================================================
