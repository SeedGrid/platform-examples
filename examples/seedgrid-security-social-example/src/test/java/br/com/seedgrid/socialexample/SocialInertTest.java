package br.com.seedgrid.socialexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

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
 * O exemplo sobe SEM credencial nenhuma: com a lista de provedores vazia o modulo social fica inerte (nenhum provedor,
 * nenhum tenant OIDC), e o login por senha da {@code seedgrid-security} segue funcionando.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SocialInertTest {

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
    void noProviderIsListed() {
        given().when().get("/public/auth/social/providers").then().statusCode(200).body("$", empty());
    }

    @Test
    void anyProviderLoginIsDisabled() {
        given().redirects().follow(false).when().get("/public/auth/social/google/login?returnTo=http://localhost:3000/")
                .then().statusCode(404).body("code", equalTo("SOCIAL_PROVIDER_DISABLED"));
    }

    @Test
    void passwordLoginStillWorks() {
        given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when().post("/public/auth/login")
                .then().statusCode(200).body("status", equalTo("OK"));
    }
}
