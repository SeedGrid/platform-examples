package br.com.seedgrid.citiesexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * O exemplo sobe num host SEM security, a extension migra e semeia sozinha, a consulta publica e o codigo do host
 * respondem sem token e a REST administrativa da extension nao existe (404) sem {@code quarkus-security}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CitiesExampleTest {

    @Test
    void publicQueryAnswersWithoutAnyToken() {
        given().queryParam("uf", "SP").queryParam("name", "campinas").get("/public/cities")
                .then().statusCode(200).body("name", hasItem("Campinas"));
    }

    @Test
    void hostCodeUsesTheBrazilGeoFacade() {
        given().get("/api/geo/capital/sp").then().statusCode(200)
                .body("name", equalTo("São Paulo")).body("ddd", equalTo(11));
        given().get("/api/geo/search?q=sao paulo&uf=SP").then().statusCode(200)
                .body("name", hasItem("São Paulo"));
    }

    @Test
    void adminRestDoesNotExistWithoutSecurity() {
        given().get("/cities-api/states").then().statusCode(404);
        given().get("/cities-api/states/by-uf/SP").then().statusCode(404);
    }
}
