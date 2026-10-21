package br.com.seedgrid.citiesexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
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
 * O exemplo sobe, a extension migra e semeia sozinha, e a REST pronta mais o codigo do host respondem a quem
 * entrou pelo login real da {@code seedgrid-security} (admin do bootstrap, que recebe CITY_READ pela V1203).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CitiesExampleTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        // O admin do bootstrap nasce com e-mail nao confirmado; confirmamos no banco para logar (o fluxo real
        // clica no link do e-mail de boas-vindas).
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    private static io.restassured.specification.RequestSpecification asAdmin() {
        String token = given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .post("/public/auth/login").then().statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");
        return given().header("Authorization", "Bearer " + token);
    }

    @Test
    void statesOfTheCountryComeFromTheSeededMigration() {
        asAdmin().get("/cities-api/states?size=100").then().statusCode(200)
                .body("totalElements", equalTo(27));
    }

    @Test
    void citiesOfAState() {
        asAdmin().get("/cities-api/states/by-uf/SP").then().statusCode(200).body("id", equalTo(35));
        asAdmin().get("/cities-api/states/35/cities?size=1").then().statusCode(200)
                .body("totalElements", equalTo(645));
    }

    @Test
    void hostCodeUsesTheBrazilGeoFacade() {
        asAdmin().get("/api/geo/capital/sp").then().statusCode(200)
                .body("name", equalTo("São Paulo")).body("ddd", equalTo(11));
        asAdmin().get("/api/geo/search?q=sao paulo&uf=SP").then().statusCode(200).body("name", hasItem("São Paulo"));
        asAdmin().get("/cities-api/regions/sul/states").then().statusCode(200)
                .body("name", contains("Paraná", "Rio Grande do Sul", "Santa Catarina"));
    }

    @Test
    void anonymousIsRejected() {
        given().get("/cities-api/states").then().statusCode(401);
        given().get("/api/geo/capital/sp").then().statusCode(401);
    }
}
