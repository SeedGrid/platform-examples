package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * GUARDIAO (plano 120, F4.T1) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> com os anexos DESLIGADOS (padrao) nenhum banco (master nem tenant) ganha
 * {@code sg_attachments} nem {@code sg_flyway_schema_history_filemanagement}, e {@code store} segue funcionando sem
 * registro.</p>
 * <p><b>Dano se quebrar:</b> produto que nao usa anexos ganha tabela que nunca pediu.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(FileManagementTenantAttachmentsDisabledGuardianIT.Profile.class)
class FileManagementTenantAttachmentsDisabledGuardianIT extends ProactiveAuthGuardianBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> m = new HashMap<>(overrides(true));
            m.put("seedgrid.filemanagement.registry-table.enabled", "false");
            return m;
        }
    }

    @ConfigProperty(name = "quarkus.datasource.master.jdbc.url")
    String masterUrl;

    private String scalar(String url, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPass);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void desligadoNenhumBancoGanhaTabelaNemHistorico() throws Exception {
        for (String url : new String[] { masterUrl, tenantBaseUrl + "/" + alfaDb, tenantBaseUrl + "/" + betaDb }) {
            assertEquals("t", scalar(url, "select to_regclass('public.sg_attachments') is null"), url);
            assertEquals("t", scalar(url,
                    "select to_regclass('public.sg_flyway_schema_history_filemanagement') is null"), url);
        }
    }

    @Test
    void storeSegueSemRegistro() throws Exception {
        Response s = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).post("/probe/filemanagement/store");
        assertEquals(200, s.statusCode(), s.body().asString());
        assertEquals("t", scalar(tenantBaseUrl + "/" + alfaDb, "select to_regclass('public.sg_attachments') is null"));
        given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).post("/probe/filemanagement/delete")
                .then().statusCode(200);
    }
}
