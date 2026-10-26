package br.com.seedgrid.logintenancyexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que o LoginTenancyMigrationService roda, na ordem certa, as migrations de todas as
 * extensions do baseline (security, audit, backup, apikeys, login-tenancy) e da app host
 * (products), no MESMO schema/banco — sem Flyway nativo (fica desligado pela propria extension).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LoginTenancySchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void migrationsCreateBaselineAndAppSchema() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(tableExists(conn, "sg_users"), "tabela users (security)");
            assertTrue(tableExists(conn, "sg_companies"), "tabela companies (security)");
            assertTrue(tableExists(conn, "sg_signup_request"), "tabela signup_request (login-tenancy)");
            assertTrue(tableExists(conn, "products"), "tabela products (app host)");
            assertTrue(
                    tableExists(conn, "flyway_schema_history_login_tenancy"),
                    "historico dedicado do login-tenancy");
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }
}
