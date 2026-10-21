package br.com.seedgrid.logintenancyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.commons.persistence.embeddables.Contact;
import br.com.seedgrid.logintenancy.entities.SignupRequestEntity;
import br.com.seedgrid.logintenancy.repositories.SignupRequestRepository;
import br.com.seedgrid.security.services.SecretEncryptionService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Demonstra o diferencial do login-tenancy em relacao ao subdomain-tenancy: isolamento LOGICO
 * (mesmo banco/schema, escopado por company_id via CompanyRequestContext), nao fisico.
 *
 * <p>Semeia 2 signup requests (cada uma vira uma Company + usuario ROOT dela apos confirm-email),
 * loga como cada root (com exatamente 1 company escopavel, o login ja nasce escopado) e prova, via
 * HTTP real, que cada company so ve e so acessa os seus proprios /api/products.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ProductCompanyIsolationTest {

    private static final String RAW_PASSWORD = "Sup3r#Secret!";

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SignupRequestRepository signupRequestRepository;

    @Test
    void eachCompanyOnlySeesItsOwnProducts() {
        String emailA = "owner-a-" + UUID.randomUUID() + "@test.com";
        String emailB = "owner-b-" + UUID.randomUUID() + "@test.com";
        String tokenA = numericToken();
        String tokenB = numericToken();

        seedSignupRequest(emailA, cnpj(), "Empresa A", tokenA);
        seedSignupRequest(emailB, cnpj(), "Empresa B", tokenB);

        confirmSignup(tokenA);
        confirmSignup(tokenB);

        String bearerA = login(emailA);
        String bearerB = login(emailB);

        Long productAId = createProduct(bearerA, "Produto A", "SKU-A", "10.00");
        Long productBId = createProduct(bearerB, "Produto B", "SKU-B", "20.00");

        List<Integer> idsForA = listProductIds(bearerA);
        List<Integer> idsForB = listProductIds(bearerB);

        assertTrue(idsForA.contains(productAId.intValue()), "A ve o proprio produto");
        assertEquals(1, idsForA.size(), "A so ve os proprios produtos");

        assertTrue(idsForB.contains(productBId.intValue()), "B ve o proprio produto");
        assertEquals(1, idsForB.size(), "B so ve os proprios produtos");

        // Isolamento: A nao consegue ler o produto de B, mesmo sabendo o id.
        // EntityNotFoundException e mapeada pelo ApiExceptionHandler do commons como erro de
        // negocio (400), nao 404 — convencao do framework para nao vazar existencia do recurso.
        given()
                .header("Authorization", "Bearer " + bearerA)
                .when()
                .get("/api/products/" + productBId)
                .then()
                .statusCode(400);
    }

    @Transactional
    void seedSignupRequest(String email, String cnpj, String companyName, String token) {
        SignupRequestEntity signup = new SignupRequestEntity();
        signup.email = email;
        signup.name = companyName;
        signup.corporateName = companyName + " LTDA";
        signup.tradeName = companyName;
        signup.cpfCnpj = cnpj;
        signup.verificationToken = token;
        signup.passwordHash = secretEncryptionService.encrypt(RAW_PASSWORD);
        signup.legalRepresentative = Contact.builder()
                .firstName("Owner")
                .lastName(companyName)
                .email(email)
                .build();
        signupRequestRepository.persist(signup);
    }

    private void confirmSignup(String token) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token))
                .when()
                .post("/public/signup/confirm-email")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"login\":\"" + email + "\",\"password\":\"" + RAW_PASSWORD + "\"}")
                .when()
                .post("/public/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .extract().path("token");
    }

    private Long createProduct(String bearer, String name, String sku, String price) {
        return given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body(Map.of("name", name, "sku", sku, "price", price))
                .when()
                .post("/api/products")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    @SuppressWarnings("unchecked")
    private List<Integer> listProductIds(String bearer) {
        return given()
                .header("Authorization", "Bearer " + bearer)
                .when()
                .get("/api/products")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");
    }

    private String numericToken() {
        return String.format("%012d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L));
    }

    private String cnpj() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L));
    }
}
