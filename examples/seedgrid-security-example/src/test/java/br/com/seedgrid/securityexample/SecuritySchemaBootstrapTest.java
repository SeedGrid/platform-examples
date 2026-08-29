package br.com.seedgrid.securityexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * IT do seedgrid-security (2026.05.04) contra Postgres real (Testcontainers).
 *
 * <p>Prova que: (1) as migrations reais do security criam o schema do modelo ADITIVO de membership
 * — incl. a V221 ({@code user_companies.is_root} + {@code user_company_roles}); (2) o bootstrap do
 * security cria a default company + o primeiro admin no startup.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SecuritySchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void migrationsCreateMembershipSchemaAndBootstrapDefaultCompany() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Schema base + modelo aditivo de membership (V213/V214/V221)
            assertTrue(tableExists(conn, "users"), "tabela users");
            assertTrue(tableExists(conn, "roles"), "tabela roles");
            assertTrue(tableExists(conn, "companies"), "tabela companies");
            assertTrue(tableExists(conn, "user_companies"), "tabela user_companies");
            assertTrue(tableExists(conn, "user_company_roles"), "tabela user_company_roles (V221)");
            assertTrue(columnExists(conn, "user_companies", "is_root"), "user_companies.is_root (V221)");

            // Bootstrap do security: default company + primeiro admin criados no startup
            assertTrue(count(conn, "companies") >= 1, "default company criada");
            assertTrue(count(conn, "users") >= 1, "primeiro admin criado");
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private long count(Connection conn, String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
