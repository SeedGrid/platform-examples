package br.com.seedgrid.backupexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Host com security + backup + user-notifications (so as dependencias): quando o export termina, o usuario que o pediu
 * encontra o aviso na caixa dele ({@code GET /user-notifications}), com o {@code exportPublicId} no payload. O download
 * continua pela rota do proprio backup. Prova tambem que o usuario comum le a caixa sem role extra (permissao
 * {@code USER_NOTIFICATION_READ} semeada como padrao de todo usuario logado).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BackupInboxNotificationTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void finishedExportLeavesANotificationInTheRequesterInbox() throws InterruptedException {
        String token = login();

        String jobId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("format", "CSV_ZIP", "scope", "RAW"))
                .when().post("/exports").then().statusCode(201).extract().path("jobId");
        assertNotNull(jobId);

        List<Map<String, Object>> items = awaitInbox(token, jobId);

        Map<String, Object> notification = items.get(0);
        assertEquals("backup.finished", notification.get("type"));
        assertEquals(Boolean.FALSE, notification.get("read"));

        given().header("Authorization", "Bearer " + token)
                .when().post("/user-notifications/" + notification.get("publicId") + "/read")
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + token)
                .when().get("/user-notifications/unread-count").then().statusCode(200).body("count", equalTo(0));
    }

    private List<Map<String, Object>> awaitInbox(String token, String jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            List<Map<String, Object>> items = given().header("Authorization", "Bearer " + token)
                    .when().get("/user-notifications?type=backup.finished").then().statusCode(200)
                    .extract().path("items.findAll { it.payload.exportPublicId == '" + jobId + "' }");
            if (items != null && !items.isEmpty()) {
                return items;
            }
            Thread.sleep(250);
        }
        return fail("Nenhuma notificacao backup.finished para o job " + jobId + " em " + TIMEOUT);
    }

    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when().post("/public/auth/login").then().statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");
    }
}
