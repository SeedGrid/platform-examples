package br.com.seedgrid.apikeysexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que as migrations reais de security + apikeys + app (reports) rodam no mesmo banco,
 * incluindo a semente de permissions/roles do dominio do exemplo (REPORT_READ/REPORT_CREATE e a
 * role de integracao REPORT_READER).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ApiKeysSchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void migrationsCreateSchemaAndSeedIntegrationRole() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(tableExists(conn, "users"), "tabela users (security)");
            assertTrue(tableExists(conn, "roles"), "tabela roles (security)");
            assertTrue(tableExists(conn, "api_keys"), "tabela api_keys (apikeys)");
            assertTrue(tableExists(conn, "reports"), "tabela reports (app host)");
            assertTrue(roleExists(conn, "REPORT_READER"), "role de integracao REPORT_READER semeada");
            assertTrue(permissionExists(conn, "REPORT_READ"), "permission REPORT_READ semeada");
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private boolean roleExists(Connection conn, String name) throws Exception {
        try (var ps = conn.prepareStatement("SELECT 1 FROM roles WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean permissionExists(Connection conn, String name) throws Exception {
        try (var ps = conn.prepareStatement("SELECT 1 FROM permissions WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
