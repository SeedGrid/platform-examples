package br.com.seedgrid.exchangeexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.net.URL;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Fluxo da extension num host SEM security: a moeda e' cadastrada com a fonte da cotacao (simulada pelo proprio
 * exemplo), o robo busca a cotacao de hoje e ela aparece na serie. A REST administrativa da extension nao existe (404)
 * sem {@code quarkus-security}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ExchangeFlowTest {

    @TestHTTPResource("/example/rate-source")
    URL rateSource;

    @Test
    void extensionRestDoesNotExistWithoutSecurity() {
        given().when().get("/admin/currencies").then().statusCode(404);
    }

    @Test
    void fetchedRateAppearsInTheSeries() {
        given().contentType(ContentType.JSON)
                .body(Map.of("code", "EUR-BRL", "name", "Euro", "sourceUrl", rateSource.toString(),
                        "fetchCron", "0 0 23 * * ?", "timeZone", "America/Sao_Paulo",
                        "rateJsonPath", "value[0].cotacaoVenda", "quotedAtJsonPath", "value[0].dataHoraCotacao",
                        "active", true))
                .when().post("/demo/exchange/currencies").then().statusCode(201);

        given().when().get("/demo/exchange/currencies/EUR-BRL/series?days=7")
                .then().statusCode(200).body("$", hasSize(0));

        given().when().post("/demo/exchange/currencies/EUR-BRL/fetch").then().statusCode(200);

        given().when().get("/demo/exchange/currencies/EUR-BRL/series?days=7")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].rate", equalTo(5.1234f));
    }
}
