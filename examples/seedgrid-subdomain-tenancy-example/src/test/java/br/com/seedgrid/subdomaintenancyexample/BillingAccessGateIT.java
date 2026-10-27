package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Demonstra o bloqueio GERAL do billing ({@code seedgrid.billing.access-gate.enabled=true}, ligado no
 * application.properties do exemplo): conta sem assinatura vigente recebe 402 em endpoint autenticado, e o
 * que o cliente precisa para entrar e pagar segue liberado (login, {@code /public}, {@code /me/subscription}).
 *
 * <p>Reaproveita o provisionamento de dois tenants da base do guardiao X11; os testes herdados tambem rodam
 * com o bloqueio ligado (o tenant recem-criado tem trial vigente, entao seguem 200).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(BillingAccessGateIT.Profile.class)
class BillingAccessGateIT extends ProactiveAuthGuardianBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides(true);
        }
    }

    @ConfigProperty(name = "seedgrid.subdomain-tenancy.master.datasource.jdbc.url")
    String masterUrl;
    @ConfigProperty(name = "seedgrid.subdomain-tenancy.master.datasource.username")
    String masterUser;
    @ConfigProperty(name = "seedgrid.subdomain-tenancy.master.datasource.password")
    String masterPass;

    @Test
    void contaSemAssinaturaRecebe402MasLoginEPathsLiberadosSeguem() throws Exception {
        // Com trial vigente o endpoint autenticado responde 200. A conta de billing (e o trial) nasce nesta
        // primeira chamada autenticada de cada tenant.
        given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/probe/me").then().statusCode(200);
        given().header("Host", HOST_BETA).auth().oauth2(betaToken).get("/probe/me").then().statusCode(200);

        // Cancela as assinaturas da conta do tenant alfa (o beta fica intacto).
        int canceled = cancelSubscriptionsOf("alfa LTDA");
        assertTrue(canceled > 0, "o tenant alfa deveria ter uma assinatura (trial) para cancelar");

        // 402 em endpoint autenticado, com o code do motivo.
        String code = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/probe/me")
                .then().statusCode(402).extract().path("code");
        assertTrue(code.equals("NO_ACTIVE_SUBSCRIPTION") || code.equals("TRIAL_EXPIRED"), "code: " + code);

        // Liberados: o login (anonimo, /public) e /me/subscription (o caminho para pagar) nao dao 402.
        given().header("Host", HOST_ALFA).contentType(ContentType.JSON)
                .body(Map.of("login", "admin@alfa.test", "password", PASSWORD))
                .post("/public/auth/login").then().statusCode(200);
        assertNotEquals(402, given().header("Host", HOST_ALFA).auth().oauth2(alfaToken)
                .get("/me/subscription").statusCode());

        // O bloqueio e' por conta: o beta, com trial, segue 200.
        given().header("Host", HOST_BETA).auth().oauth2(betaToken).get("/probe/me").then().statusCode(200);
    }

    /** Cancela toda assinatura da conta de billing com esse nome (o billing vive no banco master). */
    private int cancelSubscriptionsOf(String accountLegalName) throws Exception {
        try (Connection c = DriverManager.getConnection(masterUrl, masterUser, masterPass);
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE sg_tenant_subscriptions SET status = 'CANCELED' WHERE tenant_id IN "
                                + "(SELECT id FROM sg_accounts WHERE legal_name = ?)")) {
            ps.setString(1, accountLegalName);
            return ps.executeUpdate();
        }
    }
}
