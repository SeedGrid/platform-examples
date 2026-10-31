package br.com.seedgrid.realtimeexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Pulse FORA DO AR (porta onde nada escuta): o realtime nao derruba quem chama. O token responde 503 (a tela volta ao
 * polling) e o processo que dispara o aviso termina normalmente.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(PulseDownTest.PulseDown.class)
class PulseDownTest {

    public static class PulseDown implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("seedgrid.realtime.pulse.base-url", "http://localhost:1/fake-pulse");
        }
    }

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void confirmAdmin() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail("admin@test.com").orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void tokenIs503AndTheProcessStillFinishes() {
        String bearer = given().contentType(ContentType.JSON)
                .body("{\"login\":\"admin@test.com\",\"password\":\"Admin@12345\"}")
                .when().post("/public/auth/login").then().statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");

        given().header("Authorization", "Bearer " + bearer)
                .when().post("/realtime/connect").then().statusCode(503);

        given().header("Authorization", "Bearer " + bearer)
                .when().post("/notices/report-finished").then().statusCode(202);
    }
}
