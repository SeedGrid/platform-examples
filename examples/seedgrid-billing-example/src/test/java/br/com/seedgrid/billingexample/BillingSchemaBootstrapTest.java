package br.com.seedgrid.billingexample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * As migrations de security + auditoria + billing + app rodam no mesmo banco: as tabelas de plano,
 * assinatura e ciclo sao do billing (o produto nao cria nenhuma) e o produto so' acrescenta a sua
 * permissao e a cota do trial.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BillingSchemaBootstrapTest {

    @Inject
    DataSource dataSource;

    @Test
    void billingCriaAsTabelasEOProdutoSoAcrescentaOCampoDele() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertAll(
                    () -> assertTrue(tableExists(conn, "sg_users"), "users (security)"),
                    () -> assertTrue(tableExists(conn, "sg_plans"), "plans (billing)"),
                    () -> assertTrue(tableExists(conn, "sg_plan_terms"), "plan_terms (billing)"),
                    () -> assertTrue(tableExists(conn, "sg_tenant_subscriptions"), "assinaturas (billing)"),
                    () -> assertTrue(tableExists(conn, "sg_subscription_billing_cycles"), "ciclos (billing)"),
                    () -> assertTrue(tableExists(conn, "sg_billing_account_history"), "historico (billing)"),
                    () -> assertTrue(adminHas(conn, "PLAN_CREATE"), "ADMIN com PLAN_CREATE (billing/V510)"),
                    () -> assertTrue(adminHas(conn, "REPORT_EXPORT"), "ADMIN com REPORT_EXPORT (app/V1000)"),
                    () -> assertEquals("3", value(conn, "select m.included_units::text from sg_plan_term_meters m "
                            + "join sg_plan_terms t on t.id = m.plan_term_id "
                            + "where t.public_id = '00000000-0000-0000-0000-0000000000a2' and m.meter = 'reports'"),
                            "cota do trial no contador reports (app/V1000)"));
        }
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private static boolean adminHas(Connection conn, String permission) throws Exception {
        return value(conn, "select p.name from sg_roles r join sg_role_permissions rp on rp.role_id = r.id "
                + "join sg_permissions p on p.id = rp.permission_id where r.name = 'ADMIN' and p.name = '"
                + permission + "'") != null;
    }

    private static String value(Connection conn, String sql) throws Exception {
        try (var ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
