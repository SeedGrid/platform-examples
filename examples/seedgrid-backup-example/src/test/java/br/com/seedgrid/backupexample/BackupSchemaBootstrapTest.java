package br.com.seedgrid.backupexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que as migrations reais de security + backup + app (notes) rodam no mesmo banco, numa
 * unica tabela de historico do Flyway, incluindo a semente de permissions do dominio do exemplo
 * (NOTE_READ/NOTE_CREATE) e as permissions do proprio seedgrid-backup (BACKUP_*).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BackupSchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void migrationsCreateSchemaAndSeedPermissions() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(tableExists(conn, "users"), "tabela users (security)");
            assertTrue(tableExists(conn, "roles"), "tabela roles (security)");
            assertTrue(tableExists(conn, "export_jobs"), "tabela export_jobs (backup)");
            assertTrue(tableExists(conn, "export_artifacts"), "tabela export_artifacts (backup)");
            assertTrue(tableExists(conn, "audit_log"), "tabela audit_log (seedgrid-audit-postgresql, usada pelo @SgAudit do ExportService)");
            assertTrue(tableExists(conn, "notes"), "tabela notes (app host)");

            assertTrue(permissionExists(conn, "NOTE_READ"), "permission NOTE_READ semeada");
            assertTrue(permissionExists(conn, "NOTE_CREATE"), "permission NOTE_CREATE semeada");
            assertTrue(permissionExists(conn, "BACKUP_CREATE"), "permission BACKUP_CREATE semeada (backup)");
            assertTrue(permissionExists(conn, "BACKUP_READ"), "permission BACKUP_READ semeada (backup)");
            assertTrue(permissionExists(conn, "BACKUP_DOWNLOAD"), "permission BACKUP_DOWNLOAD semeada (backup)");

            assertTrue(adminHasPermission(conn, "NOTE_CREATE"), "ADMIN tem NOTE_CREATE");
            assertTrue(adminHasPermission(conn, "BACKUP_CREATE"), "ADMIN tem BACKUP_CREATE");

            assertTrue(singleFlywayHistoryTable(conn), "flyway aplicou tudo numa unica tabela flyway_schema_history");
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

    private boolean adminHasPermission(Connection conn, String permissionName) throws Exception {
        String sql = """
                SELECT 1 FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
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

    private boolean singleFlywayHistoryTable(Connection conn) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
