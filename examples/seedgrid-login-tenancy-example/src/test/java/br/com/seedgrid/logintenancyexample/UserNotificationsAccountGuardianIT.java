package br.com.seedgrid.logintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * GUARDIAO (plano 130, F2.T3) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no login-tenancy (banco compartilhado) a caixa de notificacoes e' migrada (V1400 + historico
 * proprio) e a notificacao gravada numa requisicao leva como conta a EMPRESA do token (BE-72): a da empresa A fica com
 * o {@code publicId} de A, a de B com o de B.</p>
 * <p><b>Dano se quebrar:</b> notificacao sem conta (visivel fora da empresa) ou com a conta trocada.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserNotificationsAccountGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";

    @Inject
    SecretEncryptionService secretEncryptionService;
    @Inject
    SignupRequestRepository signupRequestRepository;
    @Inject
    EntityManager em;
    @TestHTTPResource
    java.net.URL appUrl;

    String tokenA, tokenB, publicIdA, publicIdB;

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();

        String emailA = "owner-a-" + UUID.randomUUID() + "@test.com";
        String emailB = "owner-b-" + UUID.randomUUID() + "@test.com";
        String cnpjA = cnpj();
        String cnpjB = cnpj();
        signup(emailA, cnpjA, "Empresa A");
        signup(emailB, cnpjB, "Empresa B");
        tokenA = login(emailA);
        tokenB = login(emailB);
        publicIdA = scalar("SELECT public_id FROM sg_companies WHERE cpf_cnpj = '" + cnpjA + "'");
        publicIdB = scalar("SELECT public_id FROM sg_companies WHERE cpf_cnpj = '" + cnpjB + "'");
    }

    @Test
    void tabelaMigradaComHistoricoProprio() {
        assertEquals("true", scalar("select to_regclass('public.sg_user_notification') is not null"));
        assertEquals("1", scalar("select count(*) from sg_flyway_schema_history_user_notifications "
                + "where version = '1400' and success"));
    }

    @Test
    void notificacaoLevaAEmpresaDoTokenComoConta() {
        String idA = notify(tokenA);
        String idB = notify(tokenB);

        assertEquals(publicIdA, scalar("select account_public_id from sg_user_notification where public_id = '" + idA + "'"));
        assertEquals(publicIdB, scalar("select account_public_id from sg_user_notification where public_id = '" + idB + "'"));
    }

    /** Regra do dono: usuario nunca ve notificacao de outro usuario (mesma empresa) nem de outra empresa. */
    @Test
    void userNeverSeesAnotherUsersNotificationsInTheSameOrAnotherCompany() {
        given().header("Authorization", "Bearer " + tokenA).post("/probe/user-notifications/notify-self")
                .then().statusCode(200);
        // notificacao de OUTRO usuario da MESMA empresa A (endereco fixo do probe)
        given().header("Authorization", "Bearer " + tokenA).post("/probe/user-notifications/notify").then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenB).post("/probe/user-notifications/notify-self")
                .then().statusCode(200);

        Response a = given().header("Authorization", "Bearer " + tokenA).get("/user-notifications");
        assertEquals(200, a.statusCode(), a.body().asString());
        a.then().body("items.type.flatten().unique()", org.hamcrest.Matchers.contains("probe.self"));
        Response b = given().header("Authorization", "Bearer " + tokenB).get("/user-notifications");
        assertEquals(200, b.statusCode(), b.body().asString());
        b.then().body("items.type.flatten().unique()", org.hamcrest.Matchers.contains("probe.self"));

        String idA = a.jsonPath().getString("items[0].publicId");
        given().header("Authorization", "Bearer " + tokenB).post("/user-notifications/" + idA + "/read").then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB).delete("/user-notifications/" + idA).then().statusCode(404);
    }

    private String notify(String token) {
        Response r = given().header("Authorization", "Bearer " + token).post("/probe/user-notifications/notify");
        assertEquals(200, r.statusCode(), r.body().asString());
        return r.body().asString();
    }

    private String scalar(String sql) {
        return QuarkusTransaction.requiringNew()
                .call(() -> String.valueOf(em.createNativeQuery(sql).getSingleResult()));
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
