package br.com.seedgrid.auditexample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AuditLookupExamplesTest {

    @Inject
    DataSource dataSource;

    @Test
    void shouldAuditDeleteUsingInferredPublicIdForBaseEntityPanache() throws SQLException {
        String publicId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "cpf": "111.222.333-44",
                          "name": "Customer One",
                          "occupation": "Engineering",
                          "job": "Developer",
                          "birthDate": "1990-01-02",
                          "address": "Street 1",
                          "postalCode": "12345-678",
                          "city": "Sao Paulo",
                          "comment": "Customer comment",
                          "internalCode": "INT-CUSTOMER-01"
                        }
                        """)
                .when()
                .post("/customers")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("publicId");

        given()
                .when()
                .delete("/customers/{publicId}", publicId)
                .then()
                .statusCode(204);

        assertThat(findLatestBeforeState("Customer", "DELETE"))
                .contains(publicId)
                .contains("\"cpf\":\"***.***.***-**\"");
    }

    @Test
    void shouldAuditDeleteUsingInferredPublicIdForBaseEntity() throws SQLException {
        String publicId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "code": "BU-001",
                          "name": "Finance"
                        }
                        """)
                .when()
                .post("/business-units")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("publicId");

        given()
                .when()
                .delete("/business-units/{publicId}", publicId)
                .then()
                .statusCode(204);

        assertThat(findLatestBeforeState("BusinessUnit", "DELETE"))
                .contains(publicId)
                .contains("\"code\":\"BU-001\"");
    }

    @Test
    void shouldAuditDeleteUsingExplicitLookupForNonBaseEntity() throws SQLException {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "externalCode": "LEG-001",
                          "name": "Legacy product"
                        }
                        """)
                .when()
                .post("/legacy-products")
                .then()
                .statusCode(200);

        given()
                .when()
                .delete("/legacy-products/{externalCode}", "LEG-001")
                .then()
                .statusCode(204);

        assertThat(findLatestBeforeState("LegacyProduct", "DELETE"))
                .contains("\"externalCode\":\"LEG-001\"")
                .contains("\"name\":\"Legacy product\"");
    }

    private String findLatestBeforeState(String entity, String operation) throws SQLException {
        String sql = """
                select before_state
                from audit_log
                where entity = ? and operation = ?
                order by id desc
                limit 1
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            statement.setString(2, operation);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next())
                        .as("Expected audit_log row for entity %s and operation %s", entity, operation)
                        .isTrue();
                return resultSet.getString(1);
            }
        }
    }
}
