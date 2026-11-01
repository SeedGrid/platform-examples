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

import br.com.seedgrid.subdomaintenancy.migration.TenantMigrationCoordinator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * GUARDIAO (plano 120, F4.T1) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> na subdomain-tenancy (banco POR tenant) com
 * {@code seedgrid.filemanagement.registry-table.enabled=true}: cada tenant recebe {@code sg_attachments} e o historico
 * proprio {@code sg_flyway_schema_history_filemanagement} (V1300); o master NAO recebe; {@code store} grava a linha SO
 * no banco do tenant que serviu a requisicao (sem vazamento) e {@code delete} a remove; um tenant ANTIGO (sem a tabela)
 * a recebe pelo coordinator de migracao. O {@code store} passa pelo mesmo {@code FileStorageRegistry} do upload de
 * avatar da security, entao cobre tambem o avatar.</p>
 * <p><b>Dano se quebrar:</b> {@code store} responde 500 ({@code AttachmentRecordException}) e o arquivo fica orfao.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(FileManagementTenantAttachmentsGuardianIT.Profile.class)
class FileManagementTenantAttachmentsGuardianIT extends ProactiveAuthGuardianBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> m = new HashMap<>(overrides(true));
            m.put("seedgrid.filemanagement.registry-table.enabled", "true");
            return m;
        }
    }

    @ConfigProperty(name = "quarkus.datasource.master.jdbc.url")
    String masterUrl;

    @Inject
    TenantMigrationCoordinator coordinator;

    private String scalar(String url, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPass);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void executar(String url, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(url, dbUser, dbPass); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private String attachments(String url) throws Exception {
        return scalar(url, "select count(*) from sg_attachments");
    }

    @Test
    void cadaTenantTemATabelaEOHistoricoProprioEOMasterNao() throws Exception {
        for (String db : new String[] { alfaDb, betaDb }) {
            String url = tenantBaseUrl + "/" + db;
            assertEquals("t", scalar(url, "select to_regclass('public.sg_attachments') is not null"), db);
            assertEquals("1", scalar(url, "select count(*) from sg_flyway_schema_history_filemanagement "
                    + "where version = '1300' and success"), db);
        }
        assertEquals("t", scalar(masterUrl, "select to_regclass('public.sg_attachments') is null"));
    }

    @Test
    void storeGravaSoNoBancoDoTenantQueServiuEDeleteApaga() throws Exception {
        String alfaUrl = tenantBaseUrl + "/" + alfaDb;
        String betaUrl = tenantBaseUrl + "/" + betaDb;
        String alfaAntes = attachments(alfaUrl);
        String betaAntes = attachments(betaUrl);

        Response s = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).post("/probe/filemanagement/store");
        assertEquals(200, s.statusCode(), s.body().asString());
        assertEquals(String.valueOf(Integer.parseInt(alfaAntes) + 1), attachments(alfaUrl));
        assertEquals(betaAntes, attachments(betaUrl), "o anexo do alfa nao pode aparecer no banco do beta");

        Response d = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).post("/probe/filemanagement/delete");
        assertEquals(200, d.statusCode(), d.body().asString());
        assertEquals(alfaAntes, attachments(alfaUrl));

        Response sb = given().header("Host", HOST_BETA).auth().oauth2(betaToken).post("/probe/filemanagement/store");
        assertEquals(200, sb.statusCode(), sb.body().asString());
        assertEquals(String.valueOf(Integer.parseInt(betaAntes) + 1), attachments(betaUrl));
        assertEquals(alfaAntes, attachments(alfaUrl), "o anexo do beta nao pode aparecer no banco do alfa");
        given().header("Host", HOST_BETA).auth().oauth2(betaToken).post("/probe/filemanagement/delete")
                .then().statusCode(200);
    }

    @Test
    void tenantAntigoSemATabelaARecebePeloCoordinator() throws Exception {
        String alfaUrl = tenantBaseUrl + "/" + alfaDb;
        // simula um tenant criado ANTES do recurso ser ligado
        executar(alfaUrl, "drop table sg_attachments");
        executar(alfaUrl, "drop table sg_flyway_schema_history_filemanagement");
        assertEquals("t", scalar(alfaUrl, "select to_regclass('public.sg_attachments') is null"));

        coordinator.triggerManualMigration("alfa", true);

        assertEquals("t", scalar(alfaUrl, "select to_regclass('public.sg_attachments') is not null"));
        assertEquals("1", scalar(alfaUrl,
                "select count(*) from sg_flyway_schema_history_filemanagement where version = '1300' and success"));
    }
}
