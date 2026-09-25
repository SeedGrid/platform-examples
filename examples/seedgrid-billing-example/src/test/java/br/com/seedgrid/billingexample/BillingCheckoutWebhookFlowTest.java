package br.com.seedgrid.billingexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.seedgrid.billingexample.billing.FakeBillingProvider;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Demonstra o fluxo didatico central do exemplo: checkout -> webhook fake (assinado por HMAC) ->
 * assinatura ativa por company -> webhook de cancelamento -> assinatura inativa.
 *
 * <p>O {@link br.com.seedgrid.billingexample.billing.FakeBillingProvider} nao fala com nenhum
 * gateway real: o "pagamento confirmado" e simulado chamando o proprio endpoint publico de
 * webhook com um payload JSON sintetico (formato definido por este exemplo, ver
 * {@link br.com.seedgrid.billingexample.billing.FakeWebhookPayload}), assinado com HMAC-SHA256
 * (header {@value FakeBillingProvider#SIGNATURE_HEADER}) usando o segredo configurado em
 * {@code seedgrid.billing.fake.webhook-secret} (test: "test-fake-webhook-secret").</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingCheckoutWebhookFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String WEBHOOK_SECRET = "test-fake-webhook-secret";

    @Inject
    UserRepository userRepository;

    @Inject
    DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        // O FirstAdminBootstrapService (seedgrid-security) cria o primeiro admin com o e-mail NAO
        // confirmado; confirmamos direto no banco pra poder logar no teste (ver pegadinha #9).
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    @Order(1)
    void checkoutThenWebhookActivatesSubscriptionForCompany() throws Exception {
        String adminToken = loginAdmin();
        long companyId = findDefaultCompanyId();

        // 1. Admin (JWT) inicia um checkout para o plano PRO.
        String checkoutSessionId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "planCode", "PRO",
                        "customerEmail", ADMIN_EMAIL,
                        "successUrl", "http://localhost/success",
                        "cancelUrl", "http://localhost/cancel"))
                .when()
                .post("/billing/checkout")
                .then()
                .statusCode(200)
                .body("provider", equalTo("fake"))
                .extract().jsonPath().getString("checkoutSessionId");
        assertNotNull(checkoutSessionId);

        // 2. Simula o "pagamento confirmado": chama o proprio endpoint publico de webhook com um
        // payload sintetico CHECKOUT_COMPLETED, assinado por HMAC (sem JWT nenhum - o provider
        // valida a assinatura, nao o token).
        String providerSubscriptionId = "sub_fake_" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "evt_" + UUID.randomUUID());
        payload.put("type", "CHECKOUT_COMPLETED");
        payload.put("companyId", companyId);
        payload.put("planCode", "PRO");
        payload.put("subscriptionId", providerSubscriptionId);
        payload.put("customerId", "cus_fake_" + UUID.randomUUID());
        payload.put("checkoutSessionId", checkoutSessionId);

        postSignedWebhook(payload);

        // 3. GET /api/subscriptions/me (qualquer usuario autenticado da company) mostra o plano PRO
        // ativo.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/subscriptions/me")
                .then()
                .statusCode(200)
                .body("planCode", equalTo("PRO"))
                .body("status", equalTo("ACTIVE"))
                .body("providerSubscriptionId", equalTo(providerSubscriptionId));

        // Guarda para o proximo teste (cancelamento) via banco: nao ha estado entre testes JUnit
        // isolado por instancia, entao persistimos o id no proprio banco e recuperamos por SQL.
    }

    @Test
    @Order(2)
    void secondWebhookCancelsSubscription() throws Exception {
        String adminToken = loginAdmin();
        long companyId = findDefaultCompanyId();
        String providerSubscriptionId = findActiveSubscriptionProviderId(companyId);
        assertNotNull(providerSubscriptionId, "teste anterior deveria ter ativado uma subscription");

        // 4. Simula o cancelamento: webhook SUBSCRIPTION_CANCELED referenciando a MESMA
        // providerSubscriptionId.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "evt_" + UUID.randomUUID());
        payload.put("type", "SUBSCRIPTION_CANCELED");
        payload.put("companyId", companyId);
        payload.put("planCode", "PRO");
        payload.put("subscriptionId", providerSubscriptionId);
        payload.put("customerId", "cus_fake_cancel");

        postSignedWebhook(payload);

        // 5. GET /api/subscriptions/me agora mostra status CANCELED.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/subscriptions/me")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELED"));
    }

    @Test
    void webhookWithInvalidSignatureIsRejected() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "evt_invalid_sig");
        payload.put("type", "CHECKOUT_COMPLETED");
        payload.put("companyId", 1);
        payload.put("planCode", "PRO");
        payload.put("subscriptionId", "sub_x");
        payload.put("customerId", "cus_x");

        String body = objectMapper.writeValueAsString(payload);

        given()
                .contentType(ContentType.JSON)
                .header(FakeBillingProvider.SIGNATURE_HEADER, "assinatura-invalida")
                .body(body)
                .when()
                .post("/public/billing/webhook")
                .then()
                .statusCode(400);
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

    private void postSignedWebhook(Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        String signature = hmacHex(body);

        given()
                .contentType(ContentType.JSON)
                .header(FakeBillingProvider.SIGNATURE_HEADER, signature)
                .body(body)
                .when()
                .post("/public/billing/webhook")
                .then()
                .statusCode(200);
    }

    private String hmacHex(String body) throws NoSuchAlgorithmException, java.security.InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private long findDefaultCompanyId() throws Exception {
        try (Connection conn = dataSource.getConnection();
                var stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id FROM companies ORDER BY id LIMIT 1")) {
            if (!rs.next()) {
                throw new IllegalStateException("Nenhuma company encontrada (bootstrap nao rodou?)");
            }
            return rs.getLong(1);
        }
    }

    private String findActiveSubscriptionProviderId(long companyId) throws Exception {
        try (Connection conn = dataSource.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT provider_subscription_id FROM subscriptions WHERE company_id = ?")) {
            ps.setLong(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
