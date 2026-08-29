package br.com.seedgrid.apikeysexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.entities.BaseUser;
import br.com.seedgrid.security.repositories.BaseUserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Demonstra o diferencial do seedgrid-apikeys: autenticacao M2M via header x-api-key, sem JWT
 * nenhum, onde as permissoes efetivas da chave vem 100% da ROLE atribuida a ela (nao ha
 * "scopes" proprios da key).
 *
 * <p>Fluxo: admin humano (JWT) cria um report e emite uma API key atrelada a role de integracao
 * REPORT_READER (so tem REPORT_READ). Prova que a chave: (1) le o report via /api/reports mesmo
 * sem qualquer JWT; (2) NAO consegue criar um novo report (403), porque sua role nao carrega
 * REPORT_CREATE.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ApiKeyReportAccessTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Inject
    BaseUserRepository userRepository;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        // O FirstAdminBootstrapService (seedgrid-security) cria o primeiro admin com o e-mail
        // NAO confirmado (fluxo real exige clicar no link do e-mail de boas-vindas); confirmamos
        // direto no banco pra poder logar no teste.
        QuarkusTransaction.requiringNew().run(() -> {
            BaseUser admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void integrationRoleCanReadButNotCreateReports() {
        String adminToken = loginAdmin();

        createReport(adminToken, "Relatorio mensal", "Conteudo de exemplo");

        String rawApiKey = createApiKeyForRole(adminToken, "REPORT_READER", "job-noturno", 90);

        // A API key (sem JWT nenhum) consegue LER, porque a role REPORT_READER tem REPORT_READ.
        java.util.List<Integer> titles = given()
                .header("x-api-key", rawApiKey)
                .when()
                .get("/api/reports")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");
        assertTrue(!titles.isEmpty(), "a api key deve enxergar os reports existentes");

        // A MESMA api key NAO consegue CRIAR: sua role nao tem REPORT_CREATE.
        given()
                .header("x-api-key", rawApiKey)
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Nao deveria criar", "content", "x"))
                .when()
                .post("/api/reports")
                .then()
                .statusCode(403);
    }

    private String loginAdmin() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when()
                .post("/public/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .extract().path("token");
    }

    private void createReport(String bearer, String title, String content) {
        given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body(Map.of("title", title, "content", content))
                .when()
                .post("/api/reports")
                .then()
                .statusCode(201);
    }

    private String createApiKeyForRole(String bearer, String roleName, String name, int expiresDays) {
        return given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body(Map.of("name", name, "expiresDays", expiresDays))
                .when()
                .post("/apikeys/role/" + roleName)
                .then()
                .statusCode(201)
                .extract().jsonPath().getString("apiKey");
    }
}
