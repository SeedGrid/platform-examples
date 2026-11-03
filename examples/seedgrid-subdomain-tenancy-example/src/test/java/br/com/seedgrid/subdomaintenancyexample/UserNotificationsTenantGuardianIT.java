package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * GUARDIAO (plano 130, F2.T3) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> na subdomain-tenancy (banco POR tenant) a caixa de notificacoes e' migrada em CADA banco de
 * tenant ({@code sg_user_notification} + historico proprio, V1400), o master NAO a recebe, e a notificacao gravada na
 * requisicao de um tenant fica SO no banco dele.</p>
 * <p><b>Dano se quebrar:</b> aviso de um tenant visivel em outro, ou tenant sem a tabela (500 ao notificar).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(UserNotificationsTenantGuardianIT.Profile.class)
class UserNotificationsTenantGuardianIT extends ProactiveAuthGuardianBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides(true);
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

    private String notifications(String url) throws Exception {
        return scalar(url, "select count(*) from sg_user_notification");
    }

    @Test
    void cadaTenantTemATabelaEOHistoricoProprioEOMasterNao() throws Exception {
        for (String db : new String[] { alfaDb, betaDb }) {
            String url = tenantBaseUrl + "/" + db;
            assertEquals("t", scalar(url, "select to_regclass('public.sg_user_notification') is not null"), db);
            assertEquals("1", scalar(url, "select count(*) from sg_flyway_schema_history_user_notifications "
                    + "where version = '1400' and success"), db);
        }
        assertEquals("t", scalar(masterUrl, "select to_regclass('public.sg_user_notification') is null"));
    }

    @Test
    void notificarGravaSoNoBancoDoTenantQueServiu() throws Exception {
        String alfaUrl = tenantBaseUrl + "/" + alfaDb;
        String betaUrl = tenantBaseUrl + "/" + betaDb;
        String alfaAntes = notifications(alfaUrl);
        String betaAntes = notifications(betaUrl);

        Response a = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken)
                .post("/probe/user-notifications/notify");
        assertEquals(200, a.statusCode(), a.body().asString());
        assertEquals(String.valueOf(Integer.parseInt(alfaAntes) + 1), notifications(alfaUrl));
        assertEquals(betaAntes, notifications(betaUrl), "o aviso do alfa nao pode aparecer no banco do beta");

        Response b = given().header("Host", HOST_BETA).auth().oauth2(betaToken)
                .post("/probe/user-notifications/notify");
        assertEquals(200, b.statusCode(), b.body().asString());
        assertEquals(String.valueOf(Integer.parseInt(betaAntes) + 1), notifications(betaUrl));
        assertEquals(String.valueOf(Integer.parseInt(alfaAntes) + 1), notifications(alfaUrl),
                "o aviso do beta nao pode aparecer no banco do alfa");
    }
}
