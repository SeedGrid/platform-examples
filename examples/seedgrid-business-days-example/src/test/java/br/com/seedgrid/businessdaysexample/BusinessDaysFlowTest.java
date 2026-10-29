package br.com.seedgrid.businessdaysexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

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
 * Fluxo da extension com a security real: login, feriado nacional semeado, feriado da empresa,
 * contagem e deslocamento em dias uteis.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BusinessDaysFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        // O primeiro admin nasce com e-mail nao confirmado; confirmamos direto no banco para logar no teste.
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void withoutTokenIsRefused() {
        given().when().get("/business-days/check?date=2026-04-21").then().statusCode(401);
    }

    @Test
    void nationalHolidayIsNotABusinessDay() {
        given().header("Authorization", "Bearer " + loginAdmin())
                .when().get("/business-days/check?date=2026-04-21")
                .then().statusCode(200)
                .body("businessDay", equalTo(false))
                .body("holiday", equalTo(true));
    }

    @Test
    void companyHolidayChangesTheCountAndTheShift() {
        String token = loginAdmin();

        // Semana de 20 a 24/04/2026: terca 21 e' Tiradentes => 4 dias uteis.
        given().header("Authorization", "Bearer " + token)
                .when().get("/business-days/count?from=2026-04-20&to=2026-04-24")
                .then().statusCode(200).body("businessDays", equalTo(4));

        // Andar 2 dias uteis a partir de segunda 20: pula a terca feriado => quinta 23.
        given().header("Authorization", "Bearer " + token)
                .when().get("/business-days/add?from=2026-04-20&days=2")
                .then().statusCode(200).body("result", equalTo("2026-04-23"));

        // Feriado da empresa na quarta 22/04/2026 (so' neste ano): sai mais um dia util.
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"name\":\"Feriado da empresa\",\"month\":4,\"day\":22,\"year\":2026}")
                .when().post("/business-days/holidays")
                .then().statusCode(201);

        given().header("Authorization", "Bearer " + token)
                .when().get("/business-days/count?from=2026-04-20&to=2026-04-24")
                .then().statusCode(200).body("businessDays", equalTo(3));

        given().header("Authorization", "Bearer " + token)
                .when().get("/business-days/holidays?year=2026")
                .then().statusCode(200).body("name", hasItem("Feriado da empresa"));

        // Mesma data de novo: ja existe (409).
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"name\":\"Outro\",\"month\":4,\"day\":22,\"year\":2026}")
                .when().post("/business-days/holidays")
                .then().statusCode(409);
    }

    private String loginAdmin() {
        return given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when().post("/public/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
