package br.com.seedgrid.citiesexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/** O exemplo sobe, a extension migra e semeia sozinha, e a REST pronta mais o codigo do host respondem. */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CitiesExampleTest {

    private static io.restassured.specification.RequestSpecification asReader() {
        return given().auth().preemptive().basic("reader", "reader");
    }

    @Test
    void statesOfTheCountryComeFromTheSeededMigration() {
        asReader().get("/cities-api/states?size=100").then().statusCode(200)
                .body("totalElements", equalTo(27));
    }

    @Test
    void citiesOfAState() {
        asReader().get("/cities-api/states/by-uf/SP").then().statusCode(200).body("id", equalTo(35));
        asReader().get("/cities-api/states/35/cities?size=1").then().statusCode(200)
                .body("totalElements", equalTo(645));
    }

    @Test
    void hostCodeUsesTheBrazilGeoFacade() {
        asReader().get("/api/geo/capital/sp").then().statusCode(200)
                .body("name", equalTo("São Paulo")).body("ddd", equalTo(11));
        asReader().get("/api/geo/search?q=sao paulo&uf=SP").then().statusCode(200).body("name", hasItem("São Paulo"));
        asReader().get("/cities-api/regions/sul/states").then().statusCode(200)
                .body("name", contains("Paraná", "Rio Grande do Sul", "Santa Catarina"));
    }

    @Test
    void anonymousIsRejected() {
        given().get("/cities-api/states").then().statusCode(401);
        given().get("/api/geo/capital/sp").then().statusCode(401);
    }
}
