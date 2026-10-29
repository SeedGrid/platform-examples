package br.com.seedgrid.aipricingexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.net.URL;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Fluxo do robo com aprovacao humana num host SEM security: o modelo e' cadastrado, o robo le a pagina de precos do
 * proprio exemplo, propoe o preco e so' depois da aprovacao ele passa a valer. A REST administrativa da extension
 * nao existe (404) sem {@code quarkus-security}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class PriceApprovalFlowTest {

    @TestHTTPResource("/example/pricing-page")
    URL pricingPage;

    @Test
    void extensionRestDoesNotExistWithoutSecurity() {
        given().when().get("/admin/ai/models").then().statusCode(404);
    }

    @Test
    void detectedPriceOnlyValidAfterApproval() {
        given().contentType(ContentType.JSON).body(Map.of("provider", "acme", "model", "acme-small"))
                .when().post("/demo/ai-pricing/models").then().statusCode(201);

        given().contentType(ContentType.JSON).body(Map.of("sourceUrl", pricingPage.toString()))
                .when().put("/demo/ai-pricing/source").then().statusCode(200);

        // O robo le a pagina e PROPOE: o preco vigente do modelo continua vazio.
        given().when().post("/demo/ai-pricing/check")
                .then().statusCode(200)
                .body("status", equalTo("CHANGES"))
                .body("changedCount", equalTo(1));

        given().when().get("/demo/ai-pricing/models")
                .then().statusCode(200)
                .body("find { it.model == 'acme-small' }.inputPricePerMillion", nullValue());

        String proposalId = given().when().get("/demo/ai-pricing/proposals")
                .then().statusCode(200)
                .body("status", hasItem("PENDING"))
                .extract().path("[0].id");

        given().when().post("/demo/ai-pricing/proposals/" + proposalId + "/approve").then().statusCode(200);

        // Depois da aprovacao o preco vale.
        given().when().get("/demo/ai-pricing/models")
                .then().statusCode(200)
                .body("find { it.model == 'acme-small' }.inputPricePerMillion", notNullValue())
                .body("find { it.model == 'acme-small' }.outputPricePerMillion", equalTo(0.6f));
    }
}
