package br.com.seedgrid.notificationexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Fluxo da extension num host SEM security: o host define o evento, liga a regra no canal e-mail, publica o evento e a
 * extension enfileira e envia. A REST administrativa da extension nao existe (404) sem {@code quarkus-security}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class NotificationFlowTest {

    @Test
    void extensionRestDoesNotExistWithoutSecurity() {
        given().when().get("/admin/notifications/events").then().statusCode(404);
    }

    @Test
    void publishedEventIsQueuedAndSentByEmail() {
        given().contentType(ContentType.JSON).body(Map.of("emails", List.of("cliente@example.com")))
                .when().put("/demo/notification/rule").then().statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of("customer", "Maria"))
                .when().post("/demo/notification/orders").then().statusCode(201);

        given().when().get("/demo/notification/deliveries").then().statusCode(200)
                .body("$", hasSize(1))
                .body("recipient", contains("cliente@example.com"))
                .body("subject", contains("Novo pedido de Maria"))
                .body("status", contains("PENDING"));

        given().when().post("/demo/notification/dispatch").then().statusCode(200);

        given().when().get("/demo/notification/deliveries").then().statusCode(200)
                .body("status", contains("SENT"));
    }
}
