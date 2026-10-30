-- Cria o banco de cada exemplo.
--
-- O Postgres executa tudo que estiver em /docker-entrypoint-initdb.d na PRIMEIRA
-- vez que o volume e' criado. Por isso voce nao precisa criar banco nenhum na mao:
-- `podman compose up -d` ja deixa tudo pronto.
--
-- Se precisar recomecar do zero:  podman compose -f infra/compose.yml down -v
-- (o -v apaga os volumes; sem ele, este script nao roda de novo)
--
-- As extensoes rodam as migrations do Flyway dentro de um banco que ja existe —
-- elas nao criam o banco. E' so isso que este arquivo resolve.

-- Fundacao
CREATE DATABASE apikeys_example;
CREATE DATABASE testeaudit;
CREATE DATABASE privacy_example;
CREATE DATABASE filemanagement_example;

-- Multi-tenant
-- login-tenancy: banco unico, tudo aqui dentro
CREATE DATABASE login_tenancy_example;
-- subdomain-tenancy: este e' o banco MASTER (cadastro de tenants, planos,
-- usuarios da plataforma). Os bancos de cada tenant sao criados pela propria
-- aplicacao no provisionamento.
CREATE DATABASE crm_master;

-- Operacao
CREATE DATABASE backup_example;
CREATE DATABASE billing_example;
CREATE DATABASE whatsapp_example;

-- security-example
CREATE DATABASE clinica;
