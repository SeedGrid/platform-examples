package br.com.seedgrid.logintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.persistence.embeddables.Contact;
import br.com.seedgrid.logintenancy.entities.SignupRequestEntity;
import br.com.seedgrid.logintenancy.repositories.SignupRequestRepository;
import br.com.seedgrid.security.services.SecretEncryptionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * LIVRO DE REGRAS (comportamento de HOJE): isolamento de API keys e de JWT entre EMPRESAS no login-tenancy
 * (banco compartilhado), com Postgres real. Contraparte do ApiKeyTenantIsolationRuleBookTest do subdomain-tenancy.
 * Plano: seedgrid-platform-work/planos/transversal/2026-10-18-escopo-acesso-calendario.md (X8). Resultado inesperado e' achado, nao teste
 * a "consertar".
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyIsolationGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";
    static final String ROLE = "PROBE_ROLE";

    @Inject
    SecretEncryptionService secretEncryptionService;
    @Inject
    SignupRequestRepository signupRequestRepository;
    @Inject
    EntityManager em;
    @TestHTTPResource
    java.net.URL appUrl;

    String emailA, emailB, tokenA, tokenB, keyA, keyB;
    long companyA, companyB, productA, productB;

    @BeforeAll
    void setUp() {
        // Porta real do app (licoes de teste do plano): sem isto o RestAssured pode falar com outro processo local.
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();

        emailA = "owner-a-" + UUID.randomUUID() + "@test.com";
        emailB = "owner-b-" + UUID.randomUUID() + "@test.com";
        String cnpjA = cnpj();
        String cnpjB = cnpj();
        signup(emailA, cnpjA, "Empresa A");
        signup(emailB, cnpjB, "Empresa B");
        tokenA = login(emailA);
        tokenB = login(emailB);
        companyA = companyId(cnpjA);
        companyB = companyId(cnpjB);

        productA = createProduct(tokenA, "Produto A", "SKU-A");
        productB = createProduct(tokenB, "Produto B", "SKU-B");

        // Mesmo nome de role de integracao nas duas empresas (o indice unico por empresa permite), mesma permissao.
        QuarkusTransaction.requiringNew().run(() -> {
            sql("INSERT INTO sg_permissions (name, description, public_id) VALUES ('PROBE_READ', 'probe', '"
                    + UUID.randomUUID() + "') ON CONFLICT DO NOTHING");
            for (long company : new long[] { companyA, companyB }) {
                sql("INSERT INTO sg_roles (name, description, public_id, for_integration, company_id) VALUES ('" + ROLE
                        + "', 'probe', '" + UUID.randomUUID() + "', TRUE, " + company + ")");
                sql("INSERT INTO sg_role_permissions (role_id, permission_id) SELECT r.id, p.id FROM sg_roles r, sg_permissions p "
                        + "WHERE r.name = '" + ROLE + "' AND r.company_id = " + company + " AND p.name = 'PROBE_READ'");
            }
        });
        keyA = createKey(tokenA);
        keyB = createKey(tokenB);
    }

    // ---------------------------------------------------------------- API key

    @Test
    void keyOfCompanyASeesOnlyCompanyAProducts() {
        List<Integer> ids = given().header("x-api-key", keyA).get("/api/products").then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertEquals(List.of((int) productA), ids);
    }

    @Test
    void keyOfCompanyBSeesOnlyCompanyBProducts() {
        List<Integer> ids = given().header("x-api-key", keyB).get("/api/products").then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertEquals(List.of((int) productB), ids);
    }

    @Test
    void keyOfAHasCompanyAAsCurrentCompanyAndHeaderCannotChangeIt() {
        given().header("x-api-key", keyA).get("/probe/permission").then().statusCode(200)
                .body(org.hamcrest.Matchers.equalTo(String.valueOf(companyA)));
        Response r = given().header("x-api-key", keyA).header("X-Company-Id", companyB).get("/probe/permission");
        System.out.println("[X8] chave de A com X-Company-Id=B -> " + r.statusCode() + " " + r.body().asString());
        assertEquals(String.valueOf(companyA), r.body().asString(), "header nao pode redirecionar a chave para B");
    }

    @Test
    void keyOfACannotReadProductOfB() {
        given().header("x-api-key", keyA).get("/api/products/" + productB).then().statusCode(400);
    }

    @Test
    void tokenOfACannotCreateKeyForRoleOfAnotherCompany() {
        QuarkusTransaction.requiringNew().run(() -> sql(
                "INSERT INTO sg_roles (name, description, public_id, for_integration, company_id) VALUES ('ONLY_B_ROLE', 'x', '"
                        + UUID.randomUUID() + "', TRUE, " + companyB + ")"));
        Response r = given().header("Authorization", "Bearer " + tokenA).contentType(ContentType.JSON)
                .body(Map.of("name", "k", "expiresDays", 5)).post("/apikeys/role/ONLY_B_ROLE");
        System.out.println("[X8] A cria chave para role exclusiva de B -> " + r.statusCode());
        assertTrue(r.statusCode() >= 400, "A nao pode criar chave com role de outra empresa: " + r.body().asString());
    }

    @Test
    void keyStopsWorkingWhenRoleLosesIntegrationFlagOrIsInactive() {
        String key = createKey(tokenA);
        given().header("x-api-key", key).get("/probe/permission").then().statusCode(200);
        update("UPDATE sg_roles SET for_integration = FALSE WHERE name = '" + ROLE + "' AND company_id = " + companyA);
        given().header("x-api-key", key).get("/probe/permission").then().statusCode(401);
        update("UPDATE sg_roles SET for_integration = TRUE, active = FALSE WHERE name = '" + ROLE + "' AND company_id = "
                + companyA);
        given().header("x-api-key", key).get("/probe/permission").then().statusCode(401);
        update("UPDATE sg_roles SET active = TRUE WHERE name = '" + ROLE + "' AND company_id = " + companyA);
        given().header("x-api-key", key).get("/probe/permission").then().statusCode(200);
    }

    @Test
    void scopedKeyStopsWhenOwnerLosesMembership() {
        String key = createKey(tokenA);
        update("UPDATE sg_user_companies SET active = FALSE WHERE company_id = " + companyA);
        try {
            given().header("x-api-key", key).get("/probe/permission").then().statusCode(401);
        } finally {
            update("UPDATE sg_user_companies SET active = TRUE WHERE company_id = " + companyA);
        }
        given().header("x-api-key", key).get("/probe/permission").then().statusCode(200);
    }

    // ---------------------------------------------------------------- JWT

    @Test
    void tokenOfAIsScopedToCompanyAAndHeaderDoesNotSwitchCompany() {
        Response r = given().header("Authorization", "Bearer " + tokenA).header("X-Company-Id", companyB)
                .get("/api/products");
        System.out.println("[X8] token de A com X-Company-Id=B -> " + r.statusCode() + " " + r.body().asString());
        List<Integer> ids = r.then().statusCode(200).extract().jsonPath().getList("id");
        assertEquals(List.of((int) productA), ids, "token escopado em A nao pode ver B pelo header");
    }

    // O teste "tokenKeepsWorkingAfterTheMembershipIsDeactivatedUntilItExpires" (token antigo seguia valendo 8 h depois de
    // desativar o vinculo) foi INVERTIDO (esperado 401 SESSION_REVOKED) e promovido a SessionRevocationGuardianIT
    // (Fase 2, T2.3; decisao do dono: invalidar a sessao de imediato, marcador no seedgrid-cache).

    // ---------------------------------------------------------------- helpers

    private void update(String sql) {
        QuarkusTransaction.requiringNew().run(() -> sql(sql));
    }

    private void sql(String sql) {
        em.createNativeQuery(sql).executeUpdate();
    }

    private long companyId(String cnpj) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("SELECT id FROM sg_companies WHERE cpf_cnpj = :c").setParameter("c", cnpj)
                .getSingleResult()).longValue());
    }

    private void signup(String email, String cnpj, String name) {
        String token = String.format("%012d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L));
        QuarkusTransaction.requiringNew().run(() -> {
            SignupRequestEntity s = new SignupRequestEntity();
            s.email = email;
            s.name = name;
            s.corporateName = name + " LTDA";
            s.tradeName = name;
            s.cpfCnpj = cnpj;
            s.verificationToken = token;
            s.passwordHash = secretEncryptionService.encrypt(PASSWORD);
            s.legalRepresentative = Contact.builder().firstName("Owner").lastName(name).email(email).build();
            signupRequestRepository.persist(s);
        });
        given().contentType(ContentType.JSON).body(Map.of("token", token)).post("/public/signup/confirm-email")
                .then().statusCode(200);
    }

    private String login(String email) {
        String t = given().contentType(ContentType.JSON).body(Map.of("login", email, "password", PASSWORD))
                .post("/public/auth/login").then().statusCode(200).extract().path("token");
        assertNotNull(t);
        return t;
    }

    private long createProduct(String token, String name, String sku) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("name", name, "sku", sku, "price", "10.00")).post("/api/products")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private String createKey(String token) {
        Response r = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("name", "k-" + UUID.randomUUID(), "expiresDays", 30)).post("/apikeys/role/" + ROLE);
        assertEquals(201, r.statusCode(), "criar chave: " + r.body().asString());
        return r.jsonPath().getString("apiKey");
    }

    private String cnpj() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L));
    }
}
