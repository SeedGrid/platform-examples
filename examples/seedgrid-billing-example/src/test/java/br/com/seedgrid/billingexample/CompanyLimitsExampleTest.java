package br.com.seedgrid.billingexample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import br.com.seedgrid.billingexample.gateway.FakeGatewayResource;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * Condicoes comerciais novas do {@code PlanTerm} (Fase 5 do plano de escopo/acesso): limite de empresas ativas
 * ({@code maxCompanies}), limite de empresas em somente leitura ({@code maxReadOnlyCompanies}) e os prazos da
 * inadimplencia ({@code pastDueReadOnlyAfterDays}, {@code pastDueDisableAfterDays}).
 *
 * <p>Este exemplo e single-company por conta (o dono tem uma empresa), entao o teste cobre: (1) os quatro campos
 * no term e no trial, gravados e devolvidos pela API; (2) o {@code PlanCompanyQuotaValidator} do billing-core,
 * acionado de verdade pelo {@code POST /companies} da security: recusa com {@code 402 COMPANY_QUOTA_EXCEEDED} no
 * limite do plano e libera quando o limite e {@code 0} (sem limite). A selecao de empresas no downgrade e o
 * agendador de inadimplencia sao cobertos nos testes do proprio billing-core ({@code CompanyLimitsTest}).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CompanyLimitsExampleTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String TRIAL = "/admin/plans/trial";

    @Inject
    UserRepository userRepository;

    @Inject
    ExampleClients clients;

    private String adminToken;

    @BeforeEach
    void loginPlatformAdmin() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
        adminToken = ExampleClients.login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @AfterEach
    void resetTrial() {
        as(adminToken).body(Map.of("maxCompanies", 0, "maxReadOnlyCompanies", 0, "pastDueReadOnlyAfterDays", 7,
                "pastDueDisableAfterDays", 30)).when().patch(TRIAL).then().statusCode(200);
    }

    @Test
    void trialAndTermStoreAndReturnTheFourNewFields() {
        JsonPath patched = as(adminToken).body(Map.of("maxCompanies", 3, "maxReadOnlyCompanies", 1,
                "pastDueReadOnlyAfterDays", 5, "pastDueDisableAfterDays", 0))
                .when().patch(TRIAL).then().statusCode(200).extract().jsonPath();
        // PATCH parcial nao apaga o que ja estava
        as(adminToken).body(Map.of("trialDays", 4)).when().patch(TRIAL).then().statusCode(200);
        JsonPath read = as(adminToken).when().get(TRIAL).then().statusCode(200).extract().jsonPath();
        as(adminToken).body(Map.of("maxCompanies", -1)).when().patch(TRIAL).then().statusCode(400);

        String planId = createPlan();
        // term futuro (ainda editavel): um term ja vigente nao aceita PATCH
        Map<String, Object> body = termBody(5, 2, 3, 12);
        body.put("effectiveFrom", OffsetDateTime.now(ZoneOffset.UTC).plusDays(10).toString());
        JsonPath created = as(adminToken).body(body).when().post("/admin/plans/" + planId + "/terms")
                .then().statusCode(201).extract().jsonPath();
        String termId = created.getString("publicId");
        JsonPath edited = as(adminToken).body(Map.of("maxCompanies", 9, "pastDueDisableAfterDays", 0))
                .when().patch("/admin/plans/" + planId + "/terms/" + termId)
                .then().statusCode(200).extract().jsonPath();

        assertAll(
                () -> assertThat(patched.getInt("maxCompanies")).isEqualTo(3),
                () -> assertThat(patched.getInt("maxReadOnlyCompanies")).isEqualTo(1),
                () -> assertThat(patched.getInt("pastDueReadOnlyAfterDays")).isEqualTo(5),
                () -> assertThat(patched.getInt("pastDueDisableAfterDays")).isZero(),
                () -> assertThat(read.getInt("maxCompanies")).isEqualTo(3),
                () -> assertThat(read.getInt("pastDueReadOnlyAfterDays")).isEqualTo(5),
                () -> assertThat(read.getInt("trialDays")).isEqualTo(4),
                () -> assertThat(created.getInt("maxCompanies")).isEqualTo(5),
                () -> assertThat(created.getInt("maxReadOnlyCompanies")).isEqualTo(2),
                () -> assertThat(created.getInt("pastDueReadOnlyAfterDays")).isEqualTo(3),
                () -> assertThat(created.getInt("pastDueDisableAfterDays")).isEqualTo(12),
                // edicao parcial: o que nao veio fica; 0 grava 0 ("nao aplica o passo")
                () -> assertThat(edited.getInt("maxCompanies")).isEqualTo(9),
                () -> assertThat(edited.getInt("maxReadOnlyCompanies")).isEqualTo(2),
                () -> assertThat(edited.getInt("pastDueReadOnlyAfterDays")).isEqualTo(3),
                () -> assertThat(edited.getInt("pastDueDisableAfterDays")).isZero());
    }

    @Test
    void createCompanyIsRefusedAtThePlanLimitAndAllowedWhenTheLimitIsZero() throws Exception {
        // Plano que comporta 2 empresas ativas: o dono ja tem 1, entao cabe mais 1 e a seguinte e recusada.
        String limitedToken = subscribe(termBody(2, 0, null, null));
        as(limitedToken).body(newCompany()).when().post("/companies").then().statusCode(201);
        as(limitedToken).body(newCompany()).when().post("/companies")
                .then().statusCode(402).body("code", equalTo("COMPANY_QUOTA_EXCEEDED"));

        // maxCompanies = 0: sem limite
        String freeToken = subscribe(termBody(0, 0, null, null));
        as(freeToken).body(newCompany()).when().post("/companies").then().statusCode(201);
        as(freeToken).body(newCompany()).when().post("/companies").then().statusCode(201);
        as(freeToken).body(newCompany()).when().post("/companies").then().statusCode(201);
    }

    /** Cria o plano com o term informado, faz o cliente novo assinar (checkout + webhook) e devolve o token dele. */
    private String subscribe(Map<String, Object> term) throws Exception {
        String planId = createPlan();
        as(adminToken).body(term).when().post("/admin/plans/" + planId + "/terms").then().statusCode(201);

        String clientToken = ExampleClients.login(clients.newClientOwnerEmail(), ExampleClients.PASSWORD);
        String session = as(clientToken).body(Map.of("planId", planId)).when().post("/me/billing/checkout")
                .then().statusCode(200).extract().path("checkoutSessionId");

        // O gateway fake paga a sessao e entrega o webhook assinado ao mesmo processamento do webhook publico.
        given().when().get(FakeGatewayResource.PATH + "/checkout/" + session).then().statusCode(200)
                .body("status", equalTo("PAID"));

        as(clientToken).when().get("/me/subscription").then().statusCode(200).body("status", equalTo("ACTIVE"));
        return clientToken;
    }

    private String createPlan() {
        return as(adminToken).body(Map.of("name", "Plano " + UUID.randomUUID().toString().substring(0, 6)))
                .when().post("/admin/plans").then().statusCode(201).extract().path("publicId");
    }

    private static Map<String, Object> termBody(Integer maxCompanies, Integer maxReadOnlyCompanies,
            Integer pastDueReadOnlyAfterDays, Integer pastDueDisableAfterDays) {
        Map<String, Object> body = new HashMap<>();
        body.put("monthlyPrice", 20);
        body.put("meters", List.of());
        body.put("effectiveFrom", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString());
        body.put("maxCompanies", maxCompanies);
        body.put("maxReadOnlyCompanies", maxReadOnlyCompanies);
        body.put("pastDueReadOnlyAfterDays", pastDueReadOnlyAfterDays);
        body.put("pastDueDisableAfterDays", pastDueDisableAfterDays);
        return body;
    }

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static Map<String, Object> newCompany() {
        Map<String, Object> body = new HashMap<>();
        body.put("personType", "PJ");
        body.put("cpfCnpj", validCnpj());
        body.put("corporateName", "Nova " + UUID.randomUUID());
        body.put("tradeName", "Nova");
        return body;
    }

    private static String validCnpj() {
        int[] digits = new int[14];
        for (int i = 0; i < 12; i++) {
            digits[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[12] = checkDigit(digits, new int[] { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
        digits[13] = checkDigit(digits, new int[] { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
        StringBuilder cnpj = new StringBuilder();
        for (int d : digits) {
            cnpj.append(d);
        }
        return cnpj.toString();
    }

    private static int checkDigit(int[] digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digits[i] * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
