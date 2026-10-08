package br.com.seedgrid.billingexample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

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
 * A jornada inteira de um produto que so' configura o billing: trial com a cota do produto, bloqueio por
 * franquia e por funcionalidade, plano pago criado pelo admin, checkout, pagamento no gateway fake e acesso
 * liberado. Nenhuma linha de billing no produto alem do validador e das anotacoes.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BillingCheckoutWebhookFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Inject
    UserRepository userRepository;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void confirmBootstrapAdmin() {
        // O primeiro admin nasce com e-mail nao confirmado e troca de senha obrigatoria.
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void trialFranquiaPlanoPagoCheckoutEAcessoLiberado() throws Exception {
        String token = login();

        // 1. A conta nasce no trial, com a cota que ESTE produto definiu (V1000: 3 relatorios).
        JsonPath trial = as(token).when().get("/me/subscription").then().statusCode(200).extract().jsonPath();
        assertAll(
                () -> assertThat(trial.getBoolean("plan.isTrial")).isTrue(),
                () -> assertThat(trial.getString("planTerm.meters[0].meter")).isEqualTo("reports"),
                () -> assertThat(trial.getInt("planTerm.meters[0].includedUnits")).isEqualTo(3),
                () -> assertThat(trial.getInt("billingCycle.meters[0].includedUnits")).isEqualTo(3));

        // 2. Cada relatorio gasta uma unidade; acabou a franquia, 402 com o motivo.
        for (int i = 0; i < 3; i++) {
            as(token).when().post("/api/reports").then().statusCode(200);
        }
        String quotaCode = as(token).when().post("/api/reports").then().statusCode(402).extract().path("code");
        // 3. Funcionalidade que o trial nao libera.
        String featureCode = as(token).when().get("/api/reports/export").then().statusCode(402).extract().path("code");
        assertAll(
                () -> assertThat(quotaCode).isEqualTo("TRIAL_QUOTA_EXCEEDED"),
                () -> assertThat(featureCode).isEqualTo("FEATURE_NOT_IN_PLAN"));

        // 4. O admin (empresa da plataforma) cria o plano Pro pelos endpoints do billing: franquia e
        //    excedente do contador `reports` vao em `meters`, e o term libera a exportacao.
        String planId = as(token).body(Map.of("name", "Pro")).when().post("/admin/plans")
                .then().statusCode(201).extract().path("publicId");
        as(token).body(Map.of(
                        "monthlyPrice", 99.90,
                        "meters", List.of(Map.of("meter", "reports", "includedUnits", 100, "overagePrice", 0.50)),
                        "effectiveFrom", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                        "permissionPublicIds", List.of(permissionPublicId("REPORT_EXPORT"))))
                .when().post("/admin/plans/" + planId + "/terms")
                .then().statusCode(201);

        // 5. Checkout: o client manda so' o plano; a extension monta o resto.
        String checkoutUrl = as(token).body(Map.of("planId", planId)).when().post("/me/billing/checkout")
                .then().statusCode(200).extract().path("checkoutUrl");
        assertThat(checkoutUrl).contains(FakeGatewayResource.PATH + "/checkout/");

        // 6. "Pagar": abrir a URL do checkout no gateway fake, que entrega o webhook assinado.
        given().when().get(checkoutUrl.substring(checkoutUrl.indexOf(FakeGatewayResource.PATH)))
                .then().statusCode(200);

        // 7. Plano pago ativo, franquia nova, exportacao liberada.
        JsonPath paid = as(token).when().get("/me/subscription").then().statusCode(200).extract().jsonPath();
        assertAll(
                () -> assertThat(paid.getBoolean("plan.isTrial")).isFalse(),
                () -> assertThat(paid.getString("plan.id")).isEqualTo(planId),
                () -> assertThat(paid.getInt("billingCycle.meters[0].includedUnits")).isEqualTo(100),
                () -> as(token).when().post("/api/reports").then().statusCode(200),
                () -> as(token).when().get("/api/reports/export").then().statusCode(200));
    }

    @Test
    void termComContadorQueOProdutoNaoDeclaraERecusado() {
        String token = login();
        String planId = as(token).body(Map.of("name", "Contador errado")).when().post("/admin/plans")
                .then().statusCode(201).extract().path("publicId");

        String code = as(token).body(Map.of("monthlyPrice", 10,
                        "meters", List.of(Map.of("meter", "relatorios", "includedUnits", 10, "overagePrice", 0))))
                .when().post("/admin/plans/" + planId + "/terms")
                .then().statusCode(400).extract().path("code");

        assertThat(code).isEqualTo("INVALID_PLAN_TERM_METER");
    }

    @Test
    void webhookComAssinaturaInvalidaERecusado() {
        given().contentType(ContentType.JSON)
                .header("x-fake-signature", "assinatura-errada")
                .body("{\"eventId\":\"evt_x\",\"type\":\"CHECKOUT_COMPLETED\"}")
                .when().post("/public/billing/webhook")
                .then().statusCode(400);
    }

    private String login() {
        return given().contentType(ContentType.JSON)
                .body(Map.of("login", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .when().post("/public/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String permissionPublicId(String name) throws Exception {
        try (var conn = dataSource.getConnection();
                var ps = conn.prepareStatement("select public_id from permissions where name = ?")) {
            ps.setString(1, name);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
