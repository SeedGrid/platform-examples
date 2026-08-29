package br.com.seedgrid.billingexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que as migrations reais de security + billing (seedgrid-billing-core, V500/V501) + app
 * (plans/subscriptions, V1000) rodam TODAS no mesmo banco/tabela de historico do Flyway, incluindo
 * a semente de permissions do billing (vinculadas a ADMIN) e dos planos de exemplo.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BillingSchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void migrationsCreateSchemaAndSeedPlansAndPermissions() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(tableExists(conn, "users"), "tabela users (security)");
            assertTrue(tableExists(conn, "companies"), "tabela companies (security)");
            assertTrue(tableExists(conn, "billing_events"), "tabela billing_events (billing-core)");
            assertTrue(tableExists(conn, "plans"), "tabela plans (app host)");
            assertTrue(tableExists(conn, "subscriptions"), "tabela subscriptions (app host)");

            assertTrue(permissionExists(conn, "BILLING_CHECKOUT_CREATE"), "permission BILLING_CHECKOUT_CREATE semeada");
            assertTrue(permissionExists(conn, "BILLING_PORTAL_CREATE"), "permission BILLING_PORTAL_CREATE semeada");
            assertTrue(adminHasPermission(conn, "BILLING_CHECKOUT_CREATE"),
                    "ADMIN deve ter BILLING_CHECKOUT_CREATE (V501 do billing-core)");

            assertTrue(planExists(conn, "BASIC"), "plano BASIC semeado (app host)");
            assertTrue(planExists(conn, "PRO"), "plano PRO semeado (app host)");
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            return rs.next();
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

    private boolean planExists(Connection conn, String code) throws Exception {
        try (var ps = conn.prepareStatement("SELECT 1 FROM plans WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean adminHasPermission(Connection conn, String permissionName) throws Exception {
        String sql = """
                SELECT 1 FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE r.name = 'ADMIN' AND p.name = ?
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, permissionName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
