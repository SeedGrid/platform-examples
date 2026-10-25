package br.com.seedgrid.logintenancyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.persistence.embeddables.Contact;
import br.com.seedgrid.logintenancy.entities.SignupRequestEntity;
import br.com.seedgrid.logintenancy.repositories.SignupRequestRepository;
import br.com.seedgrid.security.entities.CompanyAccessMode;
import br.com.seedgrid.security.services.CompanyAccessService;
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
 * GUARDIAO (Fase 5, T5.1 - criterio "API key obedece", plano 4.12) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem
 * decisao explicita do dono.
 *
 * <p><b>O que protege:</b> a API key REAL (extension apikeys, {@code x-api-key}) obedece ao estado de acesso da
 * empresa dela: {@code ACTIVE} escreve e le; {@code READ_ONLY} le (200) mas a escrita e' 403
 * {@code COMPANY_READ_ONLY}; {@code DISABLED} e' 403 {@code COMPANY_DISABLED} ate' na leitura; voltar a
 * {@code ACTIVE} restaura; e a chave de OUTRA empresa nao e' afetada. Banco compartilhado (login-tenancy), Postgres
 * real. O {@code CompanyAccessGuardianIT} do security so imitou o filtro de empresa da chave; aqui e' a chave de
 * verdade.</p>
 * <p><b>Por que existe:</b> integracao por chave nao passa pelo login nem pelo marcador de sessao; o filtro de
 * acesso e' a UNICA barreira. Se a chave escapar dele (ordem dos filtros, empresa da chave nao publicada no contexto),
 * a empresa inadimplente segue escrevendo por API.</p>
 * <p><b>Dano se quebrar:</b> receita perdida (escrita liberada por chave em conta bloqueada) ou, no sentido inverso,
 * a empresa em dia bloqueada por engano.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyCompanyAccessGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";
    static final String ROLE = "ACCESS_ROLE";

    @Inject
    SecretEncryptionService secretEncryptionService;
    @Inject
    SignupRequestRepository signupRequestRepository;
    @Inject
    CompanyAccessService access;
    @Inject
    EntityManager em;
    @TestHTTPResource
    java.net.URL appUrl;

    String keyA, keyB, publicIdA, publicIdB;

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();

        String emailA = "owner-a-" + UUID.randomUUID() + "@test.com";
        String emailB = "owner-b-" + UUID.randomUUID() + "@test.com";
        String emailC = "owner-c-" + UUID.randomUUID() + "@test.com";
        String cnpjA = cnpj(), cnpjB = cnpj(), cnpjC = cnpj();
        signup(emailA, cnpjA, "Empresa A");
        signup(emailB, cnpjB, "Empresa B");
        signup(emailC, cnpjC, "Empresa C");
        long companyA = scalar("SELECT id FROM sg_companies WHERE cpf_cnpj = '" + cnpjA + "'");
        long companyB = scalar("SELECT id FROM sg_companies WHERE cpf_cnpj = '" + cnpjB + "'");
        long companyC = scalar("SELECT id FROM sg_companies WHERE cpf_cnpj = '" + cnpjC + "'");
        publicIdA = text("SELECT public_id FROM sg_companies WHERE id = " + companyA);
        publicIdB = text("SELECT public_id FROM sg_companies WHERE id = " + companyB);
        String tokenA = login(emailA);
        String tokenB = login(emailB);
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
        // As chaves nascem ANTES de mudar o estado (mudar o estado derruba os JWT da empresa, nao as chaves).
        keyA = createKey(tokenA);
        keyB = createKey(tokenB);
        // A conta de A passa a ter tambem a empresa C (depois de criar a chave, que exige usuario de 1 empresa): sem
        // isso "DISABLED" na unica empresa da conta e' recusado (409 COMPANY_ACCESS_ALL_DISABLED, regra do security).
        long ownerA = scalar("SELECT id FROM sg_users WHERE email = '" + emailA + "'");
        update("INSERT INTO sg_user_companies (user_id, company_id) VALUES (" + ownerA + ", " + companyC + ")");
    }

    @AfterEach
    void backToActive() {
        access.setMode(publicIdA, CompanyAccessMode.ACTIVE, "reset do teste");
        access.setMode(publicIdB, CompanyAccessMode.ACTIVE, "reset do teste");
    }

    @Test
    void activeCompanyKeyWritesAndReads() {
        write(keyA, "SKU-ACT").then().statusCode(201);
        read(keyA).then().statusCode(200);
    }

    @Test
    void readOnlyCompanyKeyReadsButWriteIs403CompanyReadOnly() {
        access.setMode(publicIdA, CompanyAccessMode.READ_ONLY, "teste");

        read(keyA).then().statusCode(200);
        write(keyA, "SKU-RO").then().statusCode(403)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("COMPANY_READ_ONLY"))
                .body("companyPublicId", equalTo(publicIdA));
        // PUT e DELETE tambem sao escrita: barrados ANTES de chegar ao recurso (id inexistente nao vira 404)
        for (String method : new String[] { "PUT", "DELETE" }) {
            given().header("x-api-key", keyA).contentType(ContentType.JSON).body(Map.of("name", "x"))
                    .request(method, "/api/products/999999").then().statusCode(403)
                    .body("code", equalTo("COMPANY_READ_ONLY"));
        }
    }

    @Test
    void disabledCompanyKeyIs403CompanyDisabledEvenForRead() {
        access.setMode(publicIdA, CompanyAccessMode.DISABLED, "teste");

        read(keyA).then().statusCode(403).body("code", equalTo("COMPANY_DISABLED"));
        write(keyA, "SKU-DIS").then().statusCode(403).body("code", equalTo("COMPANY_DISABLED"));
    }

    @Test
    void backToActiveRestoresTheKey() {
        access.setMode(publicIdA, CompanyAccessMode.READ_ONLY, "teste");
        write(keyA, "SKU-X1").then().statusCode(403);
        access.setMode(publicIdA, CompanyAccessMode.DISABLED, "teste");
        read(keyA).then().statusCode(403);
        access.setMode(publicIdA, CompanyAccessMode.ACTIVE, "pago");

        write(keyA, "SKU-BACK").then().statusCode(201);
        read(keyA).then().statusCode(200);
    }

    @Test
    void keyOfAnotherCompanyIsNotAffected() {
        access.setMode(publicIdA, CompanyAccessMode.READ_ONLY, "teste");
        write(keyB, "SKU-B-RO").then().statusCode(201);
        access.setMode(publicIdA, CompanyAccessMode.DISABLED, "teste");
        write(keyB, "SKU-B-DIS").then().statusCode(201);
        read(keyB).then().statusCode(200);
        // e o inverso: B em READ_ONLY nao mexe na chave de A (ACTIVE)
        access.setMode(publicIdA, CompanyAccessMode.ACTIVE, "pago");
        access.setMode(publicIdB, CompanyAccessMode.READ_ONLY, "teste");
        write(keyA, "SKU-A-OK").then().statusCode(201);
        write(keyB, "SKU-B-BLOCK").then().statusCode(403).body("code", equalTo("COMPANY_READ_ONLY"));
    }

    // ---------------------------------------------------------------- helpers

    private Response read(String key) {
        return given().header("x-api-key", key).get("/api/products");
    }

    private Response write(String key, String sku) {
        return given().header("x-api-key", key).contentType(ContentType.JSON)
                .body(Map.of("name", "Produto " + sku, "sku", sku + "-" + UUID.randomUUID().toString().substring(0, 8), "price", "10.00"))
                .post("/api/products");
    }

    private void update(String sql) {
        QuarkusTransaction.requiringNew().run(() -> sql(sql));
    }

    private void sql(String sql) {
        em.createNativeQuery(sql).executeUpdate();
    }

    private long scalar(String sql) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    private String text(String sql) {
        return QuarkusTransaction.requiringNew().call(() -> String.valueOf(em.createNativeQuery(sql).getSingleResult()));
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
