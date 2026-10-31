package br.com.seedgrid.realtimeexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.realtimeexample.fakepulse.FakePulseResource;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * O fluxo do exemplo, com o security de verdade (login JWT) e o Pulse falso servido pelo proprio app:
 * sem login nao ha token; logado, o token e do usuario da sessao; e o aviso disparado pelo processo chega ao Pulse
 * para aquele usuario, sem o processo conhecer o realtime.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RealtimeFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Inject
    UserRepository userRepository;

    @Inject
    FakePulseResource fakePulse;

    @BeforeEach
    void prepare() {
        // o primeiro admin nasce com o e-mail nao confirmado; confirmamos no banco para poder logar
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
        fakePulse.clear();
    }

    @Test
    void withoutLoginThereIsNoToken() {
        given().when().post("/realtime/connect").then().statusCode(401);
    }

    @Test
    void loggedInUserGetsATokenForTheirOwnSession() {
        given().header("Authorization", "Bearer " + loginAdmin())
                .when().post("/realtime/connect").then()
                .statusCode(200)
                .body("enabled", equalTo(true))
                .body("token", startsWith("pct_"))
                .body("connectUrl", startsWith("wss://"));
    }

    @Test
    void aProcessNoticeReachesPulseForTheLoggedUser() {
        String notified = given().header("Authorization", "Bearer " + loginAdmin())
                .when().post("/notices/report-finished").then()
                .statusCode(202)
                .extract().path("notified");

        Map<String, Object> message = awaitOneMessage();
        assertEquals("report", message.get("channel"));
        assertEquals("report.finished.success", message.get("event"));
        assertEquals(notified, message.get("user"), "o aviso e para o usuario da sessao");
    }

    @Test
    void noticeWithoutLoginIsRejected() {
        given().when().post("/notices/report-finished").then().statusCode(401);
    }

    /** A publicacao e assincrona: espera ela chegar ao Pulse falso. */
    private Map<String, Object> awaitOneMessage() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        List<Map<String, Object>> messages = fakePulse.received();
        while (messages.isEmpty() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            messages = fakePulse.received();
        }
        assertEquals(1, messages.size(), "esperava 1 mensagem no Pulse");
        return messages.get(0);
    }

    private String loginAdmin() {
        return given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when().post("/public/auth/login").then()
                .statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");
    }
}
