package br.com.seedgrid.businessdaysexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Fluxo da extension num host SEM security: feriado nacional semeado, contagem e deslocamento em dias uteis, feriado
 * proprio. A REST administrativa da extension nao existe (404) sem {@code quarkus-security}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BusinessDaysFlowTest {

    @Test
    void extensionRestDoesNotExistWithoutSecurity() {
        given().when().get("/business-days/check?date=2026-04-21").then().statusCode(404);
        given().when().get("/business-days/holidays").then().statusCode(404);
    }

    @Test
    void nationalHolidayIsNotABusinessDay() {
        given().when().get("/demo/business-days/check?date=2026-04-21")
                .then().statusCode(200)
                .body("businessDay", equalTo(false))
                .body("holiday", equalTo(true));
    }

    @Test
    void ownHolidayChangesTheCountAndTheShift() {
        // Semana de 20 a 24/04/2026: terca 21 e' Tiradentes => 4 dias uteis.
        given().when().get("/demo/business-days/count?from=2026-04-20&to=2026-04-24")
                .then().statusCode(200).body("businessDays", equalTo(4));

        // Andar 2 dias uteis a partir de segunda 20: pula a terca feriado => quinta 23.
        given().when().get("/demo/business-days/add?from=2026-04-20&days=2")
                .then().statusCode(200).body("result", equalTo("2026-04-23"));

        // Feriado proprio na quarta 22/04/2026 (so' neste ano): sai mais um dia util.
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Feriado da empresa\",\"month\":4,\"day\":22,\"year\":2026}")
                .when().post("/demo/business-days/holidays")
                .then().statusCode(201);

        given().when().get("/demo/business-days/count?from=2026-04-20&to=2026-04-24")
                .then().statusCode(200).body("businessDays", equalTo(3));

        given().when().get("/demo/business-days/holidays?year=2026")
                .then().statusCode(200).body("", hasItem("Feriado da empresa"));
    }
}
