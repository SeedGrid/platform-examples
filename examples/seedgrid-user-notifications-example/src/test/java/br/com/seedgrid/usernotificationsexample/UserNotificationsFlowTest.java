package br.com.seedgrid.usernotificationsexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Projeto novo + a dependencia = funciona, sem propriedade nenhuma da extension: a migration roda sozinha, o host
 * notifica pelo UserNotifier e a caixa (listar, contar, ler, excluir) vem das rotas da extension.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UserNotificationsFlowTest {

    @Test
    void notifyThenReadAndDeleteThroughTheExtensionRoutes() {
        given().when().delete("/user-notifications").then().statusCode(200);

        String id = given().when().post("/demo/notify?title=Primeira").then().statusCode(201)
                .extract().path("publicId");

        given().when().get("/user-notifications/unread-count").then().statusCode(200).body("count", equalTo(1));
        given().when().get("/user-notifications").then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].publicId", equalTo(id))
                .body("items[0].title", equalTo("Primeira"))
                .body("items[0].payload.reportId", equalTo("demo-1"))
                .body("items[0].read", equalTo(false));

        given().when().post("/user-notifications/" + id + "/read").then().statusCode(204);
        given().when().get("/user-notifications/unread-count").then().body("count", equalTo(0));

        given().when().delete("/user-notifications/" + id).then().statusCode(204);
        given().when().get("/user-notifications").then().body("items", hasSize(0));
    }
}
