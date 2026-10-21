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
 * GUARDIAO (Fase 5, T5.12) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no login-tenancy (banco compartilhado, uma empresa por conta-cliente) login e refresh
 * devolvem {@code currentCompany.accessMode} ({@code READ_ONLY} loga e devolve {@code READ_ONLY}) e o {@code /users} (que traz {@code memberships} de TODAS as contas) continua fechado a conta-cliente:
 * ela le os usuarios da propria empresa so' por {@code /me/security/users}. Tambem prova (T5.15) que
 * {@code /companies} (CRU de empresas) e' da plataforma: a conta-cliente leva 403.</p>
 * <p><b>Por que existe:</b> a faixa "somente leitura" do front le o estado do login sem /me/companies; e o
 * {@code memberships} nao pode virar janela para as outras contas do banco compartilhado.</p>
 * <p><b>Dano se quebrar:</b> cliente escrevendo em conta somente leitura sem aviso, ou vazamento entre contas.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthAccessModeGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";

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

    String emailA, emailB, publicIdA, publicIdB;

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();

        emailA = "owner-a-" + UUID.randomUUID() + "@test.com";
        emailB = "owner-b-" + UUID.randomUUID() + "@test.com";
        String emailC = "owner-c-" + UUID.randomUUID() + "@test.com";
        String cnpjA = cnpj(), cnpjB = cnpj(), cnpjC = cnpj();
        signup(emailA, cnpjA, "Empresa A");
        signup(emailB, cnpjB, "Empresa B");
        signup(emailC, cnpjC, "Empresa C");
        long companyA = scalar("SELECT id FROM companies WHERE cpf_cnpj = '" + cnpjA + "'");
        long companyC = scalar("SELECT id FROM companies WHERE cpf_cnpj = '" + cnpjC + "'");
        publicIdA = text("SELECT public_id FROM companies WHERE id = " + companyA);
        publicIdB = text("SELECT public_id FROM companies WHERE cpf_cnpj = '" + cnpjB + "'");
        // A conta de A passa a ter tambem a empresa C, depois do login (que escopa na unica empresa): sem isso
        // "DISABLED" na unica empresa da conta e' recusado (409 COMPANY_ACCESS_ALL_DISABLED, regra do security).
        long ownerA = scalar("SELECT id FROM users WHERE email = '" + emailA + "'");
        update("INSERT INTO user_companies (user_id, company_id) VALUES (" + ownerA + ", " + companyC + ")");
    }

    @AfterEach
    void backToActive() {
        access.setMode(publicIdA, CompanyAccessMode.ACTIVE, "reset do teste");
        access.setMode(publicIdB, CompanyAccessMode.ACTIVE, "reset do teste");
    }

    @Test
    void readOnlyCompanyLogsInAndRefreshesWithTheAccessMode() {
        access.setMode(publicIdB, CompanyAccessMode.READ_ONLY, "teste");

        Response login = given().contentType(ContentType.JSON).body(Map.of("login", emailB, "password", PASSWORD))
                .post("/public/auth/login");
        assertEquals(200, login.statusCode(), login.body().asString());
        assertEquals("READ_ONLY", login.jsonPath().getString("currentCompany.accessMode"));
        assertEquals(publicIdB, login.jsonPath().getString("currentCompany.publicId"));

        given().contentType(ContentType.JSON).body(Map.of("refreshToken", login.jsonPath().getString("refreshToken")))
                .post("/public/auth/refresh")
                .then().statusCode(200)
                .body("currentCompany.accessMode", equalTo("READ_ONLY"))
                .body("currentCompany.publicId", equalTo(publicIdB));
    }

    @Test
    void activeCompanyLoginReturnsActive() {
        given().contentType(ContentType.JSON).body(Map.of("login", emailB, "password", PASSWORD))
                .post("/public/auth/login")
                .then().statusCode(200).body("currentCompany.accessMode", equalTo("ACTIVE"));
    }

    @Test
    void usersEndpointWithMembershipsStaysClosedToTheClientAccount() {
        String tokenB = login(emailB);
        String ownerB = text("SELECT public_id FROM users WHERE email = '" + emailB + "'");
        given().header("Authorization", "Bearer " + tokenB).get("/users/" + ownerB).then().statusCode(403);
        // T5.15: a conta-cliente NAO administra empresas por /companies (so' a plataforma; @PlatformScope)
        given().header("Authorization", "Bearer " + tokenB).get("/companies").then().statusCode(403);
        given().header("Authorization", "Bearer " + tokenB).contentType(ContentType.JSON)
                .body(Map.of("corporateName", "Outra LTDA", "cpfCnpj", cnpj())).post("/companies")
                .then().statusCode(403);
        // a conta-cliente le os usuarios da propria empresa pelo caminho escopado
        given().header("Authorization", "Bearer " + tokenB).get("/me/security/users").then().statusCode(200);
    }

    // ---------------------------------------------------------------- helpers

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

    private String cnpj() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L));
    }
}
